/*
 * Janino - An embedded Java[TM] compiler
 *
 * Copyright (c) 2026 Stefan Zobel. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *       following disclaimer.
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *       following disclaimer in the documentation and/or other materials provided with the distribution.
 *    3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote
 *       products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.codehaus.commons.compiler.sandbox.tests;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.codehaus.commons.compiler.sandbox.Guard;
import org.codehaus.commons.compiler.sandbox.SandboxExecutor;
import org.codehaus.commons.compiler.sandbox.SandboxLimitExceededError;
import org.codehaus.commons.compiler.sandbox.SandboxLimitExceededException;
import org.codehaus.commons.compiler.sandbox.SandboxLimits;
import org.codehaus.commons.compiler.sandbox.SandboxLimits.Limit;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * Tests for {@link SandboxExecutor}, {@link SandboxLimits} and {@link Guard}. The tasks call {@link Guard#tick()}
 * themselves, like code that JANINO compiles with a sandbox policy.
 */
public
class SandboxExecutorTest {

    @SuppressWarnings("static-method") @Test public void
    testResult() throws Exception {
        SandboxExecutor executor = new SandboxExecutor(SandboxLimits.builder().timeout(10, TimeUnit.SECONDS).build());

        Thread thread = executor.call(() -> Thread.currentThread());
        Assert.assertNotSame(Thread.currentThread(), thread);
        Assert.assertTrue(thread.getName(), thread.getName().startsWith("sandbox-executor-"));
        Assert.assertTrue(thread.isDaemon());

        Assert.assertEquals("x", new SandboxExecutor(SandboxLimits.builder().build()).call(() -> "x"));
    }

    @SuppressWarnings("static-method") @Test public void
    testTicks() throws Exception {
        SandboxExecutor executor = new SandboxExecutor(SandboxLimits.builder().maxTicks(10_000).build());

        // Exactly the maximum number of ticks is allowed.
        Assert.assertEquals("ok", executor.call(() -> {
            for (int i = 0; i < 10_000; i++) Guard.tick();
            return "ok";
        }));

        SandboxExecutorTest.assertLimitExceeded(Limit.TICKS, true, executor, () -> {
            for (int i = 0; i < 10_001; i++) Guard.tick();
            return "ok";
        });
        SandboxExecutorTest.assertLimitExceeded(Limit.TICKS, true, executor, () -> {
            for (;;) Guard.tick();
        });
    }

    @SuppressWarnings("static-method") @Test public void
    testTimeout() throws Exception {
        SandboxExecutor executor = new SandboxExecutor(
            SandboxLimits.builder().timeout(100, TimeUnit.MILLISECONDS).build()
        );

        long start = System.nanoTime();
        SandboxExecutorTest.assertLimitExceeded(Limit.TIME, true, executor, () -> {
            for (;;) Guard.tick();
        });
        Assert.assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(5));
    }

    /**
     * Code that does not tick cannot be stopped; its thread is abandoned.
     */
    @SuppressWarnings("static-method") @Test public void
    testTimeoutWithoutTicks() throws Exception {
        SandboxExecutor executor = new SandboxExecutor(
            SandboxLimits.builder().timeout(100, TimeUnit.MILLISECONDS).build()
        );

        // Terminates by itself after two seconds.
        SandboxExecutorTest.assertLimitExceeded(Limit.TIME, false, executor, () -> {
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (System.nanoTime() < end);
            return "done";
        });
    }

    @SuppressWarnings("static-method") @Test public void
    testMemory() throws Exception {
        Assume.assumeTrue("Allocated bytes cannot be measured", SandboxLimits.isMemoryLimitSupported());

        SandboxExecutor executor = new SandboxExecutor(SandboxLimits.builder().maxAllocatedBytes(10L << 20).build());

        SandboxExecutorTest.assertLimitExceeded(Limit.MEMORY, true, executor, () -> {
            int sum = 0;
            for (;;) {
                Guard.tick();
                sum += new byte[1024].length;
            }
        });

        // The array is too big for the remaining budget.
        SandboxExecutorTest.assertLimitExceeded(Limit.MEMORY, true, executor, () -> {
            return new long[Guard.arrayLength(2_000_000, 8)];
        });
        Assert.assertEquals(1000, executor.call(() -> new long[Guard.arrayLength(1000, 8)]).length);

        // Each of the arrays is small, but all of them together are too big.
        SandboxExecutorTest.assertLimitExceeded(Limit.MEMORY, true, executor, () -> {
            return Guard.newArray(int.class, new int[] { 100_000, 100_000 });
        });
        int[][] a = (int[][]) executor.call(() -> Guard.newArray(int.class, new int[] { 2, 3 }));
        Assert.assertEquals(2, a.length);
        Assert.assertEquals(3, a[1].length);
    }

    /**
     * Once tripped, the guard throws on every tick, so that code cannot continue by catching the error; and the
     * executor reports the exceeded limit even if the code completes normally.
     */
    @SuppressWarnings("static-method") @Test public void
    testTrippedGuardIsSticky() throws Exception {
        SandboxExecutor executor = new SandboxExecutor(SandboxLimits.builder().maxTicks(1000).build());

        final AtomicInteger thrown = new AtomicInteger();
        SandboxExecutorTest.assertLimitExceeded(Limit.TICKS, true, executor, () -> {
            try {
                for (;;) Guard.tick();
            } catch (Throwable t) {
                ;
            }
            for (int i = 0; i < 3; i++) {
                try {
                    Guard.tick();
                } catch (SandboxLimitExceededError slee) {
                    thrown.incrementAndGet();
                }
            }
            return "escaped";
        });
        Assert.assertEquals(3, thrown.get());
    }

    @SuppressWarnings("static-method") @Test public void
    testExceptionsArePassedThrough() throws Exception {
        SandboxExecutor executor = new SandboxExecutor(SandboxLimits.builder().maxTicks(1000).build());

        final IOException ioe = new IOException("x");
        try {
            executor.call(() -> { throw ioe; });
            Assert.fail();
        } catch (IOException e) {
            Assert.assertSame(ioe, e);
        }

        final AssertionError ae = new AssertionError("y");
        try {
            executor.call(() -> { throw ae; });
            Assert.fail();
        } catch (AssertionError e) {
            Assert.assertSame(ae, e);
        }
    }

    /**
     * An exceeded limit takes precedence over an exception that wraps the error.
     */
    @SuppressWarnings("static-method") @Test public void
    testWrappedError() throws Exception {
        SandboxExecutor executor = new SandboxExecutor(SandboxLimits.builder().maxTicks(1000).build());

        SandboxExecutorTest.assertLimitExceeded(Limit.TICKS, true, executor, () -> {
            try {
                for (;;) Guard.tick();
            } catch (SandboxLimitExceededError slee) {
                throw new IllegalStateException(slee);
            }
        });
    }

    @SuppressWarnings("static-method") @Test public void
    testOutsideOfExecutor() {

        // Without an executor, the guard does nothing.
        for (int i = 0; i < 100_000; i++) Guard.tick();
        Assert.assertEquals(Integer.MAX_VALUE, Guard.arrayLength(Integer.MAX_VALUE, 8));
        Assert.assertEquals(4, ((String[][]) Guard.newArray(String.class, new int[] { 4, 1 })).length);
    }

    @SuppressWarnings("static-method") @Test public void
    testThreadFactory() throws Exception {
        final AtomicReference<Thread> created = new AtomicReference<Thread>();
        SandboxExecutor executor = new SandboxExecutor(SandboxLimits.builder().build(), r -> {
            Thread t = new Thread(r, "custom");
            created.set(t);
            return t;
        });
        Assert.assertSame(executor.call(() -> Thread.currentThread()), created.get());
    }

    @SuppressWarnings("static-method") @Test public void
    testInvalidLimits() {
        try {
            SandboxLimits.builder().timeout(0, TimeUnit.SECONDS);
            Assert.fail();
        } catch (IllegalArgumentException iae) {
            ;
        }
        try {
            SandboxLimits.builder().maxTicks(-1);
            Assert.fail();
        } catch (IllegalArgumentException iae) {
            ;
        }
        try {
            SandboxLimits.builder().maxAllocatedBytes(0);
            Assert.fail();
        } catch (IllegalArgumentException iae) {
            ;
        }

        SandboxLimits limits = SandboxLimits.builder().build();
        Assert.assertEquals(SandboxLimits.UNLIMITED, limits.getTimeoutNanos());
        Assert.assertEquals(SandboxLimits.UNLIMITED, limits.getMaxTicks());
        Assert.assertEquals(SandboxLimits.UNLIMITED, limits.getMaxAllocatedBytes());
    }

    private static void
    assertLimitExceeded(Limit limit, boolean threadTerminated, SandboxExecutor executor, Callable<?> task)
    throws Exception {
        try {
            executor.call(task);
            Assert.fail("SandboxLimitExceededException expected");
        } catch (SandboxLimitExceededException slee) {
            Assert.assertEquals(limit, slee.getLimit());
            Assert.assertEquals(threadTerminated, slee.isThreadTerminated());
        }
    }
}
