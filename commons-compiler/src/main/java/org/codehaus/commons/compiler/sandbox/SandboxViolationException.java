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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Indicates that one or more classes were rejected by a {@link BytecodeVerifier}.
 */
public
class SandboxViolationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final List<SandboxViolation> violations;

    /**
     * @param violations Must not be empty
     */
    public
    SandboxViolationException(List<SandboxViolation> violations) {
        super(SandboxViolationException.message(violations));
        this.violations = Collections.unmodifiableList(new ArrayList<SandboxViolation>(violations));
    }

    public List<SandboxViolation> getViolations() { return this.violations; }

    /**
     * @return A message that lists all <var>violations</var>, one per line
     */
    public static String
    message(List<SandboxViolation> violations) {
        StringBuilder sb = new StringBuilder("Sandbox violation");
        if (violations.size() > 1) sb.append('s');
        sb.append(':');
        for (SandboxViolation v : violations) sb.append(System.lineSeparator()).append("  ").append(v);
        return sb.toString();
    }
}
