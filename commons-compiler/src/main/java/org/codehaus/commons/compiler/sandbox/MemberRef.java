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

import org.codehaus.commons.nullanalysis.Nullable;

/**
 * Identifies a field, method or constructor of a class, as it is subject to a {@link SandboxPolicy}.
 * <p>
 *   The class name is a binary name in the format of {@link Class#getName()} (e.g. {@code "java.util.Map$Entry"});
 *   the descriptor is a JVM field or method descriptor (e.g. {@code "(Ljava/lang/String;)V"}).
 * </p>
 */
public final
class MemberRef {

    /**
     * The kinds of members.
     */
    public
    enum Kind { FIELD, METHOD, CONSTRUCTOR }

    private final Kind   kind;
    private final String className;
    private final String name;
    private final String descriptor;

    private
    MemberRef(Kind kind, String className, String name, String descriptor) {
        this.kind       = kind;
        this.className  = className;
        this.name       = name;
        this.descriptor = descriptor;
    }

    /**
     * @param descriptor E.g. {@code "Ljava/io/PrintStream;"}
     */
    public static MemberRef
    field(String className, String name, String descriptor) {
        return new MemberRef(Kind.FIELD, className, name, descriptor);
    }

    /**
     * @param name       The method name, or {@code "<init>"} for a constructor
     * @param descriptor E.g. {@code "(Ljava/lang/String;)V"}
     * @return           A {@link Kind#CONSTRUCTOR} reference iff the <var>name</var> is {@code "<init>"}
     */
    public static MemberRef
    method(String className, String name, String descriptor) {
        return new MemberRef("<init>".equals(name) ? Kind.CONSTRUCTOR : Kind.METHOD, className, name, descriptor);
    }

    public Kind getKind() { return this.kind; }

    public String getClassName() { return this.className; }

    public String getName() { return this.name; }

    public String getDescriptor() { return this.descriptor; }

    @Override public boolean
    equals(@Nullable Object o) {
        if (!(o instanceof MemberRef)) return false;
        MemberRef that = (MemberRef) o;
        return (
            this.kind == that.kind
            && this.className.equals(that.className)
            && this.name.equals(that.name)
            && this.descriptor.equals(that.descriptor)
        );
    }

    @Override public int
    hashCode() { return this.className.hashCode() ^ this.name.hashCode() ^ this.descriptor.hashCode(); }

    /**
     * @return E.g. {@code "java.lang.System.out:Ljava/io/PrintStream;"} or {@code
     *         "java.lang.String.valueOf(I)Ljava/lang/String;"}
     */
    @Override public String
    toString() {
        return this.className + '.' + this.name + (this.kind == Kind.FIELD ? ":" : "") + this.descriptor;
    }
}
