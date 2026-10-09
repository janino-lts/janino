
/*
 * Janino - An embedded Java[TM] compiler
 *
 * Copyright (c) 2001-2018 Arno Unkrig. All rights reserved.
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

package org.codehaus.janino;

import java.util.EnumSet;

/**
 * The compilation of {@link Compiler}, {@link JavaSourceIClassLoader}, {@link SimpleCompiler} and their subclasses
 * can be configured with these options.
 * <p>
 *   The initial options of every compiler are {@link #defaultOptions()}; an explicit {@code options(...)} call
 *   replaces them.
 * </p>
 *
 * @see Compiler#options()
 * @see JavaSourceIClassLoader#options()
 * @see SimpleCompiler#options()
 */
public
enum JaninoOption {

    /**
     * Contrary to the JLS, disallow access to {@code private} members of types enclosed by the the accessing code, or
     * enclosed by the accessing code, or enclosed by the same top-level type as the accessing code.
     */
    PRIVATE_MEMBERS_OF_ENCLOSING_AND_ENCLOSED_TYPES_INACCESSIBLE,

    /**
     * Contrary to the JLS, allow <em>any</em> expression as a resource in a TRY-with-resources statement.
     */
    EXPRESSIONS_IN_TRY_WITH_RESOURCES_ALLOWED,

    /**
     * Compile like JAVAC where JANINO deviates from it on purpose (the "compliance mode").
     * <p>
     *   By default (the "compatibility mode"), code that JANINO has always accepted keeps compiling with its
     *   established behavior, even where that behavior differs from JAVAC's; applications that generate code rely on
     *   this. With this option, JANINO follows JAVAC instead, for the deviations that have been implemented so far:
     *   a {@code Boolean} operand of {@code ||} or {@code &&} is unboxed (and a {@code null} throws a {@link
     *   NullPointerException}), overload resolution considers variable arity methods only after boxing (JLS 15.12.2,
     *   phase 3), and {@code assert} statements are disabled unless assertions are enabled for the class.
     * </p>
     * <p>
     *   The compliance mode is incomplete by design: the register of deviations in {@code JAVAC_DIFFERENCES.md} is
     *   authoritative for what it does and does not do, so "compiles in the compliance mode" does not mean "is
     *   valid Java". Language features that JANINO does not implement, and everything that depends on the typing of
     *   generics, remain outside its scope. The other options apply in both modes: whoever sets them wants them.
     * </p>
     * <p>
     *   The system property {@value #JAVAC_COMPLIANCE_SYSTEM_PROPERTY} ({@code "true"}) adds this option to the
     *   initial options of every compiler that is created afterwards (see {@link #defaultOptions()}).
     * </p>
     */
    JAVAC_COMPLIANCE;

    /**
     * The name of the system property that enables {@link #JAVAC_COMPLIANCE} for all compilers in the JVM.
     */
    public static final String JAVAC_COMPLIANCE_SYSTEM_PROPERTY = "org.codehaus.janino.javacCompliance";

    /**
     * The initial options of a compiler: {@link #JAVAC_COMPLIANCE} iff the system property {@value
     * #JAVAC_COMPLIANCE_SYSTEM_PROPERTY} is {@code "true"} at the time the compiler is created, otherwise none.
     *
     * @return A new, modifiable set
     */
    public static EnumSet<JaninoOption>
    defaultOptions() {
        return (
            Boolean.getBoolean(JaninoOption.JAVAC_COMPLIANCE_SYSTEM_PROPERTY)
            ? EnumSet.of(JaninoOption.JAVAC_COMPLIANCE)
            : EnumSet.noneOf(JaninoOption.class)
        );
    }
}
