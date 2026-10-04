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

import org.codehaus.commons.compiler.sandbox.SandboxLimits.Limit;

/**
 * Thrown by {@link SandboxExecutor#call(java.util.concurrent.Callable)} when an execution exceeded one of its {@link
 * SandboxLimits}. The result of such an execution (if any) is discarded.
 */
public
class SandboxLimitExceededException extends Exception {

    private static final long serialVersionUID = 1L;

    private final Limit   limit;
    private final boolean threadTerminated;

    SandboxLimitExceededException(Limit limit, boolean threadTerminated) {
        super(
            "Sandbox limit exceeded: "
            + limit
            + (threadTerminated ? "" : " (the executing thread did not terminate and was abandoned)")
        );
        this.limit            = limit;
        this.threadTerminated = threadTerminated;
    }

    /**
     * @return The limit that was exceeded
     */
    public Limit getLimit() { return this.limit; }

    /**
     * @return Whether the thread that executed the code has terminated; {@code false} means that the code did not
     *         react to the timeout (e.g. because it was not compiled by JANINO, or because it is stuck inside a JDK
     *         method) and still runs
     */
    public boolean isThreadTerminated() { return this.threadTerminated; }
}
