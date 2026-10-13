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

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.util.Map;

import org.codehaus.commons.nullanalysis.Nullable;
import org.codehaus.janino.Mod;
import org.codehaus.janino.SimpleCompiler;
import org.codehaus.janino.util.ClassFile;
import org.junit.Assert;
import org.junit.Test;

/**
 * The access flags of the class and interface initialization method {@code <clinit>}: {@code ACC_STATIC} only, like
 * JAVAC writes them (JVMS 4.6, issue #107); before, JANINO added {@code ACC_PUBLIC}, and {@code ACC_ABSTRACT} in an
 * interface.
 */
public
class ClassInitializerFlagsTest {

    private static final String SOURCE = (
        ""
        + "public class P {\n"
        + "    static int a = Integer.parseInt(\"1\");\n"
        + "    static class N { static int b; static { b = 2; } }\n"
        + "    interface I { int X = Integer.parseInt(\"3\"); }\n"
        + "    enum E { A, B }\n"
        + "    static class K { static final int C = 4; }\n"
        + "}\n"
    );

    @Test public void
    testClassInitializerFlags() throws Exception {
        SimpleCompiler sc = new SimpleCompiler();
        sc.cook(ClassInitializerFlagsTest.SOURCE);
        Map<String, byte[]> bytecodes = sc.getBytecodes();

        for (String className : new String[] { "P", "P$N", "P$I", "P$E" }) {
            ClassFile.MethodInfo clinit = ClassInitializerFlagsTest.classInitializationMethod(bytecodes, className);
            Assert.assertNotNull(className, clinit);
            Assert.assertEquals(className, Mod.STATIC, clinit.getAccessFlags());
        }

        // A class with only constant variables has no "<clinit>".
        Assert.assertNull(ClassInitializerFlagsTest.classInitializationMethod(bytecodes, "P$K"));
    }

    /**
     * The classes are initialized as before.
     */
    @Test public void
    testClassInitialization() throws Exception {
        SimpleCompiler sc = new SimpleCompiler();
        sc.cook(ClassInitializerFlagsTest.SOURCE);
        ClassLoader cl = sc.getClassLoader();

        Assert.assertEquals(1, ClassInitializerFlagsTest.staticFieldValue(cl.loadClass("P"), "a"));
        Assert.assertEquals(2, ClassInitializerFlagsTest.staticFieldValue(cl.loadClass("P$N"), "b"));
        Assert.assertEquals(3, ClassInitializerFlagsTest.staticFieldValue(cl.loadClass("P$I"), "X"));
        Assert.assertEquals(2, cl.loadClass("P$E").getEnumConstants().length);
    }

    private static Object
    staticFieldValue(Class<?> c, String name) throws Exception {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }

    /**
     * @return The {@code <clinit>} method of the class file of <var>className</var>, or {@code null}
     */
    @Nullable private static ClassFile.MethodInfo
    classInitializationMethod(Map<String, byte[]> bytecodes, String className) throws Exception {
        byte[] bytes = bytecodes.get(className);
        Assert.assertNotNull(className + " " + bytecodes.keySet(), bytes);

        ClassFile cf = new ClassFile(new ByteArrayInputStream(bytes));
        for (ClassFile.MethodInfo mi : cf.methodInfos) {
            if ("<clinit>".equals(mi.getName())) return mi;
        }
        return null;
    }
}
