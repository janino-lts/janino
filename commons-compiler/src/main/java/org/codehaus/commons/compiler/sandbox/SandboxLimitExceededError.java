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
 * Thrown <em>inside</em> sandboxed code by {@link Guard} when a {@link SandboxLimits limit} is exceeded. It is an
 * {@link Error}, so that {@code catch (Exception e)} does not catch it. Code that catches it anyway does not gain
 * anything: the guard stays tripped and throws again on the next tick, and the {@link SandboxExecutor} reports the
 * exceeded limit as a {@link SandboxLimitExceededException} in any case.
 */
public
class SandboxLimitExceededError extends Error {

    private static final long serialVersionUID = 1L;

    private final Limit limit;

    SandboxLimitExceededError(Limit limit) {

        // Without a stack trace, because the error is thrown repeatedly once the guard has tripped.
        super("Sandbox limit exceeded: " + limit, null, false, false);
        this.limit = limit;
    }

    /**
     * @return The limit that was exceeded
     */
    public Limit getLimit() { return this.limit; }
}
