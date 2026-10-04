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

import java.lang.management.ManagementFactory;
import java.lang.reflect.Array;
import java.lang.reflect.Method;

import org.codehaus.commons.compiler.sandbox.SandboxLimits.Limit;
import org.codehaus.commons.nullanalysis.Nullable;

/**
 * Enforces the {@link SandboxLimits} of an execution by a {@link SandboxExecutor}.
 * <p>
 *   When a {@link SandboxPolicy} is set, JANINO inserts calls to {@link #tick()} at the beginning of every loop body
 *   and of every method and constructor body, wraps the dimension of one-dimensional array creations in calls to
 *   {@link #arrayLength(int, int)}, and replaces multi-dimensional array creations with calls to {@link
 *   #newArray(Class, int[])}. Sandboxed code may also call these methods itself; that is harmless.
 * </p>
 * <p>
 *   Outside of a {@link SandboxExecutor}, these methods do nothing. If the policy {@linkplain
 *   SandboxPolicy#isExecutorRequired() requires an executor}, then JANINO inserts calls to the "strict" variants
 *   {@link #tickStrict()}, {@link #arrayLengthStrict(int, int)} and {@link #newArrayStrict(Class, int[])} instead,
 *   which throw an {@link IllegalStateException} outside of a {@link SandboxExecutor}.
 * </p>
 * <p>
 *   Once a limit is exceeded, the guard stays tripped: Every subsequent call throws a {@link
 *   SandboxLimitExceededError} again, so that code cannot continue by catching the error.
 * </p>
 */
public final
class Guard {

    /**
     * The other limits are checked every that many ticks.
     */
    private static final int CHECK_INTERVAL = 1024;

    // The (approximate) sizes of a reference in an array and of the header of an array, in bytes.
    private static final int REFERENCE_SIZE    = 4;
    private static final int ARRAY_HEADER_SIZE = 16;

    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<Context>();

    private Guard() {}

    /**
     * Counts one tick and, every now and then, checks the limits of the current execution.
     *
     * @throws SandboxLimitExceededError A limit is exceeded
     */
    public static void
    tick() {
        Context c = (Context) Guard.CONTEXT.get();
        if (c != null && --c.countdown <= 0) c.check();
    }

    /**
     * Same as {@link #tick()}, but throws an {@link IllegalStateException} outside of a {@link SandboxExecutor}.
     *
     * @throws SandboxLimitExceededError A limit is exceeded
     */
    public static void
    tickStrict() {
        Context c = Guard.requireContext();
        if (--c.countdown <= 0) c.check();
    }

    /**
     * Checks whether an array of the given size fits into the remaining memory budget of the current execution.
     *
     * @param elementSize The size of one array element in bytes
     * @return            The <var>length</var>
     * @throws SandboxLimitExceededError The array would exceed the memory limit, or another limit is exceeded
     */
    public static int
    arrayLength(int length, int elementSize) {
        Context c = (Context) Guard.CONTEXT.get();
        if (c != null) c.checkArray(length, elementSize);
        return length;
    }

    /**
     * Same as {@link #arrayLength(int, int)}, but throws an {@link IllegalStateException} outside of a {@link
     * SandboxExecutor}.
     *
     * @throws SandboxLimitExceededError The array would exceed the memory limit, or another limit is exceeded
     */
    public static int
    arrayLengthStrict(int length, int elementSize) {
        Guard.requireContext().checkArray(length, elementSize);
        return length;
    }

    /**
     * Creates a multi-dimensional array, like {@link java.lang.reflect.Array#newInstance(Class, int...)}, after
     * checking whether all the arrays that are created fit into the remaining memory budget of the current execution.
     *
     * @param componentType The type of the elements of the innermost arrays that are created
     * @throws SandboxLimitExceededError The arrays would exceed the memory limit, or another limit is exceeded
     */
    public static Object
    newArray(Class<?> componentType, int[] dimensions) {
        Context c = (Context) Guard.CONTEXT.get();
        if (c != null) c.checkArrays(componentType, dimensions);
        return Array.newInstance(componentType, dimensions);
    }

    /**
     * Same as {@link #newArray(Class, int[])}, but throws an {@link IllegalStateException} outside of a {@link
     * SandboxExecutor}.
     *
     * @throws SandboxLimitExceededError The arrays would exceed the memory limit, or another limit is exceeded
     */
    public static Object
    newArrayStrict(Class<?> componentType, int[] dimensions) {
        Guard.requireContext().checkArrays(componentType, dimensions);
        return Array.newInstance(componentType, dimensions);
    }

    /**
     * @return The context of the current execution
     * @throws IllegalStateException The current thread is not executing a {@link SandboxExecutor} task
     */
    private static Context
    requireContext() {
        Context c = (Context) Guard.CONTEXT.get();
        if (c == null) throw new IllegalStateException("Sandboxed code must be executed by a SandboxExecutor");
        return c;
    }

    /**
     * Binds the <var>context</var> to the current thread.
     */
    static void
    enter(Context context) {
        context.start();
        Guard.CONTEXT.set(context);
    }

    /**
     * Unbinds the context from the current thread.
     */
    static void
    exit() { Guard.CONTEXT.remove(); }

    /**
     * The state of one execution.
     */
    static final
    class Context {

        private final SandboxLimits limits;

        // Set by "start()", i.e. in the executing thread.
        private long threadId;
        private long deadline;
        private long allocatedBytesAtStart = -1;

        // The ticks that are counted down until the next check.
        private int countdown;
        private int interval;

        // The ticks before the current interval.
        private long ticks;

        @Nullable private volatile Limit tripped;
        private volatile boolean         cancelled;

        Context(SandboxLimits limits) { this.limits = limits; }

        void
        start() {
            this.threadId = Thread.currentThread().getId();

            long timeoutNanos = this.limits.getTimeoutNanos();
            if (timeoutNanos != SandboxLimits.UNLIMITED) this.deadline = System.nanoTime() + timeoutNanos;

            if (this.limits.getMaxAllocatedBytes() != SandboxLimits.UNLIMITED) {
                this.allocatedBytesAtStart = Guard.allocatedBytes(this.threadId);
            }

            this.countdown = this.interval = this.nextInterval();
        }

        /**
         * Requests the execution to stop; the next check trips the guard.
         */
        void
        cancel() {
            this.cancelled = true;
            this.countdown = 0;
        }

        /**
         * @return The limit that the execution exceeded, or {@code null}
         */
        @Nullable Limit
        getTripped() { return this.tripped; }

        void
        check() {
            this.checkTripped();

            if (this.cancelled) this.trip(Limit.TIME);

            this.ticks    += this.interval;
            this.countdown = this.interval = this.nextInterval();

            long maxTicks = this.limits.getMaxTicks();
            if (maxTicks != SandboxLimits.UNLIMITED && this.ticks > maxTicks) this.trip(Limit.TICKS);

            if (
                this.limits.getTimeoutNanos() != SandboxLimits.UNLIMITED
                && System.nanoTime() - this.deadline >= 0
            ) this.trip(Limit.TIME);

            if (this.remainingBytes() < 0) this.trip(Limit.MEMORY);
        }

        void
        checkArray(int length, int elementSize) {
            this.checkTripped();
            if (length > 0 && (long) length * elementSize > this.remainingBytes()) this.trip(Limit.MEMORY);
        }

        void
        checkArrays(Class<?> componentType, int[] dimensions) {
            this.checkTripped();

            // Estimate the size of all arrays, level by level ("double" cannot overflow).
            double bytes  = 0;
            double arrays = 1;
            for (int i = 0; i < dimensions.length && arrays > 0; i++) {
                int length = dimensions[i];
                if (length < 0) return; // "Array.newInstance()" throws a NegativeArraySizeException.

                int elementSize = i == dimensions.length - 1 ? Guard.elementSize(componentType) : Guard.REFERENCE_SIZE;
                bytes  += arrays * (Guard.ARRAY_HEADER_SIZE + (double) length * elementSize);
                arrays *= length;
            }

            if (bytes > this.remainingBytes()) this.trip(Limit.MEMORY);
        }

        private void
        checkTripped() {
            Limit t = this.tripped;
            if (t != null) {
                this.countdown = 0;
                throw new SandboxLimitExceededError(t);
            }
        }

        private void
        trip(Limit limit) {
            if (this.tripped == null) this.tripped = limit;
            this.checkTripped();
        }

        /**
         * @return The number of ticks until the next check; the tick that exceeds the maximum number of ticks is
         *         always a check
         */
        private int
        nextInterval() {
            long maxTicks = this.limits.getMaxTicks();
            if (maxTicks == SandboxLimits.UNLIMITED) return Guard.CHECK_INTERVAL;
            return (int) Math.max(1, Math.min(Guard.CHECK_INTERVAL, maxTicks - this.ticks + 1));
        }

        /**
         * @return The number of bytes that the thread may still allocate, or {@link Long#MAX_VALUE}
         */
        private long
        remainingBytes() {
            long maxAllocatedBytes = this.limits.getMaxAllocatedBytes();
            if (maxAllocatedBytes == SandboxLimits.UNLIMITED || this.allocatedBytesAtStart == -1) return Long.MAX_VALUE;
            return maxAllocatedBytes - (Guard.allocatedBytes(this.threadId) - this.allocatedBytesAtStart);
        }
    }

    /**
     * @return The size of an array element of the given type, in bytes
     */
    private static int
    elementSize(Class<?> type) {
        if (type == boolean.class || type == byte.class) return 1;
        if (type == char.class || type == short.class)   return 2;
        if (type == long.class || type == double.class)  return 8;
        if (type == int.class || type == float.class)    return 4;
        return Guard.REFERENCE_SIZE;
    }

    /**
     * @return The number of bytes that the thread has allocated so far, or -1 if the JVM cannot measure it
     */
    static long
    allocatedBytes(long threadId) { return AllocatedBytes.get(threadId); }

    /**
     * Measures the allocated bytes with {@code com.sun.management.ThreadMXBean.getThreadAllocatedBytes(long)}, which
     * is not available on all JVMs, and is therefore accessed through reflection.
     */
    private static final
    class AllocatedBytes {

        @Nullable private static final Object THREAD_MX_BEAN;
        @Nullable private static final Method GET_THREAD_ALLOCATED_BYTES;
        static {
            Object threadMxBean            = null;
            Method getThreadAllocatedBytes = null;
            try {
                Class<?> c    = Class.forName("com.sun.management.ThreadMXBean");
                Object   tmxb = ManagementFactory.getThreadMXBean();
                if (
                    c.isInstance(tmxb)
                    && Boolean.TRUE.equals(c.getMethod("isThreadAllocatedMemorySupported").invoke(tmxb))
                    && Boolean.TRUE.equals(c.getMethod("isThreadAllocatedMemoryEnabled").invoke(tmxb))
                ) {
                    threadMxBean            = tmxb;
                    getThreadAllocatedBytes = c.getMethod("getThreadAllocatedBytes", long.class);
                }
            } catch (Exception e) {
                ;
            } catch (LinkageError le) {
                ;
            }
            THREAD_MX_BEAN             = threadMxBean;
            GET_THREAD_ALLOCATED_BYTES = getThreadAllocatedBytes;
        }

        private AllocatedBytes() {}

        static long
        get(long threadId) {
            Object tmxb = AllocatedBytes.THREAD_MX_BEAN;
            Method m    = AllocatedBytes.GET_THREAD_ALLOCATED_BYTES;
            if (tmxb == null || m == null) return -1;
            try {
                return ((Long) m.invoke(tmxb, new Object[] { Long.valueOf(threadId) })).longValue();
            } catch (Exception e) {
                return -1;
            }
        }
    }
}
