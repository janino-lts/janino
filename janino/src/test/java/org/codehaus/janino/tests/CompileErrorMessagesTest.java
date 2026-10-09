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

package org.codehaus.janino.tests;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.janino.SimpleCompiler;
import org.junit.Assert;
import org.junit.Test;

/**
 * The compile error messages that issue #31 improves. Applications and their tests match message texts, so each
 * message keeps the text that it had before, at its beginning, and an explanation is appended; the location is
 * unchanged. The only exception is the typo "Duplication access modifier". The negative tests ({@code
 * InvalidCodeTest}) still expect the old texts.
 */
public
class CompileErrorMessagesTest {

    @Test public void
    testDuplicateAccessModifier() throws Exception {
        CompileErrorMessagesTest.assertMessage(
            "Line 1, Column 25: Duplicate access modifier \"public\"",
            "public class P { public public void f() {} }"
        );
    }

    /**
     * The scanner splits "09" into the tokens "0" and "9".
     */
    @Test public void
    testInvalidOctalLiteral() throws Exception {
        CompileErrorMessagesTest.assertMessage(
            "Line 2, Column 14: ';' expected instead of '9'; digit '9' not allowed in octal literal",
            "public class P {\n    int i = 09;\n}"
        );
        CompileErrorMessagesTest.assertMessage(
            "Line 1, Column 49: ')' expected instead of '9'; digit '9' not allowed in octal literal",
            "public class P { void g(int x) {} void f() { g(09); } }"
        );
        CompileErrorMessagesTest.assertMessage(
            "Line 1, Column 31: ',' expected instead of '9'; digit '9' not allowed in octal literal",
            "public class P { int[] a = { 09, 1 }; }"
        );
        CompileErrorMessagesTest.assertMessage(
            "Line 1, Column 28: ';' expected instead of '9L'; digit '9' not allowed in octal literal",
            "public class P { long l = 09L; }"
        );
        CompileErrorMessagesTest.assertMessage(
            "Line 1, Column 27: ';' expected instead of '8'; digit '8' not allowed in octal literal",
            "public class P { int i = 08; }"
        );

        // Not directly after the "0", or not an integer literal: no explanation.
        CompileErrorMessagesTest.assertMessage(
            "Line 1, Column 28: ';' expected instead of '9'",
            "public class P { int i = 0 9; }"
        );
        CompileErrorMessagesTest.assertMessage(
            "Line 1, Column 30: ';' expected instead of '9.5'",
            "public class P { double d = 09.5; }"
        );
    }

    @Test public void
    testInitializerInInterface() throws Exception {
        CompileErrorMessagesTest.assertMessage(
            "Line 1, Column 29: IDENTIFIER expected instead of '{'; an interface cannot declare an initializer",
            "public interface I { static { } }"
        );
        CompileErrorMessagesTest.assertMessage(
            "Line 1, Column 22: IDENTIFIER expected instead of '{'; an interface cannot declare an initializer",
            "public interface I { { } }"
        );
        CompileErrorMessagesTest.assertMessage(
            "Line 1, Column 30: IDENTIFIER expected instead of '{'; an interface cannot declare an initializer",
            "public @interface A { static { } }"
        );
    }

    @Test public void
    testExplicitConstructorInvocationNotFirst() throws Exception {
        CompileErrorMessagesTest.assertMessage(
            (
                "Line 2, Column 16: Expression \"this()\" is not an rvalue; an explicit constructor invocation is only "
                + "allowed as the first statement of a constructor body"
            ),
            "public class P {\n    P() { f(); this(1); }\n    P(int x) {}\n    void f() {}\n}"
        );
        CompileErrorMessagesTest.assertMessage(
            (
                "Line 2, Column 16: Expression \"this()\" is not an rvalue; an explicit constructor invocation is only "
                + "allowed as the first statement of a constructor body"
            ),
            "public class P {\n    void f() { this(1); }\n    P(int x) {}\n}"
        );
        CompileErrorMessagesTest.assertMessage(
            (
                "Line 2, Column 16: Expression \"super()\" is not an rvalue; an explicit constructor invocation is "
                + "only allowed as the first statement of a constructor body"
            ),
            "public class P extends A {\n    P() { f(); super(1); }\n    void f() {}\n}\nclass A { A(int x) {} A() {} }"
        );
    }

    /**
     * The location is that of the class declaration, as before.
     */
    @Test public void
    testNonStaticFieldInStaticContext() throws Exception {
        CompileErrorMessagesTest.assertMessage(
            (
                "Line 1, Column 8: Expression \"P\" is not an rvalue; non-static field \"x\" cannot be referenced "
                + "from a static context"
            ),
            "public class P {\n    int x;\n    static int f() { return x; }\n}"
        );
        CompileErrorMessagesTest.assertMessage(
            (
                "Line 1, Column 8: Expression \"P\" is not an rvalue; non-static field \"x\" cannot be referenced "
                + "from a static context"
            ),
            "public class P {\n    int x;\n    static void f() { x = 1; }\n}"
        );
        CompileErrorMessagesTest.assertMessage(
            (
                "Line 1, Column 8: Expression \"P\" is not an rvalue; non-static field \"x\" cannot be referenced "
                + "from a static context"
            ),
            "public class P extends A {\n    static int f() { return x; }\n}\nclass A { int x; }"
        );
        CompileErrorMessagesTest.assertMessage(
            (
                "Line 3, Column 24: Expression \"P\" is not an rvalue; non-static field \"x\" cannot be referenced "
                + "from a static context"
            ),
            "public class P {\n    int x;\n    int f() { return P.x; }\n}"
        );
    }

    @Test public void
    testUnknownQualifiedType() throws Exception {
        CompileErrorMessagesTest.assertMessage(
            (
                "Line 1, Column 16: Line 1, Column 16: Cannot determine simple type name \"java\"; no type "
                + "\"java.foo.Bar\" found"
            ),
            "public class P {\n    java.foo.Bar x;\n}"
        );
        CompileErrorMessagesTest.assertMessage(
            (
                "Line 2, Column 16: Line 2, Column 16: Cannot determine simple type name \"java\"; no type "
                + "\"java.foo.Bar\" found"
            ),
            "public class P {\n    Object o = new java.foo.Bar();\n}"
        );
        CompileErrorMessagesTest.assertMessage(
            (
                "Line 1, Column 16: Line 1, Column 16: Cannot determine simple type name \"java\"; no type "
                + "\"java.util.Mapx.Entry\" found"
            ),
            "public class P {\n    java.util.Mapx.Entry x;\n}"
        );
    }

    /**
     * Messages that issue #31 does not change; Apache Spark's tests expect the message about the simple type name.
     */
    @Test public void
    testUnchangedMessages() throws Exception {
        CompileErrorMessagesTest.assertMessage(
            "Line 1, Column 16: Line 1, Column 16: Cannot determine simple type name \"NoSuchElementException\"",
            "public class P {\n    NoSuchElementException x;\n}"
        );
        CompileErrorMessagesTest.assertMessage(
            "Line 2, Column 16: Line 2, Column 16: Cannot determine simple type name \"Foo\"",
            "public class P {\n    Object o = new Foo();\n}"
        );
        CompileErrorMessagesTest.assertMessage(
            "Line 2, Column 33: Expression \"y\" is not an rvalue",
            "public class P {\n    int f() { int x = 0; return y; }\n}"
        );
        CompileErrorMessagesTest.assertMessage(
            "Line 3, Column 23: Instance method \"default void P.g()\" cannot be invoked in static context",
            "public class P {\n    void g() {}\n    static void f() { g(); }\n}"
        );
    }

    private static void
    assertMessage(String expected, String source) throws Exception {
        try {
            new SimpleCompiler().cook(source);
        } catch (CompileException ce) {
            Assert.assertEquals(source, expected, ce.getMessage());
            return;
        }
        Assert.fail("Compiled without error: " + source);
    }
}
