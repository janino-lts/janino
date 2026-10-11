
/*
 * Janino - An embedded Java[TM] compiler
 *
 * Copyright (c) 2001-2019 Arno Unkrig. All rights reserved.
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

// SUPPRESS CHECKSTYLE JavadocMethod|MethodName:9999

package org.codehaus.commons.compiler.tests;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.codehaus.commons.compiler.IClassBodyEvaluator;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import util.CommonsCompilerTestSuite;
import util.TestUtil;

/**
 * Tests against the <a href="http://docs.oracle.com/javase/specs/">Java Language Specification, Java SE 7 Edition</a>.
 */
@RunWith(Parameterized.class) public
class JlsTest extends CommonsCompilerTestSuite {

    @Parameters(name = "{0}, {1}") public static List<Object[]>
    compilerFactories() throws Exception {
        return TestUtil.getCompilerFactoriesAndModesForParameters();
    }

    public
    JlsTest(ICompilerFactory compilerFactory, String mode) throws Exception {
        super(compilerFactory, mode);
    }

    @SuppressWarnings("static-method")
    @Before public void
    setUp() throws Exception {

        // Optionally print class file disassemblies to the console.
        if (Boolean.getBoolean("disasm")) {
            Logger scl = Logger.getLogger("org.codehaus.janino.UnitCompiler");
            for (Handler h : scl.getHandlers()) h.setLevel(Level.FINEST);
            scl.setLevel(Level.FINEST);
        }
    }

    @Test public void
    test_3__Lexical_Structure() throws Exception {
        // 3.1. Lexical Structure -- Unicode
        this.assertExpressionEvaluatesTrue("'\\u00e4' == '\u00e4'");

        // 3.2. Lexical Structure -- Translations
        this.assertScriptUncookable("3--4");

        // 3.3. Lexical Structure -- Unicode Escapes
        this.assertExpressionUncookable("aaa\\u123gbbb");
        this.assertExpressionEvaluatesTrue("\"\\u0041\".equals(\"A\")");
        this.assertExpressionEvaluatesTrue("\"\\uu0041\".equals(\"A\")");
        this.assertExpressionEvaluatesTrue("\"\\uuu0041\".equals(\"A\")");
        this.assertExpressionEvaluatesTrue("\"\\\\u0041\".equals(\"\\\\\" + \"u0041\")");
        this.assertExpressionEvaluatesTrue("\"\\\\\\u0041\".equals(\"\\\\\" + \"A\")");

        // 3.3. Lexical Structure -- Line Terminators
        this.assertExpressionEvaluatesTrue("1//\r+//\r\n2//\n==//\n\r3");

        // 3.6. Lexical Structure -- White Space
        this.assertExpressionEvaluatesTrue("3\t\r \n==3");

        // 3.7. Lexical Structure -- Comments
        this.assertExpressionEvaluatesTrue("7/* */==7");
        this.assertExpressionEvaluatesTrue("7/**/==7");
        this.assertExpressionEvaluatesTrue("7/***/==7");
        this.assertExpressionUncookable("7/*/==7");
        this.assertExpressionEvaluatesTrue("7/*\r*/==7");
        this.assertExpressionEvaluatesTrue("7//\r==7");
        this.assertExpressionEvaluatesTrue("7//\n==7");
        this.assertExpressionEvaluatesTrue("7//\r\n==7");
        this.assertExpressionEvaluatesTrue("7//\n\r==7");
        this.assertScriptUncookable("7// /*\n\rXXX*/==7");

        // 3.8. Lexical Structure -- Identifiers
        this.assertScriptExecutable("int a;");
        this.assertScriptExecutable("int \u00e4\u00e4\u00e4;");
        this.assertScriptExecutable("int \\u0391;"); // Greek alpha
        this.assertScriptExecutable("int _aaa;");
        this.assertScriptExecutable("int $aaa;");
        this.assertScriptUncookable("int 9aaa;");
        this.assertScriptUncookable("int const;");
    }

    @Test public void
    test_3_10_1__Integer_Literals_decimal() throws Exception {
        this.assertExpressionEvaluatesTrue("17 == 17L");
    }

    @Test public void
    test_3_10_1__Integer_Literals_hex() throws Exception {
        this.assertExpressionEvaluatesTrue("255 == 0xFFl");
    }

    @Test public void
    test_3_10_1__Integer_Literals_octal() throws Exception {
        this.assertExpressionEvaluatesTrue("17 == 021L");
        this.assertExpressionUncookable(
            "17 == 029",
            (
                "Digit '9' not allowed in octal literal"
                + "|compiler.err.int.number.too.large"
                + "|';' expected"
                + "|illegal digit in an octal literal"
            )
        );
    }

    @Test public void
    test_3_10_1__Integer_Literals_int_range() throws Exception {
        this.assertExpressionEvaluatesTrue("2 * 2147483647 == -2");
        this.assertExpressionEvaluatesTrue("2 * -2147483648 == 0");
        this.assertExpressionUncookable("2147483648");
        this.assertExpressionEvaluatable("-2147483648");
        this.assertExpressionEvaluatesTrue("-1 == 0xffffffff");
        this.assertExpressionEvaluatesTrue("1 == -0xffffffff");
        this.assertExpressionEvaluatesTrue("-0xf == -15");

        // https://github.com/janino-compiler/janino/issues/41 :
        this.assertExpressionEvaluatesTrue("-(-2147483648) == -2147483648");
        this.assertExpressionEvaluatesTrue("- -2147483648  == -2147483648");

        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION >= 7) {
            this.assertExpressionEvaluatesTrue("- -2147_483648  == -2147483648");
        }
    }

    @Test public void
    test_3_10_1__Integer_Literals_long_range() throws Exception {
        this.assertExpressionEvaluatable("9223372036854775807L");
        this.assertExpressionUncookable("9223372036854775808L");
        this.assertExpressionUncookable("9223372036854775809L");
        this.assertExpressionUncookable("99999999999999999999999999999L");
        this.assertExpressionEvaluatable("-9223372036854775808L");
        this.assertExpressionUncookable("-9223372036854775809L");

        // https://github.com/janino-compiler/janino/issues/41 :
        this.assertExpressionEvaluatesTrue("-(-9223372036854775808L) == -9223372036854775808L");
        this.assertExpressionEvaluatesTrue("- -9223372036854775808L  == -9223372036854775808L");
        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION >= 7) {
            this.assertExpressionEvaluatesTrue("- -922337_2036854775808L == -9223372036854775808L");
            this.assertExpressionUncookable("- -9223372036854775808_L == -9223372036854775808L");
        }
    }

    @Test public void
    test_3_10_1__Integer_Literals_binary() throws Exception {

        Assume.assumeFalse(this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 7);

        this.assertExpressionEvaluatable("0b111");
        this.assertExpressionEvaluatesTrue("0b111 == 7");
        this.assertExpressionEvaluatesTrue("0b0000111 == 7");
        this.assertExpressionEvaluatesTrue("0b00 == 0");
        this.assertExpressionEvaluatesTrue("0b1111111111111111111111111111111 == 0x7fffffff");
        this.assertExpressionEvaluatesTrue("0b10000000000000000000000000000000 == 0x80000000");
        this.assertExpressionEvaluatesTrue("0b11111111111111111111111111111111 == 0xffffffff");
        this.assertExpressionUncookable("0b100000000000000000000000000000000");
        this.assertExpressionEvaluatesTrue("-0b1111111111111111111111111111111 == 0x80000001");
        this.assertExpressionEvaluatesTrue("-0b10000000000000000000000000000000 == 0x80000000");
        this.assertExpressionEvaluatesTrue("-0b11111111111111111111111111111111 == 1");
        this.assertExpressionUncookable("-0b100000000000000000000000000000000");
        this.assertExpressionEvaluatable("0b111111111111111111111111111111111111111111111111111111111111111L");
        this.assertExpressionEvaluatable("0b1000000000000000000000000000000000000000000000000000000000000000L");
        this.assertExpressionEvaluatable("0b1111111111111111111111111111111111111111111111111111111111111111L");
        this.assertExpressionUncookable("0b10000000000000000000000000000000000000000000000000000000000000000L");
    }

    @Test public void
    test_3_10_1__Integer_Literals_underscores() throws Exception {

        Assume.assumeFalse(this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 7);

        this.assertExpressionEvaluatesTrue("1_23 == 12_3");
        this.assertExpressionEvaluatesTrue("1__3 == 13");
        this.assertExpressionUncookable("_13 == 13"); // Leading underscor not allowed
        this.assertExpressionUncookable("13_ == 13"); // Trailing underscore not allowed
        this.assertExpressionEvaluatesTrue("1_23L == 12_3L");
        this.assertExpressionEvaluatesTrue("1__3L == 13L");
        this.assertExpressionUncookable("_13L == 13L"); // Leading underscor not allowed
        this.assertExpressionUncookable("13_L == 13L"); // Trailing underscore not allowed
    }

    @Test public void
    test_3_10_2__Floating_Point_Literals_float() throws Exception {
        this.assertExpressionEvaluatesTrue("1e1f == 10f");
        this.assertExpressionEvaluatesTrue("1E1F == 10f");
        this.assertExpressionEvaluatesTrue(".3f == 0.3f");
        this.assertExpressionEvaluatesTrue("0f == (float) 0");
        this.assertExpressionEvaluatable("3.14f");
        this.assertExpressionEvaluatable("3.40282347e+38f");
        this.assertExpressionUncookable("3.40282357e+38f");
        this.assertExpressionEvaluatable("1.40239846e-45f");
        this.assertExpressionUncookable("7.0e-46f");
    }

    @Test public void
    test_3_10_2__Floating_Point_Literals_double() throws Exception {
        this.assertExpressionEvaluatable("1.79769313486231570e+308D");
        this.assertExpressionUncookable("1.79769313486231581e+308d");
        this.assertExpressionEvaluatable("4.94065645841246544e-324D");
        this.assertExpressionUncookable("2e-324D");
    }

    /**
     * Hex float literals, JLS8 3.10.2
     */
    @Test public void
    test_3_10_2__Floating_Point_Literals_hexadecimal() throws Exception {
        this.assertExpressionEvaluatesTrue("0x1D           == 29"); // "D" is NOT a float type suffix, but a hex digit!
        this.assertExpressionEvaluatesTrue("0x1p0D         == 1");
        this.assertExpressionEvaluatesTrue("0x.8p0         == 0.5");
        this.assertExpressionEvaluatesTrue("0x1p0          == 1");
        this.assertExpressionEvaluatesTrue("0xfp1          == 30");
        this.assertExpressionEvaluatesTrue("0xfp+1         == 30");
        this.assertExpressionEvaluatesTrue("0xfp-1         == 7.5");
        this.assertExpressionEvaluatesTrue("0x1.0004p0F    == 0x1.0004p0");
        this.assertExpressionEvaluatesTrue("0x1.0000004p0F != 0x1.0000004p0");
    }

    @Test public void
    test_3_10_2__Floating_Point_Literals_underscores() throws Exception {

        Assume.assumeFalse(this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 7);

        this.assertExpressionEvaluatesTrue("1___0.1___0 == 10.1");
    }

    @Test public void
    test_3_10_3__Boolean_Literals() throws Exception {
        this.assertExpressionEvaluatesTrue("true");
        this.assertExpressionEvaluatesTrue("! false");
    }

    @Test public void
    test_3_10_4__Character_Literals() throws Exception {
        this.assertExpressionEvaluatesTrue("'a' == 97");
        this.assertExpressionUncookable("'''");
        this.assertExpressionUncookable("'\\'");
        this.assertExpressionUncookable("'\n'");
        this.assertExpressionUncookable("'ax'");
        this.assertExpressionUncookable("'a\n'");
        this.assertExpressionEvaluatesTrue("'\"' == 34"); // Unescaped double quote is allowed!
    }

    @Test public void
    test_3_10_5__String_Literals() throws Exception {
        this.assertExpressionEvaluatesTrue("\"'\".charAt(0) == 39"); // Unescaped single quote is allowed!
        // Escape sequences already tested above for character literals.
        this.assertExpressionEvaluatesTrue("\"\\b\".charAt(0) == 8");
        this.assertExpressionUncookable("\"aaa\nbbb\"");
        this.assertExpressionUncookable("\"aaa\rbbb\"");
        this.assertExpressionEvaluatesTrue("\"aaa\" == \"aaa\"");
        this.assertExpressionEvaluatesTrue("\"aaa\" != \"bbb\"");
    }

    @Test public void
    test_3_10_6__Text_Blocks_1() throws Exception {
        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 15) return;
        this.assertExpressionUncookable("\"\"\"    a"); // No non-space allowed between leading """ and line break;
    }

    @Test public void
    test_3_10_6__Text_Blocks_2() throws Exception {
        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 15) return;
        Assert.assertEquals("abc", this.evaluateExpression("\"\"\"   \r   abc   \"\"\""));
    }

    @Test public void
    test_3_10_6__Text_Blocks_3() throws Exception {
        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 15) return;
        Assert.assertEquals(
            "Zeile 1\nZeile 2\nZeile 3\n",
            this.evaluateExpression(
                ""
                + "   \"\"\"      \n"
                + "   Zeile 1     \r"    // <= Three leading spaces
                + "\t\t\tZeile 2   \n"   // <= Three leading TABs
                + " \t Zeile 3\r\n"      // <= Leading Space-TAB-Space
                + "\t \t    \"\"\"     " // <= Leading TAB-Space-TAB
            )
        );
    }

    @Test public void
    test_3_10_6__Text_Blocks_4() throws Exception {
        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 15) return;
        Assert.assertEquals(
            "          Zeile 1\n\tZeile 2\n  \t   \tZeile 3\n",
            this.evaluateExpression(
                ""
                + "\"\"\"      \r\n"
                + "            Zeile 1     \r" // <= 12 leading SPACEs
                + "\t\t\tZeile 2   \n"         // <= 3 leading TABs
                + " \t  \t   \tZeile 3\r\n"    // <= Combination of 9 leading TABs and SPACEs
                + "  \"\"\"     "              // Two leading SPACEs
            )
        );
    }

    @Test public void
    test_3_10_7__Escape_sequences_for_character_and_string_literals() throws Exception {
        this.assertExpressionUncookable("'\\u000a'"); // 0x000a is LF
        this.assertExpressionEvaluatesTrue("'\\b' == 8");
        this.assertExpressionEvaluatesTrue("'\\t' == 9");
        this.assertExpressionEvaluatesTrue("'\\n' == 10");
        this.assertExpressionEvaluatesTrue("'\\f' == 12");
        this.assertExpressionEvaluatesTrue("'\\r' == 13");
        this.assertExpressionEvaluatesTrue("'\\\"' == 34");
        this.assertExpressionEvaluatesTrue("'\\'' == 39");
        this.assertExpressionEvaluatesTrue("'\\\\' == 92");
        this.assertExpressionEvaluatesTrue("'\\0' == 0");
        this.assertExpressionEvaluatesTrue("'\\07' == 7");
        this.assertExpressionEvaluatesTrue("'\\377' == 255");
        this.assertExpressionUncookable("'\\400'");
        this.assertExpressionUncookable("'\\1234'");
    }

    @Test public void
    test_3_10_8__The_null_literal() throws Exception {
        this.assertExpressionEvaluatable("null");
    }

    @Test public void
    test_3_11__Separators() throws Exception {
        this.assertScriptExecutable(";");
    }

    @Test public void
    test_3_12__Operators() throws Exception {
        this.assertScriptReturnsTrue("int a = -11; a >>>= 2; return a == 1073741821;");
    }

    @Ignore @Test public void
    test_4_5__Parameterized_Types() throws Exception {
        this.assertScriptReturnsTrue("import java.util.*; Map<String, String> s = /*Collections.emptyMap()*/null; String x = s.get(\"foo\"); return x ==null;");
    }

    @Test public void
    test_4_5_1__Type_arguments_and_wildcards() throws Exception {
        this.assertScriptReturnsTrue(
            ""
            + "import java.util.*;\n"
            + "final List<String> l = new ArrayList();\n"
            + "l.add(\"x\");\n"
            + "final Iterator<String> it = l.iterator();\n"
            + "return it.hasNext() && \"x\".equals(it.next()) && !it.hasNext();"
        );
    }

    /**
     * 4.12.4 {@code final} Variables: only a variable of a primitive type or of type {@link String} can be a constant
     * variable; see <a href="https://github.com/janino-lts/janino/issues/45">issue #45</a>.
     */
    @Test public void
    test_4_12_4__Constant_variables() throws Exception {

        // Fields of type "char".
        this.assertClassBodyMainReturnsTrue(
            ""
            + "static final char C1 = 'a';\n"
            + "static final char C2 = Character.MAX_VALUE;\n"
            + "static final char C3 = (short) 97;\n"
            + "static final char C4 = 97;\n"
            + "static class H { final char c = 'b'; }\n"
            + "public static boolean main() {\n"
            + "    int x = 0;\n"
            + "    switch ('a') { case C1: x = 1; }\n"
            + "    return (\"\" + C1 + C3 + C4 + new H().c).equals(\"aaab\") && C2 == 65535 && x == 1;\n"
            + "}\n"
        );

        // Fields of reference types with constant initializers are not constant variables.
        this.assertClassBodyMainReturnsTrue(
            ""
            + "static final Integer      I = 5;\n"
            + "static final Long         L = 5L;\n"
            + "static final Boolean      Z = true;\n"
            + "static final Byte         B = 5;\n"
            + "static final Short        S = 7;\n"
            + "static final Character    C = 'a';\n"
            + "static final Object       O1 = 5;\n"
            + "static final Object       O2 = \"x\";\n"
            + "static final CharSequence CS = \"x\";\n"
            + "static class H { final Integer i = 5; final Object o = \"y\"; }\n"
            + "public static boolean main() {\n"
            + "    H h = new H();\n"
            + "    return (\n"
            + "        I == Integer.valueOf(5) && L == 5L && Z && B == 5 && S == 7 && C == 'a'\n"
            + "        && O1.equals(Integer.valueOf(5)) && O2.equals(\"x\") && CS.equals(\"x\")\n"
            + "        && h.i == Integer.valueOf(5) && h.o.equals(\"y\")\n"
            + "        && (\"\" + I + C + O2).equals(\"5ax\")\n"
            + "    );\n"
            + "}\n"
        );

        // Fields of interfaces are implicitly "static final".
        this.assertClassBodyMainReturnsTrue(
            ""
            + "interface I { Integer X = 5; char C = 'a'; Object O = \"x\"; }\n"
            + "public static boolean main() { return I.X == 5 && I.C == 'a' && I.O.equals(\"x\"); }\n"
        );

        // Unchanged: "null" initializers, and constant variables of type "String".
        this.assertClassBodyMainReturnsTrue(
            ""
            + "static final Object O = null;\n"
            + "static final String N = null;\n"
            + "static final String S = \"x\";\n"
            + "public static boolean main() {\n"
            + "    int x = 0;\n"
            + "    switch (\"x\") { case S: x = 1; }\n"
            + "    return (\n"
            + "        O == null && N == null && (\"\" + O).equals(\"null\") && (\"\" + N).equals(\"null\") && x == 1\n"
            + "    );\n"
            + "}\n"
        );
    }

    /**
     * 5.1.3 Narrowing Primitive Conversion, and 5.1.4 Widening and Narrowing Primitive Conversion ({@code byte} to
     * {@code char}); see <a href="https://github.com/janino-lts/janino/issues/37">issue #37</a>.
     */
    @Test public void
    test_5_1_3__Narrowing_primitive_conversion() throws Exception {

        // To "char".
        this.assertScriptReturnsTrue("byte   b = -1;    return (int) (char) b == 65535;");
        this.assertScriptReturnsTrue("short  s = -1;    return (int) (char) s == 65535;");
        this.assertScriptReturnsTrue("int    i = -1;    return (int) (char) i == 65535;");
        this.assertScriptReturnsTrue("long   l = -1L;   return (int) (char) l == 65535;");
        this.assertScriptReturnsTrue("float  f = -1F;   return (int) (char) f == 65535;");
        this.assertScriptReturnsTrue("double d = -1D;   return (int) (char) d == 65535;");
        this.assertScriptReturnsTrue("long   l = 65537; return (char) l == 1;");
        this.assertScriptReturnsTrue("byte   b = -128;  Object o = (char) b; return o.equals((char) 0xFF80);");

        // From "char".
        this.assertScriptReturnsTrue("char c = 65535;  return (int) (short) c == -1;");
        this.assertScriptReturnsTrue("char c = 0x8000; return (short) c == Short.MIN_VALUE;");
        this.assertScriptReturnsTrue("char c = 0xFF80; return (byte) c == -128;");

        // Other narrowing conversions.
        this.assertScriptReturnsTrue("int    i = 0x18000;      return (short) i == Short.MIN_VALUE;");
        this.assertScriptReturnsTrue("long   l = 0x100000080L; return (short) l == 128 && (byte) l == -128;");
        this.assertScriptReturnsTrue("float  f = 200.5F;       return (byte) f == -56 && (short) f == 200;");
        this.assertScriptReturnsTrue("double d = -129.5D;      return (byte) d == 127 && (int) d == -129;");
    }

    @Test public void
    test_5_1_7__Boxing_conversion() throws Exception {
        this.assertScriptReturnsTrue("Boolean   b = true;        return b.booleanValue();");
        this.assertScriptReturnsTrue("Boolean   b = false;       return !b.booleanValue();");
        this.assertScriptReturnsTrue("Byte      b = (byte) 7;    return b.equals(new Byte((byte) 7));");
        this.assertScriptReturnsTrue("Character c = 'X';         return c.equals(new Character('X'));");
        this.assertScriptReturnsTrue("Short     s = (short) 322; return s.equals(new Short((short) 322));");
        this.assertScriptReturnsTrue("Integer   i = 99;          return i.equals(new Integer(99));");
        this.assertScriptReturnsTrue("Long      l = 733L;        return l.equals(new Long(733L));");
        this.assertScriptReturnsTrue("Float     f = 12.5F;       return f.equals(new Float(12.5F));");
        this.assertScriptReturnsTrue("Double    d = 14.3D;       return d.equals(new Double(14.3D));");
    }

    @Test public void
    test_5_1_8__Unboxing_conversion() throws Exception {
        this.assertExpressionEvaluatesTrue("Boolean.TRUE");
        this.assertExpressionEvaluatesTrue("!Boolean.FALSE");
        this.assertExpressionEvaluatesTrue("new Byte((byte) 9) == (byte) 9");
        this.assertExpressionEvaluatesTrue("new Character('Y') == 'Y'");
        this.assertExpressionEvaluatesTrue("new Short((short) 33) == (short) 33");
        this.assertExpressionEvaluatesTrue("new Integer(-444) == -444");
        this.assertExpressionEvaluatesTrue("new Long(987654321L) == 987654321L");
        this.assertExpressionEvaluatesTrue("new Float(33.3F) == 33.3F");
        this.assertExpressionEvaluatesTrue("new Double(939.939D) == 939.939D");
    }

    @Test public void
    test_5_2__Assignment_conversion() throws Exception {
        this.assertScriptReturnsTrue("int i = 7; return i == 7;");
        this.assertScriptReturnsTrue("String s = \"S\"; return s.equals(\"S\");");
        this.assertScriptReturnsTrue("long l = 7; return l == 7L;");
        this.assertScriptReturnsTrue("Object o = \"A\"; return o.equals(\"A\");");
        this.assertScriptReturnsTrue("Integer i = 7; return i.intValue() == 7;");
        this.assertScriptReturnsTrue("Object o = 7; return o.equals(new Integer(7));");
        this.assertScriptReturnsTrue("int i = new Integer(7); return i == 7;");
        this.assertScriptReturnsTrue("long l = new Integer(7); return l == 7L;");
        this.assertScriptExecutable("byte b = -128;");
        this.assertScriptUncookable("byte b = 128;");
        this.assertScriptExecutable("short s = -32768;");
        this.assertScriptUncookable("short s = 32768;");
        this.assertScriptUncookable("char c = -1;");
        this.assertScriptExecutable("char c = 0;");
        this.assertScriptExecutable("char c = 65535;");
        this.assertScriptUncookable("char c = 65536;");
        this.assertScriptExecutable("Byte b = -128;");
        this.assertScriptUncookable("Byte b = 128;");
        this.assertScriptExecutable("Short s = -32768;");
        this.assertScriptUncookable("Short s = 32768;");
        this.assertScriptUncookable("Character c = -1;");
        this.assertScriptExecutable("Character c = 0;");
        this.assertScriptExecutable("Character c = 65535;");
        this.assertScriptUncookable("Character c = 65536;");
    }

    @Test public void
    test_5_5__Casting_conversion() throws Exception {
        this.assertExpressionEvaluatesTrue("7 == (int) 7");
        this.assertExpressionEvaluatesTrue("(int) 'a' == 97");
        this.assertExpressionEvaluatesTrue("(int) 10000000000L == 1410065408");
        this.assertExpressionEvaluatesTrue("((Object) \"SS\").equals(\"SS\")");
        this.assertScriptReturnsTrue("Object o = \"SS\"; return ((String) o).length() == 2;");
        this.assertExpressionEvaluatesTrue("((Integer) 7).intValue() == 7");
        this.assertExpressionEvaluatesTrue("(int) new Integer(7) == 7");

        // Boxing conversion followed by widening reference conversion - not described in JLS7, but supported by
        // JAVAC. See JLS7 5.1.7, and JANINO-153.
        this.assertExpressionEvaluatesTrue("null != (Comparable) 5.0");

        // Unboxing conversion followed by widening primitive conversion - not described in JLS7, but supported by
        // JAVAC. See JLS7 5.1.7, and JANINO-153.
        this.assertExpressionEvaluatesTrue("0L != (long) new Integer(8)");
    }

    @Test public void
    test_5_6__Number_promotions() throws Exception {
        // 5.6.1 Unary Numeric Promotion
        this.assertExpressionEvaluatesTrue("-new Byte((byte) 7) == -7");
        this.assertExpressionEvaluatesTrue("-new Double(10.0D) == -10.0D");
        this.assertScriptReturnsTrue("char c = 'a'; return -c == -97;");

        // 5.6.2 Binary Numeric Promotion
        this.assertExpressionEvaluatesTrue("2.5D * new Integer(4) == 10D");
        this.assertExpressionEvaluatesTrue("7 % new Float(2.5F) == 2F");
        this.assertExpressionEvaluatesTrue("2000000000 + 2000000000L == 4000000000L");
        this.assertExpressionEvaluatesTrue("(short) 32767 + (byte) 100 == 32867");
    }

    @Test public void
    test_6_6_1__Determining_Accessibility_member_access() throws Exception {

        // SUPPRESS CHECKSTYLE Whitespace:4
        this.assertExpressionEvaluatesTrue("for_sandbox_tests.ClassWithFields.publicField        == 1");
        this.assertExpressionUncookable   ("for_sandbox_tests.ClassWithFields.protectedField     == 2", "Protected member cannot be accessed|compiler.err.report.access");
        this.assertExpressionUncookable   ("for_sandbox_tests.ClassWithFields.packageAccessField == 3", "Member with \"package\" access cannot be accessed|compiler.err.not.def.public.cant.access");
        this.assertExpressionUncookable   ("for_sandbox_tests.ClassWithFields.privateField       == 4", "Private member cannot be accessed|compiler.err.report.access");
    }

    @Test public void
    test_6_6_2_1__Access_to_a_protected_Member() throws Exception {

        // A protected instance member of a class in another package is accessible through an expression only if the
        // type of the expression is the class in which the access occurs, or a subclass of it (issue #54).
        String u   = "Protected member cannot be accessed through an expression|compiler.err.report.access";
        String fis = "import java.io.*; public class Foo extends FilterInputStream { Foo() { super(null); }\n";

        this.assertCompilationUnitMainReturnsTrue(
            fis
            + "    static class Bar extends Foo {}\n"
            + "    Object f() { return this.in; }\n"
            + "    Object g() { return in; }\n"
            + "    Object h() { return super.in; }\n"
            + "    static Object i(Foo foo) { return foo.in; }\n"
            + "    static Object j(Bar bar) { return bar.in; }\n"
            + "    public static boolean main() {\n"
            + "        Foo foo = new Foo();\n"
            + "        return foo.f() == null && foo.g() == null && foo.h() == null && i(foo) == null\n"
            + "            && j(new Bar()) == null;\n"
            + "    }\n"
            + "}\n",
            "Foo"
        );
        this.assertCompilationUnitMainReturnsTrue(
            ""
            + "public class Foo implements Cloneable {\n"
            + "    static class Bar extends Foo {}\n"
            + "    Object f() throws Exception { return super.clone(); }\n"
            + "    Object g() throws Exception { return this.clone(); }\n"
            + "    Object h() throws Exception { return clone(); }\n"
            + "    public static boolean main() throws Exception {\n"
            + "        Foo foo = new Foo(); Bar bar = new Bar(); int[] a = { 1 };\n"
            + "        return foo.f() != null && foo.g() != null && foo.h() != null && foo.clone() != null\n"
            + "            && bar.clone() != null && a.clone() != a;\n"
            + "    }\n"
            + "}\n",
            "Foo"
        );
        this.assertCompilationUnitMainReturnsTrue(
            ""
            + "public class Foo extends java.io.ByteArrayOutputStream {\n"
            + "    public static boolean main() { Foo foo = new Foo(); foo.count = 1; return foo.count == 1; }\n"
            + "}\n",
            "Foo"
        );

        // A protected static member is accessible through any expression.
        this.assertCompilationUnitMainReturnsTrue(
            ""
            + "public class Foo extends ClassLoader {\n"
            + "    public static boolean main() {\n"
            + "        return (registerAsParallelCapable() || true)\n"
            + "            && (ClassLoader.registerAsParallelCapable() || true);\n"
            + "    }\n"
            + "}\n",
            "Foo"
        );

        // The type of the expression is the superclass, a sibling subclass, or "this" cast to the superclass.
        this.assertCompilationUnitUncookable(fis + "static Object f(FilterInputStream s) { return s.in; } }", u);
        this.assertCompilationUnitUncookable(fis + "static Object f(BufferedInputStream s) { return s.in; } }", u);
        this.assertCompilationUnitUncookable(fis + "Object f() { return ((FilterInputStream) this).in; } }", u);
        this.assertCompilationUnitUncookable(
            fis + "static Object f() { FilterInputStream s = new Foo(); return s.in; } }",
            u
        );
        this.assertCompilationUnitUncookable(
            "class Foo { static Object f(Object o) throws Exception { return o.clone(); } }",
            u
        );
        this.assertCompilationUnitUncookable(
            "class Foo implements Cloneable { Object f() throws Exception { return ((Object) this).clone(); } }",
            u
        );
        this.assertCompilationUnitUncookable(
            "class Foo implements Cloneable { Object f() throws Exception { Object o = new Foo(); return o.clone(); } }"
            ,
            u
        );
        this.assertCompilationUnitUncookable(
            "import java.io.*; class Foo extends ByteArrayOutputStream {"
            + " void f(ByteArrayOutputStream s) { s.count = 1; } }",
            u
        );
    }

    @Test public void
    test_6_6_2_1__Access_to_a_protected_Member_from_an_inner_class() throws Exception {

        // A protected member that an enclosing class inherits from a class in another package is accessible from
        // the inner classes of that class (JLS 6.6.2.1: "within the body of a subclass"), through a synthetic
        // accessor method of the enclosing class, like with JAVAC (issue #59).

        // Fields: read, assigned, compound assignment, crement, through "Foo.this", "Foo.super", a variable of the
        // class or of a subclass; from an inner class of an inner class.
        this.assertCompilationUnitMainReturnsTrue(
            ""
            + "import java.io.*;\n"
            + "public class Foo extends ByteArrayOutputStream {\n"
            + "    static class Sub extends Foo {}\n"
            + "    class Inner {\n"
            + "        int read() { return count; }\n"
            + "        int write(int c) { return count = c; }\n"
            + "        int add(int c) { count += c; ++count; return count--; }\n"
            + "        Object buffer() { return Foo.this.buf; }\n"
            + "        Object viaSuper() { return Foo.super.buf; }\n"
            + "        int other(Foo foo) { return foo.count; }\n"
            + "        int other(Sub sub) { return sub.count; }\n"
            + "        class Inner2 { int read() { return count; } }\n"
            + "    }\n"
            + "    public static boolean main() {\n"
            + "        Foo foo = new Foo(); Inner inner = foo.new Inner(); Sub sub = new Sub();\n"
            + "        sub.count = 7;\n"
            + "        return (\n"
            + "            inner.write(3) == 3 && inner.read() == 3 && foo.count == 3\n"
            + "            && inner.add(2) == 6 && foo.count == 5\n"
            + "            && inner.buffer() == foo.buf && inner.viaSuper() == foo.buf\n"
            + "            && inner.other(foo) == 5 && inner.other(sub) == 7\n"
            + "            && inner.new Inner2().read() == 5\n"
            + "        );\n"
            + "    }\n"
            + "}\n",
            "Foo"
        );

        // Methods: unqualified, through "Foo.this", "Foo.super" and a variable of the class, with arguments and
        // checked exceptions; from anonymous and local classes; the reflective properties of the accessor method.
        this.assertCompilationUnitMainReturnsTrue(
            ""
            + "import java.lang.reflect.*;\n"
            + "import java.util.*;\n"
            + "public class Foo extends ArrayList<String> implements Cloneable {\n"
            + "    class Inner {\n"
            + "        int a() { removeRange(0, 1); return size(); }\n"
            + "        int b() { Foo.this.removeRange(0, 1); return size(); }\n"
            + "        int c() { Foo.super.removeRange(0, 1); return size(); }\n"
            + "        int d(Foo foo) { foo.removeRange(0, 1); return foo.size(); }\n"
            + "        Object copy() throws CloneNotSupportedException { return Foo.this.clone(); }\n"
            + "    }\n"
            + "    int anon() {\n"
            + "        return new Object() { public String toString() { removeRange(0, 1); return \"\"; } }\n"
            + "            .toString().length() + size();\n"
            + "    }\n"
            + "    int local() { class L { int g() { removeRange(0, 1); return size(); } } return new L().g(); }\n"
            + "    public static boolean main() throws Exception {\n"
            + "        Foo foo = new Foo();\n"
            + "        foo.addAll(Arrays.asList(\"a\", \"b\", \"c\", \"d\", \"e\", \"f\", \"g\"));\n"
            + "        Inner inner = foo.new Inner();\n"
            + "        Method a = Foo.class.getDeclaredMethod(\"access$000\", Foo.class, int.class, int.class);\n"
            + "        return (\n"
            + "            inner.a() == 6 && inner.b() == 5 && inner.c() == 4 && inner.d(foo) == 3\n"
            + "            && foo.anon() == 2 && foo.local() == 1\n"
            + "            && inner.copy() instanceof Foo && inner.copy() != foo\n"
            + "            && a.isSynthetic() && Modifier.isStatic(a.getModifiers())\n"
            + "        );\n"
            + "    }\n"
            + "}\n",
            "Foo"
        );

        // Static members: from an inner class, a static nested class and a local class in a static method.
        this.assertCompilationUnitMainReturnsTrue(
            ""
            + "public class Foo extends ClassLoader {\n"
            + "    class Inner { boolean f() { return registerAsParallelCapable(); } }\n"
            + "    static class Nested { boolean f() { return ClassLoader.registerAsParallelCapable(); } }\n"
            + "    static boolean local() {\n"
            + "        class L { boolean f() { return registerAsParallelCapable(); } }\n"
            + "        return new L().f();\n"
            + "    }\n"
            + "    public static boolean main() {\n"
            + "        return (new Foo().new Inner().f() || true) && (new Nested().f() || true) && (local() || true);\n"
            + "    }\n"
            + "}\n",
            "Foo"
        );
        this.assertCompilationUnitMainReturnsTrue(
            ""
            + "public class Foo extends for_sandbox_tests.ClassWithFields {\n"
            + "    class Inner { int f() { return protectedField; } }\n"
            + "    static class Nested { int f() { protectedField += 10; return protectedField; } }\n"
            + "    public static boolean main() {\n"
            + "        int before = new Foo().new Inner().f();\n"
            + "        return new Nested().f() == before + 10 && new Foo().new Inner().f() == before + 10;\n"
            + "    }\n"
            + "}\n",
            "Foo"
        );

        // Not accessible: from an inner class of a class that is not a subclass, or through an expression whose type
        // is neither the enclosing class nor a subclass of it.
        String u = "Protected member cannot be accessed|compiler.err.report.access";
        this.assertCompilationUnitUncookable(
            "class Foo { class Inner { Object f(java.io.FilterInputStream s) { return s.in; } } }",
            u
        );
        this.assertCompilationUnitUncookable(
            "import java.io.*; class Foo extends FilterInputStream { Foo() { super(null); }"
            + " class Inner { Object f() { return ((FilterInputStream) Foo.this).in; } } }",
            u
        );
        this.assertCompilationUnitUncookable(
            "import java.io.*; class Foo extends FilterInputStream { Foo() { super(null); }"
            + " class Inner { Object f(BufferedInputStream b) { return b.in; } } }",
            u
        );
    }

    @Test public void
    test_6_6_2_2__Qualified_Access_to_a_protected_Constructor() throws Exception {

        // A protected constructor can be invoked by a class instance creation expression only from within the package
        // of the class, but by an anonymous class instance creation expression or "super(...)" from anywhere (issue
        // #54).
        String u = "Protected constructor cannot be invoked|has protected access|compiler.err.report.access";
        this.assertCompilationUnitUncookable(
            ""
            + "class Foo extends java.io.FilterInputStream {\n"
            + "    Foo() { super(null); }\n"
            + "    static Object f() { return new java.io.FilterInputStream(null); }\n"
            + "}\n",
            u
        );
        this.assertCompilationUnitMainReturnsTrue(
            ""
            + "public class Foo extends java.io.FilterInputStream {\n"
            + "    Foo() { super(null); }\n"
            + "    public static boolean main() {\n"
            + "        return new Foo() != null && new java.io.FilterInputStream(null) {} != null;\n"
            + "    }\n"
            + "}\n",
            "Foo"
        );
    }

    @Test public void
    test_7_5__Import_declarations() throws Exception {

        // Default imports
        this.assertExpressionEvaluatesTrue(
            "import java.util.*; new ArrayList().getClass().getName().equals(\"java.util.ArrayList\")"
        );
        this.assertScriptUncookable("import java.util#;");
        this.assertScriptUncookable("import java.util.9;");
        this.assertScriptCookable("import java.util.*;");
        this.assertClassBodyMainReturnsTrue(
            "import java.util.*; public static boolean main() { return new ArrayList() instanceof List; }"
        );
        this.assertExpressionUncookable("import java.io.*; new ArrayList()");

        // 7.5.1 Import Declarations -- Single-Type-Import
        this.assertExpressionEvaluatable("import java.util.ArrayList; new ArrayList()");
        this.assertExpressionEvaluatable("import java.util.ArrayList; import java.util.ArrayList; new ArrayList()");
        this.assertScriptUncookable("import java.util.List; import java.awt.List;");

        // 7.5.2 Import Declarations -- Import-on-Demand
        this.assertExpressionEvaluatable("import java.util.*; new ArrayList()");
        this.assertExpressionEvaluatable("import java.util.*; import java.util.*; new ArrayList()");

        // 7.5.3 Import Declarations -- Single Static Import
        this.assertExpressionEvaluatesTrue(
            "import static java.util.Collections.EMPTY_SET; EMPTY_SET instanceof java.util.Set"
        );
        this.assertExpressionEvaluatesTrue(
            "import static java.util.Collections.EMPTY_SET;"
            + "import static java.util.Collections.EMPTY_SET;"
            + "EMPTY_SET instanceof java.util.Set"
        );
        this.assertScriptExecutable("import static java.util.Map.Entry; Entry e;");
        this.assertScriptExecutable(
            "import static java.util.Map.Entry;"
            + "import static java.util.Map.Entry;"
            + "Entry e;"
        );
        this.assertScriptUncookable(
            "import static java.util.Map.Entry;"
            + "import static java.security.KeyStore.Entry;"
            + "Entry e;"
        );
        this.assertExpressionEvaluatesTrue(
            "import static java.util.Arrays.asList;"
            + "asList(new String[] { \"HELLO\", \"WORLD\" }).size() == 2"
        );
        // A duplicate single static import of a method fails with an internal compiler error (#133); in the
        // compliance mode also for a variable arity method with an array argument. Until #133 is fixed:
        if (!this.isCompliant) {
            this.assertExpressionEvaluatesTrue(
                "import static java.util.Arrays.asList;"
                + "import static java.util.Arrays.asList;"
                + "asList(new String[] { \"HELLO\", \"WORLD\" }).size() == 2"
            );
        }
        this.assertScriptUncookable(
            "import static java.lang.Integer.decode;"
            + "import static java.lang.Long.decode;"
            + "decode(\"4000000000\");"
        );

        // 7.5.4 Import Declarations -- Static-Import-on-Demand
        this.assertExpressionEvaluatesTrue("import static java.util.Collections.*; EMPTY_SET instanceof java.util.Set");
        this.assertScriptExecutable("import static java.util.Map.*; Entry e;");
        this.assertExpressionEvaluatesTrue(
            "import static java.util.Arrays.*;"
            + "asList(new String[] { \"HELLO\", \"WORLD\" }).size() == 2"
        );
    }

    @Test public void
    test_8_1_1__Class_Modifiers() throws Exception {

        // Modifiers for package member class:
        this.assertCompilationUnitCookable("@SuppressWarnings(\"foo\") class Foo {}");
        this.assertCompilationUnitCookable("abstract                   class Foo {}");
        this.assertCompilationUnitCookable("final                      class Foo {}");
        this.assertCompilationUnitCookable("public                     class Foo {}");
        this.assertCompilationUnitCookable("strictfp                   class Foo {}");
        this.assertCompilationUnitUncookable("default                    class Foo {}", "default not allowed|expected");
        this.assertCompilationUnitUncookable("native                     class Foo {}", "native not allowed");
        this.assertCompilationUnitUncookable("private                    class Foo {}", "private not allowed");
        this.assertCompilationUnitUncookable("protected                  class Foo {}", "protected not allowed");
        this.assertCompilationUnitUncookable("static                     class Foo {}", "static not allowed");
        this.assertCompilationUnitUncookable("synchronized               class Foo {}", "synchronized not allowed");
        this.assertCompilationUnitUncookable("transient                  class Foo {}", "transient not allowed");
        this.assertCompilationUnitUncookable("volatile                   class Foo {}", "volatile not allowed");
        this.assertCompilationUnitUncookable("@SuppressWarnings(\"foo\") @SuppressWarnings(\"bar\")  class Foo {}", "(?i)duplicate annotation|is not .* repeatable");
        this.assertCompilationUnitUncookable("public protected           class Foo {}", "allowed");
        this.assertCompilationUnitUncookable("protected private          class Foo {}", "allowed");
        this.assertCompilationUnitUncookable("private public             class Foo {}", "allowed");
        this.assertCompilationUnitUncookable("abstract final             class Foo {}", "Only one of abstract final is allowed|illegal combination");
    }

    @Test public void
    test_8_1_5__Superinterfaces() throws Exception {

        // The same interface twice (issue #54).
        String u = "Duplicate interface|repeated interface|compiler.err.repeated.interface";
        this.assertCompilationUnitUncookable("class Foo implements Runnable, Runnable { public void run() {} }", u);
        this.assertCompilationUnitUncookable("class Foo implements java.lang.Cloneable, Cloneable {}", u);
        this.assertCompilationUnitUncookable("interface I {} interface Foo extends I, I {}", u);

        // A superinterface of another superinterface, or of the superclass, may be repeated.
        this.assertCompilationUnitCookable("interface I {} interface J extends I {} class Foo implements I, J {}");
        this.assertCompilationUnitCookable(
            "abstract class A implements Cloneable {} class Foo extends A implements Cloneable {}"
        );
    }

    @Test public void
    test_8_3_1__Field_Modifiers() throws Exception {

        // "final" and "volatile" (issue #54).
        String u = "(?i)illegal combination of modifiers|compiler.err.illegal.combination.of.modifiers";
        this.assertClassBodyUncookable("final volatile int x = 1;", u);
        this.assertClassBodyUncookable("static final volatile int x = 1;", u);
        this.assertClassBodyCookable("transient final int x = 1;");
        this.assertClassBodyCookable("static transient volatile int x;");
    }

    @Test public void
    test_8_4_3__Method_Modifiers() throws Exception {

        // "abstract" with another modifier (issue #54).
        String u = "(?i)illegal combination of modifiers|compiler.err.illegal.combination.of.modifiers";
        this.assertCompilationUnitUncookable("abstract class Foo { abstract static void f(); }", u);
        this.assertCompilationUnitUncookable("abstract class Foo { private abstract void f(); }", u);
        this.assertCompilationUnitUncookable("abstract class Foo { abstract native void f(); }", u);
        this.assertCompilationUnitUncookable("abstract class Foo { abstract synchronized void f(); }", u);
        this.assertCompilationUnitUncookable("abstract class Foo { abstract strictfp void f(); }", u);
        this.assertCompilationUnitUncookable("interface Foo { abstract static void f(); }", u);
        this.assertCompilationUnitUncookable("interface Foo { abstract default void f() {} }", u + "|must not have a body");
        this.assertCompilationUnitCookable(
            "abstract class Foo { abstract void f(); native void g(); synchronized native void h(); }"
        );
    }

    @Test public void
    test_8_5__Member_Type_Declarations() throws Exception {

        // The implicit modifiers of member types (JLS 8.5.1, 8.9, 9.1.1), as the "InnerClasses" attribute records
        // them, are visible through "Class.getModifiers()"; the attribute also provides the declaring class and the
        // simple name (issue #43).
        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "import java.lang.reflect.Modifier;\n"
            + "public class Foo {\n"
            + "    static class Sc {}\n"
            + "    class Ic {}\n"
            + "    public abstract static class Asc {}\n"
            + "    interface I {}\n"
            + "    strictfp interface J {}\n"
            + "    enum E { X }\n"
            + "    @interface A {}\n"
            + "    public static boolean main() {\n"
            + "        return (\n"
            + "            Sc.class.getModifiers() == Modifier.STATIC\n"
            + "            && Ic.class.getModifiers() == 0\n"
            + "            && Asc.class.getModifiers() == (Modifier.PUBLIC | Modifier.ABSTRACT | Modifier.STATIC)\n"
            + "            && I.class.getModifiers() == (Modifier.STATIC | Modifier.ABSTRACT | Modifier.INTERFACE)\n"
            + "            && J.class.getModifiers() == (Modifier.STATIC | Modifier.ABSTRACT | Modifier.INTERFACE)\n"
            + "            && E.class.getModifiers() == (Modifier.STATIC | Modifier.FINAL | 0x4000)\n"
            + "            && A.class.getModifiers() == (\n"
            + "                Modifier.STATIC | Modifier.ABSTRACT | Modifier.INTERFACE | 0x2000\n"
            + "            )\n"
            + "            && E.class.isEnum() && A.class.isAnnotation()\n"
            + "            && I.class.isMemberClass() && I.class.getDeclaringClass() == Foo.class\n"
            + "            && I.class.getEnclosingClass() == Foo.class && \"I\".equals(I.class.getSimpleName())\n"
            + "            && Foo.class.getDeclaredClasses().length == 7\n"
            + "        );\n"
            + "    }\n"
            + "}\n"
        ), "Foo");
    }

    @Test public void
    test_8_1_2__Generic_Classes_and_Type_Parameters() throws Exception {
        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "public class Bag<T> {\n"
            + "\n"
            + "    private T contents;\n"
            + "\n"
            + "    void put(T element) { this.contents = element; }\n"
            + "\n"
            + "    T get() { return this.contents; }\n"
            + "\n"
            + "    public static boolean main() {\n"
            + "        Bag<String> b = new Bag<String>();\n"
            + "        b.put(\"FOO\");\n"
            + "        String s = (String) b.get();\n"
            + "        return \"FOO\".equals(s);\n"
            + "    }\n"
            + "}\n"
        ), "Bag");
    }

    @Test public void
    test_8_1_3__Inner_Classes_and_Enclosing_Instances() throws Exception {
        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "import org.junit.Assert;\n"
            + "\n"
            + "public class Main {\n"
            + "    public static boolean\n"
            + "    main() {\n"
            + "        meth(1, new int[][] { { 2 } }, \"c\", new String[][] { { \"d\" } });\n"
            + "        return true;\n"
            + "    }\n"
            + "\n"
            + "    public static boolean\n"
            + "    meth(final int a, final int[] b[], final String c, final String[] d[]) {\n"
            + "        final int      e   = 5;\n"
            + "        final int[]    f[] = { { 6 } };\n"
            + "        final String   g   = \"g\";\n"
            + "        final String[] h[] = { { \"h\" } };\n"
            + "        new Runnable() {\n"
            + "            public void run() {\n"
            + "                Assert.assertEquals(1, a);\n"
            + "                Assert.assertEquals(2, b[0][0]);\n"
            + "                Assert.assertEquals(\"c\", c);\n"
            + "                Assert.assertEquals(\"d\", d[0][0]);\n"
            + "                Assert.assertEquals(5, e);\n"
            + "                Assert.assertEquals(6, f[0][0]);\n"
            + "                Assert.assertEquals(\"g\", g);\n"
            + "                Assert.assertEquals(\"h\", h[0][0]);\n"
            + "            }\n"
            + "        }.run();\n"
            + "        return true;\n"
            + "    }\n"
            + "}\n"
        ), "Main");
    }

    @Test public void
    test_8_1_3__Inner_Classes_and_Enclosing_Instances__effectively_final() throws Exception {

        // Since Java 8, a local or anonymous class may access local variables and parameters of the enclosing
        // method that are effectively final (JLS8 4.12.4): not declared "final", but never assigned (issue #24).
        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "import java.util.Arrays;\n"
            + "\n"
            + "public class Main {\n"
            + "    public static boolean\n"
            + "    main() {\n"
            + "        return meth(1, \"c\") && catchParameter().equals(\"boom\") && loops().equals(\"ab02\");\n"
            + "    }\n"
            + "\n"
            + "    public static boolean\n"
            + "    meth(int a, String c) {\n"
            + "        int    e = 5;\n"
            + "        long   l = 6L;\n"
            + "        double d = 7.5;\n"
            + "        int[]  f = { 8 };\n"
            + "        f[0] = 9;\n"
            + "        class Local { int get() { return e; } }\n"
            + "        Object o = new Object() {\n"
            + "            public String toString() {\n"
            + "                return a + c + e + l + d + f[0] + new Local().get() + new Object() {\n"
            + "                    public String toString() { return \"\" + a; }\n"
            + "                };\n"
            + "            }\n"
            + "        };\n"
            + "        return o.toString().equals(\"1c567.5951\") && e == 5;\n"
            + "    }\n"
            + "\n"
            + "    public static String\n"
            + "    catchParameter() {\n"
            + "        try {\n"
            + "            throw new RuntimeException(\"boom\");\n"
            + "        } catch (RuntimeException re) {\n"
            + "            return new Object() { public String toString() { return re.getMessage(); } }.toString();\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    public static String\n"
            + "    loops() {\n"
            + "        StringBuilder sb = new StringBuilder();\n"
            + "        for (String s : Arrays.asList(\"a\", \"b\")) {\n"
            + "            sb.append(new Object() { public String toString() { return s; } });\n"
            + "        }\n"
            + "        for (int i = 0; i < 2; i++) {\n"
            + "            int x = i * 2;\n"
            + "            sb.append(new Object() { public String toString() { return \"\" + x; } });\n"
            + "        }\n"
            + "        return sb.toString();\n"
            + "    }\n"
            + "}\n"
        ), "Main");

        // Assigned after the inner class captured it.
        this.assertCompilationUnitUncookable(
            ""
            + "public class Main {\n"
            + "    void f() {\n"
            + "        int x = 1;\n"
            + "        Runnable r = new Runnable() { public void run() { int y = x; } };\n"
            + "        x = 2;\n"
            + "    }\n"
            + "}\n",
            "Cannot access non-final local variable|compiler.err.cant.ref.non.effectively.final.var"
        );

        // Incremented.
        this.assertCompilationUnitUncookable(
            ""
            + "public class Main {\n"
            + "    void f() {\n"
            + "        int x = 1;\n"
            + "        x++;\n"
            + "        Runnable r = new Runnable() { public void run() { int y = x; } };\n"
            + "    }\n"
            + "}\n",
            "Cannot access non-final local variable|compiler.err.cant.ref.non.effectively.final.var"
        );

        // Assigned in the inner class.
        this.assertCompilationUnitUncookable(
            ""
            + "public class Main {\n"
            + "    void f() {\n"
            + "        int x = 1;\n"
            + "        Runnable r = new Runnable() { public void run() { x = 2; } };\n"
            + "    }\n"
            + "}\n",
            "Cannot access non-final local variable|compiler.err.cant.ref.non.effectively.final.var"
        );
    }

    @Test public void
    test_8_4_8_3__Requirements_in_Overriding_and_Hiding() throws Exception {

        this.assertClassBodyExecutable(
            ""
            + "public static interface FirstCloneable extends Cloneable {\n"
            + "   public Object clone() throws CloneNotSupportedException;\n"
            + "}\n"
            + "\n"
            + "public static interface SecondCloneable extends Cloneable {\n"
            + "    public SecondCloneable clone() throws CloneNotSupportedException;\n"
            + "}\n"
            + "\n"
            + "public static abstract class BaseClone implements FirstCloneable, SecondCloneable {\n"
            + "    @Override public BaseClone clone() throws CloneNotSupportedException {\n"
            + "        return (BaseClone)super.clone();\n"
            + "    }\n"
            + "}\n"
            + "\n"
            + "public static class KidClone extends BaseClone {}\n"
            + "\n"
            + "public static void main() throws Exception {\n"
            + "    new KidClone().clone();\n"
            + "}\n"
        );

        this.assertExpressionUncookable("new Object() { public void toString() {}}.toString()", "incompatible");
    }

    @Test public void
    test_8_6__Instance_Initializers() throws Exception {

        this.assertClassBodyMainReturnsTrue((
            ""
            + "public static boolean main() { return new " + IClassBodyEvaluator.DEFAULT_CLASS_NAME + "().inited; }\n"
            + "boolean inited;\n"
            + "{ this.inited = true; }\n"
        ));

        // Inistance initializer with local variable.
        // See issue #89.
        this.assertClassBodyMainReturnsTrue((
            ""
            + "public static boolean main() { return new " + IClassBodyEvaluator.DEFAULT_CLASS_NAME + "().inited; }\n"
            + "boolean inited;\n"
            + "{ boolean b = true; this.inited = b; }\n"
        ));
    }

    @Test public void
    test_8_7__Static_Initializers() throws Exception {

        this.assertClassBodyMainReturnsTrue((
            ""
            + "public static boolean main() { return " + IClassBodyEvaluator.DEFAULT_CLASS_NAME + ".inited; }\n"
            + "static boolean inited;\n"
            + "static { " + IClassBodyEvaluator.DEFAULT_CLASS_NAME + ".inited = true; }\n"
        ));

        // Static initializer with local variable.
        this.assertClassBodyMainReturnsTrue((
            ""
            + "public static boolean main() { return " + IClassBodyEvaluator.DEFAULT_CLASS_NAME + ".inited; }\n"
            + "static boolean inited;\n"
            + "static { boolean b = true; " + IClassBodyEvaluator.DEFAULT_CLASS_NAME + ".inited = b; }\n"
        ));
    }

    @Test public void
    test_8_9__Enums__1() throws Exception {

        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "public\n"
            + "enum Color { RED, GREEN, BLACK }\n"
            + "\n"
            + "public\n"
            + "class Main {\n"
            + "\n"
            + "    public static Object\n"
            + "    main() {\n"
            + "        if (Color.RED.ordinal()   != 0) return 1;\n"
            + "        if (Color.GREEN.ordinal() != 1) return 2;\n"
            + "        if (Color.BLACK.ordinal() != 2) return 3;\n"
            + "        if (!\"RED\".equals(Color.RED.toString()))     return Color.RED.toString();\n"
            + "        if (!\"GREEN\".equals(Color.GREEN.toString())) return 5;\n"
            + "        if (!\"BLACK\".equals(Color.BLACK.toString())) return 6;\n"
            + "        return true;\n"
            + "    }\n"
            + "}"
        ), "Main");
    }

    @Test public void
    test_8_9__Enums__2() throws Exception {

        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "public\n"
            + "enum Shape {\n"
            + "\n"
            + "    SQUARE(1, 2),\n"
            + "    CIRCLE(3),\n"
            + "    ;\n"
            + "\n"
            + "    private final int length;\n"
            + "    private final int width;\n"
            + "\n"
            + "    Shape(int length, int width) {\n"
            + "        this.length = length;\n"
            + "        this.width = width;\n"
            + "    }\n"
            + "\n"
            + "    Shape(int size) {\n"
            + "        this.length =  this.width = size;\n"
            + "    }\n"
            + "    public int length() { return this.length; }\n"
            + "    public int width() { return this.width; }\n"
            + "}\n"
            + "\n"
            + "public\n"
            + "class Main {\n"
            + "\n"
            + "    public static Object\n"
            + "    main() {\n"
            + "        if (Shape.SQUARE.ordinal() != 0) return 100 + Shape.SQUARE.ordinal();\n"
            + "        if (Shape.CIRCLE.ordinal() != 1) return 200 + Shape.CIRCLE.ordinal();\n"
            + "\n"
            + "        if (!\"SQUARE\".equals(Shape.SQUARE.toString())) return 3;\n"
            + "        if (!\"CIRCLE\".equals(Shape.CIRCLE.toString())) return 4;\n"
            + "\n"
            + "        if (Shape.SQUARE.length() != 1) return 5;\n"
            + "        if (Shape.SQUARE.width()  != 2) return 6;\n"
            + "        if (Shape.CIRCLE.length() != 3) return 7;\n"
            + "        if (Shape.CIRCLE.width()  != 3) return 8;\n"
            + "\n"
            + "        return true;\n"
            + "    }\n"
            + "}"
        ), "Main");
    }

    @Test public void
    test_8_9__Enums__valueOf() throws Exception {

        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "public\n"
            + "enum Shape {\n"
            + "\n"
            + "    SQUARE,\n"
            + "    CIRCLE,\n"
            + "}\n"
            + "\n"
            + "public\n"
            + "class Main {\n"
            + "\n"
            + "    public static Object\n"
            + "    main() {\n"
            + "        if (Shape.valueOf(\"SQUARE\") != Shape.SQUARE) return \"100\" + Shape.valueOf(\"SQUARE\");\n"
            + "        if (Shape.valueOf(\"CIRCLE\") != Shape.CIRCLE) return \"200\" + Shape.valueOf(\"CIRCLE\");\n"
            + "        try {\n"
            + "            Shape.valueOf(\"SQUARE \");\n"
            + "            return 500;\n"
            + "        } catch (IllegalArgumentException iae) {\n"
            + "            ;\n"
            + "        }\n"
            + "\n"
            + "        return true;\n"
            + "    }\n"
            + "}"
        ), "Main");
    }

    @Test public void
    test_8_9__Enums__values() throws Exception {

        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "public\n"
            + "enum Shape {\n"
            + "\n"
            + "    SQUARE,\n"
            + "    CIRCLE,\n"
            + "}\n"
            + "\n"
            + "public\n"
            + "class Main {\n"
            + "\n"
            + "    public static Object\n"
            + "    main() {\n"
            + "        Shape[] ss = Shape.values();\n"
            + "        if (ss == null)            return 100;\n"
            + "        if (ss.length != 2)        return 200 + ss.length;\n"
            + "        if (ss[0] == null)         return 300;\n"
            + "        if (ss[0] != Shape.SQUARE) return 400 + ss[0].toString();\n"
            + "        if (ss[1] == null)         return 500;\n"
            + "        if (ss[1] != Shape.CIRCLE) return 600 + ss[0].toString();\n"
            + "\n"
            + "        return true;\n"
            + "    }\n"
            + "}"
        ), "Main");
    }

    /**
     * Class bodies of enum constants; see <a href="https://github.com/janino-lts/janino/issues/44">issue #44</a>.
     */
    @Test public void
    test_8_9_1__Enum_Constants__Class_Bodies() throws Exception {

        // A constant with a class body is an instance of an anonymous subclass of the enum, which overrides and
        // implements methods, and has fields, initializers, member classes and constructor arguments.
        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "import java.lang.reflect.Modifier;\n"
            + "import java.util.EnumSet;\n"
            + "public class Main {\n"
            + "    interface I { String i(); }\n"
            + "    static int X = 7;\n"
            + "    enum E implements I {\n"
            + "        A(1) {\n"
            + "            int x = 10;\n"
            + "            { x++; }\n"
            + "            class Q { int q() { return 4; } }\n"
            + "            String n() { return \"a\" + x + new Q().q() + v + X; }\n"
            + "            public String toString() { return \"ta\"; }\n"
            + "            public String i() { return \"ia\"; }\n"
            + "        },\n"
            + "        B(2) {\n"
            + "            String n() { return super.n() + \"b\"; }\n"
            + "            public String i() { return \"ib\"; }\n"
            + "        },\n"
            + "        C(3);\n"
            + "        final int v;\n"
            + "        E(int v) { this.v = v; }\n"
            + "        String n() { return \"n\" + v; }\n"
            + "        public String i() { return \"ic\"; }\n"
            + "    }\n"
            + "    enum F { A { void f() {} }; abstract void f(); }\n"
            + "    enum G { A, B }\n"
            + "    static int sw(E e) { switch (e) { case A: return 1; case B: return 2; default: return 3; } }\n"
            + "    public static boolean main() {\n"
            + "        return (\n"
            + "            \"a11417\".equals(E.A.n()) && \"n2b\".equals(E.B.n()) && \"n3\".equals(E.C.n())\n"
            + "            && \"ta\".equals(E.A.toString()) && \"B\".equals(E.B.toString())\n"
            + "            && \"A\".equals(E.A.name())\n"
            + "            && \"ia\".equals(((I) E.A).i()) && \"ib\".equals(E.B.i()) && \"ic\".equals(E.C.i())\n"
            + "            && E.A.getClass() != E.class && E.A.getClass().getSuperclass() == E.class\n"
            + "            && E.A.getDeclaringClass() == E.class && E.C.getClass() == E.class\n"
            + "            && E.values().length == 3 && E.valueOf(\"B\") == E.B && E.B.ordinal() == 1\n"
            + "            && sw(E.A) == 1 && sw(E.B) == 2 && sw(E.C) == 3\n"
            + "            && EnumSet.allOf(E.class).size() == 3 && EnumSet.of(E.B).contains(E.B)\n"
            + "            && !Modifier.isFinal(E.class.getModifiers())\n"
            + "            && !Modifier.isAbstract(E.class.getModifiers())\n"
            + "            && Modifier.isAbstract(F.class.getModifiers()) && Modifier.isFinal(G.class.getModifiers())\n"
            + "        );\n"
            + "    }\n"
            + "}\n"
        ), "Main");

        // An enum with an abstract method is implicitly abstract: each constant must have a class body that
        // implements the method; an enum must not be declared abstract.
        this.assertCompilationUnitUncookable(
            "class Foo { enum E { A, B { String n() { return \"b\"; } }; abstract String n(); } }",
            "must have a class body|is abstract; cannot be instantiated|compiler.err.abstract.cant.be.instantiated"
        );
        this.assertCompilationUnitUncookable(
            "class Foo { enum E { A { void f() {} }; abstract void f(); abstract void g(); } }",
            "must implement method|does not override abstract method|compiler.err.does.not.override.abstract"
        );
        this.assertCompilationUnitUncookable(
            "class Foo { enum E { ; abstract void f(); } }",
            "must implement method|does not override abstract method|compiler.err.does.not.override.abstract"
        );
        this.assertCompilationUnitUncookable(
            "class Foo { abstract enum E { A } }",
            "not allowed|compiler.err.mod.not.allowed.here"
        );
    }

    @Test public void
    test_9_3_1__Initialization_of_Fields_in_Interfaces__1() throws Exception {
        this.assertClassBodyCookable("public final static double x = 0;");
        this.assertClassBodyCookable("public final static double x = 0F;");
        this.assertClassBodyCookable("public final static double x = 0D;");
    }
    @Test public void
    test_9_3_1__Initialization_of_Fields_in_Interfaces__2() throws Exception {
        this.assertClassBodyCookable("public final static float x = 0;");
        this.assertClassBodyCookable("public final static float x = 0F;");
        this.assertClassBodyUncookable("public final static float x = 0D;");
    }
    @Test public void
    test_9_3_1__Initialization_of_Fields_in_Interfaces__3() throws Exception {
        this.assertClassBodyCookable("public final static int x = 0;");
    }
    @Test public void
    test_9_3_1__Initialization_of_Fields_in_Interfaces__4() throws Exception {
        this.assertClassBodyCookable("public final static byte x = 0;");
        this.assertClassBodyCookable("public final static byte x = 127;");
        this.assertClassBodyUncookable("public final static byte x = 128;");
    }
    @Test public void
    test_9_3_1__Initialization_of_Fields_in_Interfaces__5() throws Exception {
        this.assertClassBodyCookable("public final static java.util.Map x = null;");
    }
    @Test public void
    test_9_3_1__Initialization_of_Fields_in_Interfaces__6() throws Exception {
        this.assertClassBodyCookable("public final static String x = null;");
        this.assertClassBodyCookable("public final static String x = \"ABC\";");
        this.assertClassBodyCookable("public final static String x = \"ABC\" + \"DEF\";");
    }

    @Test public void
    test_9_4__Method_Declarations__1() throws Exception {

        this.assertCompilationUnitCookable((
            ""
            + "public interface A {\n"
            + "    A meth1();\n"
            + "}\n"
        ));
    }

    @Test public void
    test_9_4__Method_Declarations__2() throws Exception {

        // Static interface methods MUST declare a body.
        this.assertCompilationUnitUncookable((
            ""
            + "public interface A {\n"
            + "    static A meth1();\n"
            + "}\n"
        ));
    }

    @Test public void
    test_9_4__Method_Declarations__3() throws Exception {

        String cu = (
            ""
            + "public interface A {\n"
            + "    static A meth1() { return null; }\n"
            + "}\n"
            + "\n"
            + "public\n"
            + "class Main {\n"
            + "\n"
            + "    public static Object\n"
            + "    main() {\n"
            + "        return A.meth1() == null;\n"
            + "    }\n"
            + "}\n"
        );

        this.assertCompilationUnitCookable(cu, "only available for target version 8\\+|compiler\\.err\\.mod\\.not\\.allowed\\.here");
    }

    @Test public void
    test_9_4__Method_Declarations__4() throws Exception {

        // Default interface methods - a Java 8 feature.

        String cu = (
            ""
            + "public interface A { default boolean isTrue() { return true; } }\n"
            + "public class B implements A {}\n"
            + "public class Foo { public static boolean main() { return new B().isTrue(); } }\n"
        );

        // JAVAC 20+ no longer supports source version 7.
        if (!this.isJdk || CommonsCompilerTestSuite.JVM_VERSION < 20) {
            SimpleCompilerTest sct = new SimpleCompilerTest(cu, "Foo");
            sct.setSourceVersion(7);
            sct.assertUncookable((
                ""
                + "Default interface methods only available for source version 8+"
                + "|"
                + "default methods are not supported in -source (1\\.)?7"
                + "|"
                + "compiler\\.err\\.illegal\\.start\\.of\\.type"
            ));
        }

        if (CommonsCompilerTestSuite.JVM_VERSION <= 7 && this.isJdk) return;

        {
            SimpleCompilerTest sct = new SimpleCompilerTest(cu, "Foo");
            sct.setSourceVersion(8);
            sct.setTargetVersion(8);
            if (CommonsCompilerTestSuite.JVM_VERSION < 8) {
                sct.assertCookable();
            } else {
                sct.assertResultTrue();
            }
        }
    }

    @Test public void
    test_9_4__Method_Declarations__5() throws Exception {

        // Modifiers for interface method:
        this.assertCompilationUnitCookable("interface Foo { @SuppressWarnings(\"foo\") void meth();   }");
        this.assertCompilationUnitCookable("interface Foo { abstract                   void meth();   }");
        this.assertCompilationUnitCookable("interface Foo { public                     void meth();   }");
        this.assertCompilationUnitCookable("interface Foo { static                     void meth() {} }", "only available for target version 8\\+|modifier static not allowed");
        this.assertCompilationUnitUncookable("interface Foo { final                      void meth();   }", "final not allowed|illegal combination");
        this.assertCompilationUnitUncookable("interface Foo { native                     void meth();   }", "native not allowed");
        this.assertCompilationUnitUncookable("interface Foo { protected                  void meth();   }", "protected not allowed");
        this.assertCompilationUnitUncookable("interface Foo { strictfp                   void meth();   }", "strictfp (not|only) allowed");
        this.assertCompilationUnitUncookable("interface Foo { synchronized               void meth();   }", "synchronized not allowed");
        this.assertCompilationUnitUncookable("interface Foo { transient                  void meth();   }", "transient not allowed");
        this.assertCompilationUnitUncookable("interface Foo { volatile                   void meth();   }", "volatile not allowed");
        this.assertCompilationUnitUncookable("interface Foo { @SuppressWarnings(\"foo\") @SuppressWarnings(\"bar\") void meth(); }", "(?i)duplicate.*annotation");
        this.assertCompilationUnitUncookable("interface Foo { public protected           void meth();   }", "allowed");
        this.assertCompilationUnitUncookable("interface Foo { protected private          void meth();   }", "allowed");
        this.assertCompilationUnitUncookable("interface Foo { private public             void meth();   }", "allowed|(?i)illegal\\.combination");
        this.assertCompilationUnitUncookable("interface Foo { abstract final             void meth();   }", "Only one of abstract final is allowed|illegal combination|not allowed");
    }

    @Test public void
    test_9_4__Method_Declarations__Default_methods() throws Exception {

        // Default methods (a Java 8 feature).
        String cu = (
            ""
            + "public interface MyInterface { default boolean isTrue() { return true; } }\n"
            + "public class Foo { public static boolean main() { return new MyInterface() {}.isTrue(); } }\n"
        );

        // JAVAC 20+ no longer supports source version 7.
        if (!this.isJdk || CommonsCompilerTestSuite.JVM_VERSION < 20) {
            SimpleCompilerTest sct = new SimpleCompilerTest(cu, "Foo");
            sct.setSourceVersion(7);
            sct.assertUncookable("Default interface methods only available for source version 8+|default methods are not supported in -source (1\\.)?7|compiler\\.err\\.illegal\\.start\\.of\\.type");
        }

        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 8) return;

        {
            SimpleCompilerTest sct = new SimpleCompilerTest(cu, "Foo");
            sct.setSourceVersion(8);
            sct.setTargetVersion(8);
            if (CommonsCompilerTestSuite.JVM_VERSION < 8) {
                sct.assertCookable();
            } else {
                sct.assertResultTrue();
            }
        }
    }

    @Test public void
    test_9_4__Method_Declarations__Static_interface_methods() throws Exception {

        // Static interface methods (a Java 8 feature).

        String cu = (
            ""
            + "public interface MyInterface { static boolean isTrue() { return true; } }\n"
            + "public class Foo { public static boolean main() { return MyInterface.isTrue(); } }\n"
        );

        // JAVAC 20+ no longer supports source version 7.
        if (!this.isJdk || CommonsCompilerTestSuite.JVM_VERSION < 20) {
            SimpleCompilerTest sct = new SimpleCompilerTest(cu, "Foo");
            sct.setSourceVersion(7);
            sct.assertUncookable(
                ""
                + "Static interface methods only available for source version 8+"
                + "|"
                + "static interface methods are not supported in -source (1\\.)?7"
                + "|"
                + "modifier static not allowed here"
            );
        }

        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 8) return;

        {
            SimpleCompilerTest sct = new SimpleCompilerTest(cu, "Foo");
            sct.setSourceVersion(8);
            sct.setTargetVersion(8);
            if (CommonsCompilerTestSuite.JVM_VERSION < 8) {
                sct.assertCookable();
            } else {
                sct.assertResultTrue();
            }
        }

        // A class that implements an interface with a static method, both declared in source code (issue #53):
        // directly, through an abstract superclass, through a subinterface, together with abstract and default
        // methods, as nested types, and with a static or an instance method of the same name in the class.
        String[] cus = {
            ""
            + "interface I { static String s() { return \"s\"; } }\n"
            + "public class Foo implements I { public static boolean main() { return I.s().equals(\"s\"); } }\n",
            ""
            + "interface I { static String s() { return \"s\"; } }\n"
            + "abstract class A implements I {}\n"
            + "public class Foo extends A { public static boolean main() { return I.s().equals(\"s\"); } }\n",
            ""
            + "interface I { static String s() { return \"s\"; } }\n"
            + "interface J extends I {}\n"
            + "public class Foo implements J { public static boolean main() { return I.s().equals(\"s\"); } }\n",
            ""
            + "interface I { static String s(int x) { return \"s\" + x; } String t(); "
            + "default String d() { return \"d\"; } }\n"
            + "public class Foo implements I {\n"
            + "    public String t() { return \"t\"; }\n"
            + "    public static boolean main() {\n"
            + "        Foo f = new Foo(); return (f.t() + f.d() + I.s(1)).equals(\"tds1\");\n"
            + "    }\n"
            + "}\n",
            ""
            + "public class Foo {\n"
            + "    interface I { static String s() { return \"s\"; } }\n"
            + "    static class X implements I {}\n"
            + "    public static boolean main() { new X(); return I.s().equals(\"s\"); }\n"
            + "}\n",
            ""
            + "interface I { static String s() { return \"s\"; } }\n"
            + "public class Foo implements I {\n"
            + "    public static String s() { return \"p\"; }\n"
            + "    public static boolean main() { return (s() + I.s()).equals(\"ps\"); }\n"
            + "}\n",
            ""
            + "interface I { static String s() { return \"s\"; } }\n"
            + "public class Foo implements I {\n"
            + "    public String s() { return \"p\"; }\n"
            + "    public static boolean main() { return (new Foo().s() + I.s()).equals(\"ps\"); }\n"
            + "}\n",
        };
        for (String cu2 : cus) {
            SimpleCompilerTest sct = new SimpleCompilerTest(cu2, "Foo");
            sct.setSourceVersion(8);
            sct.setTargetVersion(8);
            if (CommonsCompilerTestSuite.JVM_VERSION < 8) {
                sct.assertCookable();
            } else {
                sct.assertResultTrue();
            }
        }
    }

    @Test public void
    test_9_4__Method_Declarations__Private_interface_methods() throws Exception {

        // Static interface methods (a Java 8 feature).

        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 9) return; // Default methods=Java 8+, private interface mathod=Java 9+

        String cu = (
            ""
            + "public interface MyInterface {\n"
            + "    private boolean isTrue2() { return true; }\n"
            + "    default boolean isTrue() { return isTrue2(); }\n"
            + "}\n"
            + "public class Foo { public static boolean main() { return new MyInterface() {}.isTrue(); } }\n"
        );

        {
            SimpleCompilerTest sct = new SimpleCompilerTest(cu, "Foo");
            sct.setSourceVersion(8);
            sct.assertUncookable(
                "Private interface methods only available for target version 9\\+"
                + "|private interface methods are not supported in -source 8"
                + "|modifier private not allowed here"
            );
        }

        {
            SimpleCompilerTest sct = new SimpleCompilerTest(cu, "Foo");
            sct.setSourceVersion(9);
            sct.setTargetVersion(9);
            if (CommonsCompilerTestSuite.JVM_VERSION < 9) {
                if (this.isJanino) sct.assertCookable();
            } else {
                sct.assertResultTrue();
            }
        }
    }

    @Test public void
    test_9_5__Member_Type_Declarations() throws Exception {

        // A member type declaration in an interface is implicitly public and static (issue #43).
        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "import java.lang.reflect.Modifier;\n"
            + "interface I {\n"
            + "    class C { int f() { return 1; } }\n"
            + "    interface J {}\n"
            + "    enum E { X, Y }\n"
            + "    @interface A {}\n"
            + "}\n"
            + "public class Foo {\n"
            + "    static int f(I.E e) { switch (e) { case X: return 1; default: return 2; } }\n"
            + "    public static boolean main() {\n"
            + "        return (\n"
            + "            I.C.class.getModifiers() == (Modifier.PUBLIC | Modifier.STATIC)\n"
            + "            && I.J.class.getModifiers() == (\n"
            + "                Modifier.PUBLIC | Modifier.STATIC | Modifier.ABSTRACT | Modifier.INTERFACE\n"
            + "            )\n"
            + "            && I.E.class.getModifiers() == (\n"
            + "                Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL | 0x4000\n"
            + "            )\n"
            + "            && I.A.class.getModifiers() == (\n"
            + "                Modifier.PUBLIC | Modifier.STATIC | Modifier.ABSTRACT | Modifier.INTERFACE | 0x2000\n"
            + "            )\n"
            + "            && new I.C().f() == 1 && I.E.values().length == 2 && f(I.E.Y) == 2\n"
            + "        );\n"
            + "    }\n"
            + "}\n"
        ), "Foo");
    }

    @Test public void
    test_9_6__Annotation_Types() throws Exception {

        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "public\n"
            + "@interface MyAnno {\n"
            + "}\n"
            + "\n"
            + "public\n"
            + "class Main {\n"
            + "\n"
            + "    public static boolean\n"
            + "    main() throws Exception {\n"
            + "        Class c = Main.class.getClassLoader().loadClass(\"MyAnno\");\n"
            + "//        System.out.println(c.getModifiers());\n"
            + "        return c.getModifiers() == 0x2601;\n" // 2000=ANNOTATION, 400=ABSTRACT, 200=INTERFACE, 1=PUBLIC
            + "    }\n"
            + "}"
        ), "Main");
    }

    @Test public void
    test_9_6_2__Defaults_for_annotation_type_elements() throws Exception {
        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "import java.lang.annotation.Retention;\n"
            + "import java.lang.annotation.RetentionPolicy;\n"
            + "\n"
            + "@Retention(RetentionPolicy.RUNTIME) public\n"
            + "@interface MyAnno { boolean value() default true; }\n"
            + "\n"
            + "@MyAnno public\n"
            + "class Main {\n"
            + "\n"
            + "    public static boolean\n"
            + "    main() throws Exception {\n"
            + "        Class mc = Main.class;\n"
            + "//        System.out.printf(\"mc=%s%n\", mc.toString());\n"
            + "        Class ac = MyAnno.class;\n"
            + "//        System.out.printf(\"ac=%s%n\", ac.toString());\n"
            + "        Object a = mc.getAnnotation(ac);\n"
            + "//        System.out.printf(\"a=%s%n\", a);\n"
            + "        return ((MyAnno) a).value();\n"
            + "    }\n"
            + "}"
        ), "Main");

        // The default value is converted to the type of the element (JLS 9.6.2); see
        // https://github.com/janino-lts/janino/issues/48.
        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "import java.lang.annotation.Retention;\n"
            + "import java.lang.annotation.RetentionPolicy;\n"
            + "\n"
            + "@Retention(RetentionPolicy.RUNTIME) @interface Defaults {\n"
            + "    long     l()   default 0;\n"
            + "    float    f()   default 1;\n"
            + "    double   d()   default 2;\n"
            + "    byte     b()   default 3;\n"
            + "    short    s()   default 4;\n"
            + "    char     c()   default 97;\n"
            + "    int      i()   default 'a';\n"
            + "    long[]   ls()  default { 1, 2 };\n"
            + "    double[] ds()  default 5;\n"
            + "    String   str() default \"x\";\n"
            + "    boolean  z()   default true;\n"
            + "}\n"
            + "\n"
            + "@Defaults public\n"
            + "class Main {\n"
            + "\n"
            + "    public static boolean\n"
            + "    main() {\n"
            + "        Defaults a = (Defaults) Main.class.getAnnotation(Defaults.class);\n"
            + "        return (\n"
            + "            a.l() == 0L && a.f() == 1F && a.d() == 2D && a.b() == 3 && a.s() == 4 && a.c() == 'a'\n"
            + "            && a.i() == 97 && a.ls().length == 2 && a.ls()[1] == 2L && a.ds().length == 1\n"
            + "            && a.ds()[0] == 5D && a.str().equals(\"x\") && a.z()\n"
            + "        );\n"
            + "    }\n"
            + "}"
        ), "Main");
    }

    /**
     * Annotation types declared as member types; see <a
     * href="https://github.com/janino-lts/janino/issues/43">issue #43</a>.
     */
    @Test public void
    test_9_6__Annotation_Types__Member_Annotation_Types() throws Exception {

        // The member annotation type is an annotation type, and its annotations are visible at run time.
        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "import java.lang.annotation.*;\n"
            + "import java.lang.reflect.Modifier;\n"
            + "@Main.A(7) public class Main {\n"
            + "    @Retention(RetentionPolicy.RUNTIME) @interface A { int value() default 5; }\n"
            + "    @Retention(RetentionPolicy.RUNTIME) @interface B { A a(); RetentionPolicy p(); Class<?> c(); }\n"
            + "    private @interface C {}\n"
            + "    @A public static int field;\n"
            + "    @A public Main() {}\n"
            + "    @A(1) @B(a = @A(2), p = RetentionPolicy.CLASS, c = String.class)\n"
            + "    public static void m(@A(3) int x) {}\n"
            + "    @A static class Q {}\n"
            + "    @A enum E { X }\n"
            + "    @A interface I {}\n"
            + "    @A @interface D {}\n"
            + "    public static boolean main() throws Exception {\n"
            + "        A ma = (A) Main.class.getMethod(\"m\", int.class).getAnnotation(A.class);\n"
            + "        B mb = (B) Main.class.getMethod(\"m\", int.class).getAnnotation(B.class);\n"
            + "        Annotation[][] pas = Main.class.getMethod(\"m\", int.class).getParameterAnnotations();\n"
            + "        return (\n"
            + "            A.class.isAnnotation() && A.class.isInterface()\n"
            + "            && A.class.getInterfaces()[0] == Annotation.class\n"
            + "            && A.class.getModifiers() == (\n"
            + "                Modifier.STATIC | Modifier.ABSTRACT | Modifier.INTERFACE | 0x2000\n"
            + "            )\n"
            + "            && C.class.getModifiers() == (\n"
            + "                Modifier.PRIVATE | Modifier.STATIC | Modifier.ABSTRACT | Modifier.INTERFACE | 0x2000\n"
            + "            )\n"
            + "            && A.class.getDeclaringClass() == Main.class && \"A\".equals(A.class.getSimpleName())\n"
            + "            && ((A) Main.class.getAnnotation(A.class)).value() == 7\n"
            + "            && ((A) Main.class.getField(\"field\").getAnnotation(A.class)).value() == 5\n"
            + "            && Main.class.getConstructor().isAnnotationPresent(A.class)\n"
            + "            && ma.value() == 1 && mb.a().value() == 2 && mb.p() == RetentionPolicy.CLASS\n"
            + "            && mb.c() == String.class\n"
            + "            && pas[0].length == 1 && ((A) pas[0][0]).value() == 3\n"
            + "            && Q.class.isAnnotationPresent(A.class) && E.class.isAnnotationPresent(A.class)\n"
            + "            && I.class.isAnnotationPresent(A.class) && D.class.isAnnotationPresent(A.class)\n"
            + "        );\n"
            + "    }\n"
            + "}\n"
        ), "Main");

        // Declared in an interface (implicitly public), used before its declaration, with a fully qualified
        // meta-annotation; the retention CLASS and SOURCE make it invisible at run time.
        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "import java.lang.annotation.*;\n"
            + "import java.lang.reflect.Modifier;\n"
            + "interface I {\n"
            + "    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) @interface A {}\n"
            + "}\n"
            + "@I.A public class Main {\n"
            + "    @A @B @C public static void m() {}\n"
            + "    @Retention(RetentionPolicy.RUNTIME) @interface A {}\n"
            + "    @interface B {}\n"
            + "    @Retention(RetentionPolicy.SOURCE) @interface C {}\n"
            + "    public static boolean main() throws Exception {\n"
            + "        return (\n"
            + "            I.A.class.getModifiers() == (\n"
            + "                Modifier.PUBLIC | Modifier.STATIC | Modifier.ABSTRACT | Modifier.INTERFACE | 0x2000\n"
            + "            )\n"
            + "            && Main.class.isAnnotationPresent(I.A.class)\n"
            + "            && Main.class.getMethod(\"m\").isAnnotationPresent(A.class)\n"
            + "            && !Main.class.getMethod(\"m\").isAnnotationPresent(B.class)\n"
            + "            && !Main.class.getMethod(\"m\").isAnnotationPresent(C.class)\n"
            + "        );\n"
            + "    }\n"
            + "}\n"
        ), "Main");

        // A member annotation type is declared like a top-level annotation type: no type parameters, no "extends",
        // no default or static methods; a class that implements it must implement "annotationType()".
        String u = "'\\{' expected|not allowed|must implement method|compiler.err";
        this.assertCompilationUnitUncookable("class Foo { @interface A<T> {} }", u);
        this.assertCompilationUnitUncookable("class Foo { @interface A extends Runnable {} }", u);
        this.assertCompilationUnitUncookable("class Foo { @interface A { default int f() { return 1; } } }", u);
        this.assertCompilationUnitUncookable("class Foo { @interface A { static int f() { return 1; } } }", u);
        this.assertCompilationUnitUncookable("class Foo { @interface A {} static class Impl implements A {} }", u);

        // A single-element annotation requires an element "value".
        this.assertCompilationUnitUncookable(
            "class Foo { @interface A {} @A(8) void m() {} }",
            "has no element \"value\"|compiler.err.cant.resolve"
        );
    }

    /**
     * The element values are converted to the types of the elements (JLS 9.7.1); see <a
     * href="https://github.com/janino-lts/janino/issues/48">issue #48</a>.
     */
    @Test public void
    test_9_7_1__Normal_Annotations() throws Exception {
        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "import java.lang.annotation.Retention;\n"
            + "import java.lang.annotation.RetentionPolicy;\n"
            + "\n"
            + "@Retention(RetentionPolicy.RUNTIME) @interface Ann {\n"
            + "    long l(); float f(); double d(); byte b(); short s(); char c(); int i();\n"
            + "    long[] ls(); double[] ds(); String str(); boolean z();\n"
            + "}\n"
            + "@Retention(RetentionPolicy.RUNTIME) @interface V     { long value(); }\n"
            + "@Retention(RetentionPolicy.RUNTIME) @interface Outer { V inner(); }\n"
            + "\n"
            + "@Ann(\n"
            + "    l = 5, f = 6, d = 7, b = 8, s = 'a', c = 98, i = 'b', ls = 3, ds = { 1, 2 },\n"
            + "    str = \"y\", z = false\n"
            + ")\n"
            + "@V(7)\n"
            + "@Outer(inner = @V(9))\n"
            + "class Converted {}\n"
            + "\n"
            + "@Ann(\n"
            + "    l = 5L, f = 6F, d = 7D, b = (byte) 8, s = (short) 97, c = 'b', i = 98, ls = { 3L },\n"
            + "    ds = { 1D, 2D }, str = \"y\", z = false\n"
            + ")\n"
            + "class Exact {}\n"
            + "\n"
            + "public\n"
            + "class Main {\n"
            + "\n"
            + "    public static boolean\n"
            + "    main() {\n"
            + "        V     v = (V)     Converted.class.getAnnotation(V.class);\n"
            + "        Outer o = (Outer) Converted.class.getAnnotation(Outer.class);\n"
            + "        return (\n"
            + "            Main.check((Ann) Converted.class.getAnnotation(Ann.class))\n"
            + "            && Main.check((Ann) Exact.class.getAnnotation(Ann.class))\n"
            + "            && v.value() == 7L\n"
            + "            && o.inner().value() == 9L\n"
            + "        );\n"
            + "    }\n"
            + "\n"
            + "    static boolean\n"
            + "    check(Ann a) {\n"
            + "        return (\n"
            + "            a.l() == 5L && a.f() == 6F && a.d() == 7D && a.b() == 8 && a.s() == 97 && a.c() == 'b'\n"
            + "            && a.i() == 98 && a.ls().length == 1 && a.ls()[0] == 3L && a.ds().length == 2\n"
            + "            && a.ds()[1] == 2D && a.str().equals(\"y\") && !a.z()\n"
            + "        );\n"
            + "    }\n"
            + "}"
        ), "Main");
    }

    @Test public void
    test_9_7_2__Marker_Annotations() throws Exception {

        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "import org.codehaus.commons.compiler.tests.annotation.RuntimeRetainedAnnotation1;\n"
            + "\n"
            + "@RuntimeRetainedAnnotation1\n"
            + "public\n"
            + "class Main {\n"
            + "\n"
            + "    public static boolean\n"
            + "    main() {\n"
            + "        RuntimeRetainedAnnotation1 anno = (\n"
            + "            (RuntimeRetainedAnnotation1) Main.class.getAnnotation(RuntimeRetainedAnnotation1.class)\n"
            + "        );\n"
            + "        return anno != null;\n"
            + "    }\n"
            + "}"
        ), "Main");
    }

    @Test public void
    test_9_7_3__Single_Element_Annotations() throws Exception {

        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "import org.codehaus.commons.compiler.tests.annotation.RuntimeRetainedAnnotation2;\n"
            + "\n"
            + "@RuntimeRetainedAnnotation2(\"Foo\")\n"
            + "public\n"
            + "class Main {\n"
            + "\n"
            + "    public static boolean\n"
            + "    main() {\n"
            + "        RuntimeRetainedAnnotation2 anno = (\n"
            + "            (RuntimeRetainedAnnotation2) Main.class.getAnnotation(RuntimeRetainedAnnotation2.class)\n"
            + "        );\n"
            + "        if (anno == null) throw new AssertionError(1);\n"
            + "        if (!anno.value().equals(\"Foo\")) throw new AssertionError(2);\n"
            + "        return true;\n"
            + "    }\n"
            + "}"
        ), "Main");
    }

    @Test public void
    test_9_7_1__Normal_Annotations1() throws Exception {

        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "import org.codehaus.commons.compiler.tests.annotation.RuntimeRetainedAnnotation2;\n"
            + "\n"
            + "@RuntimeRetainedAnnotation2(value = \"Bar\")\n"
            + "public\n"
            + "class Main {\n"
            + "\n"
            + "    public static boolean\n"
            + "    main() {\n"
            + "        RuntimeRetainedAnnotation2 anno = (\n"
            + "            (RuntimeRetainedAnnotation2) Main.class.getAnnotation(RuntimeRetainedAnnotation2.class)\n"
            + "        );\n"
            + "        if (anno == null) throw new AssertionError(1);\n"
            + "        if (!anno.value().equals(\"Bar\")) throw new AssertionError(2);\n"
            + "        return true;\n"
            + "    }\n"
            + "}"
        ), "Main");
    }

    @Test public void
    test_9_7_1__Normal_Annotations2() throws Exception {

        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "import java.util.Arrays;\n"
            + "import org.codehaus.commons.compiler.tests.annotation.RuntimeRetainedAnnotation3;\n"
            + "\n"
            + "@RuntimeRetainedAnnotation3(\n"
            + "    booleanValue     = true,\n"
            + "    byteValue        = (byte) 127,\n"
            + "    shortValue       = (short) 32767,\n"
            + "    intValue         = 99999,\n"
            + "    longValue        = 9999999999L,\n"
            + "    floatValue       = 123.5F,\n"
            + "    doubleValue      = 3.1415927,\n"
            + "    charValue        = 'X',\n"
            + "    stringValue      = \"Foo\",\n"
            + "    classValue       = String.class,\n"
            + "    annotationValue  = @Override,\n"
            + "    stringArrayValue = { \"Foo\", \"Bar\" },\n"
            + "    intArrayValue    = { 1, 2, 3 }\n"
            + ")\n"
            + "public\n"
            + "class Main {\n"
            + "\n"
            + "    public static boolean\n"
            + "    main() {\n"
            + "        RuntimeRetainedAnnotation3 anno = (\n"
            + "            (RuntimeRetainedAnnotation3) Main.class.getAnnotation(RuntimeRetainedAnnotation3.class)\n"
            + "        );\n"
            + "        if (anno == null) throw new AssertionError(1);\n"
            + "\n"
            + "        if (!anno.booleanValue())                                                       throw new AssertionError(2);\n"
            + "        if (anno.byteValue() != 127)                                                    throw new AssertionError(3);\n"
            + "        if (anno.shortValue() != 32767)                                                 throw new AssertionError(4);\n"
            + "        if (anno.intValue() != 99999)                                                   throw new AssertionError(5);\n"
            + "        if (anno.longValue() != 9999999999L)                                            throw new AssertionError(6);\n"
            + "        if (anno.floatValue() != 123.5F)                                                throw new AssertionError(7);\n"
            + "        if (anno.doubleValue() != 3.1415927)                                            throw new AssertionError(8);\n"
            + "        if (anno.charValue() != 'X')                                                    throw new AssertionError(9);\n"
            + "        if (!anno.stringValue().equals(\"Foo\"))                                        throw new AssertionError(10);\n"
            + "        if (anno.classValue() != String.class)                                          throw new AssertionError(11);\n"
            + "        if (!(anno.annotationValue() instanceof Override))                              throw new AssertionError(12);\n"
            + "        if (!Arrays.equals(anno.stringArrayValue(), new String[] { \"Foo\", \"Bar\" })) throw new AssertionError(13);\n"
            + "        if (!Arrays.equals(anno.intArrayValue(), new int[] { 1, 2, 3 }))                throw new AssertionError(14);\n"
            + "        return true;\n"
            + "    }\n"
            + "}"
        ), "Main");
    }

    @Test public void
    test_9_7_1__Normal_Annotations3() throws Exception {

        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "import java.util.Arrays;\n"
            + "import org.codehaus.commons.compiler.tests.annotation.RuntimeRetainedAnnotation5;\n"
            + "\n"
            + "@RuntimeRetainedAnnotation5(String.class)\n"
            + "public\n"
            + "class Main {\n"
            + "\n"
            + "    public static boolean\n"
            + "    main() {\n"
            + "        RuntimeRetainedAnnotation5 anno = (\n"
            + "            (RuntimeRetainedAnnotation5) Main.class.getAnnotation(RuntimeRetainedAnnotation5.class)\n"
            + "        );\n"
            + "        if (anno == null) throw new AssertionError(1);\n"
            + "\n"
            + "        if (!(anno.value() instanceof Class[])) throw new AssertionError(2);\n"
            + "        if (!(anno.value()[0] == String.class)) throw new AssertionError(3);\n"
            + "        return true;\n"
            + "    }\n"
            + "}"
        ), "Main");
    }

    @Test public void
    test_9_7_4__Where_Annotations_May_Appear_field() throws Exception {

        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "import org.codehaus.commons.compiler.tests.annotation.RuntimeRetainedAnnotation2;\n"
            + "\n"
            + "public\n"
            + "class Main {\n"
            + "\n"
            + "    @RuntimeRetainedAnnotation2(\"Foo\") public int field;\n"
            + "\n"
            + "    public static boolean\n"
            + "    main() throws Exception {\n"
            + "        RuntimeRetainedAnnotation2 anno = ((RuntimeRetainedAnnotation2) Main.class.getField(\n"
            + "            \"field\"\n"
            + "        ).getAnnotation(RuntimeRetainedAnnotation2.class));\n"
            + "        if (anno == null) throw new AssertionError(1);\n"
            + "        if (!anno.value().equals(\"Foo\")) throw new AssertionError(2);\n"
            + "        return true;\n"
            + "    }\n"
            + "}"
        ), "Main");
    }

    @Test public void
    test_9_7_4__Where_Annotations_May_Appear_method() throws Exception {

        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "import org.codehaus.commons.compiler.tests.annotation.RuntimeRetainedAnnotation2;\n"
            + "\n"
            + "public\n"
            + "class Main {\n"
            + "\n"
            + "    @RuntimeRetainedAnnotation2(\"Foo\") public void method() {}\n"
            + "\n"
            + "    public static boolean\n"
            + "    main() throws Exception {\n"
            + "        RuntimeRetainedAnnotation2 anno = ((RuntimeRetainedAnnotation2) Main.class.getMethod(\n"
            + "            \"method\"\n"
            + "        ).getAnnotation(RuntimeRetainedAnnotation2.class));\n"
            + "        if (anno == null) throw new AssertionError(1);\n"
            + "        if (!anno.value().equals(\"Foo\")) throw new AssertionError(2);\n"
            + "        return true;\n"
            + "    }\n"
            + "}"
        ), "Main");
    }

    /**
     * Annotations on formal parameters; see <a href="https://github.com/janino-lts/janino/issues/43">issue #43</a>.
     */
    @Test public void
    test_9_7_4__Where_Annotations_May_Appear_parameter() throws Exception {

        // The number of annotations per parameter is compared, because the position of the annotated parameter in
        // the array differs between the compilers for the constructors of inner classes (JVMS 4.7.18 leaves the
        // treatment of implicit parameters to the compiler).
        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "import java.lang.annotation.Annotation;\n"
            + "import org.codehaus.commons.compiler.tests.annotation.RuntimeRetainedAnnotation2;\n"
            + "\n"
            + "public\n"
            + "class Main {\n"
            + "\n"
            + "    @interface Invisible {}\n"
            + "\n"
            + "    public Main(@RuntimeRetainedAnnotation2(\"Ctor\") int x) {}\n"
            + "    public static void method(\n"
            + "        @RuntimeRetainedAnnotation2(\"Foo\") int x,\n"
            + "        @Invisible @SuppressWarnings(\"unused\") int y,\n"
            + "        @Deprecated @RuntimeRetainedAnnotation2(\"Bar\") final String... z\n"
            + "    ) {}\n"
            + "    interface I { void m(@RuntimeRetainedAnnotation2(\"I\") int x); }\n"
            + "    class Inner { Inner(@RuntimeRetainedAnnotation2(\"Inner\") int x) {} }\n"
            + "    enum E { X(1); E(@RuntimeRetainedAnnotation2(\"E\") int v) {} }\n"
            + "\n"
            + "    static int count(Annotation[][] pas) {\n"
            + "        int n = 0;\n"
            + "        for (Annotation[] pa : pas) n += pa.length;\n"
            + "        return n;\n"
            + "    }\n"
            + "\n"
            + "    public static boolean\n"
            + "    main() throws Exception {\n"
            + "        Annotation[][] pas = Main.class.getMethod(\n"
            + "            \"method\", int.class, int.class, String[].class\n"
            + "        ).getParameterAnnotations();\n"
            + "        if (pas.length != 3) throw new AssertionError(1);\n"
            + "        if (pas[0].length != 1 || pas[1].length != 0 || pas[2].length != 2) {\n"
            + "            throw new AssertionError(2);\n"
            + "        }\n"
            + "        if (!((RuntimeRetainedAnnotation2) pas[0][0]).value().equals(\"Foo\")) {\n"
            + "            throw new AssertionError(3);\n"
            + "        }\n"
            + "        pas = Main.class.getConstructor(int.class).getParameterAnnotations();\n"
            + "        if (pas.length != 1 || pas[0].length != 1) throw new AssertionError(4);\n"
            + "        if (!((RuntimeRetainedAnnotation2) pas[0][0]).value().equals(\"Ctor\")) {\n"
            + "            throw new AssertionError(5);\n"
            + "        }\n"
            + "        pas = I.class.getMethod(\"m\", int.class).getParameterAnnotations();\n"
            + "        if (pas.length != 1 || pas[0].length != 1) throw new AssertionError(6);\n"
            + "        if (count(Inner.class.getDeclaredConstructors()[0].getParameterAnnotations()) != 1) {\n"
            + "            throw new AssertionError(7);\n"
            + "        }\n"
            + "        if (count(E.class.getDeclaredConstructors()[0].getParameterAnnotations()) != 1) {\n"
            + "            throw new AssertionError(8);\n"
            + "        }\n"
            + "        return true;\n"
            + "    }\n"
            + "}"
        ), "Main");

        // A duplicate annotation on a parameter.
        this.assertCompilationUnitUncookable(
            "class Foo { void m(@Deprecated @Deprecated int x) {} }",
            "(?i)duplicate annotation|is not .* repeatable"
        );
    }

    @Test public void
    test_10_6__Array_Initializers() throws Exception {
        this.assertCompilationUnitCookable(
            ""
            + "class Foo {\n"
            + "    String[] sa = { \"a\", \"b\" };\n"
            + "    String sa2[] = { \"a\", \"b\" };\n"
            + "    void meth() {\n"
            + "        Number[] na = { 1, 2, 3.3 };\n"
            + "        Number na2[] = { 1, 2, 3.3 };\n"
            + "    }\n"
            + "}\n"
        );
    }

    @Test public void
    test_10_7__Array_Members() throws Exception {

        // "clone()" of an array has the array type.
        this.assertScriptReturnsTrue("int[] a = { 1, 2 }; int[] b = a.clone(); return b != a && b[1] == 2;");
        this.assertScriptReturnsTrue("int[] a = { 1, 2 }; return a.clone().length == 2;");
        this.assertScriptReturnsTrue("int[] a = { 1, 2 }; return a.clone()[1] == 2;");
        this.assertScriptReturnsTrue("String[] a = { \"x\" }; String[] b = a.clone(); return b[0] == \"x\";");

        // The clone of a multi-dimensional array is shallow.
        this.assertScriptReturnsTrue("int[][] a = { { 3 } }; int[][] b = a.clone(); return b != a && b[0] == a[0];");
        this.assertScriptReturnsTrue("int[][] a = { { 3 } }; return a.clone()[0][0] == 3;");

        // The clone in other contexts: expression statement, argument, comparison, string concatenation, "Object".
        this.assertScriptReturnsTrue("int[] a = { 1 }; a.clone(); return true;");
        this.assertScriptReturnsTrue("int[] a = { 1 }; return java.util.Arrays.equals(a, a.clone());");
        this.assertScriptReturnsTrue("int[] a = { 1 }; return a.clone() != a;");
        this.assertScriptReturnsTrue("int[] a = { 1 }; return (\"\" + a.clone()).startsWith(\"[I@\");");
        this.assertScriptReturnsTrue("int[] a = { 1 }; Object o = a.clone(); return o instanceof int[];");
        this.assertScriptReturnsTrue("int[] a = { 1 }; int[] b = (int[]) a.clone(); return b[0] == 1;");
        this.assertScriptReturnsTrue("int[] a = { 1 }; return a.clone().clone()[0] == 1;");

        // "clone()" of an array does not throw "CloneNotSupportedException".
        this.assertScriptReturnsTrue("int[] a = { 1 }; try { return a.clone()[0] == 1; } finally { }");

        // "length" is a final field.
        this.assertScriptUncookable("int[] a = { 1 }; a.length = 2;");
    }

    @Test public void
    test_11_2__Compile_Time_Checking_of_Exceptions__all_uppercase_names() throws Exception {

        // An exception class whose simple name consists of uppercase letters only is not a type parameter (issue
        // #65): the "catch" of such an exception is reachable, and the "Exceptions" attribute lists the exception.
        this.assertCompilationUnitMainReturnsTrue(
            ""
            + "import java.lang.reflect.*;\n"
            + "public class Foo {\n"
            + "    static class X extends Exception {}\n"
            + "    static class IOEXC extends Exception {}\n"
            + "    static void m(boolean b) throws X { if (b) throw new X(); }\n"
            + "    static void n() throws X, IOEXC { throw new IOEXC(); }\n"
            + "    static <T extends Exception> void g(boolean b) throws T, X { if (b) throw new X(); }\n"
            + "    Foo(boolean b) throws X { if (b) throw new X(); }\n"
            + "    public static boolean main() throws Exception {\n"
            + "        String r = \"\";\n"
            + "        try { m(true); r += \"-\"; } catch (X e) { r += \"X\"; }\n"
            + "        try { m(false); r += \"-\"; } catch (X e) { r += \"X\"; }\n"
            + "        try { n(); } catch (X | IOEXC e) { r += e.getClass().getSimpleName(); }\n"
            + "        try { g(true); } catch (X e) { r += \"G\"; }\n"
            + "        try { new Foo(true); } catch (X e) { r += \"C\"; }\n"
            + "        Method mm = Foo.class.getDeclaredMethod(\"m\", boolean.class);\n"
            + "        Constructor<?> c = Foo.class.getDeclaredConstructor(boolean.class);\n"
            + "        return (\n"
            + "            r.equals(\"X-IOEXCGC\")\n"
            + "            && mm.getExceptionTypes().length == 1 && mm.getExceptionTypes()[0] == X.class\n"
            + "            && c.getExceptionTypes().length == 1 && c.getExceptionTypes()[0] == X.class\n"
            + "        );\n"
            + "    }\n"
            + "}\n",
            "Foo"
        );

        // Not thrown in the "try" block.
        this.assertCompilationUnitUncookable(
            ""
            + "class Foo {\n"
            + "    static class X extends Exception {}\n"
            + "    static void m() {}\n"
            + "    void f() { try { m(); } catch (X e) {} }\n"
            + "}\n",
            "Catch clause is unreachable|compiler.err.except.never.thrown.in.try"
        );
    }

    @Test public void
    test_14_3__Local_class_declarations() throws Exception {
        this.assertScriptReturnsTrue(
            "class S2 extends SC { public int foo() { return 37; } }; return new S2().foo() == 37;"
        );

        // Local class modifiers: "abstract", "final", "strictfp" and annotations.
        this.assertScriptReturnsTrue(
            "final class L {} return java.lang.reflect.Modifier.isFinal(L.class.getModifiers());"
        );
        this.assertScriptReturnsTrue(
            "abstract class L { abstract int f(); } class M extends L { int f() { return 7; } }"
            + " return new M().f() == 7 && java.lang.reflect.Modifier.isAbstract(L.class.getModifiers());"
        );
        this.assertScriptReturnsTrue("strictfp class L { double f() { return 1.5; } } return new L().f() == 1.5;");
        this.assertScriptReturnsTrue("@Deprecated class L {} return L.class.isAnnotationPresent(Deprecated.class);");
        this.assertScriptReturnsTrue(
            "@Deprecated final class L { int f() { return 1; } } return new L().f() == 1;"
        );
        this.assertScriptReturnsTrue("/** A doc comment. */ final class L {} return new L() != null;");

        // Other modifiers are not allowed (JLS 14.3), and "abstract" and "final" are mutually exclusive.
        this.assertScriptUncookable("static class L {}");
        this.assertScriptUncookable("public class L {}");
        this.assertScriptUncookable("private class L {}");
        this.assertScriptUncookable("abstract final class L {}");

        // An abstract local class cannot be instantiated, a final one cannot be extended.
        this.assertScriptUncookable("abstract class L {} new L();");
        this.assertScriptUncookable("final class L {} class M extends L {}");

        // Local variable declarations with modifiers are unaffected. (In a script, a declaration with modifiers
        // may also be a method declaration, and the modifiers are not checked; hence the class body.)
        this.assertScriptReturnsTrue("final int x = 1; return x == 1;");
        this.assertScriptReturnsTrue("@SuppressWarnings(\"unused\") final int x = 1; return x == 1;");
        this.assertClassBodyUncookable("void f() { abstract int x; }");
        this.assertClassBodyUncookable("void f() { strictfp int x = 1; }");
    }

    @Test public void
    test_14_4__Local_Variable_Declaration_Statements() throws Exception {
        this.assertScriptReturnsTrue(
            "class S2 extends SC { public int foo() { return 37; } }; return new S2().foo() == 37;"
        );

        String script = "var f = java.util.function.Function.<String>identity();\n";
        if (this.isJanino)                                            this.assertScriptUncookable(script, "NYI");
        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION >= 10) this.assertScriptExecutable(script);
    }

    @Test public void
    test_14_8__Expression_statements() throws Exception {
        this.assertScriptReturnsTrue("int a; a = 8; ++a; a++; if (a != 10) return false; --a; a--; return a == 8;");
        this.assertScriptExecutable("System.currentTimeMillis();");
        this.assertScriptExecutable("new Object();");
        this.assertScriptUncookable("new Object[3];", "not a statement|not allowed as an expression statement");
        this.assertScriptUncookable("int a; a;",      "not a statement|not allowed as an expression statement");
    }

    @Test public void
    test_14_10__The_assert_statement() throws Exception {

        this.assertScriptExecutable("assert true;");
        this.assertScriptReturnsTrue("try { assert false;                  } catch (AssertionError ae) { return true;                          } return false;");
        this.assertScriptReturnsTrue("try { assert false : \"x\";          } catch (AssertionError ae) { return \"x\".equals(ae.getMessage()); } return false;");
        this.assertScriptReturnsTrue("try { assert false : 3;              } catch (AssertionError ae) { return \"3\".equals(ae.getMessage()); } return false;");
        this.assertScriptReturnsTrue("try { assert false : new Integer(8); } catch (AssertionError ae) { return \"8\".equals(ae.getMessage()); } return false;");
    }

    @Test public void
    test_14_11__The_switch_statement() throws Exception {
        this.assertScriptReturnsTrue("int x = 37; switch (x) {} return x == 37;");
        this.assertScriptReturnsTrue("int x = 37; switch (x) { default: ++x; break; } return x == 38;");
        this.assertScriptReturnsTrue(
            "int x = 37; switch (x) { case 36: case 37: case 38: x += x; break; } return x == 74;"
        );
        this.assertScriptReturnsTrue(
            "int x = 37; switch (x) { case 36: case 37: case 1000: x += x; break; } return x == 74;"
        );
        this.assertScriptReturnsTrue(
            "int x = 37; switch (x) { case -10000: break; case 10000: break; } return x == 37;"
        );
        this.assertScriptReturnsTrue(
            "int x = 37; switch (x) { case -2000000000: break; case 2000000000: break; } return x == 37;"
        );
    }

    @Test public void
    test_14_11__The_switch_statement_enum() throws Exception {
        this.assertScriptReturnsTrue(
            ""
            + "import java.lang.annotation.ElementType;\n"
            + "\n"
            + "ElementType x = ElementType.FIELD;\n"
            + "switch (x) {\n"
            + "case ANNOTATION_TYPE:\n"
            + "    return false;\n"
            + "case FIELD:\n"
            + "    return true;\n"
            + "default:\n"
            + "    break;\n"
            + "}\n"
            + "return false;"
        );
    }

    @Test public void
    test_14_11__The_switch_statement_String1() throws Exception {

        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 7) return;

        this.assertScriptReturnsTrue(
            ""
            + "String s = \"a\";\n"
            + "\n"
            + "switch (s) {\n"
            + "case \"a\": case \"b\": case \"c\":\n"
            + "    return true;\n"
            + "case \"d\": case \"e\": case \"f\":\n"
            + "    return false;\n"
            + "default:\n"
            + "    return false;"
            + "}\n"
        );
    }

    @Test public void
    test_14_11__The_switch_statement_String2() throws Exception {

        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 7) return;

        this.assertScriptReturnsTrue(
            ""
            + "String s = \"f\";\n"
            + "\n"
            + "switch (s) {\n"
            + "case \"a\": case \"b\": case \"c\":\n"
            + "    return false;\n"
            + "case \"d\": case \"e\": case \"f\":\n"
            + "    return true;\n"
            + "default:\n"
            + "    return false;"
            + "}\n"
        );
    }

    @Test public void
    test_14_11__The_switch_statement_String3() throws Exception {

        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 7) return;

        this.assertScriptReturnsTrue(
            ""
            + "String s = \"g\";\n"
            + "\n"
            + "switch (s) {\n"
            + "case \"a\": case \"b\": case \"c\":\n"
            + "    return false;\n"
            + "case \"d\": case \"e\": case \"f\":\n"
            + "    return false;\n"
            + "default:\n"
            + "    return true;"
            + "}\n"
        );
    }

    @Test public void
    test_14_11__The_switch_statement_String4() throws Exception {

        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 7) return;

        this.assertScriptReturnsTrue(
            ""
            + "String s = \"g\";\n"
            + "\n"
            + "switch (s) {\n"
            + "case \"a\": case \"b\": case \"c\":\n"
            + "    return false;\n"
            + "case \"d\": case \"e\": case \"f\":\n"
            + "    return false;\n"
            + "}\n"
            + "return true;"
        );
    }

    @Test public void
    test_14_11__The_switch_statement_String5() throws Exception {

        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 7) return;

        String s1 = "AaAaAa", s2 = "AaAaBB";
        Assert.assertEquals(s1.hashCode(), s2.hashCode());

        this.assertScriptReturnsTrue(
            ""
            + "switch (\"" + s1 + "\") {\n"
            + "case \"" + s1 + "\":\n"
            + "    return true;\n"
            + "}\n"
            + "return false;"
        );

        this.assertScriptReturnsTrue(
            ""
            + "switch (\"" + s1 + "\") {\n"
            + "case \"" + s1 + "\":\n"
            + "case \"" + s2 + "\":\n"
            + "    return true;\n"
            + "}\n"
            + "return false;"
        );

        this.assertScriptReturnsTrue(
            ""
            + "switch (\"" + s1 + "\") {\n"
            + "case \"" + s1 + "\":\n"
            + "    return true;\n"
            + "case \"" + s2 + "\":\n"
            + "    return false;\n"
            + "}\n"
            + "return false;"
        );

        this.assertScriptReturnsTrue(
            ""
            + "switch (\"" + s1 + "\") {\n"
            + "case \"" + s2 + "\":\n"
            + "    return false;\n"
            + "}\n"
            + "return true;"
        );
    }

    @Test public void
    test_14_11__The_switch_statement_String_DuplicateCaseValue() throws Exception {

        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 7) return;

        this.assertScriptUncookable((
            ""
            + "String s = \"c\";\n"
            + "\n"
            + "switch (s) {\n"
            + "case \"a\": case \"b\": case \"c\":\n"
            + "    return;\n"
            + "case \"c\": case \"d\": case \"e\":\n"
            + "    return;\n"
            + "default:\n"
            + "    return;"
            + "}\n"
        ), "(?i)Duplicate case label");
    }

    @Test public void
    test_14_11__The_switch_statement_DefiniteAssignment() throws Exception {
        this.assertScriptReturnsTrue(
            ""
            + "int a = 2;\n"
            + "int b; // = -99;\n"  // <= Do not initialize "b" here.
            + "// This will compile into a TABLESWITCH because the case labels are so contiguous:\n"
            + "switch (a) {\n"
            + "case 0:\n"
            + "    b = 0;\n"
            + "    break;\n"
            + "case 1:\n"
            + "    b = 11;\n"
            + "    break;\n"
            + "case 2:\n"
            + "    b = 22;\n"
            + "    break;\n"
            + "default:\n"
            + "    throw new AssertionError();\n"
            + "}\n"
            + "return b == 22;\n"   // <= Is "b" initialized at this point?
        );
    }

    @Test public void
    test_14_14_1__The_basic_for_statement__captured_loop_variable() throws Exception {

        // An inner class may access the "final" loop variable of a basic FOR statement (issue #75; before, that was
        // an internal compiler error), and a loop variable that is effectively final (issue #24).
        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "public class Main {\n"
            + "    public static boolean\n"
            + "    main() {\n"
            + "        String r = \"\";\n"
            + "        for (final int i = 1, j = 2; i < 9; ) {\n"
            + "            r += new Object() { public String toString() { return \"\" + (i + j); } };\n"
            + "            break;\n"
            + "        }\n"
            + "        for (int i = 4; i < 9; ) {\n"
            + "            class L { int get() { return i; } }\n"
            + "            if (i > 0) r += new L().get();\n"
            + "            break;\n"
            + "        }\n"
            + "        for (final int i = 5; new Object() { boolean b() { return i < 9; } }.b(); ) {\n"
            + "            r += i;\n"
            + "            break;\n"
            + "        }\n"
            + "        return r.equals(\"345\");\n"
            + "    }\n"
            + "}\n"
        ), "Main");

        // A loop variable that is incremented is not effectively final.
        this.assertCompilationUnitUncookable(
            ""
            + "public class Main {\n"
            + "    void f() {\n"
            + "        for (int i = 0; i < 3; i++) {\n"
            + "            Runnable r = new Runnable() { public void run() { int y = i; } };\n"
            + "        }\n"
            + "    }\n"
            + "}\n",
            "Cannot access non-final local variable|compiler.err.cant.ref.non.effectively.final.var"
        );
    }

    @Test public void
    test_14_14_2_1__The_enhanced_for_statement_Iterable1() throws Exception {
        this.assertScriptReturnsTrue(
            "String x = \"A\";\n"
            + "for (Object y : java.util.Arrays.asList(new String[] { \"B\", \"C\" })) x += y;\n"
            + "return x.equals(\"ABC\");"
        );
    }

    @Test public void
    test_14_14_2_1__The_enhanced_for_statement_Iterable2() throws Exception {
        this.assertScriptReturnsTrue(
            "String x = \"A\";\n"
            + "for (String y : java.util.Arrays.asList(new String[] { \"B\", \"C\" })) x += y.length();\n"
            + "return x.equals(\"A11\");"
        );
    }

    @Test public void
    test_14_14_2_1__The_enhanced_for_statement_Iterable3() throws Exception {
        String script = (
            ""
            + "String x = \"A\";\n"
            + "for (var y : java.util.Arrays.asList(new String[] { \"B\", \"C\" })) x += y.length();\n"
            + "return x.equals(\"A11\");"
        );
        if (this.isJanino)                                            this.assertScriptUncookable(script, "NYI");
        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION >= 10) this.assertScriptReturnsTrue(script);
    }

    @Test public void
    test_14_14_2_1__The_enhanced_for_statement_Iterable4() throws Exception {
        this.assertScriptReturnsTrue(
            "java.util.List<Integer> l = new java.util.ArrayList<Integer>();\n"
            + "l.add(1);\n"
            + "l.add(2);\n"
            + "l.add(3);\n"
            + "for (int i : l) {\n"
            + "    if (i != 1 && i != 2 && i != 3) return false;\n"
            + "}\n"
            + "return true;\n"
        );
    }

    @Test public void
    test_14_14_2_2__The_enhanced_for_statement_Array() throws Exception {

        // Primitive array.
        this.assertScriptReturnsTrue(
            "int x = 1; for (int y : new int[] { 1, 2, 3 }) x += x * y; return x == 24;"
        );
        this.assertScriptReturnsTrue(
            "int x = 1; for (int y : new short[] { 1, 2, 3 }) x += x * y; return x == 24;"
        );
        this.assertScriptUncookable(
            "int x = 1; for (short y : new int[] { 1, 2, 3 }) x += x * y;",
            "conversion not possible|possible loss of precision|possible lossy conversion"
        );

        // Object array.
        this.assertScriptReturnsTrue(
            "String x = \"A\"; for (String y : new String[] { \"B\", \"C\" }) x += y; return x.equals(\"ABC\");"
        );
        this.assertScriptReturnsTrue(
            "String x = \"A\"; for (Object y : new String[] { \"B\", \"C\" }) x += y; return x.equals(\"ABC\");"
        );
        this.assertScriptUncookable(
            "String x = \"A\"; for (Number y : new String[] { \"B\", \"C\" }) x += y; return x.equals(\"ABC\");",
            "conversion not possible|incompatible types"
        );
        this.assertScriptReturnsTrue(
            "String x = \"A\"; String[] sa = { \"B\",\"C\" }; for (String y : sa) x += y; return x.equals(\"ABC\");"
        );
        this.assertScriptReturnsTrue(
            ""
            + "final StringBuilder sb = new StringBuilder();\n"
            + "for (final String y : new String[] { \"A\", \"B\", \"C\" }) {\n"
            + "    new Runnable() {\n"
            + "        public void run() { sb.append(y); }\n"
            + "    }.run();\n"
            + "}\n"
            + "return sb.toString().equals(\"ABC\");"
        );
    }

    @Test public void
    test_14_15__The_break_Statement() throws Exception {
        this.assertScriptReturnsTrue(
            ""
            + "int result = 0;\n"
            + "for (int i = 0; i < 10; i++) {\n"
            + "    LABEL: {\n"
            + "        if (i == 3) break LABEL;\n"
            + "        if (i == 5) break;\n"       // <= Breaks the FOR loop, not the labeled BREAK!
            + "        result++;\n"
            + "    }\n"
            + "}\n"
            + "return result == 4;\n"
        );
    }

    @Test public void
    test_14_20__The_try_Statement__Multi_catch() throws Exception {

        // A multi-catch clause ("catch (A | B e)") catches each of its alternatives; the type of the parameter is
        // the nearest common superclass of the alternatives; the parameter is implicitly final, so an anonymous
        // class may access it; "throw e;" rethrows the alternatives (precise rethrow).
        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "import java.io.IOException;\n"
            + "import java.sql.SQLException;\n"
            + "public class Main {\n"
            + "    static class ExA extends Exception { }\n"
            + "    static class ExB extends Exception { }\n"
            + "    static StringBuilder t = new StringBuilder();\n"
            + "    static class R implements AutoCloseable { public void close() { t.append('c'); } }\n"
            + "    static void thrower(int i) throws IOException, SQLException, ExA, ExB {\n"
            + "        switch (i) {\n"
            + "        case 1: throw new IllegalStateException(\"ise\");\n"
            + "        case 2: throw new IllegalArgumentException(\"iae\");\n"
            + "        case 3: throw new IOException(\"io\");\n"
            + "        case 4: throw new SQLException(\"sql\");\n"
            + "        case 5: throw new ExA();\n"
            + "        case 6: throw new ExB();\n"
            + "        case 7: throw new ArithmeticException(\"ae\");\n"
            + "        default: break;\n"
            + "        }\n"
            + "    }\n"
            + "    static String f(int i) {\n"
            + "        try {\n"
            + "            thrower(i);\n"
            + "            return \"-\";\n"
            + "        } catch (IllegalStateException | IllegalArgumentException e) {\n"
            + "            RuntimeException re = e;\n"
            + "            return \"R\" + re.getMessage();\n"
            + "        } catch (final IOException | SQLException e) {\n"
            + "            Exception x = e;\n"
            + "            return \"E\" + x.getMessage();\n"
            + "        } catch (ExA | ExB e) {\n"
            + "            return e.getClass().getSimpleName();\n"
            + "        } catch (ArithmeticException | StackOverflowError e) {\n"
            + "            Throwable th = e;\n"
            + "            return \"T\" + th.getMessage();\n"
            + "        }\n"
            + "    }\n"
            + "    static String rethrow(int i) throws IOException, SQLException {\n"
            + "        try {\n"
            + "            thrower(i);\n"
            + "            return \"-\";\n"
            + "        } catch (IOException | SQLException e) {\n"
            + "            throw e;\n"
            + "        } catch (ExA | ExB e) {\n"
            + "            return \"ab\";\n"
            + "        }\n"
            + "    }\n"
            + "    static String g(int i) {\n"
            + "        try {\n"
            + "            return rethrow(i);\n"
            + "        } catch (IOException e) {\n"
            + "            return \"I\";\n"
            + "        } catch (SQLException e) {\n"
            + "            return \"S\";\n"
            + "        }\n"
            + "    }\n"
            + "    static String h() {\n"
            + "        for (int i = 0; i < 4; i++) {\n"
            + "            try (R r = new R()) {\n"
            + "                if (i == 1) throw new IllegalStateException();\n"
            + "                if (i == 2) throw new IllegalArgumentException(\"x\");\n"
            + "                t.append(i);\n"
            + "            } catch (IllegalStateException | IllegalArgumentException e) {\n"
            + "                Runnable rn = new Runnable() {\n"
            + "                    public void run() { t.append(e.getMessage() == null ? 'C' : 'D'); }\n"
            + "                };\n"
            + "                rn.run();\n"
            + "                if (i == 1) continue;\n"
            + "                break;\n"
            + "            } finally {\n"
            + "                t.append('F');\n"
            + "            }\n"
            + "            t.append('.');\n"
            + "        }\n"
            + "        return t.toString();\n"
            + "    }\n"
            + "    public static boolean main() {\n"
            + "        return (\n"
            + "            \"-\".equals(f(0)) && \"Rise\".equals(f(1)) && \"Riae\".equals(f(2))\n"
            + "            && \"Eio\".equals(f(3)) && \"Esql\".equals(f(4))\n"
            + "            && \"ExA\".equals(f(5)) && \"ExB\".equals(f(6)) && \"Tae\".equals(f(7))\n"
            + "            && \"-\".equals(g(0)) && \"I\".equals(g(3)) && \"S\".equals(g(4)) && \"ab\".equals(g(5))\n"
            + "            && \"0cF.cCFcDF\".equals(h())\n"
            + "        );\n"
            + "    }\n"
            + "}\n"
        ), "Main");

        // A multi-catch parameter must not be assigned; the alternatives must not be related by subclassing, and
        // must be throwable.
        this.assertCompilationUnitUncookable((
            ""
            + "class Foo { void f() { try { throw new IllegalStateException(); }"
            + " catch (IllegalStateException | IllegalArgumentException e) { e = null; } } }"
        ), "must not be assigned|may not be assigned|compiler.err.multicatch.parameter.may.not.be.assigned");
        this.assertCompilationUnitUncookable((
            ""
            + "class Foo { void f() { try { throw new IllegalStateException(); }"
            + " catch (RuntimeException | IllegalArgumentException e) { } } }"
        ), "is a subtype of|related by subclassing|compiler.err.multicatch.types.must.be.disjoint");
        this.assertCompilationUnitUncookable((
            ""
            + "class Foo { void f() { try { throw new IllegalStateException(); }"
            + " catch (IllegalStateException | IllegalStateException e) { } } }"
        ), "is a subtype of|related by subclassing|compiler.err.multicatch.types.must.be.disjoint");
        this.assertCompilationUnitUncookable((
            ""
            + "class Foo { void f() { try { throw new IllegalStateException(); }"
            + " catch (String | IllegalStateException e) { } } }"
        ), "is not assignable to \"Throwable\"|incompatible types|compiler.err.prob.found.req");
    }

    @Test public void
    test_14_20_1__Execution_of_try_catch__1() throws Exception {
        this.assertClassBodyMainReturnsTrue(
            ""
            + "static void meth() throws Throwable {\n"
            + "}\n"
            + "\n"
            + "public static boolean main() { \n"
            + "    try {\n"
            + "        meth();\n"
            + "    } catch (java.io.FileNotFoundException fnfe) {\n"
            + "        return false;\n"
            + "    } catch (java.io.IOException ioe) {\n"
            + "        return false;\n"
            + "    } catch (Exception e) {\n"
            + "        return false;\n"
            + "    } catch (Throwable t) {\n"
            + "        return false;\n"
            + "    }\n"
            + "    return true;\n"
            + "}\n"
        );
    }

    @Test public void
    test_14_20_1__Execution_of_try_catch__2() throws Exception {
        this.assertClassBodyUncookable(
            ""
            + "static void meth() throws Throwable {\n"
            + "}\n"
            + "\n"
            + "public static boolean main() { \n"
            + "    try {\n"
            + "        meth();\n"
            + "    } catch (java.io.FileNotFoundException fnfe) {\n"
            + "        return false;\n"
            + "    } catch (Exception e) {\n"
            + "        return false;\n"
            + "    } catch (java.io.IOException ioe) {\n"  // <= Hidden by preceding "catch (Exception)"
            + "        return false;\n"
            + "    } catch (Throwable t) {\n"
            + "        return false;\n"
            + "    }\n"
            + "    return true;\n"
            + "}\n"
        );
    }

    @Test public void
    test_14_20_1__Execution_of_try_catch__3() throws Exception {
        this.assertClassBodyMainReturnsTrue(
            ""
            + "static void meth() throws java.io.IOException {\n"
            + "}\n"
            + "\n"
            + "public static boolean main() { \n"
            + "    try {\n"
            + "        meth();\n"
            + "    } catch (java.io.FileNotFoundException fnfe) {\n"
            + "        return false;\n"
            + "    } catch (java.io.IOException ioe) {\n"
            + "        return false;\n"
            + "    } catch (Exception e) {\n"
            + "        return false;\n"
            + "    } catch (Throwable t) {\n"
            + "        return false;\n"
            + "    }\n"
            + "    return true;\n"
            + "}\n"
        );
    }

    @Test public void
    test_14_20_1__Execution_of_try_catch__4() throws Exception {

        // JAVAC does not detect this condition although, I believe, it should, according to the
        // JLS.
        Assume.assumeFalse(this.isJdk);

        this.assertClassBodyUncookable(
            ""
            + "static void meth() throws java.io.FileNotFoundException {\n"
            + "}\n"
            + "\n"
            + "public static boolean main() { \n"
            + "    try {\n"
            + "        meth();\n"
            + "    } catch (java.io.FileNotFoundException fnfe) {\n"
            + "        return false;\n"
            + "    } catch (java.io.IOException ioe) {\n" // <= Not thrown by 'meth()', but JDKs 6...8 don't detect that
            + "        return false;\n"
            + "    } catch (Exception e) {\n"
            + "        return false;\n"
            + "    } catch (Throwable t) {\n"
            + "        return false;\n"
            + "    }\n"
            + "    return true;\n"
            + "}\n"
        );
    }

    @Test public void
    test_14_20_1__Execution_of_try_catch__5() throws Exception {
        this.assertClassBodyMainReturnsTrue(
            ""
            + "public static boolean main() { \n"
            + "    try {\n"
            + "        if (true) throw new java.io.IOException();\n"
            + "    } catch (java.io.FileNotFoundException fnfe) {\n" // <= Not thrown by TRY block, but neither JDK 6
            + "        return false;\n"                              //    nor JANINO detect that
            + "    } catch (java.io.IOException ioe) {\n"
            + "        return true;\n"
            + "    } catch (Exception e) {\n"
            + "        return false;\n"
            + "    } catch (Throwable t) {\n"
            + "        return false;\n"
            + "    }\n"
            + "    return false;\n"
            + "}\n"
        );
    }

    @Test public void
    test_14_20_1__Execution_of_try_catch__6() throws Exception {
        this.assertCompilationUnitCookable(
            ""
            + "public class TestIt {\n"
            + "    public static class MyException extends Exception implements Runnable {\n"
            + "        public MyException(String st) {\n"
            + "            super(st);\n"
            + "        }\n"
            + "\n"
            + "        public void run() {\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    public void foo() throws MyException {\n"
            + "        if (true) {\n"
            + "            try {\n"
            + "                if (false != false) {\n"
            + "                    throw new MyException(\"my exc\");\n"
            + "                }\n"
            + "                System.out.println(\"abc\");\n"
            + "                System.out.println(\"xyz\");\n"
            + "\n"
            + "            } catch (MyException e) {\n"
            + "                throw new java.lang.RuntimeException(e);\n"
            + "            }\n"
            + "        }\n"
            + "    }\n"
            + "\n"
            + "    public static boolean main() {\n"
            + "        try {\n"
            + "            new TestIt().foo();\n"
            + "        } catch (MyException e) {\n"
            + "            System.out.println(\"caught\");\n"
            + "        }\n"
            + "        return true;\n"
            + "    }\n"
            + "}\n"
        );
    }

    @Test public void
    test_14_20_2__Execution_of_try_finally_and_try_catch_finally__1() throws Exception {

        this.assertScriptReturnsNull(
            ""
            + "int x = 7;\n"
            + "//System.out.println(\"A\" + x);\n"
            + "try {\n"
            + "    //System.out.println(\"B\" + x);\n"
            + "    if (x != 7) return x;\n"
            + "    //System.out.println(\"C\" + x);\n"
            + "    x++;\n"
            + "    //System.out.println(\"D\" + x);\n"
            + "} finally {\n"
            + "    //System.out.println(\"E\" + x);\n"
            + "    if (x != 8) return x;\n"
            + "    //System.out.println(\"F\" + x);\n"
            + "    x++;\n"
            + "    //System.out.println(\"G\" + x);\n"
            + "}\n"
            + "//System.out.println(\"H\" + x);\n"
            + "if (x != 9) return x;\n"
            + "return null;\n"
        );
    }

    @Test public void
    test_14_20_2__Execution_of_try_finally_and_try_catch_finally__2() throws Exception {

        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 7) return;

        this.assertScriptExecutable(
            ""
            + "try {\n"
            + "    int a = 7;\n"
            + "} finally {\n"
            + "    ;\n"
            + "}\n"
        );
    }

    @Test public void
    test_14_20_3__try_with_resources__1() throws Exception {

        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 7) return;

        this.assertScriptReturnsNull(
            ""
            + "final int[] closed = new int[1];\n"
            + "class MyCloseable implements java.io.Closeable {\n"
            + "    public void close() { closed[0]++; }\n"
            + "}\n"
            + "\n"
            + "try (MyCloseable mc = new MyCloseable()) {\n"
            + "    if (closed[0] != 0) return closed[0];\n"
            + "    System.currentTimeMillis();\n"
            + "}\n"
            + "if (closed[0] != 1) return closed[0];\n"
            + "return null;\n"
        );
    }

    @Test public void
    test_14_20_3__try_with_resources__2() throws Exception {

        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 7) return;

        this.assertScriptReturnsTrue(
            ""
            + "final int[] closed = new int[1];\n"
            + "class MyCloseable implements java.io.Closeable {\n"
            + "    public void close() { closed[0]++; }\n"
            + "}\n"
            + "\n"
            + "try (\n"
            + "    MyCloseable mc1 = new MyCloseable();\n"
            + "    MyCloseable mc2 = new MyCloseable();\n"
            + "    MyCloseable mc3 = new MyCloseable()\n"
            + ") {\n"
            + "    if (closed[0] != 0) return false;\n"
            + "    System.currentTimeMillis();\n"
            + "}\n"
            + "if (closed[0] != 3) return false;\n"
            + "return true;\n"
        );
    }

    @Test public void
    test_14_20_3__try_with_resources__2a() throws Exception {

        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 7) return;

        this.assertScriptExecutable(
            ""
            + "class MyCloseable implements java.io.Closeable {\n"
            + "    public void close() {}\n"
            + "}\n"
            + "\n"
            + "try (\n"
            + "    MyCloseable mc1 = new MyCloseable();\n"
            + "    MyCloseable mc2 = new MyCloseable()\n"
            + ") {\n"
            + "    System.currentTimeMillis();\n"
            + "}\n"
        );
    }

    @Test public void
    test_14_20_3__try_with_resources__3() throws Exception {

        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 7) return;

        this.assertScriptReturnsTrue(
            ""
            + "final int[] closed = new int[1];\n"
            + "class MyCloseable implements java.io.Closeable {\n"
            + "    public void close() { closed[0]++; }\n"
            + "}\n"
            + "\n"
            + "try (\n"
            + "    MyCloseable mc1 = new MyCloseable();\n"
            + "    MyCloseable mc2 = null;\n"
            + "    MyCloseable mc3 = new MyCloseable()\n"
            + ") {\n"
            + "    if (closed[0] != 0) return false;\n"
            + "    System.currentTimeMillis();\n"
            + "}\n"
            + "if (closed[0] != 2) return false;\n"
            + "return true;\n"
        );
    }

    @Test public void
    test_14_20_3__try_with_resources__4() throws Exception {

        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 7) return;

        // The resource variable is in scope in the block, in the initializers of the following resources, and in
        // the anonymous and local classes declared in the block.
        this.assertScriptReturnsTrue(
            ""
            + "class R implements AutoCloseable {\n"
            + "    final String n;\n"
            + "    R(String n) { this.n = n; }\n"
            + "    public void close() {}\n"
            + "}\n"
            + "try (R a = new R(\"a\"); R b = new R(a.n + \"b\")) {\n"
            + "    class L { String g() { return b.n; } }\n"
            + "    Object o = new Object() { public String toString() { return a.n; } };\n"
            + "    if (!a.n.equals(\"a\") || !b.n.equals(\"ab\")) return false;\n"
            + "    return new L().g().equals(\"ab\") && o.toString().equals(\"a\");\n"
            + "}\n"
        );
    }

    @Test public void
    test_14_20_3__try_with_resources__5() throws Exception {

        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 7) return;

        // The resource is closed when the block completes, also when the block accesses the resource variable.
        this.assertScriptReturnsTrue(
            ""
            + "final int[] closed = new int[1];\n"
            + "class R implements AutoCloseable {\n"
            + "    public void close() { closed[0]++; }\n"
            + "    int n() { return closed[0]; }\n"
            + "}\n"
            + "try (R r = new R()) {\n"
            + "    if (r.n() != 0) return false;\n"
            + "}\n"
            + "return closed[0] == 1;\n"
        );
    }

    @Test public void
    test_14_20_3__try_with_resources__6() throws Exception {

        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 7) return;

        // The resource variable is not in scope in the CATCH clauses, in the FINALLY clause, after the statement,
        // and in the initializers of the preceding resources.
        String r = "class R implements AutoCloseable { R() {} R(R o) {} public void close() {} }\n";
        this.assertScriptUncookable(r + "try (R r = new R()) {} catch (Exception e) { r.close(); }\n");
        this.assertScriptUncookable(r + "try (R r = new R()) {} finally { r.close(); }\n");
        this.assertScriptUncookable(r + "try (R r = new R()) {} r.close();\n");
        this.assertScriptUncookable(r + "try (R b = new R(a); R a = new R()) {}\n");
        this.assertScriptUncookable(r + "try (R a = new R(a)) {}\n");
    }

    /**
     * Tests the "enhanced try-with-resources statement" that was introduced with Java 9 with a "local variable
     * declarator resource" with a local variable access.
     */
    @Test public void
    test_14_20_3__try_with_resources__10a() throws Exception {

        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 9) return;

        this.assertScriptExecutable(
            ""
            + "import java.io.Closeable;\n"
            + "import java.io.IOException;\n"
            + "try {\n"
            + "    final int[] x = new int[1];\n"
            + "    Closeable lv = new Closeable() {\n"
            + "        public void close() { if (++x[0] != 2) throw new AssertionError(); }\n"
            + "    };\n"
            + "    \n"
            + "    try (lv) {\n"
            + "        if (++x[0] != 1) throw new AssertionError();\n"
            + "    }\n"
            + "    if (++x[0] != 3) throw new AssertionError();\n"
            + "} catch (IOException ioe) {\n"
            + "    throw new AssertionError(ioe);\n"
            + "}\n"
        );
    }

    /**
     * Tests the "enhanced try-with-resources statement" that was introduced with Java 9 with a "variable access
     * resource" with a static field access.
     */
    @Test public void
    test_14_20_3__try_with_resources__10b() throws Exception {

        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 9) return;

        this.assertClassBodyExecutable(
            ""
            + "import java.io.Closeable;\n"
            + "import java.io.IOException;\n"
            + "\n"
            + "public static final Closeable sf = new Closeable() {\n"
            + "    public void close() { if (++x[0] != 2) throw new AssertionError(); }\n"
            + "};\n"
            + "public static final int[] x = new int[1];\n"
            + "\n"
            + "public static void main() {\n"
            + "    try {\n"
            + "        \n"
            + "        try (SC.sf) {\n"
            + "            if (++x[0] != 1) throw new AssertionError();\n"
            + "        }\n"
            + "        if (++x[0] != 3) throw new AssertionError();\n"
            + "    } catch (IOException ioe) {\n"
            + "        throw new AssertionError(ioe.toString());\n"
            + "    }\n"
            + "}\n"
        );
    }

    /**
     * Tests the "enhanced try-with-resources statement" that was introduced with Java 9 with a "variable access
     * resource" with a non-static field access.
     */
    @Test public void
    test_14_20_3__try_with_resources__10c() throws Exception {

        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 9) return;

        this.assertClassBodyExecutable(
            ""
            + "import java.io.Closeable;\n"
            + "import java.io.IOException;\n"
            + "\n"
            + "public final Closeable sf = new Closeable() {\n"
            + "    public void close() { if (++SC.this.x[0] != 2) throw new AssertionError(); }\n"
            + "};\n"
            + "public final int[] x = new int[1];\n"
            + "\n"
            + "public void main() {\n"
            + "    try {\n"
            + "        \n"
            + "        try (this.sf) {\n"
            + "            if (++this.x[0] != 1) throw new AssertionError();\n"
            + "        }\n"
            + "        if (++this.x[0] != 3) throw new AssertionError();\n"
            + "    } catch (IOException ioe) {\n"
            + "        throw new AssertionError(ioe.toString());\n"
            + "    }\n"
            + "}\n"
        );
    }

    /**
     * Tests the "enhanced try-with-resources statement" that was introduced with Java 9 with a "local variable
     * declarator resource" with an invalid variable access.
     */
    @Test public void
    test_14_20_3__try_with_resources__10d() throws Exception {

        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION < 9) return;

        this.assertScriptUncookable(
            (
                ""
                + "import java.io.Closeable;\n"
                + "import java.io.IOException;\n"
                + "import org.junit.Assert;\n"
                + "try {\n"
                + "    try (new Closeable() { public void close() {} }) {\n"
                + "    }\n"
                + "} catch (Exception ioe) {\n"
                + "    Assert.fail(ioe.toString());\n"
                + "}\n"
            ),
            (
                "compiler.err.try.with.resources.expr.needs.var"
                + "|"
                + "NewAnonymousClassInstance rvalue not allowed as a resource"
            )
        );
    }

    @Test public void
    test_14_21__Unreachable_statements() throws Exception {
        this.assertClassBodyUncookable(
            ""
            + "public void test() throws Exception {}\n"
            + "public void test2() {\n"
            + "    try {\n"
            + "        test();\n"
            + "    } catch (Exception e) {\n"
            + "        ;\n"
            + "    } catch (java.io.IOException e) {\n"
            + "        // This CATCH clause is unreachable.\n"
            + "    }\n"
            + "}\n"
        );

        this.assertClassBodyCookable(
            ""
            + "public void test2() {\n"
            + "    try {\n"
            + "        throw new java.io.IOException();\n"
            + "    } catch (java.io.IOException e) {\n"
            + "        ;\n"
            + "    } catch (NullPointerException e) {\n"
            + "        ;\n"
            + "    } catch (RuntimeException e) {\n"
            + "        ;\n"
            + "    } catch (Exception e) {\n"
            + "        ;\n"
            + "    } catch (NoClassDefFoundError e) {\n"
            + "        ;\n"
            + "    } catch (Throwable e) {\n"
            + "        ;\n"
            + "    }\n"
            + "}\n"
        );
    }

    @Test public void
    test_15_2_2_5__Choosing_the_most_specific_vararg_method_1() throws Exception {
        this.assertClassBodyMainReturnsTrue(
            ""
            + "public static boolean main() {\n"
            + "    return meth(new int[][] { { 3 } }) == 1;\n"
            + "}\n"
            + "\n"
            + "static int meth(int[][]...a) {\n"
            + "    return 0;\n"
            + "}\n"
            + "\n"
            + "static int meth(int[]... b) {\n"
            + "    return 1;\n"
            + "}\n"
        );
    }

    @Test public void
    test_15_9_1__Determining_the_class_being_Instantiated() throws Exception {

        this.assertExpressionEvaluatesTrue("new Object() instanceof Object");
        this.assertExpressionUncookable("new java.util.List()");
        this.assertExpressionUncookable("new other_package.PackageClass()");
        this.assertExpressionUncookable("new java.util.AbstractList()");
        this.assertExpressionEvaluatesTrue(
            "new other_package.Foo(3).new PublicMemberClass() instanceof other_package.Foo.PublicMemberClass"
        );
        this.assertExpressionUncookable("new other_package.Foo(3).new Foo.PublicMemberClass()");
        this.assertExpressionUncookable("new other_package.Foo(3).new other_package.Foo.PublicMemberClass()");
        this.assertExpressionUncookable("new other_package.Foo(3).new PackageMemberClass()");
        this.assertExpressionUncookable("new other_package.Foo(3).new PublicAbstractMemberClass()");
        this.assertExpressionUncookable("new other_package.Foo(3).new PublicStaticMemberClass()");
        this.assertExpressionUncookable("new other_package.Foo(3).new PublicMemberInterface()");
        this.assertExpressionUncookable("new java.util.ArrayList().new PublicMemberClass()");

        // The following one is tricky: A Java 6 JRE declares
        //    public int          File.compareTo(File)
        //    public abstract int Comparable.compareTo(Object)
        // , and yet "File" is not abstract!
        this.assertCompilationUnitMainReturnsTrue((
            "class MyFile extends java.io.File {\n"
            + "    public MyFile() { super(\"/my/file\"); }\n"
            + "}\n"
            + "public class Main {\n"
            + "    public static boolean main() {\n"
            + "        return 0 == new MyFile().compareTo(new MyFile());\n"
            + "    }\n"
            + "}"
        ), "Main");

        // "Type interference for generic instance creation" (a.k.a. the "diamond operator"); a Java 7 feature.
        if (this.isJdk && CommonsCompilerTestSuite.JVM_VERSION >= 7) {
            this.assertScriptReturnsTrue(
                "java.util.Map<String, Integer> map = new java.util.HashMap<>(); return !map.containsKey(\"\");"
            );
        }
    }

    @Test public void
    test_15_9_3a__Choosing_the_Constructor_and_its_Arguments() throws Exception {

        this.assertExpressionEvaluatable("new Integer(3)");
        this.assertExpressionEvaluatable("new Integer(new Integer(3))");
        this.assertExpressionEvaluatable("new Integer(new Byte((byte) 3))");
        this.assertExpressionUncookable("new Integer(new Object())");
    }

    @Test public void
    test_15_9_3b__Choosing_the_Constructor_and_its_Arguments() throws Exception {

        // "Diamond operator".
        this.assertScriptExecutable(
            ""
            + "import java.util.*;\n"
            + "List<String> l = new ArrayList<>();\n"
        );
    }

    @Test public void
    test_15_9_5a__Anonymous_Class_Declarations() throws Exception {

        this.assertCompilationUnitMainExecutable((
            ""
            + "public class Foo {\n"
            + "    public static void main() { new Foo().meth(); }\n"
            + "    private Object meth() {\n"
            + "        return new Object() {};\n"
            + "    }\n"
            + "}\n"
        ), "Foo");
    }

    @Test public void
    test_15_9_5b__Anonymous_Class_Declarations() throws Exception {

        this.assertCompilationUnitMainExecutable((
            ""
            + "public class A {\n"
            + "    public static void main() { new A(); }\n"
            + "    public A(Object o) {}\n"
            + "    public A() {\n"
            + "        this(new Object() {});\n"
            + "    }\n"
            + "}\n"
        ), "A");
    }

    /**
     * Notice: In JLS2 and JLS7, this section had number "15.3" (which, since JLS8, is the number for section "Method
     * Reference Expressions"). Since JLS8 it has number "15.10.3".
     */
    @Test public void
    test_15_10_3__Array_Access_Expressions() throws Exception {
        this.assertExpressionCookable("(new int[3])[(byte) 0]");
        this.assertExpressionCookable("(new int[3])[(char) 0]");
        this.assertExpressionCookable("(new int[3])[(short) 0]");
        this.assertExpressionCookable("(new int[3])[0]");
        this.assertExpressionUncookable("(new int[3])[0L]");

        // Array access expressions as method and constructor arguments, and as array indexes.
        this.assertScriptReturnsTrue("int[] a = { 7 }; return String.valueOf(a[0]).equals(\"7\");");
        this.assertScriptReturnsTrue("Object[] o = { \"x\" }; return String.valueOf(o[0]).equals(\"x\");");
        this.assertScriptReturnsTrue("Object o = new Object[] { \"x\" }; return ((Object[]) o)[0].equals(\"x\");");
        this.assertScriptReturnsTrue("Object o = new String[] { \"x\" }; return \"x\".equals(((Object[]) o)[0]);");
        this.assertScriptReturnsTrue("int[] a = { 7 }; return new StringBuilder(a[0]).capacity() == 7;");
        this.assertScriptReturnsTrue("int[] a = { 1, 2 }; int[] b = { 1 }; return a[b[0]] == 2;");
        this.assertScriptReturnsTrue("int[][] a = { { 1, 2 } }; return String.valueOf(a[0][1]).equals(\"2\");");
        this.assertScriptReturnsTrue("String[] s = { \"ab\" }; return s[0].substring(s[0].length() - 1).equals(\"b\");");
        this.assertScriptUncookable("Object o = new int[1]; return String.valueOf(o[0]);");
        this.assertScriptUncookable("String s = \"a\"; return String.valueOf(s[0]);");
        this.assertScriptUncookable("Object o = null; return o[0];");
        this.assertScriptUncookable("int[] a = { 1 }; Object o = null; return a[o[0]];");
    }

    @Test public void
    test_15_11_2__Accessing_Superclass_Members_using_super() throws Exception {

        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "public class T1            { int x = 1; }\n"
            + "public class T2 extends T1 { int x = 2; }\n"
            + "public class T3 extends T2 {\n"
            + "    int x = 3;\n"
            + "    public static boolean main() {\n"
            + "        return new T3().test2();\n"
            + "    }\n"
            + "    public boolean test2() {\n"
            + "        return (\n"
            + "            x == 3\n"
            + "            && super.x == 2\n"
            + "            && T3.super.x == 2\n"
            + "//            && T2.super.x == 1\n" // <= Does not work with the SUN JDK 1.6 compiler
            + "            && ((T2) this).x == 2\n"
            + "            && ((T1) this).x == 1\n"
            + "        );\n"
            + "    }\n"
            + "}\n"
        ), "T3");
    }

    @Test public void
    test_15_12_1__Qualified_superclass_and_superinterface_method_invocations() throws Exception {

        // "ClassName.super.m()" invokes the superclass method of the current class, or of a lexically enclosing
        // class (through a synthetic static accessor method of that class, like JAVAC); "InterfaceName.super.m()"
        // invokes a default method of a direct superinterface.
        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "import java.lang.reflect.Method;\n"
            + "import java.lang.reflect.Modifier;\n"
            + "public class Main {\n"
            + "    static StringBuilder t = new StringBuilder();\n"
            + "    interface H { default String h() { return \"h\"; } }\n"
            + "    interface I extends H {\n"
            + "        default String m() { return \"i\"; }\n"
            + "        default String n(int x) { return \"n\" + x; }\n"
            + "    }\n"
            + "    interface J { default String m() { return \"j\"; } }\n"
            + "    interface K extends I { default String m() { return \"k\" + I.super.m(); } }\n"
            + "    static class C implements I, J {\n"
            + "        final String c;\n"
            + "        C() { c = I.super.n(0); }\n"
            + "        public String m() { return I.super.m() + J.super.m() + I.super.n(1) + I.super.h(); }\n"
            + "    }\n"
            + "    static class Base {\n"
            + "        String f = \"basef\";\n"
            + "        String m() { return \"base\"; }\n"
            + "        String p(int a, String b) { return \"p\" + a + b; }\n"
            + "        void v() { t.append(\"basev\"); }\n"
            + "        String e() throws Exception { throw new Exception(\"e\"); }\n"
            + "    }\n"
            + "    static class Sub extends Base {\n"
            + "        String f = \"subf\";\n"
            + "        String m() { return \"sub\"; }\n"
            + "        String p(int a, String b) { return \"subp\"; }\n"
            + "        void v() { t.append(\"subv\"); }\n"
            + "        String e() { return \"sube\"; }\n"
            + "        String own() { return Sub.super.m() + Sub.super.f; }\n"
            + "        class Inner {\n"
            + "            String call() {\n"
            + "                Sub.super.v();\n"
            + "                String r = Sub.super.m() + Sub.super.p(1, \"x\") + Sub.super.f + Sub.this.m();\n"
            + "                try { r += Sub.super.e(); } catch (Exception ex) { r += ex.getMessage(); }\n"
            + "                return r;\n"
            + "            }\n"
            + "            class Inner2 { String call2() { return Sub.super.m() + Sub.super.f; } }\n"
            + "        }\n"
            + "        String anon() {\n"
            + "            return new Object() { public String toString() { return Sub.super.m(); } }.toString();\n"
            + "        }\n"
            + "        String local() { class L { String g() { return Sub.super.m(); } } return new L().g(); }\n"
            + "    }\n"
            + "    static class Sub2 extends Sub {\n"
            + "        String m() { return \"sub2\"; }\n"
            + "        class Inner3 { String call3() { return Sub2.super.m(); } }\n"
            + "    }\n"
            + "    public static boolean main() throws Exception {\n"
            + "        C c = new C();\n"
            + "        Sub s = new Sub();\n"
            + "        Sub2 s2 = new Sub2();\n"
            + "        Method a = Sub.class.getDeclaredMethod(\"access$001\", Sub.class);\n"
            + "        return (\n"
            + "            \"ijn1h\".equals(c.m()) && \"n0\".equals(c.c) && \"ki\".equals(new K() {}.m())\n"
            + "            && \"basebasef\".equals(s.own())\n"
            + "            && \"basep1xbasefsube\".equals(s.new Inner().call())\n"
            + "            && \"basebasef\".equals(s.new Inner().new Inner2().call2())\n"
            + "            && \"base\".equals(s.anon()) && \"base\".equals(s.local())\n"
            + "            && \"basep1xbasefsub2e\".equals(s2.new Inner().call())\n"
            + "            && \"sub\".equals(s2.new Inner3().call3())\n"
            + "            && a.isSynthetic() && Modifier.isStatic(a.getModifiers())\n"
            + "            && \"basevbasev\".equals(t.toString())\n"
            + "        );\n"
            + "    }\n"
            + "}\n"
        ), "Main");

        // The qualifying interface must be a direct superinterface that no other direct supertype extends, and the
        // method must not be abstract; the qualifying class must be the current class or an enclosing class.
        this.assertCompilationUnitUncookable((
            ""
            + "class Foo { interface I { default String m() { return \"i\"; } } interface J extends I { }"
            + " static class C implements J { public String m() { return I.super.m(); } } }"
        ), "not a direct superinterface|not an enclosing class|compiler.err.not.encl.class");
        this.assertCompilationUnitUncookable((
            ""
            + "class Foo { interface I { default String m() { return \"i\"; } }"
            + " interface J extends I { default String m() { return \"j\"; } }"
            + " static class C implements I, J { public String m() { return I.super.m(); } } }"
        ), "another direct supertype|bad type qualifier|compiler.err.illegal.default.super.call");
        this.assertCompilationUnitUncookable((
            ""
            + "class Foo { interface I { String m(); }"
            + " static class C implements I { public String m() { return I.super.m(); } } }"
        ), "cannot be invoked through|cannot be accessed directly|compiler.err.abstract.cant.be.accessed.directly");
        this.assertCompilationUnitUncookable((
            ""
            + "class Foo { static class Base { String m() { return \"b\"; } }"
            + " static class Sub extends Base { String m() { return Base.super.m(); } } }"
        ), "neither the current class nor an enclosing class|not an enclosing class|compiler.err.not.encl.class");
    }

    @Test public void
    test_15_12_2_4__Phase3Identify_applicable_variable_arity_methods__1() throws Exception {
        this.assertExpressionEvaluatesTrue("\"two one\".equals(String.format(\"%2$s %1$s\", \"one\", \"two\"))");
    }

    @Test public void
    test_15_12_2_4__Phase3Identify_applicable_variable_arity_methods__2() throws Exception {
        this.assertClassBodyMainReturnsTrue(
            ""
            + "public static boolean\n"
            + "main() {\n"
            + "    return (\n"
            + "        meth(6,  1, 2, 3)\n"
            + "        && meth(10, 1, 2, 3, 4)\n"
            + "        && meth(15, 1, 2, 3, 4, 5)\n"
            + "        && meth(21, 1, 2, 3, 4, 5, 6)\n"
            + "    );\n"
            + "}\n"
            + "\n"
            + "static boolean\n"
            + "meth(int expected, int... operands) {\n"
            + "    int sum = 0;\n"
            + "    for (int i = 0; i < operands.length; i++) sum += operands[i];\n"
            + "    return sum == expected;\n"
            + "}\n"
        );
    }

    @Test public void
    test_15_12_2_4__Phase3Identify_applicable_variable_arity_methods__3() throws Exception {
        this.assertClassBodyMainReturnsTrue(
            ""
            + "public static boolean main() {\n"
            + "    if (meth(1, 2, 3) != 5) return false;\n"
            + "    if (meth(1) != 0) return false;\n"
            + "    if (meth(1, null) != 99) return false;\n"
            + "    return true;\n"
            + "}\n"
            + "\n"
            + "static double meth(int x, double... va) {\n"
            + "    if (va == null) return 99;\n"
            + "\n"
            + "    double sum = 0;\n"
            + "    for (int i = 0; i < va.length; i++) sum += va[i];\n"
            + "    return sum;\n"
            + "}\n"
        );
    }

    @Test public void
    test_15_12_2_4__Phase3Identify_applicable_variable_arity_methods__4() throws Exception {
        this.assertScriptReturnsTrue(
            ""
            + "class LocalClass {\n"
            + "    int x;\n"
            + "\n"
            + "    LocalClass(String s, Object... oa) {\n"
            + "        x = oa.length;\n"
            + "    }\n"
            + "}\n"
            + "\n"
            + "if (new LocalClass(\"\").x != 0) return false;\n"
            + "if (new LocalClass(\"\", 1, 2).x != 2) return false;\n"
            + "return true;\n"
        );
    }

    @Test public void
    test_15_12_2_5__Choose_the_Most_Specific_Method() throws Exception {

        this.assertCompilationUnitUncookable(
            ""
            + "public class Main { public static boolean test() { return new A().meth(\"x\", \"y\"); } }\n"
            + "public class A {\n"
            + "    public boolean meth(String s, Object o) { return true; }\n"
            + "    public boolean meth(Object o, String s) { return false; }\n"
            + "}\n"
        );

        // The following case is tricky: JLS7 says that the invocation is AMBIGUOUS, but only JAVAC 1.2 issues an
        // error; JAVAC 1.4.1, 1.5.0 and 1.6.0 obviously ignore the declaring type and invoke "A.meth(String)".
        // JLS7 is not clear about this. For compatibility with JAVA 1.4.1, 1.5.0 and 1.6.0, JANINO also ignores the
        // declaring type.
        //
        // See also JANINO-79 and "IClass.IInvocable.isMoreSpecificThan()".
        this.assertCompilationUnitMainReturnsTrue((
            ""
            + "public class Main        { public static boolean main()  { return new B().meth(\"x\"); } }\n"
            + "public class A           { public boolean meth(String s) { return true; } }\n"
            + "public class B extends A { public boolean meth(Object o) { return false; } }\n"
        ), "Main");
    }

    @Test public void
    test_15_12_2_6__Identify_applicable_variable_arity_methods__4() throws Exception {
        this.assertClassBodyMainReturnsTrue(
            ""
            + "public static boolean main() {\n"
            + "    return meth((byte)1, (byte)2) == 1;\n"
            + "}\n"
            + "\n"
            + "static int meth(byte...a) {\n"
            + "    return 0;\n"
            + "}\n"
            + "\n"
            + "static int meth(int a, int b){\n"
            + "    return 1;\n"
            + "}\n"
            + "\n"
            + "static int meth(int a, double b){\n"
            + "     return 2;\n"
            + "}\n"
        );
    }

    @Test public void
    test_15_12_2_7__Identify_applicable_variable_arity_methods__4() throws Exception {

        // The resolution phases should go like this:
        //  - all three methods are *applicable*, but fixed-arity ones have higher priority
        //    (meaning the chosen one, if any, must be a fixed-arity)
        //  - now we are left with (int, int) and (byte, double)
        //  - neither of these is more specific than the other,
        //    therefore it is an ambiguous case
        //  Ref: http://docs.oracle.com/javase/specs/jls/se7/html/jls-15.html#jls-15.12.2.1
        //
        // (Note: Some versions of javac choose the variable-arity method ("return 0"). Their reasoning seems to be
        // that there is ambiguity amongst fixed-arity applicables, so picking a vararg is acceptable if that means
        // there is no ambiguity. I have not been able to find any piece of documentation about this in the docs.)

        // JDK 1.7.0_17 and _21 do _not_ issue an error, although they should!?
        Assume.assumeFalse(this.isJdk && CommonsCompilerTestSuite.JVM_VERSION == 7);

        this.assertClassBodyUncookable((
            ""
            + "public static Object main() {\n"
            + "    return meth((byte) 1, (byte) 2);\n"
            + "}\n"
            + "\n"
            + "static int meth(byte...a) {\n"
            + "    return 0;\n"
            + "}\n"
            + "\n"
            + "static int meth(int a, int b){\n"
            + "    return 1;\n"
            + "}\n"
            + "\n"
            + "static int meth(byte a, double b){\n"
            + "    return 2;\n"
            + "}\n"
        ), "Invocation of.*method.*is ambiguous|compiler\\.err\\.ref\\.ambiguous");
    }

    @Test public void
    test_15_13__Method_reference_expressions() throws Exception {

        if (CommonsCompilerTestSuite.JVM_VERSION < 9) return;

        // ExpressionName '::' [ TypeArguments ] Identifier  (ExpressionName = a{.b})
        this.assertScriptExecutable(
            "Runnable r = new Runnable() { @Override public void run() { } }; Runnable s = r::run;"
        );

        // Primary '::' [ TypeArguments ] Identifier
        this.assertScriptExecutable(
            "Runnable r = new Runnable() { @Override public void run() { } }; Runnable s = (r)::run;"
        );

        // ReferenceType '::' [ TypeArguments ] Identifier
        this.assertScriptExecutable(
            ""
            + "Runnable r = new Runnable() { @Override public void run() { } };\n"
            + "Runnable t = java.util.Collections::emptySet;\n"
        );

        // 'super' '::' [ TypeArguments ] Identifier
        // TODO

        // TypeName '.' 'super' '::' [ TypeArguments ] Identifier
        // TODO

        // ClassType '::' [ TypeArguments ] 'new'
        this.assertScriptExecutable("Runnable r4 = java.util.HashMap::new;");

        // ArrayType '::' 'new'
        this.assertScriptExecutable("java.util.function.Consumer<Integer> c1 = int[]::new;");
    }

    @Test public void
    test_15_14_2__Postfix_Increment_Operator() throws Exception {

        this.assertScriptReturnsTrue("int i = 7; i++; return i == 8;");
        this.assertScriptReturnsTrue("Integer i = new Integer(7); i++; return i.intValue() == 8;");
        this.assertScriptReturnsTrue("int i = 7; return i == 7 && i++ == 7 && i == 8;");
        this.assertScriptReturnsTrue(
            "Integer i = new Integer(7);"
            + "return i.intValue() == 7 && (i++).intValue() == 7 && i.intValue() == 8;"
        );

        // byte

        this.assertScriptReturnsTrue("byte b = -1;  b++; return b == 0;");
        this.assertScriptReturnsTrue("byte b = 0;   b++; return b == 1;");
        this.assertScriptReturnsTrue("byte b = 127; b++; return b == -128;");

        this.assertScriptReturnsTrue("byte b = 0;    return b++ == 0;");
        this.assertScriptReturnsTrue("byte b = 127;  return b++ == 127;");
        this.assertScriptReturnsTrue("byte b = -128; return b++ == -128;");

        // short

        this.assertScriptReturnsTrue("short s = -1;    s++; return s == 0;");
        this.assertScriptReturnsTrue("short s = 0;     s++; return s == 1;");
        this.assertScriptReturnsTrue("short s = 127;   s++; return s == 128;");
        this.assertScriptReturnsTrue("short s = 32767; s++; return s == -32768;");

        this.assertScriptReturnsTrue("short s = 0;      return s++ == 0;");
        this.assertScriptReturnsTrue("short s = 32767;  return s++ == 32767;");
        this.assertScriptReturnsTrue("short s = -32768; return s++ == -32768;");

        // int

        this.assertScriptReturnsTrue("int i = -1;                i++; return i == 0;");
        this.assertScriptReturnsTrue("int i = 0;                 i++; return i == 1;");
        this.assertScriptReturnsTrue("int i = 127;               i++; return i == 128;");
        this.assertScriptReturnsTrue("int i = 32767;             i++; return i == 32768;");
        this.assertScriptReturnsTrue("int i = Integer.MAX_VALUE; i++; return i == Integer.MIN_VALUE;");

        this.assertScriptReturnsTrue("int i = 0;                 return i++ == 0;");
        this.assertScriptReturnsTrue("int i = 32767;             return i++ == 32767;");
        this.assertScriptReturnsTrue("int i = -32768;            return i++ == -32768;");
        this.assertScriptReturnsTrue("int i = Integer.MIN_VALUE; return i++ == Integer.MIN_VALUE;");
        this.assertScriptReturnsTrue("int i = Integer.MAX_VALUE; return i++ == Integer.MAX_VALUE;");

        // long

        this.assertScriptReturnsTrue("long i = -1;                i++; return i == 0;");
        this.assertScriptReturnsTrue("long i = 0;                 i++; return i == 1;");
        this.assertScriptReturnsTrue("long i = 127;               i++; return i == 128;");
        this.assertScriptReturnsTrue("long i = 32767;             i++; return i == 32768;");
        this.assertScriptReturnsTrue("long i = Integer.MAX_VALUE; i++; return i == Integer.MAX_VALUE + 1L;");
        this.assertScriptReturnsTrue("long i = Long.MAX_VALUE;    i++; return i == Long.MIN_VALUE;");

        this.assertScriptReturnsTrue("long i = 0;                 return i++ == 0;");
        this.assertScriptReturnsTrue("long i = 32767;             return i++ == 32767;");
        this.assertScriptReturnsTrue("long i = -32768;            return i++ == -32768;");
        this.assertScriptReturnsTrue("long i = Integer.MIN_VALUE; return i++ == Integer.MIN_VALUE;");
        this.assertScriptReturnsTrue("long i = Long.MIN_VALUE;    return i++ == Long.MIN_VALUE;");
        this.assertScriptReturnsTrue("long i = Long.MAX_VALUE;    return i++ == Long.MAX_VALUE;");

        // char

        this.assertScriptReturnsTrue("char c = 0;     c++; return c == 1;");
        this.assertScriptReturnsTrue("char c = 127;   c++; return c == 128;");
        this.assertScriptReturnsTrue("char c = 255;   c++; return c == 256;");
        this.assertScriptReturnsTrue("char c = 32767; c++; return c == 32768;");
        this.assertScriptReturnsTrue("char c = 65535; c++; return c == 0;");

        this.assertScriptReturnsTrue("char c = 0;     return c++ == 0;");
        this.assertScriptReturnsTrue("char c = 127;   return c++ == 127;");
        this.assertScriptReturnsTrue("char c = 128;   return c++ == 128;");
        this.assertScriptReturnsTrue("char c = 255;   return c++ == 255;");
        this.assertScriptReturnsTrue("char c = 256;   return c++ == 256;");
        this.assertScriptReturnsTrue("char c = 32767; return c++ == 32767;");
        this.assertScriptReturnsTrue("char c = 32768; return c++ == 32768;");
        this.assertScriptReturnsTrue("char c = 65535; return c++ == 65535;");

        // float

        this.assertScriptReturnsTrue("float f = 3.0F; f++; return f == 4.0F;");

        // double

        this.assertScriptReturnsTrue("double d = 17.9; d++; return d == 18.9;");

        // boolean

        this.assertScriptUncookable("boolean b = true; b++;");
    }

    @Test public void
    test_15_14_3__Postfix_Decrement_Operator() throws Exception {
        this.assertScriptReturnsTrue("int i = 7; i--; return i == 6;");
        this.assertScriptReturnsTrue("Integer i = new Integer(7); i--; return i.intValue() == 6;");
        this.assertScriptReturnsTrue("int i = 7; return i == 7 && i-- == 7 && i == 6;");
        this.assertScriptReturnsTrue(
            "Integer i = new Integer(7);"
            + "return i.intValue() == 7 && (i--).intValue() == 7 && i.intValue() == 6;"
        );

        // byte

        this.assertScriptReturnsTrue("byte b = 0;    b--; return b == -1;");
        this.assertScriptReturnsTrue("byte b = 1;    b--; return b == 0;");
        this.assertScriptReturnsTrue("byte b = -128; b--; return b == 127;");

        this.assertScriptReturnsTrue("byte b = 0;    return b-- == 0;");
        this.assertScriptReturnsTrue("byte b = 127;  return b-- == 127;");
        this.assertScriptReturnsTrue("byte b = -128; return b-- == -128;");

        // short

        this.assertScriptReturnsTrue("short s = 0;      s--; return s == -1;");
        this.assertScriptReturnsTrue("short s = 1;      s--; return s == 0;");
        this.assertScriptReturnsTrue("short s = 128;    s--; return s == 127;");
        this.assertScriptReturnsTrue("short s = -32768; s--; return s == 32767;");

        this.assertScriptReturnsTrue("short s = 0;      return s-- == 0;");
        this.assertScriptReturnsTrue("short s = 32767;  return s-- == 32767;");
        this.assertScriptReturnsTrue("short s = -32768; return s-- == -32768;");

        // int

        this.assertScriptReturnsTrue("int i = 0;                 i--; return i == -1;");
        this.assertScriptReturnsTrue("int i = 1;                 i--; return i == 0;");
        this.assertScriptReturnsTrue("int i = 128;               i--; return i == 127;");
        this.assertScriptReturnsTrue("int i = 32768;             i--; return i == 32767;");
        this.assertScriptReturnsTrue("int i = Integer.MIN_VALUE; i--; return i == Integer.MAX_VALUE;");

        this.assertScriptReturnsTrue("int i = 0;                 return i-- == 0;");
        this.assertScriptReturnsTrue("int i = 32767;             return i-- == 32767;");
        this.assertScriptReturnsTrue("int i = -32768;            return i-- == -32768;");
        this.assertScriptReturnsTrue("int i = Integer.MIN_VALUE; return i-- == Integer.MIN_VALUE;");
        this.assertScriptReturnsTrue("int i = Integer.MAX_VALUE; return i-- == Integer.MAX_VALUE;");

        // long

        this.assertScriptReturnsTrue("long i = 0;                      i--; return i == -1;");
        this.assertScriptReturnsTrue("long i = 1;                      i--; return i == 0;");
        this.assertScriptReturnsTrue("long i = 128;                    i--; return i == 127;");
        this.assertScriptReturnsTrue("long i = 32768;                  i--; return i == 32767;");
        this.assertScriptReturnsTrue("long i = Integer.MAX_VALUE + 1L; i--; return i == Integer.MAX_VALUE;");
        this.assertScriptReturnsTrue("long i = Long.MIN_VALUE     ;    i--; return i == Long.MAX_VALUE;");

        this.assertScriptReturnsTrue("long i = 0;                 return i-- == 0;");
        this.assertScriptReturnsTrue("long i = 32767;             return i-- == 32767;");
        this.assertScriptReturnsTrue("long i = -32768;            return i-- == -32768;");
        this.assertScriptReturnsTrue("long i = Integer.MIN_VALUE; return i-- == Integer.MIN_VALUE;");
        this.assertScriptReturnsTrue("long i = Long.MIN_VALUE;    return i-- == Long.MIN_VALUE;");
        this.assertScriptReturnsTrue("long i = Long.MAX_VALUE;    return i-- == Long.MAX_VALUE;");

        // char

        this.assertScriptReturnsTrue("char c = 1;     c--; return c == 0;");
        this.assertScriptReturnsTrue("char c = 128;   c--; return c == 127;");
        this.assertScriptReturnsTrue("char c = 256;   c--; return c == 255;");
        this.assertScriptReturnsTrue("char c = 32768; c--; return c == 32767;");
        this.assertScriptReturnsTrue("char c = 0;     c--; return c == 65535;");

        this.assertScriptReturnsTrue("char c = 0;     return c-- == 0;");
        this.assertScriptReturnsTrue("char c = 127;   return c-- == 127;");
        this.assertScriptReturnsTrue("char c = 128;   return c-- == 128;");
        this.assertScriptReturnsTrue("char c = 255;   return c-- == 255;");
        this.assertScriptReturnsTrue("char c = 256;   return c-- == 256;");
        this.assertScriptReturnsTrue("char c = 32767; return c-- == 32767;");
        this.assertScriptReturnsTrue("char c = 32768; return c-- == 32768;");
        this.assertScriptReturnsTrue("char c = 65535; return c-- == 65535;");

        // float

        this.assertScriptReturnsTrue("float f = 4.0F; f--; return f == 3.0F;");

        // double

        this.assertScriptReturnsTrue("double d = 18.9; d--; return d == 17.9;");

        // boolean

        this.assertScriptUncookable("boolean b = true; b--;");
    }

    @Test public void
    test_15_15_1__Prefix_Increment_Operator() throws Exception {

        this.assertScriptReturnsTrue("int i = 7; ++i; return i == 8;");
        this.assertScriptReturnsTrue("Integer i = new Integer(7); ++i; return i.intValue() == 8;");
        this.assertScriptReturnsTrue("int i = 7; return i == 7 && ++i == 8 && i == 8;");
        this.assertScriptReturnsTrue(
            "Integer i = new Integer(7);"
            + "return i.intValue() == 7 && (++i).intValue() == 8 && i.intValue() == 8;"
        );

        // byte

        this.assertScriptReturnsTrue("byte b = -1;  ++b; return b == 0;");
        this.assertScriptReturnsTrue("byte b = 0;   ++b; return b == 1;");
        this.assertScriptReturnsTrue("byte b = 127; ++b; return b == -128;");

        this.assertScriptReturnsTrue("byte b = 0;    return ++b == 1;");
        this.assertScriptReturnsTrue("byte b = 127;  return ++b == -128;");
        this.assertScriptReturnsTrue("byte b = -128; return ++b == -127;");

        // short

        this.assertScriptReturnsTrue("short s = -1;    ++s; return s == 0;");
        this.assertScriptReturnsTrue("short s = 0;     ++s; return s == 1;");
        this.assertScriptReturnsTrue("short s = 127;   ++s; return s == 128;");
        this.assertScriptReturnsTrue("short s = 32767; ++s; return s == -32768;");

        this.assertScriptReturnsTrue("short s = 0;      return ++s == 1;");
        this.assertScriptReturnsTrue("short s = 32767;  return ++s == -32768;");
        this.assertScriptReturnsTrue("short s = -32768; return ++s == -32767;");

        // int

        this.assertScriptReturnsTrue("int i = -1;                ++i; return i == 0;");
        this.assertScriptReturnsTrue("int i = 0;                 ++i; return i == 1;");
        this.assertScriptReturnsTrue("int i = 127;               ++i; return i == 128;");
        this.assertScriptReturnsTrue("int i = 32767;             ++i; return i == 32768;");
        this.assertScriptReturnsTrue("int i = Integer.MAX_VALUE; ++i; return i == Integer.MIN_VALUE;");

        this.assertScriptReturnsTrue("int i = 0;                 return ++i == 1;");
        this.assertScriptReturnsTrue("int i = 32767;             return ++i == 32768;");
        this.assertScriptReturnsTrue("int i = -32769;            return ++i == -32768;");
        this.assertScriptReturnsTrue("int i = Integer.MIN_VALUE; return ++i == Integer.MIN_VALUE + 1;");
        this.assertScriptReturnsTrue("int i = Integer.MAX_VALUE; return ++i == Integer.MIN_VALUE;");

        // long

        this.assertScriptReturnsTrue("long i = -1;                ++i; return i == 0;");
        this.assertScriptReturnsTrue("long i = 0;                 ++i; return i == 1;");
        this.assertScriptReturnsTrue("long i = 127;               ++i; return i == 128;");
        this.assertScriptReturnsTrue("long i = 32767;             ++i; return i == 32768;");
        this.assertScriptReturnsTrue("long i = Integer.MAX_VALUE; ++i; return i == Integer.MAX_VALUE + 1L;");
        this.assertScriptReturnsTrue("long i = Long.MAX_VALUE;    ++i; return i == Long.MIN_VALUE;");

        this.assertScriptReturnsTrue("long i = 0;                      return ++i == 1;");
        this.assertScriptReturnsTrue("long i = 32767;                  return ++i == 32768;");
        this.assertScriptReturnsTrue("long i = -32769;                 return ++i == -32768;");
        this.assertScriptReturnsTrue("long i = Integer.MIN_VALUE - 1L; return ++i == Integer.MIN_VALUE;");
        this.assertScriptReturnsTrue("long i = Long.MIN_VALUE;         return ++i == Long.MIN_VALUE + 1;");
        this.assertScriptReturnsTrue("long i = Long.MAX_VALUE;         return ++i == Long.MIN_VALUE;");

        // char

        this.assertScriptReturnsTrue("char c = 0;     ++c; return c == 1;");
        this.assertScriptReturnsTrue("char c = 127;   ++c; return c == 128;");
        this.assertScriptReturnsTrue("char c = 255;   ++c; return c == 256;");
        this.assertScriptReturnsTrue("char c = 32767; ++c; return c == 32768;");
        this.assertScriptReturnsTrue("char c = 65535; ++c; return c == 0;");

        this.assertScriptReturnsTrue("char c = 0;     return ++c == 1;");
        this.assertScriptReturnsTrue("char c = 127;   return ++c == 128;");
        this.assertScriptReturnsTrue("char c = 128;   return ++c == 129;");
        this.assertScriptReturnsTrue("char c = 255;   return ++c == 256;");
        this.assertScriptReturnsTrue("char c = 256;   return ++c == 257;");
        this.assertScriptReturnsTrue("char c = 32767; return ++c == 32768;");
        this.assertScriptReturnsTrue("char c = 32768; return ++c == 32769;");
        this.assertScriptReturnsTrue("char c = 65535; return ++c == 0;");

        // float

        this.assertScriptReturnsTrue("float f = 3.0F; ++f; return f == 4.0F;");

        // double

        this.assertScriptReturnsTrue("double d = 17.9; ++d; return d == 18.9;");

        // boolean

        this.assertScriptUncookable("boolean b = true; ++b;");
    }

    @Test public void
    test_15_15_2__Prefix_Decrement_Operator() throws Exception {

        this.assertScriptReturnsTrue("int i = 7; --i; return i == 6;");
        this.assertScriptReturnsTrue("Integer i = new Integer(7); --i; return i.intValue() == 6;");
        this.assertScriptReturnsTrue("int i = 7; return i == 7 && --i == 6 && i == 6;");
        this.assertScriptReturnsTrue(
            "Integer i = new Integer(7);"
            + "return i.intValue() == 7 && (--i).intValue() == 6 && i.intValue() == 6;"
        );

        // byte

        this.assertScriptReturnsTrue("byte b = 0;    --b; return b == -1;");
        this.assertScriptReturnsTrue("byte b = 1;    --b; return b == 0;");
        this.assertScriptReturnsTrue("byte b = -128; --b; return b == 127;");

        this.assertScriptReturnsTrue("byte b = 0;    return --b == -1;");
        this.assertScriptReturnsTrue("byte b = 127;  return --b == 126;");
        this.assertScriptReturnsTrue("byte b = -128; return --b == 127;");

        // short

        this.assertScriptReturnsTrue("short s = 0;      --s; return s == -1;");
        this.assertScriptReturnsTrue("short s = 1;      --s; return s == 0;");
        this.assertScriptReturnsTrue("short s = 128;    --s; return s == 127;");
        this.assertScriptReturnsTrue("short s = -32768; --s; return s == 32767;");

        this.assertScriptReturnsTrue("short s = 0;      return --s == -1;");
        this.assertScriptReturnsTrue("short s = 32767;  return --s == 32766;");
        this.assertScriptReturnsTrue("short s = -32768; return --s == 32767;");

        // int

        this.assertScriptReturnsTrue("int i = 0;                 --i; return i == -1;");
        this.assertScriptReturnsTrue("int i = 1;                 --i; return i == 0;");
        this.assertScriptReturnsTrue("int i = 128;               --i; return i == 127;");
        this.assertScriptReturnsTrue("int i = 32768;             --i; return i == 32767;");
        this.assertScriptReturnsTrue("int i = Integer.MIN_VALUE; --i; return i == Integer.MAX_VALUE;");

        this.assertScriptReturnsTrue("int i = 0;                 return --i == -1;");
        this.assertScriptReturnsTrue("int i = 127;               return --i == 126;");
        this.assertScriptReturnsTrue("int i = 128;               return --i == 127;");
        this.assertScriptReturnsTrue("int i = -128;              return --i == -129;");
        this.assertScriptReturnsTrue("int i = 32767;             return --i == 32766;");
        this.assertScriptReturnsTrue("int i = -32768;            return --i == -32769;");
        this.assertScriptReturnsTrue("int i = Integer.MIN_VALUE; return --i == Integer.MAX_VALUE;");
        this.assertScriptReturnsTrue("int i = Integer.MAX_VALUE; return --i == Integer.MAX_VALUE - 1;");

        // long

        this.assertScriptReturnsTrue("long i = 0;                      --i; return i == -1;");
        this.assertScriptReturnsTrue("long i = 1;                      --i; return i == 0;");
        this.assertScriptReturnsTrue("long i = 128;                    --i; return i == 127;");
        this.assertScriptReturnsTrue("long i = 32768;                  --i; return i == 32767;");
        this.assertScriptReturnsTrue("long i = Integer.MAX_VALUE + 1L; --i; return i == Integer.MAX_VALUE;");
        this.assertScriptReturnsTrue("long i = Long.MIN_VALUE;         --i; return i == Long.MAX_VALUE;");

        this.assertScriptReturnsTrue("long i = 0;                 return --i == -1;");
        this.assertScriptReturnsTrue("long i = 32767;             return --i == 32766;");
        this.assertScriptReturnsTrue("long i = -32768;            return --i == -32769;");
        this.assertScriptReturnsTrue("long i = Integer.MIN_VALUE; return --i == Integer.MIN_VALUE - 1L;");
        this.assertScriptReturnsTrue("long i = Long.MIN_VALUE;    return --i == Long.MAX_VALUE;");
        this.assertScriptReturnsTrue("long i = Long.MAX_VALUE;    return --i == Long.MAX_VALUE - 1;");

        // char

        this.assertScriptReturnsTrue("char c = 1;     --c; return c == 0;");
        this.assertScriptReturnsTrue("char c = 128;   --c; return c == 127;");
        this.assertScriptReturnsTrue("char c = 256;   --c; return c == 255;");
        this.assertScriptReturnsTrue("char c = 32768; --c; return c == 32767;");
        this.assertScriptReturnsTrue("char c = 0;     --c; return c == 65535;");

        this.assertScriptReturnsTrue("char c = 0;     return --c == 65535;");
        this.assertScriptReturnsTrue("char c = 127;   return --c == 126;");
        this.assertScriptReturnsTrue("char c = 128;   return --c == 127;");
        this.assertScriptReturnsTrue("char c = 255;   return --c == 254;");
        this.assertScriptReturnsTrue("char c = 256;   return --c == 255;");
        this.assertScriptReturnsTrue("char c = 32767; return --c == 32766;");
        this.assertScriptReturnsTrue("char c = 32768; return --c == 32767;");
        this.assertScriptReturnsTrue("char c = 65535; return --c == 65534;");

        // float

        this.assertScriptReturnsTrue("float f = 4.0F; --f; return f == 3.0F;");

        // double

        this.assertScriptReturnsTrue("double d = 18.9; --d; return d == 17.9;");

        // boolean

        this.assertScriptUncookable("boolean b = true; --b;");
    }

    @Test public void
    test_15_15_3__Unary_Plus_Operator() throws Exception {
        this.assertExpressionEvaluatesTrue("new Integer(+new Integer(7)).intValue() == 7");

        // The operand is promoted to "int", also in constant expressions (issue #39).
        this.assertExpressionEvaluatesTrue("(\"\" + (+'a')).equals(\"97\")");
        this.assertExpressionEvaluatesTrue("(\"\" + (+(byte) 5)).equals(\"5\")");
        this.assertScriptReturnsTrue("Object o = +'a'; return o.equals(97);");
        this.assertScriptReturnsTrue("Object o = +(short) 7; return o.equals(7);");
        this.assertScriptReturnsTrue("char c = +'a'; return c == 'a';");
        this.assertScriptReturnsTrue("Character c = +'a'; return c == 'a';");
        this.assertScriptReturnsTrue("byte b = +(byte) 5; return b == 5;");
    }

    @Test public void
    test_15_15_4__Unary_Minus_Operator() throws Exception {
        this.assertExpressionEvaluatesTrue("new Integer(-new Integer(7)).intValue() == -7");

        // The operand is promoted to "int", also in constant expressions (issue #39).
        this.assertExpressionEvaluatesTrue("-Byte.MIN_VALUE == 128");
        this.assertExpressionEvaluatesTrue("-((byte) -128) == 128");
        this.assertExpressionEvaluatesTrue("-Short.MIN_VALUE == 32768");
        this.assertExpressionEvaluatesTrue("(\"\" + (-Byte.MIN_VALUE)).equals(\"128\")");
        this.assertExpressionEvaluatesTrue("(\"\" + (-'a')).equals(\"-97\")");
        this.assertScriptReturnsTrue("Object o = -Byte.MIN_VALUE; return o.equals(128);");
        this.assertScriptReturnsTrue("byte b = -(byte) 5; return b == -5;");
        this.assertScriptReturnsTrue("int i = -2147483648; long l = -9223372036854775808L; return i < 0 && l < 0;");
        this.assertClassBodyMainReturnsTrue(
            ""
            + "static final int  X = -Byte.MIN_VALUE;\n"
            + "static final long Y = -Short.MIN_VALUE;\n"
            + "public static boolean main() { return X == 128 && Y == 32768L; }\n"
        );
        this.assertScriptUncookable("byte b = -Byte.MIN_VALUE;");
        this.assertScriptUncookable("short s = -Short.MIN_VALUE;");
    }

    @Test public void
    test_15_17__Multiplicative_operators() throws Exception {
        this.assertExpressionEvaluatesTrue("new Integer(new Byte((byte) 2) * new Short((short) 3)).intValue() == 6");
    }

    @Test public void
    test_15_18_1__String_Concatenation_Operator_plus() throws Exception {
        this.assertScriptExecutable(
            ""
//            + "            final IClassLoader\n"
            + "            final Object\n"
//            + "            iClassLoader = new CompilerIClassLoader(this.sourceFinder, this.classFileFinder, this.getIClassLoader());\n"
            + "            iClassLoader = new Object();\n"
            + "\n"
            + "            // Initialize compile time fields.\n"
//            + "            this.parsedCompilationUnits.clear();\n"
            + "            Object.class.hashCode();\n"
            + "\n"
            + "            // Parse all source files.\n"
            + "            for (Object sourceResource : new Object[1]) {\n"
//            + "//            for (int ii = 0; ii < sourceResources.length; ii++) {\n"
//            + "//                Resource sourceResource = sourceResources[ii];\n"
            + "\n"
//            + "                Compiler.LOGGER.log(Level.FINE, \"Compiling \\\"{0}\\\"\", sourceResource);\n"
            + "                Object.class.hashCode();\n"
            + "\n"
            + "                Object uc = new Object();\n"
//            + "                UnitCompiler uc = new UnitCompiler(\n"
//            + "                    this.parseAbstractCompilationUnit(\n"
//            + "                        sourceResource.getFileName(),                   // fileName\n"
//            + "                        new BufferedInputStream(sourceResource.open()), // inputStream\n"
//            + "                        this.sourceCharset                              // charset\n"
//            + "                    ),\n"
//            + "                    iClassLoader\n"
//            + "                );\n"
//            + "                uc.setTargetVersion(this.targetVersion);\n"
//            + "                uc.setCompileErrorHandler(this.compileErrorHandler);\n"
//            + "                uc.setWarningHandler(this.warningHandler);\n"
//            + "                uc.options(this.options);\n"
            + "                uc.hashCode();\n"
//            + "\n"
//            + "                this.parsedCompilationUnits.add(uc);\n"
            + "            }\n"
            + "\n"
            + "            // Compile all parsed compilation units. The vector of parsed CUs may grow while they are being compiled,\n"
            + "            // but eventually all CUs will be compiled.\n"
            + "            for (int i = 0; i < 3; ++i) {\n"
//            + "                UnitCompiler unitCompiler = (UnitCompiler) this.parsedCompilationUnits.get(i);\n"
            + "                Object unitCompiler = (Object) \"\";\n"
//            + "\n"
//            + "                File sourceFile;\n"
            + "                Object sourceFile;\n"
            + "                {\n"
//            + "                    Java.AbstractCompilationUnit acu = unitCompiler.getAbstractCompilationUnit();\n"
//            + "                    if (acu.fileName == null) throw new InternalCompilerException();\n"
//            + "                    sourceFile = new File(acu.fileName);\n"
            + "                    sourceFile = new Object();\n"
            + "                }\n"
//            + "\n"
//            + "                unitCompiler.setTargetVersion(this.targetVersion);\n"
//            + "                unitCompiler.setCompileErrorHandler(this.compileErrorHandler);\n"
//            + "                unitCompiler.setWarningHandler(this.warningHandler);\n"
//            + "\n"
//            + "                this.benchmark.beginReporting(\"Compiling compilation unit \\\"\" + sourceFile + \"\\\"\");\n"
//            + "                ClassFile[] classFiles;\n"
            + "                Object[] classFiles;\n"
            + "\n"
            + "                // Compile the compilation unit.\n"
//            + "                classFiles = unitCompiler.compileUnit(this.debugSource, this.debugLines, this.debugVars);\n"
            + "                classFiles = new Object[] { \"\" };\n"
//            + "\n"
            + "                // Store the compiled classes and interfaces into class files.\n"
            + "                new String(\n"
            + "                    \"Storing \"\n"
//            + "                    + classFiles.length\n"
            + "                    + 7\n"
            + "                    + \" class file(s) resulting from compilation unit \\\"\"\n"
//            + "                    + sourceFile\n"
            + "                    + new Object()\n"
            + "//                    + \"\\\"\"\n"
            + "                );\n"
//            + "                for (ClassFile classFile : classFiles) this.storeClassFile(classFile, sourceFile);\n"
//            + "                for (Object classFile : new Object[1]) System.out.printf(\"%s%s\", classFile, sourceFile);\n"
            + "            }\n"
        );

        // String conversion of "null" (JLS 5.1.11), in every operand position (issue #49).
        this.assertExpressionEvaluatesTrue("(null + \"a\").equals(\"nulla\")");
        this.assertExpressionEvaluatesTrue("(\"a\" + null + null).equals(\"anullnull\")");
        this.assertExpressionEvaluatesTrue("(\"a\" + \"b\" + null).equals(\"abnull\")");
        this.assertExpressionEvaluatesTrue("(null + \"a\" + null).equals(\"nullanull\")");
        this.assertExpressionEvaluatesTrue("(\"a\" + null).equals(\"anull\")");
        this.assertExpressionEvaluatesTrue("(\"a\" + null + \"b\").equals(\"anullb\")");
        this.assertClassBodyMainReturnsTrue(
            ""
            + "static final String N = null;\n"
            + "static final Object O = null;\n"
            + "static final String S = \"a\" + null + null;\n"
            + "public static boolean main() {\n"
            + "    return (\n"
            + "        (N + \"a\" + N).equals(\"nullanull\")\n"
            + "        && (\"\" + O + N).equals(\"nullnull\")\n"
            + "        && S.equals(\"anullnull\")\n"
            + "    );\n"
            + "}\n"
        );

        // Other operand types.
        this.assertExpressionEvaluatesTrue("(1 + 2 + \"a\").equals(\"3a\")");
        this.assertExpressionEvaluatesTrue("(\"a\" + 1 + 2).equals(\"a12\")");
        this.assertExpressionEvaluatesTrue("(\"\" + 'a' + true + 1.5f).equals(\"atrue1.5\")");
    }

    @Test public void
    test_15_18__Additive_operators() throws Exception {
        // 15.18 Additive Operators -- Numeric
        this.assertExpressionEvaluatesTrue("(new Byte((byte) 7) - new Double(1.5D) + \"x\").equals(\"5.5x\")");

        // 15.18.1.3 Additive Operators -- String Concatentation
        this.assertExpressionEvaluatesTrue(
            "(\"The square root of 6.25 is \" + Math.sqrt(6.25)).equals(\"The square root of 6.25 is 2.5\")"
        );
        this.assertExpressionEvaluatesTrue("1 + 2 + \" fiddlers\" == \"3 fiddlers\"");
        this.assertExpressionEvaluatesTrue("\"fiddlers \" + 1 + 2 == \"fiddlers 12\"");

        // JAVAC does not supports "super-long string literals".
        Assume.assumeFalse(this.isJdk);

        for (int i = 65530; i <= 65537; ++i) {
            char[] ca = new char[i];
            Arrays.fill(ca, 'x');
            String s1 = new String(ca);
            this.assertExpressionEvaluatesTrue("\"" + s1 + "\".length() == " + i);
            this.assertExpressionEvaluatesTrue("(\"" + s1 + "\" + \"XXX\").length() == " + (i + 3));
        }
    }

    @Test public void
    test_15_19__Shift_operators() throws Exception {
        this.assertExpressionEvaluatesTrue("(5 << 2) == 20");
        this.assertExpressionEvaluatesTrue("(-8L >> 1) == -4L");
        this.assertExpressionEvaluatesTrue("(-1 >>> 28) == 15");

        // Unboxing of the operands.
        this.assertScriptReturnsTrue("Integer i = 5; Object o = i << 1; return o.equals(10);");
        this.assertScriptReturnsTrue("Long l = 5L; Object o = l >> 1L; return o.equals(2L);");
        this.assertScriptReturnsTrue("Character c = 'a'; Object o = c >>> 1; return o.equals(48);");
        this.assertScriptReturnsTrue("int i = 5; Integer d = 2; Object o = i << d; return o.equals(20);");
        this.assertScriptReturnsTrue("Byte b = 1; return String.valueOf(b << 8).equals(\"256\");");

        this.assertExpressionUncookable("1.0 << 1");
        this.assertExpressionUncookable("Double.valueOf(1.0) << 1");
        this.assertExpressionUncookable("Boolean.TRUE << 1");
    }

    @Test public void
    test_15_20__Relation_operators() throws Exception {
        // 15.20.1 Numerical Comparison Operators <, <=, > and >=
        this.assertExpressionEvaluatesTrue("new Integer(7) > new Byte((byte) 5)");
    }

    @Test public void
    test_15_21__Equality_operators() throws Exception {

        // 15.21.1 Numerical Equality Operators == and !=
        this.assertExpressionUncookable("new Integer(7) != new Byte((byte) 5)");
        this.assertExpressionEvaluatesTrue("new Integer(7) == 7");
        this.assertExpressionEvaluatesTrue("new Byte((byte) -7) == -7");
        this.assertExpressionEvaluatesTrue("5 == new Byte((byte) 5)");

        // 15.21.2 Boolean Equality Operators == and !=
        this.assertExpressionEvaluatesTrue("new Boolean(true) != new Boolean(true)");
        this.assertExpressionEvaluatesTrue("new Boolean(true) == true");
        this.assertExpressionEvaluatesTrue("false == new Boolean(false)");
        this.assertExpressionEvaluatesTrue("false != true");

        // 15.21.3 Reference Equality Operators == and !=
        this.assertExpressionEvaluatesTrue("new Object() != new Object()");
        this.assertExpressionEvaluatesTrue("new Object() != null");
        this.assertExpressionEvaluatesTrue("new Object() != \"foo\"");
        this.assertExpressionUncookable("new Integer(3) == \"foo\"");
    }

    @Test public void
    test_15_22__Bitwise_and_logical_operators() throws Exception {

        // 15.22.1 Integer Bitwise Operators &, ^, and |
        this.assertExpressionEvaluatesTrue("(7 & 12) == 4");
        this.assertExpressionEvaluatesTrue("(7L & 12) == 4");
        this.assertExpressionUncookable("(7.0 & 12) == 4");
        this.assertExpressionEvaluatesTrue("(new Long(7L) & 12) == 4");
        this.assertExpressionEvaluatesTrue("(Long.valueOf(7) & Byte.valueOf((byte) 12)) == Short.valueOf((short) 4)");
        this.assertExpressionUncookable("(7 & Boolean.TRUE) == 4");
        this.assertScriptReturnsTrue("Integer i = 5; Object o = i & 1; return o.equals(1);");
        this.assertScriptReturnsTrue("Integer i = 5, j = 3; Object o = i & j; return o.equals(1);");
        this.assertScriptReturnsTrue("Long l = 5L; Object o = l | 2L; return o.equals(7L);");
        this.assertScriptReturnsTrue("Byte b = 5; char c = 3; Object o = b ^ c; return o.equals(6);");
        this.assertScriptReturnsTrue("Integer i = 5; return String.valueOf(i & 6).equals(\"4\");");
        this.assertExpressionUncookable("Double.valueOf(7) & 12");

        // 15.22.2 Boolean Logical Operators &, ^, and |
        this.assertExpressionEvaluatesTrue("new Boolean(true) & new Boolean(true)");
        this.assertExpressionEvaluatesTrue("new Boolean(true) ^ false");
        this.assertExpressionEvaluatesTrue("false | new Boolean(true)");
    }

    @Test public void
    test_15_23__Conditional_and_operator() throws Exception {
        // 15.23 Conditional-And Operator &&
        this.assertExpressionEvaluatesTrue("new Boolean(true) && new Boolean(true)");
        this.assertExpressionEvaluatesTrue("new Boolean(true) && true");
        this.assertExpressionEvaluatesTrue("true && new Boolean(true)");
    }

    @Test public void
    test_15_24__Conditional_or_operator() throws Exception {
        // 15.24 Conditional-Or Operator ||
        this.assertExpressionEvaluatesTrue("new Boolean(true) || new Boolean(false)");
        this.assertExpressionEvaluatesTrue("new Boolean(false) || true");
        this.assertExpressionEvaluatesTrue("true || new Boolean(true)");
    }

    /**
     * 15.25 Conditional Operator ? :
     */
    @Test public void
    test_15_25__Conditional_operator__1() throws Exception {

        this.assertExpressionEvaluatesTrue("99 == (true ? 99 : -1)");
        this.assertExpressionEvaluatesTrue("-1 == (false ? 99 : -1)");

        this.assertExpressionEvaluatesTrue("99   == (true  ? 99   : null)");
        this.assertExpressionEvaluatesTrue("null == (false ? 99   : null)");
        this.assertExpressionEvaluatesTrue("null == (true  ? null : 99)");
        this.assertExpressionEvaluatesTrue("99   == (false ? null : 99)");

        // Related to "#85 Ternary expression resolves to strange supertype":
        this.assertScriptCookable(
            ""
            + "import java.util.*;\n"
            + "List list = true ? new ArrayList() : Arrays.asList(new String [] {});"
        );

        this.assertExpressionEvaluatesTrue("7 == (true ? 7 : 9)");
        this.assertExpressionEvaluatesTrue("9 == (Boolean.FALSE ? 7 : 9)");
        this.assertExpressionUncookable("1 ? 2 : 3)");
        this.assertExpressionUncookable("true ? 2 : System.currentTimeMillis())");

        // List 1, bullet 1
        this.assertExpressionEvaluatesTrue("(true ? 2 : 3) == 2");
        this.assertExpressionEvaluatesTrue("(true ? null : null) == null");

        // List 1, bullet 2
        this.assertExpressionEvaluatesTrue("(true ? 'a' : Character.valueOf('b')) == 'a'");

        // List 1, bullet 3
        this.assertExpressionEvaluatesTrue("(true ? \"\" : null).getClass() == String.class");

        // List 1, bullet 4, bullet 1
        this.assertScriptExecutable("short s = true ? (byte) 1 : (short) 2;");

        // List 1, bullet 4, bullet 2
        this.assertScriptUncookable("byte b = false ? (byte) 1 : -129;");
        this.assertScriptExecutable("byte b = false ? (byte) 1 : -128;");
        this.assertScriptExecutable("byte b = false ? (byte) 1 : 127;");
        this.assertScriptUncookable("byte b = false ? (byte) 1 : 128;");
        this.assertScriptUncookable("short s = false ? (short) 1 : -32769;");
        this.assertScriptExecutable("short s = false ? (short) 1 : -32768;");
        this.assertScriptExecutable("short s = false ? (short) 1 : 32767;");
        this.assertScriptUncookable("short s = false ? (short) 1 : 32768;");
        this.assertScriptUncookable("char c = false ? 'A' : -1;");
        this.assertScriptExecutable("char c = false ? 'A' : 0;");
        this.assertScriptExecutable("char c = false ? 'A' : 65535;");
        this.assertScriptUncookable("char c = false ? 'A' : 65536;");

        // List 1, bullet 4, bullet 3
        this.assertScriptUncookable("byte b = false ? Byte.valueOf((byte) 1) : -129;");
        this.assertScriptExecutable("byte b = false ? Byte.valueOf((byte) 1) : -128;");
        this.assertScriptExecutable("byte b = false ? Byte.valueOf((byte) 1) : 127;");
        this.assertScriptUncookable("byte b = false ? Byte.valueOf((byte) 1) : 128;");
        this.assertScriptUncookable("short s = false ? Short.valueOf((short) 1) : -32769;");
        this.assertScriptExecutable("short s = false ? Short.valueOf((short) 1) : -32768;");
        this.assertScriptExecutable("short s = false ? Short.valueOf((short) 1) : 32767;");
        this.assertScriptUncookable("short s = false ? Short.valueOf((short) 1) : 32768;");
        this.assertScriptUncookable("char c = false ? Character.valueOf('A') : -1;");
        this.assertScriptExecutable("char c = false ? Character.valueOf('A') : 0;");
        this.assertScriptExecutable("char c = false ? Character.valueOf('A') : 65535;");
        this.assertScriptUncookable("char c = false ? Character.valueOf('A') : 65536;");

        // List 1, bullet 4, bullet 4
        this.assertScriptExecutable("long l = false ? 1 : 1L;");
        this.assertScriptUncookable("int i = false ? 1 : 1L;");

        // List 1, bullet 5
        this.assertExpressionEvaluatesTrue("(true ? new Object() : \"\") != null");
        this.assertExpressionEvaluatesTrue("(true ? new Object() : 7).getClass().getName().equals(\"java.lang.Object\")");
        this.assertExpressionEvaluatesTrue("(true ? new Object() : Integer.valueOf(7)).getClass().getName().equals(\"java.lang.Object\")");
        this.assertExpressionEvaluatesTrue("(true ? Integer.valueOf(9) : Integer.valueOf(7)).getClass().getName().equals(\"java.lang.Integer\")");
        this.assertExpressionEvaluatesTrue("(true ? Integer.valueOf(9) : Long.valueOf(7)) == 9L");
        this.assertScriptCookable("import org.codehaus.commons.compiler.tests.JlsTest; (true ? new JlsTest.D1() : new JlsTest.D2()).c1();");
        this.assertScriptCookable("import org.codehaus.commons.compiler.tests.JlsTest; (true ? new JlsTest.D3() : new JlsTest.D4()).c1();");
        // Why, for god's sake, can JAVAC compile these assignments?? Some kind of type inference must happen here...
        if (this.isJdk) {
            this.assertScriptCookable("import org.codehaus.commons.compiler.tests.JlsTest; JlsTest.I1 i1 = (\"\".equals(\"\") ? new JlsTest.D3() : new JlsTest.D4());");
            this.assertScriptCookable("import org.codehaus.commons.compiler.tests.JlsTest; JlsTest.I2 i2 = (true ? new JlsTest.D3() : new JlsTest.D4());");
        }
        this.assertScriptUncookable("import org.codehaus.commons.compiler.tests.JlsTest; JlsTest.I3 i3 = (true ? new JlsTest.D3() : new JlsTest.D4());");

        // List 2, bullet 1
        this.assertScriptReturnsTrue("int a = 3; return (a == 0 ? ++a : a + a) == 6;");

        // List 2, bullet 2
        this.assertScriptReturnsTrue("int a = 3; return (a != 0 ? ++a : a + a) == 4;");
    }
    public static class C1                              { public void c1() {} } // SUPPRESS CHECKSTYLE Javadoc|Align:7
    public static class D1 extends C1                   {}
    public static class D2 extends C1                   {}
    public        interface I1                          { void                  i1(); }
    public        interface I2                          { void                  i2(); }
    public        interface I3                          { void                  i3(); }
    public static class D3 extends C1 implements I1, I2 { @Override public void i1() {} @Override public void i2() {} }
    public static class D4 extends C1 implements I1, I2 { @Override public void i1() {} @Override public void i2() {} }

    @Test public void
    test_15_25__Conditional_operator__2() throws Exception {
//        IScriptEvaluator eval = new ScriptEvaluator();
//        eval.setReturnType(Object[].class);
        String script = (
            ""
            + "class A {\n"
            + "    private Integer val;\n"
            + "    public A(Integer v) {\n"
            + "         val = v;\n"
            + "    }\n"
            + "    public boolean isNull() {\n"
            + "        return val == null;\n"
            + "    }\n"
            + "    public int getInt() {\n"
            + "        return val;\n"
            + "    }\n"
            + "}\n"
            + "A a = new A(3);\n"
            + "Object[] c = new Object[] {\n"
            + "    !a.isNull() ? (Object) a.getInt() : null,\n" // auto boxing & casting in LHS
            + "    !a.isNull() ? a.getInt() : null,\n"          // auto boxing & no explicit casting in LHS
            + "    a.isNull() ? null : (Object) a.getInt(),\n"  // auto boxing & casting in RHS
            + "    a.isNull() ? null : a.getInt(),\n"           // auto boxing & no explicit casting in RHS
            + "    (Object) \"hello\",\n"                       // simple casting
            + "};\n"
            + "return c;"
        );
        final Object[] result = (Object[]) this.assertScriptExecutable(script, Object[].class);
        Assert.assertArrayEquals(new Object[] {
            3,
            3,
            3,
            3,
            "hello",
        }, result);
    }

    @Test public void
    test_15_25__Conditional_operator__3() throws Exception {

        // A constant of type "byte" or "short" that is not representable in the type "char" of the other operand:
        // the type is "int" (binary numeric promotion), not "char" (issue #51).
        String cz = "char c = 'a'; boolean z = false; ";
        this.assertScriptReturnsTrue("char c = 'a'; Object x = true ? c : (short) -1; return x.equals(97);");
        this.assertScriptReturnsTrue("char c = 'a'; Object x = false ? c : (short) -1; return x.equals(-1);");
        this.assertScriptReturnsTrue(cz + "Object x = z ? c : (short) -1; return x.equals(-1);");
        this.assertScriptReturnsTrue(cz + "Object x = z ? c : (byte) -1; return x.equals(-1);");
        this.assertScriptReturnsTrue(cz + "int i = !z ? c : (short) -1; return i == 97;");

        // An "int" constant that is not representable in the type "byte" of the other operand.
        this.assertScriptReturnsTrue("byte b = 1; boolean z = false; Object x = z ? b : 500; return x.equals(500);");

        // Constant expressions: the value has the type "int".
        this.assertExpressionEvaluatesTrue("(\"\" + (true ? 'a' : (short) -1)).equals(\"97\")");
        this.assertScriptReturnsTrue("final char C = 'a'; return (\"\" + (true ? C : (byte) -1)).equals(\"97\");");
        this.assertScriptReturnsTrue("Object x = true ? 'a' : (short) -1; return x.equals(97);");
        this.assertScriptReturnsTrue("char c = true ? 'a' : (short) -1; return c == 'a';");
        this.assertScriptReturnsTrue("byte b = true ? 'a' : (short) -1; return b == 97;");

        // The same with a "long", "float" or "double" operand.
        this.assertScriptReturnsTrue("Object x = true ? 'a' : 1L; return x.equals(97L);");
        this.assertScriptReturnsTrue("Object x = true ? 'a' : 1.5f; return x.equals(97.0f);");
        this.assertScriptReturnsTrue("Object x = true ? (short) -1 : 2.5; return x.equals(-1.0);");
        this.assertScriptReturnsTrue(cz + "Object x = !z ? c : 1L; return x.equals(97L);");
        this.assertExpressionEvaluatesTrue("(\"\" + (true ? 'a' : 1.5)).equals(\"97.0\")");
        this.assertExpressionEvaluatesTrue("(\"\" + (true ? (byte) 1 : 2L)).equals(\"1\")");
    }

    @Test public void
    test_15_25__Conditional_operator__4() throws Exception {

        // An "int" constant that is representable in the type of the other operand (issue #56).
        String decl = "boolean z = false; byte b = 1; short s = 1; char c = 'a'; Short S = 1; Long J = 1L; ";
        this.assertScriptReturnsTrue(decl + "Object x = z ? b : 5; return x.equals((byte) 5);");
        this.assertScriptReturnsTrue(decl + "Object x = z ? 5 : b; return x.equals((byte) 1);");
        this.assertScriptReturnsTrue(decl + "Object x = z ? s : 5; return x.equals((short) 5);");
        this.assertScriptReturnsTrue(decl + "Object x = z ? c : 66; return x.equals('B');");
        this.assertScriptReturnsTrue(decl + "byte x = z ? b : 5; return x == 5;");
        this.assertScriptReturnsTrue(decl + "char x = !z ? c : 66; return x == 'a';");

        // Two operands of different types, neither of which is such a constant: binary numeric promotion.
        this.assertScriptReturnsTrue(decl + "Object x = z ? s : c; return x.equals(97);");
        this.assertScriptReturnsTrue(decl + "Object x = z ? b : c; return x.equals(97);");
        this.assertScriptReturnsTrue(decl + "Object x = z ? c : s; return x.equals(1);");
        this.assertScriptReturnsTrue(decl + "Object x = z ? (short) -1 : c; return x.equals(97);");
        this.assertScriptReturnsTrue(decl + "Object x = z ? c : (byte) 1; return x.equals(1);");
        this.assertScriptReturnsTrue(decl + "Object x = z ? c : (short) 66; return x.equals(66);");
        this.assertScriptReturnsTrue(decl + "Object x = z ? S : c; return x.equals(97);");
        this.assertScriptReturnsTrue(decl + "Object x = z ? J : s; return x.equals(1L);");
        this.assertScriptReturnsTrue(decl + "int x = z ? s : c; return x == 97;");

        // The constant value of a conditional expression has the type of the expression (issue #55).
        this.assertExpressionEvaluatesTrue("(\"\" + (true ? 1 : 2.0)).equals(\"1.0\")");
        this.assertExpressionEvaluatesTrue("(\"\" + (false ? 1L : 2.5f)).equals(\"2.5\")");
        this.assertExpressionEvaluatesTrue("(\"\" + (true ? (byte) 1 : 2L)).equals(\"1\")");
        this.assertExpressionEvaluatesTrue("String.valueOf(true ? 1 : 2.0).equals(\"1.0\")");
        this.assertExpressionEvaluatesTrue("(true ? 1 : 2L) == 1L");
        this.assertScriptReturnsTrue("char c = 'a'; return (\"\" + (true ? 97 : c)).equals(\"a\");");
        this.assertScriptReturnsTrue("Object x = true ? 1 : 2L; return x.equals(1L);");
        this.assertScriptReturnsTrue("Object x = true ? 1 : 'a'; return x.equals((char) 1);");
        this.assertScriptReturnsTrue("Long x = true ? 1 : 2L; return x == 1L;");
        this.assertClassBodyMainReturnsTrue(
            ""
            + "static String d(double x) { return \"d\" + x; }\n"
            + "public static boolean main() { return d(true ? 1 : 2.0).equals(\"d1.0\"); }\n"
        );

        // Invalid neighbors: the constant is not representable, or the type of the expression is wider than the
        // type of the variable.
        this.assertScriptUncookable("boolean z = false; byte b = 1; byte x = z ? b : 500;");
        this.assertScriptUncookable("boolean z = false; char c = 'a'; char x = z ? c : -1;");
        this.assertScriptUncookable("boolean z = false; short s = 1; char c = 'a'; short x = z ? s : c;");
        this.assertScriptUncookable("byte x = true ? 1 : 2L;");
    }

    /**
     * 15.26 Assignment Operators
     */
    @Test public void
    test_15_26__Assignment_operators() throws Exception {

        // 15.26.2 Compound Assignment Operators
        this.assertScriptReturnsTrue("int a = 7; a += 3; return a == 10;");
        this.assertScriptReturnsTrue("int a = 7; a %= 3; return a == 1;");
        this.assertScriptUncookable("Object a = \"foo\"; a += 3;");
        this.assertScriptUncookable("int a = 7; a += \"foo\";");
        this.assertScriptReturnsTrue("String a = \"foo\"; a += 3; return a.equals(\"foo3\");");
        this.assertScriptReturnsTrue("String a = \"foo\"; a += 'o'; return a.equals(\"fooo\");");
        this.assertScriptReturnsTrue("String a = \"foo\"; a += 1.0; return a.equals(\"foo1.0\");");
        this.assertScriptReturnsTrue("String[] a = { \"foo\" }; a[0] += 1.0; return a[0].equals(\"foo1.0\");");
        this.assertScriptReturnsTrue("Integer a = 7; a += 3; return a == 10;");
        this.assertScriptReturnsTrue("int a = 7; a += new Integer(3); return a == 10;");
        // JANINO-155: Compound assignment does not implement boxing conversion
        this.assertScriptReturnsTrue("Double[] a = { 1.0, 2.0 }; a[0] += 1.0; return a[0] == 2.0;");

        // The value of a compound assignment is the value of the variable after the assignment.
        this.assertScriptReturnsTrue("int i = 1, j = 10; return j - (i += 2) == 7 && i == 3;");
        this.assertScriptReturnsTrue("int i = 1; return 100 - (i += 2) == 97;");
        this.assertScriptReturnsTrue("int i = 7; int x = (i += 2); return x == 9 && i == 9;");
        this.assertScriptReturnsTrue("long l = 7; long x = (l <<= 2); return x == 28 && l == 28;");
        this.assertScriptReturnsTrue("byte b = 100; int x = (b += 100); return x == -56 && b == -56;");
        this.assertScriptReturnsTrue("int[] a = { 0, 0 }; int i = 1; a[1] -= (i += 2); return a[1] == -3;");
        this.assertScriptReturnsTrue("int[] a = { 0, 0 }; int i = 1; a[1] = (i += 2); return a[1] == 3 && a[0] == 0;");
        this.assertScriptReturnsTrue("int[] a = { 7 }; int x = (a[0] += 2); return x == 9 && a[0] == 9;");
        this.assertScriptReturnsTrue("double[] a = { 7 }; double x = (a[0] *= 2); return x == 14 && a[0] == 14;");
        this.assertScriptReturnsTrue("Integer i = 7; Integer x = (i += 2); return x == 9 && i == 9;");
        this.assertScriptReturnsTrue("Integer[] a = { 7 }; Object x = (a[0] += 2); return x.equals(9) && a[0] == 9;");
        this.assertScriptReturnsTrue("String s = \"a\"; Object x = (s += \"b\"); return x == s && s.equals(\"ab\");");
        this.assertClassBodyMainReturnsTrue(
            ""
            + "static int  f = 7;\n"
            + "static long g = 7;\n"
            + "static class H { int x = 7; long y = 7; }\n"
            + "public static boolean main() {\n"
            + "    int  a = (f += 2);\n"
            + "    long b = 10 - (g -= 2);\n"
            + "    H    o = new H();\n"
            + "    int  i = 1;\n"
            + "    o.x = (i += 2);\n"
            + "    long c = (o.y *= 3);\n"
            + "    return a == 9 && f == 9 && b == 5 && g == 5 && o.x == 3 && i == 3 && c == 21 && o.y == 21;\n"
            + "}\n"
        );
        this.assertScriptUncookable("Byte b = 1; Object x = (b += 1);");

        // The result is narrowed to the type of the variable, also to and from "char" (issue #37).
        this.assertScriptReturnsTrue("char c = 0; long j = 0xFFFFL; c |= j; return (int) c == 65535;");
        this.assertScriptReturnsTrue("char c = 0; double d = 65535.0; c += d; return (int) c == 65535;");
        this.assertScriptReturnsTrue("char c = 1; float f = 2F; c -= f; return (int) c == 65535;");
        this.assertScriptReturnsTrue("char c = 0; byte b = -1; c ^= b; return (int) c == 65535;");
        this.assertScriptReturnsTrue("short s = 0; char c = 65535; s += c; return s == -1;");
        this.assertScriptReturnsTrue("char c = 0; long j = 0xFFFFL; int x = (c |= j); return x == 65535 && c == x;");
        this.assertScriptReturnsTrue("char[] a = { 0 }; long j = -1L; int x = (a[0] += j); return x == 65535;");
    }

    @Test public void
    test_15_27_1__Lambda_parameters() throws Exception {

        // "java.util.Function" only since Java 10.
        if (CommonsCompilerTestSuite.JVM_VERSION < 10) return;

        this.assertScriptExecutable("java.util.function.Function<String, Integer> f = (var s) -> s.length();\n");
    }

    @Test public void
    test_16_2_13__break_yield_continue_return_and_throw_Statements() throws Exception {
        this.assertClassBodyMainReturnsTrue(
            ""
            + "public static boolean\n"
            + "main() {\n"
            + "    String s;\n"
            + "    if (System.currentTimeMillis() == 7) {\n"
            + "        s = \"seven\";\n"
            + "    } else\n"
            + "    if (System.currentTimeMillis() != 8) {\n"
            + "        s = \"not eight\";\n"
            + "    } else\n"
            + "    {\n"
            + "        throw new RuntimeException();\n"
            + "    }\n"
            + "\n"
            + "    return s.equals(\"not eight\");\n"
            + "}\n"
        );
    }

    @Test public void
    test_16__Definite_Assignment() throws Exception {

        // A local variable that is assigned in the body of a loop, in a condition or in a branch; the local variable
        // "y" is declared after "x", which matters for the stack maps that Janino generates (issue #54).
        String u = "is not initialized|might not have been initialized|compiler.err.var.might.not";
        String d = "boolean z = false; int x; int y = 0; ";

        // Definitely assigned.
        this.assertScriptReturnsTrue(d + "do { x = 1; } while (z); return x == 1;");
        this.assertScriptReturnsTrue(d + "do { x = 1; } while (!z && x < 0); return x == 1;");
        this.assertScriptReturnsTrue(d + "do { if (z) { x = 1; } else { x = 2; } } while (x < 0); return x == 2;");
        this.assertScriptReturnsTrue(d + "if (!z && (x = 1) > 0) return x == 1; return false;");
        this.assertScriptReturnsTrue(d + "if (z || (x = 0) > 0) { y = 1; } else { y = x + 5; } return y == 5;");
        this.assertScriptReturnsTrue(d + "boolean b = z || (x = 1) > 0; return b;");
        this.assertScriptReturnsTrue(d + "boolean b = (x = 1) > 0 && (y = x) > 0; return b && y == 1;");
        this.assertScriptReturnsTrue(d + "int v = (x = 1) > 5 ? 5 : x; return v == 1;");
        this.assertScriptReturnsTrue(d + "int v = (x = 1) > 0 ? x : x; return v == 1;");
        this.assertScriptReturnsTrue(d + "while (!z && (x = 1) > 0) { y = x; z = true; } return y == 1;");
        this.assertScriptReturnsTrue(d + "for (int i = 0; (x = i) < 3; i++) { y += x; } return y == 3;");
        this.assertScriptReturnsTrue(d + "if (z) x = 1; else x = 2; return x == 2;");
        this.assertScriptReturnsTrue(d + "switch (y) { case 0: x = 1; break; default: x = 2; } return x == 1;");
        this.assertScriptReturnsTrue(d + "try { x = 1; } finally { y = 2; } return x + y == 3;");
        this.assertScriptReturnsTrue(d + "L: { x = 1; if (z) break L; x = 2; } return x == 2;");
        this.assertScriptReturnsTrue(d + "while (true) { x = 1; if (y == 0) break; } return x == 1;");
        this.assertScriptReturnsTrue("long x; int y = 0; do { x = 1L; } while (x < 0); return x == 1L;");

        // Not definitely assigned.
        this.assertScriptUncookable(d + "for (int i = 0; i < 1; i++) x = 1; y = x;", u);
        this.assertScriptUncookable(d + "while (z) { x = 1; } y = x;", u);
        this.assertScriptUncookable(d + "if (z) x = 1; y = x;", u);
        this.assertScriptUncookable(d + "if (z) { x = 1; } else { y = x; }", u);
        this.assertScriptUncookable(d + "switch (y) { case 0: x = 1; break; } y = x;", u);
        this.assertScriptUncookable(d + "try { x = 1; } catch (RuntimeException e) { } y = x;", u);
        this.assertScriptUncookable(d + "L: { if (z) break L; x = 1; } y = x;", u);
        this.assertScriptUncookable(d + "for (int i = 0; i < 1; x = ++i) {} y = x;", u);
        this.assertScriptUncookable(d + "do { if (z) break; x = 1; } while (z); y = x;", u);
        this.assertScriptUncookable(d + "boolean b = z && (x = 1) > 0; y = x;", u);
        this.assertScriptUncookable(d + "x++;", u);
        this.assertScriptUncookable(d + "x += 1;", u);
        this.assertScriptUncookable("long x; int y = 0; if (y == 0) x = 1; y = (int) x;", u);
    }
}
