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

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.codehaus.commons.compiler.sandbox.SandboxPolicy;
import org.codehaus.janino.Java;
import org.codehaus.janino.Parser;
import org.codehaus.janino.Scanner;
import org.codehaus.janino.SimpleCompiler;
import org.codehaus.janino.Unparser;
import org.codehaus.janino.util.SandboxInstrumenter;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link SandboxInstrumenter}.
 */
public
class SandboxInstrumenterTest {

    private static final String GUARD = "org.codehaus.commons.compiler.sandbox.Guard";

    @SuppressWarnings("static-method") @Test public void
    testInstrumentation() throws Exception {
        String text = (
            ""
            + "abstract class A {\n"
            + "    A(int n) { super(); this.n = n; }\n"
            + "    abstract void g();\n"
            + "    int f(int n) {\n"
            + "        while (n > 0) n--;\n"
            + "        do { n++; } while (n < 3);\n"
            + "        for (int i = 0; i < n; i++) {}\n"
            + "        for (String s : new String[0]) continue;\n"
            + "        long[][] a = new long[n][2];\n"
            + "        Object[][] o = new Object[n][];\n"
            + "        int[] c = { 1, 2 };\n"
            + "        return n;\n"
            + "    }\n"
            + "    int n;\n"
            + "}\n"
        );
        String g = SandboxInstrumenterTest.GUARD;
        String expected = (
            ""
            + "abstract class A {\n"
            + "    A(int n) { super(); " + g + ".tick(); this.n = n; }\n"
            + "    abstract void g();\n"
            + "    int f(int n) {\n"
            + "        " + g + ".tick();\n"
            + "        while (n > 0) { " + g + ".tick(); n--; }\n"
            + "        do { " + g + ".tick(); { n++; } }while (n < 3);\n"
            + "        for (int i = 0; i < n; i++) { " + g + ".tick(); {} }\n"
            + "        for (String s : new String[" + g + ".arrayLength(0, 4)]) { " + g + ".tick(); continue; }\n"
            + "        long[][] a = (long[][]) " + g + ".newArray(long.class, new int[] { n, 2 });\n"
            + "        Object[][] o = new Object[" + g + ".arrayLength(n, 4)][];\n"
            + "        int[] c = { 1, 2 };\n"
            + "        return n;\n"
            + "    }\n"
            + "    int n;\n"
            + "}\n"
        );

        Java.AbstractCompilationUnit acu = new Parser(
            new Scanner(null, new StringReader(text))
        ).parseAbstractCompilationUnit();

        StringWriter sw = new StringWriter();
        Unparser.unparse(new SandboxInstrumenter().copyAbstractCompilationUnit(acu), sw);
        Assert.assertEquals(
            UnparserTest.normalizeWhitespace(expected),
            UnparserTest.normalizeWhitespace(sw.toString())
        );
    }

    /**
     * Verifies that the "strict" checks are inserted if an executor is required.
     */
    @SuppressWarnings("static-method") @Test public void
    testStrictInstrumentation() throws Exception {
        String text = (
            ""
            + "class A {\n"
            + "    int f(int n) {\n"
            + "        while (n > 0) n--;\n"
            + "        long[][] a = new long[n][2];\n"
            + "        Object[] o = new Object[n];\n"
            + "        return n;\n"
            + "    }\n"
            + "}\n"
        );
        String g = SandboxInstrumenterTest.GUARD;
        String expected = (
            ""
            + "class A {\n"
            + "    int f(int n) {\n"
            + "        " + g + ".tickStrict();\n"
            + "        while (n > 0) { " + g + ".tickStrict(); n--; }\n"
            + "        long[][] a = (long[][]) " + g + ".newArrayStrict(long.class, new int[] { n, 2 });\n"
            + "        Object[] o = new Object[" + g + ".arrayLengthStrict(n, 4)];\n"
            + "        return n;\n"
            + "    }\n"
            + "}\n"
        );

        Java.AbstractCompilationUnit acu = new Parser(
            new Scanner(null, new StringReader(text))
        ).parseAbstractCompilationUnit();

        StringWriter sw = new StringWriter();
        Unparser.unparse(new SandboxInstrumenter(true).copyAbstractCompilationUnit(acu), sw);
        Assert.assertEquals(
            UnparserTest.normalizeWhitespace(expected),
            UnparserTest.normalizeWhitespace(sw.toString())
        );
    }

    /**
     * Verifies that only code that is compiled with a sandbox policy is instrumented.
     */
    @SuppressWarnings("static-method") @Test public void
    testOnlyWithSandboxPolicy() throws Exception {
        String text = "public class B { public static int meth(int n) { while (n > 0) n--; return n; } }";

        SimpleCompiler sc1 = new SimpleCompiler();
        sc1.cook(text);
        Assert.assertFalse(SandboxInstrumenterTest.referencesGuard(sc1.getBytecodes()));

        SimpleCompiler sc2 = new SimpleCompiler();
        sc2.setSandboxPolicy(SandboxPolicy.JAVA_LANG_BASIC);
        sc2.cook(text);
        Assert.assertTrue(SandboxInstrumenterTest.referencesGuard(sc2.getBytecodes()));
        Assert.assertEquals(0, sc2.getClassLoader().loadClass("B").getMethod("meth", int.class).invoke(null, 5));
    }

    private static boolean
    referencesGuard(Map<String, byte[]> bytecodes) {
        for (byte[] bytecode : bytecodes.values()) {

            // The constant pool of a class file stores the class name in (modified) UTF-8.
            String s = new String(bytecode, StandardCharsets.ISO_8859_1);
            if (s.contains(SandboxInstrumenterTest.GUARD.replace('.', '/'))) return true;
        }
        return false;
    }
}
