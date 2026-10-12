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

import java.util.Arrays;
import java.util.TreeSet;

import org.codehaus.janino.ScriptEvaluator;
import org.codehaus.janino.SimpleCompiler;
import org.junit.Assert;
import org.junit.Test;

/**
 * The binary names of local classes (issue #143): a local class {@code L} in a type {@code E} is named {@code E$L},
 * as before; only where that name is already taken, by a member type {@code L} of {@code E} or by another local class
 * {@code L} in {@code E}, the local class gets a number, like with JAVAC (JLS 13.1): {@code E$2L}, or {@code E$1L} next
 * to a member type. Before, all of them were named {@code E$L}, and only one class file was kept.
 */
public
class LocalClassBinaryNameTest {

    @Test public void
    testNoCollision() throws Exception {
        LocalClassBinaryNameTest.assertClassNames(
            ""
            + "public class P {\n"
            + "    static Object a() { class L { } return new L(); }\n"
            + "    static class Q { static Object b() { class L { } return new L(); } }\n"
            + "}\n",
            "P", "P$L", "P$Q", "P$Q$L"
        );
    }

    @Test public void
    testTwoLocalClasses() throws Exception {
        SimpleCompiler sc = LocalClassBinaryNameTest.assertClassNames(
            ""
            + "public class P {\n"
            + "    static Object a() { class L { } return new L(); }\n"
            + "    static Object b() { class L { } return new L(); }\n"
            + "    public static String run() { return a().getClass().getName() + \",\" + b().getClass().getName(); }\n"
            + "}\n",
            "P", "P$2L", "P$L"
        );
        Assert.assertEquals("P$L,P$2L", sc.getClassLoader().loadClass("P").getMethod("run").invoke(null));
    }

    @Test public void
    testThreeLocalClassesInBlocks() throws Exception {
        LocalClassBinaryNameTest.assertClassNames(
            ""
            + "public class P {\n"
            + "    P() { class L { } new L(); }\n"
            + "    void m() { { class L { } new L(); } { class L { } new L(); } }\n"
            + "}\n",
            "P", "P$2L", "P$3L", "P$L"
        );
    }

    @Test public void
    testMemberClass() throws Exception {
        SimpleCompiler sc = LocalClassBinaryNameTest.assertClassNames(
            ""
            + "public class P {\n"
            + "    static class L { }\n"
            + "    static Object a() { class L { } return new L(); }\n"
            + "    static Object b() { class L { } return new L(); }\n"
            + "}\n",
            "P", "P$1L", "P$2L", "P$L"
        );

        // The member class keeps its name, which other compilation units may refer to.
        Class<?> p = sc.getClassLoader().loadClass("P");
        Assert.assertEquals(p, sc.getClassLoader().loadClass("P$L").getDeclaringClass());
    }

    @Test public void
    testNestedTypes() throws Exception {
        LocalClassBinaryNameTest.assertClassNames(
            ""
            + "public class P {\n"
            + "    static class Q {\n"
            + "        static class M { }\n"
            + "        static Object f() { class M { } return new M(); }\n"
            + "    }\n"
            + "    static Object g() {\n"
            + "        return new Object() {\n"
            + "            Object a() { class L { } return new L(); }\n"
            + "            Object b() { class L { } return new L(); }\n"
            + "        };\n"
            + "    }\n"
            + "}\n",
            "P", "P$1", "P$1$2L", "P$1$L", "P$Q", "P$Q$1M", "P$Q$M"
        );
    }

    @Test public void
    testScripts() throws Exception {
        ScriptEvaluator se = new ScriptEvaluator();
        se.setReturnTypes(new Class<?>[] { String.class, String.class });
        se.cook(new String[] {
            "class L { String l() { return \"s1\"; } } return new L().l();",
            "class L { String l() { return \"s2\"; } } return new L().l();",
        });
        Assert.assertEquals("s1", se.evaluate(0, new Object[0]));
        Assert.assertEquals("s2", se.evaluate(1, new Object[0]));
        Assert.assertEquals(
            Arrays.asList("SC", "SC$2L", "SC$L"),
            Arrays.asList(new TreeSet<String>(se.getBytecodes().keySet()).toArray())
        );
    }

    private static SimpleCompiler
    assertClassNames(String source, String... expectedClassNames) throws Exception {
        SimpleCompiler sc = new SimpleCompiler();
        sc.cook(source);
        Assert.assertEquals(
            Arrays.asList(expectedClassNames),
            Arrays.asList(new TreeSet<String>(sc.getBytecodes().keySet()).toArray())
        );
        return sc;
    }
}
