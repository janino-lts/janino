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

import java.io.Serializable;

/**
 * A reason why a {@link BytecodeVerifier} rejected a class.
 */
public final
class SandboxViolation implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String className;
    private final String message;

    SandboxViolation(String className, String message) {
        this.className = className;
        this.message   = message;
    }

    /**
     * @return The name of the rejected class, e.g. {@code "pkg.Foo$1"}
     */
    public String getClassName() { return this.className; }

    /**
     * @return A human-readable description of the violation, e.g. {@code "Access to
     *         java.lang.System.getProperty(java.lang.String) is not permitted by the sandbox policy"}
     */
    public String getMessage() { return this.message; }

    @Override public String
    toString() { return this.className + ": " + this.message; }
}
