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

package org.codehaus.commons.compiler.sandbox;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.codehaus.commons.compiler.sandbox.SandboxLimits.Limit;
import org.codehaus.commons.nullanalysis.Nullable;

/**
 * Executes sandboxed code under {@link SandboxLimits}.
 * <pre>
 *     IScriptEvaluator se = ...;
 *     se.setSandboxPolicy(policy);
 *     se.cook(script);
 *
 *     SandboxExecutor executor = new SandboxExecutor(limits);
 *     Object result = executor.call(() -&gt; se.evaluate(arguments));
 * </pre>
 * <p>
 *   Each {@link #call(Callable)} executes the task on a new thread. Code that JANINO compiled with a {@link
 *   SandboxPolicy} checks the limits regularly (see {@link Guard}) and stops when one is exceeded. Code from other
 *   compilers is only subject to the timeout, and cannot be stopped: when it does not terminate after the timeout,
 *   its thread is abandoned (see {@link SandboxLimitExceededException#isThreadTerminated()}).
 * </p>
 * <p>
 *   The limits apply only to code that runs inside {@link #call(Callable)}; e.g. the instantiation of a generated
 *   class (which runs its static initializer and its constructor), and the methods of an object that the sandboxed
 *   code returns, and which the host invokes later, must also be executed through a {@link SandboxExecutor}. {@link
 *   SandboxPolicy.Builder#requireExecutor()} makes sure that this is not forgotten.
 * </p>
 */
public final
class SandboxExecutor {

    /**
     * How long to wait for the thread to terminate after the timeout.
     */
    private static final long GRACE_PERIOD_MILLIS = 500;

    private static final AtomicInteger THREAD_NUMBER = new AtomicInteger();

    private final SandboxLimits           limits;
    @Nullable private final ThreadFactory threadFactory;

    /**
     * Uses daemon threads.
     */
    public
    SandboxExecutor(SandboxLimits limits) { this(limits, null); }

    /**
     * @param threadFactory Creates the threads that execute the tasks; {@code null} means to create daemon threads
     */
    public
    SandboxExecutor(SandboxLimits limits, @Nullable ThreadFactory threadFactory) {
        this.limits        = limits;
        this.threadFactory = threadFactory;
    }

    public SandboxLimits getLimits() { return this.limits; }

    /**
     * Executes the <var>task</var> on a new thread and waits until it completes or the timeout expires.
     *
     * @return                               The result of the task
     * @throws SandboxLimitExceededException The task exceeded a limit (even if it caught the {@link
     *                                       SandboxLimitExceededError}); its result is discarded
     * @throws InterruptedException          The current thread was interrupted while waiting; the task is cancelled
     * @throws Exception                     The task threw it
     */
    public <T> T
    call(final Callable<T> task) throws Exception {

        final Guard.Context context = new Guard.Context(this.limits);

        FutureTask<T> future = new FutureTask<T>(new Callable<T>() {

            @Override public T
            call() throws Exception {
                Guard.enter(context);
                try {
                    return task.call();
                } finally {
                    Guard.exit();
                }
            }
        });

        Thread thread = this.newThread(future);
        thread.start();

        @Nullable Throwable failure;
        try {
            long timeoutNanos = this.limits.getTimeoutNanos();
            T    result       = (
                timeoutNanos == SandboxLimits.UNLIMITED
                ? future.get()
                : future.get(timeoutNanos, TimeUnit.NANOSECONDS)
            );

            // The task may have caught the "SandboxLimitExceededError".
            Limit tripped = context.getTripped();
            if (tripped != null) throw new SandboxLimitExceededException(tripped, true);

            return result;
        } catch (TimeoutException te) {
            context.cancel();
            thread.interrupt();
            thread.join(SandboxExecutor.GRACE_PERIOD_MILLIS);
            throw new SandboxLimitExceededException(Limit.TIME, !thread.isAlive());
        } catch (InterruptedException ie) {
            context.cancel();
            thread.interrupt();
            throw ie;
        } catch (ExecutionException ee) {
            failure = ee.getCause();
        }

        // The task may have wrapped the "SandboxLimitExceededError" in another exception.
        Limit tripped = context.getTripped();
        if (tripped != null) throw new SandboxLimitExceededException(tripped, true);

        if (failure instanceof Exception) throw (Exception) failure;
        if (failure instanceof Error) throw (Error) failure;
        throw new ExecutionException(failure);
    }

    private Thread
    newThread(Runnable runnable) {

        ThreadFactory tf = this.threadFactory;
        if (tf != null) return tf.newThread(runnable);

        Thread result = new Thread(runnable, "sandbox-executor-" + SandboxExecutor.THREAD_NUMBER.incrementAndGet());
        result.setDaemon(true);
        return result;
    }
}
