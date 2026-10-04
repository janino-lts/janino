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

import org.codehaus.commons.compiler.Location;
import org.codehaus.commons.nullanalysis.Nullable;

/**
 * A reason why a {@link BytecodeVerifier} rejected a class.
 * <p>
 *   If the class file contains line numbers (i.e. it was compiled with debugging information), then a violation that
 *   concerns the use of a field, method or constructor carries the source line where it is used, see {@link
 *   #getLocation()}.
 * </p>
 */
public final
class SandboxViolation implements Serializable {

    private static final long serialVersionUID = 2L;

    private final String           className;
    private final String           message;
    @Nullable private final String fileName;
    private final int              lineNumber;

    SandboxViolation(String className, String message, @Nullable String fileName, int lineNumber) {
        this.className  = className;
        this.message    = message;
        this.fileName   = fileName;
        this.lineNumber = lineNumber;
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

    /**
     * @return The name of the source file of the rejected class (as recorded in its class file), or {@code null} iff
     *         unknown
     */
    @Nullable public String getFileName() { return this.fileName; }

    /**
     * @return The source line where the violating field, method or constructor is used, or -1 iff unknown (e.g.
     *         because the class file contains no line numbers, or because the violation concerns the class as a whole,
     *         like a forbidden superclass)
     */
    public int getLineNumber() { return this.lineNumber; }

    /**
     * @return The location of the violation in the source code, or {@code null} iff the line number is unknown; the
     *         column number of the location is 0, which means "the entire line"
     */
    @Nullable public Location
    getLocation() { return this.lineNumber == -1 ? null : new Location(this.fileName, this.lineNumber, 0); }

    /**
     * Returns a copy of this violation with the given file name and line number. Compilers use this to map the
     * locations in the generated code to the locations in the document that they compiled.
     */
    public SandboxViolation
    withLocation(@Nullable String fileName, int lineNumber) {
        return new SandboxViolation(this.className, this.message, fileName, lineNumber);
    }

    /**
     * @return E.g. {@code "File 'script.txt', Line 3: Access to ... is not permitted by the sandbox policy"}, or, iff
     *         the location is unknown, {@code "pkg.Foo: Extending ... is not permitted by the sandbox policy"}
     */
    @Override public String
    toString() {
        Location location = this.getLocation();
        return (location != null ? location.toString() : this.className) + ": " + this.message;
    }
}
