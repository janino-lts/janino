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

import java.util.concurrent.TimeUnit;

/**
 * The resource limits for one execution of sandboxed code by a {@link SandboxExecutor}. Whereas a {@link
 * SandboxPolicy} determines <em>what</em> code may do, the limits determine <em>how much</em> of it.
 * <p>
 *   A {@link SandboxLimits} object is immutable; use a {@link Builder} to create one:
 * </p>
 * <pre>
 *     SandboxLimits limits = SandboxLimits.builder()
 *         .timeout(2, TimeUnit.SECONDS)
 *         .maxTicks(10_000_000)
 *         .maxAllocatedBytes(64L &lt;&lt; 20)
 *         .build();
 * </pre>
 * <p>
 *   All limits are optional; a limit that is not set is not enforced.
 * </p>
 */
public final
class SandboxLimits {

    /**
     * The kinds of resource limits.
     */
    public
    enum Limit {

        /**
         * The execution took longer than the {@link Builder#timeout(long, TimeUnit) timeout}.
         */
        TIME,

        /**
         * The code executed more loop iterations and method invocations than {@link Builder#maxTicks(long)
         * allowed}.
         */
        TICKS,

        /**
         * The executing thread allocated more memory than {@link Builder#maxAllocatedBytes(long) allowed}, or code
         * attempted to create an array that would exceed the remaining memory budget.
         */
        MEMORY
    }

    /**
     * The value of a limit that is not set.
     */
    public static final long UNLIMITED = -1;

    private final long timeoutNanos;
    private final long maxTicks;
    private final long maxAllocatedBytes;

    private
    SandboxLimits(Builder builder) {
        this.timeoutNanos      = builder.timeoutNanos;
        this.maxTicks          = builder.maxTicks;
        this.maxAllocatedBytes = builder.maxAllocatedBytes;
    }

    /**
     * @return Whether this JVM can measure the memory that a thread allocates; if not, then {@link
     *         Builder#maxAllocatedBytes(long)} is not enforced
     */
    public static boolean
    isMemoryLimitSupported() { return Guard.allocatedBytes(Thread.currentThread().getId()) != -1; }

    /**
     * @return A new builder for limits that initially limit nothing
     */
    public static Builder
    builder() { return new Builder(); }

    /**
     * @return The timeout in nanoseconds, or {@link #UNLIMITED}
     */
    public long getTimeoutNanos() { return this.timeoutNanos; }

    /**
     * @return The maximum number of ticks, or {@link #UNLIMITED}
     * @see    Builder#maxTicks(long)
     */
    public long getMaxTicks() { return this.maxTicks; }

    /**
     * @return The maximum number of bytes that the executing thread may allocate, or {@link #UNLIMITED}
     */
    public long getMaxAllocatedBytes() { return this.maxAllocatedBytes; }

    @Override public String
    toString() {
        return (
            "SandboxLimits[timeoutNanos="
            + this.timeoutNanos
            + ", maxTicks="
            + this.maxTicks
            + ", maxAllocatedBytes="
            + this.maxAllocatedBytes
            + "]"
        );
    }

    /**
     * Creates {@link SandboxLimits}.
     */
    public static final
    class Builder {

        private long timeoutNanos      = SandboxLimits.UNLIMITED;
        private long maxTicks          = SandboxLimits.UNLIMITED;
        private long maxAllocatedBytes = SandboxLimits.UNLIMITED;

        Builder() {}

        /**
         * Limits the wall-clock time of an execution.
         *
         * @param duration Must be positive
         */
        public Builder
        timeout(long duration, TimeUnit unit) {
            if (duration <= 0) throw new IllegalArgumentException("Invalid timeout " + duration);
            this.timeoutNanos = unit.toNanos(duration);
            return this;
        }

        /**
         * Limits the number of "ticks" of an execution. Code that JANINO compiles with a {@link SandboxPolicy}
         * ticks once per loop iteration and once per method or constructor invocation. This limit is
         * deterministic, i.e. independent of the speed of the machine.
         *
         * @param maxTicks Must be positive
         */
        public Builder
        maxTicks(long maxTicks) {
            if (maxTicks <= 0) throw new IllegalArgumentException("Invalid maximum number of ticks " + maxTicks);
            this.maxTicks = maxTicks;
            return this;
        }

        /**
         * Limits the number of bytes that the executing thread allocates on the heap. The allocated bytes are
         * measured with {@code com.sun.management.ThreadMXBean.getThreadAllocatedBytes(long)}; on JVMs that do not
         * support it, this limit is not enforced (see {@link SandboxLimits#isMemoryLimitSupported()}).
         *
         * @param maxAllocatedBytes Must be positive
         */
        public Builder
        maxAllocatedBytes(long maxAllocatedBytes) {
            if (maxAllocatedBytes <= 0) {
                throw new IllegalArgumentException("Invalid maximum number of bytes " + maxAllocatedBytes);
            }
            this.maxAllocatedBytes = maxAllocatedBytes;
            return this;
        }

        public SandboxLimits
        build() { return new SandboxLimits(this); }
    }
}
