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

package org.codehaus.commons.compiler.tests;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.codehaus.commons.compiler.AbstractJavaSourceClassLoader;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.codehaus.commons.compiler.IScriptEvaluator;
import org.codehaus.commons.compiler.sandbox.SandboxExecutor;
import org.codehaus.commons.compiler.sandbox.SandboxLimitExceededException;
import org.codehaus.commons.compiler.sandbox.SandboxLimits;
import org.codehaus.commons.compiler.sandbox.SandboxLimits.Limit;
import org.codehaus.commons.compiler.sandbox.SandboxPolicy;
import org.codehaus.commons.compiler.sandbox.SandboxViolationException;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import util.TestUtil;

/**
 * Tests for the resource limits of code that is compiled with a {@link SandboxPolicy} and executed by a {@link
 * SandboxExecutor}. Only JANINO inserts the checks into the generated code; for the other compilers, only the timeout
 * applies.
 */
@RunWith(Parameterized.class) public
class SandboxLimitsTest {

    private static final SandboxPolicy POLICY = SandboxPolicy.builder()
        .include(SandboxPolicy.JAVA_LANG_BASIC)
        .include(SandboxPolicy.COLLECTIONS)
        .allowMethods("java.lang.System", "nanoTime")
        .build();

    private static final SandboxLimits TICK_LIMITS = SandboxLimits.builder()
        .timeout(30, TimeUnit.SECONDS)
        .maxTicks(1_000_000)
        .build();

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final ICompilerFactory compilerFactory;

    @Parameters(name = "CompilerFactory={0}") public static List<Object[]>
    compilerFactories() throws Exception { return TestUtil.getCompilerFactoriesForParameters(); }

    public
    SandboxLimitsTest(ICompilerFactory compilerFactory) { this.compilerFactory = compilerFactory; }

    @Test public void
    testResult() throws Exception {
        Assert.assertEquals(4950, this.execute(
            SandboxLimitsTest.TICK_LIMITS,
            "int s = 0; for (int i = 0; i < 100; i++) s += i; return s;"
        ));
    }

    @Test public void
    testLoops() throws Exception {
        this.assumeJanino();

        this.assertLimitExceeded(Limit.TICKS, SandboxLimitsTest.TICK_LIMITS, "while (true) {}");
        this.assertLimitExceeded(Limit.TICKS, SandboxLimitsTest.TICK_LIMITS, "for (;;) {}");
        this.assertLimitExceeded(Limit.TICKS, SandboxLimitsTest.TICK_LIMITS, "do {} while (true);");
        this.assertLimitExceeded(
            Limit.TICKS,
            SandboxLimitsTest.TICK_LIMITS,
            "for (int i = 0; i >= 0;) continue; return null;"
        );
        this.assertLimitExceeded(Limit.TICKS, SandboxLimitsTest.TICK_LIMITS, (
            ""
            + "Iterable endless = new Iterable() {\n"
            + "    public java.util.Iterator iterator() {\n"
            + "        return new java.util.Iterator() {\n"
            + "            public boolean hasNext() { return true; }\n"
            + "            public Object next()     { return null; }\n"
            + "        };\n"
            + "    }\n"
            + "};\n"
            + "for (Object o : endless) {}\n"
            + "return null;\n"
        ));

        // A loop in a method of an anonymous class.
        this.assertLimitExceeded(
            Limit.TICKS,
            SandboxLimitsTest.TICK_LIMITS,
            "new Runnable() { public void run() { while (true) {} } }.run(); return null;"
        );
    }

    @Test public void
    testRecursion() throws Exception {
        this.assumeJanino();

        // Every invocation ticks, so the limit is exceeded before the stack overflows.
        this.assertLimitExceeded(
            Limit.TICKS,
            SandboxLimits.builder().maxTicks(1000).build(),
            "static int f(int n) { return f(n + 1); } return f(0);"
        );
    }

    @Test public void
    testTimeout() throws Exception {
        this.assumeJanino();

        long start = System.nanoTime();
        this.assertLimitExceeded(
            Limit.TIME,
            SandboxLimits.builder().timeout(200, TimeUnit.MILLISECONDS).build(),
            "while (true) {}"
        );
        Assert.assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(10));
    }

    /**
     * Catching the error does not help: the guard stays tripped, and the executor reports the exceeded limit even if
     * the code returns normally.
     */
    @Test public void
    testCatchingTheErrorDoesNotHelp() throws Exception {
        this.assumeJanino();

        this.assertLimitExceeded(Limit.TICKS, SandboxLimitsTest.TICK_LIMITS, (
            "for (;;) { try { while (true) {} } catch (Throwable t) {} }"
        ));
        this.assertLimitExceeded(Limit.TICKS, SandboxLimitsTest.TICK_LIMITS, (
            "try { while (true) {} } catch (Throwable t) {} return 7;"
        ));
        this.assertLimitExceeded(Limit.TICKS, SandboxLimitsTest.TICK_LIMITS, (
            "int n = 0; while (true) { try { for (;;) {} } finally { if (++n > 5) return n; } }"
        ));
    }

    @Test public void
    testMemory() throws Exception {
        this.assumeJanino();
        Assume.assumeTrue("Allocated bytes cannot be measured", SandboxLimits.isMemoryLimitSupported());

        SandboxLimits limits = SandboxLimits.builder().maxAllocatedBytes(16L << 20).build();

        // The array is rejected before it is allocated.
        this.assertLimitExceeded(Limit.MEMORY, limits, "long[] a = new long[Integer.MAX_VALUE - 8]; return a.length;");
        this.assertLimitExceeded(Limit.MEMORY, limits, "int n = 100000; return new int[n][n].length;");

        this.assertLimitExceeded(Limit.MEMORY, limits, "while (true) { byte[] b = new byte[1000]; }");

        Assert.assertEquals(1000, this.execute(limits, "return new long[1000].length;"));
    }

    /**
     * The limits apply only inside the executor.
     */
    @Test public void
    testCallbackOutsideOfExecutor() throws Exception {
        this.assumeJanino();

        IScriptEvaluator se = this.compilerFactory.newScriptEvaluator();
        se.setSandboxPolicy(SandboxLimitsTest.POLICY);
        se.setReturnType(Runnable.class);
        se.cook("return new Runnable() { public void run() { for (int i = 0; i < 10000; i++); } };");

        final Runnable callback = (Runnable) se.evaluate();
        callback.run();

        try {
            new SandboxExecutor(SandboxLimits.builder().maxTicks(100).build()).call(() -> {
                callback.run();
                return null;
            });
            Assert.fail("SandboxLimitExceededException expected");
        } catch (SandboxLimitExceededException slee) {
            Assert.assertEquals(Limit.TICKS, slee.getLimit());
        }
    }

    /**
     * The code can call the guard even if the parent class loader cannot see it.
     */
    @Test public void
    testIsolatedParentClassLoader() throws Exception {
        this.assumeJanino();

        ClassLoader     platformClassLoader = ClassLoader.getSystemClassLoader().getParent();
        SandboxExecutor executor            = new SandboxExecutor(SandboxLimitsTest.TICK_LIMITS);

        final IScriptEvaluator se = this.newScriptEvaluator();
        se.setParentClassLoader(platformClassLoader);
        se.cook("while (true) {}");
        try {
            executor.call(() -> se.evaluate());
            Assert.fail("SandboxLimitExceededException expected");
        } catch (SandboxLimitExceededException slee) {
            Assert.assertEquals(Limit.TICKS, slee.getLimit());
        }

        File sourceDir = this.temporaryFolder.newFolder("src");
        File file      = new File(sourceDir, "pkg/Loop.java");
        Assert.assertTrue(file.getParentFile().mkdirs());
        Files.write(file.toPath(), (
            ""
            + "package pkg;\n"
            + "public class Loop implements Runnable {\n"
            + "    public void run() { while (true) {} }\n"
            + "}\n"
        ).getBytes(StandardCharsets.UTF_8));

        AbstractJavaSourceClassLoader jscl = this.compilerFactory.newJavaSourceClassLoader(platformClassLoader);
        jscl.setSourcePath(new File[] { sourceDir });
        jscl.setSandboxPolicy(SandboxLimitsTest.POLICY);

        final Runnable loop = (Runnable) jscl.loadClass("pkg.Loop").getConstructor().newInstance();
        try {
            executor.call(() -> {
                loop.run();
                return null;
            });
            Assert.fail("SandboxLimitExceededException expected");
        } catch (SandboxLimitExceededException slee) {
            Assert.assertEquals(Limit.TICKS, slee.getLimit());
        }
    }

    /**
     * Sandboxed code may call the checks of the guard itself, but no other members of the sandbox package.
     */
    @Test public void
    testGuardIsAllowed() throws Exception {
        Assert.assertEquals(3, this.execute(
            SandboxLimitsTest.TICK_LIMITS,
            (
                "org.codehaus.commons.compiler.sandbox.Guard.tick();\n"
                + "return org.codehaus.commons.compiler.sandbox.Guard.arrayLength(3, 4);\n"
            )
        ));

        IScriptEvaluator se = this.newScriptEvaluator();
        try {
            se.cook("return new org.codehaus.commons.compiler.sandbox.SandboxExecutor(null);");
            Assert.fail("CompileException expected");
        } catch (CompileException ce) {
            Assert.assertTrue(ce.getMessage(), ce.getMessage().contains("SandboxExecutor"));
            Assert.assertTrue(String.valueOf(ce.getCause()), ce.getCause() instanceof SandboxViolationException);
        }
    }

    /**
     * Code that does not check the limits (here: compiled by JAVAC) is subject to the timeout, but cannot be stopped.
     */
    @Test public void
    testTimeoutWithoutChecks() throws Exception {

        // Terminates by itself after two seconds.
        String script = "long end = System.nanoTime() + 2000000000L; while (System.nanoTime() < end); return 0;";

        try {
            this.execute(SandboxLimits.builder().timeout(200, TimeUnit.MILLISECONDS).build(), script);
            Assert.fail("SandboxLimitExceededException expected");
        } catch (SandboxLimitExceededException slee) {
            Assert.assertEquals(Limit.TIME, slee.getLimit());

            // Only JANINO inserts the checks, which stop the code.
            Assert.assertEquals(this.isJanino(), slee.isThreadTerminated());
        }
    }

    private Object
    execute(SandboxLimits limits, String script) throws Exception {
        final IScriptEvaluator se = this.newScriptEvaluator();
        se.cook(script);
        return new SandboxExecutor(limits).call(() -> se.evaluate());
    }

    private void
    assertLimitExceeded(Limit limit, SandboxLimits limits, String script) throws Exception {
        try {
            this.execute(limits, script);
            Assert.fail("SandboxLimitExceededException expected");
        } catch (SandboxLimitExceededException slee) {
            Assert.assertEquals(script, limit, slee.getLimit());
            Assert.assertTrue(script, slee.isThreadTerminated());
        }
    }

    private IScriptEvaluator
    newScriptEvaluator() throws Exception {
        IScriptEvaluator se = this.compilerFactory.newScriptEvaluator();
        se.setSandboxPolicy(SandboxLimitsTest.POLICY);
        se.setReturnType(Object.class);
        return se;
    }

    private boolean
    isJanino() { return "org.codehaus.janino".equals(this.compilerFactory.getId()); }

    private void
    assumeJanino() { Assume.assumeTrue("JANINO only", this.isJanino()); }
}
