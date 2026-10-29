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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.janino.ClassBodyEvaluator;
import org.codehaus.janino.ClassLoaderIClassLoader;
import org.codehaus.janino.SimpleCompiler;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the {@link org.codehaus.janino.IClass}es of loaded classes, which all compilations share (see {@link
 * ClassLoaderIClassLoader}).
 */
public
class SharedIClassCacheTest {

    /**
     * A library that the class bodies use: variable arity methods, overloads, inherited and interface methods, member
     * types, and arrays.
     */
    private static final String LIBRARY = (
        ""
        + "package lib;\n"
        + "public class Lib {\n"
        + "    public interface Named { String name(); }\n"
        + "    public static class Base implements Named {\n"
        + "        public String name() { return \"base\"; }\n"
        + "        public static String join(String sep, Object... parts) {\n"
        + "            StringBuilder sb = new StringBuilder();\n"
        + "            for (int i = 0; i < parts.length; i++) sb.append(i == 0 ? \"\" : sep).append(parts[i]);\n"
        + "            return sb.toString();\n"
        + "        }\n"
        + "        public static int sum(int... values) {\n"
        + "            int result = 0;\n"
        + "            for (int v : values) result += v;\n"
        + "            return result;\n"
        + "        }\n"
        + "        public String describe(Object o) { return \"object\"; }\n"
        + "        public String describe(String s) { return \"string\"; }\n"
        + "        public String describe(int i)    { return \"int\"; }\n"
        + "        public Base[] array(int n) { return new Base[n]; }\n"
        + "        public static class Member {\n"
        + "            public int   value = 3;\n"
        + "            public int[] values() { return new int[] { 1, 2 }; }\n"
        + "        }\n"
        + "    }\n"
        + "    public static class Derived extends Base {\n"
        + "        public String name() { return \"derived\"; }\n"
        + "        public String describe(CharSequence cs) { return \"chars\"; }\n"
        + "    }\n"
        + "}\n"
    );

    private static final String[] DEFAULT_IMPORTS = {
        "lib.Lib.Base", "lib.Lib.Derived", "lib.Lib.Named", "java.util.Arrays", "java.util.List"
    };

    /**
     * Class bodies, each with a method "{@code public static String f()}".
     */
    private static final String[] CLASS_BODIES = {

        // Variable arity invocations: the arguments are collected into an array, or an array is passed.
        ""
        + "public static String f() {\n"
        + "    Object[] a = { \"x\", \"y\" };\n"
        + "    return (\n"
        + "        Base.join(\",\", 1, \"two\", 3L) + Base.join(\",\", a) + Base.join(\",\")\n"
        + "        + Base.sum(1, 2, 3) + Base.sum(new int[] { 4, 5 }) + Base.sum()\n"
        + "        + String.format(\"%s %s\", 1, \"x\") + String.format(\"%s\", a)\n"
        + "        + Arrays.asList(1, 2) + Arrays.asList(a)\n"
        + "    );\n"
        + "}\n",

        // Arrays of loaded, of compiled and of primitive types.
        ""
        + "public static String f() {\n"
        + "    String[] s = \"a,b\".split(\",\");\n"
        + "    String[] t = (String[]) s.clone();\n"
        + "    Object[] o = t;\n"
        + "    int[]    i = new Base.Member().values();\n"
        + "    Arrays.fill(i, 7);\n"
        + "    System.arraycopy(s, 0, t, 0, 1);\n"
        + "    Base[]   b = new Derived().array(2);\n"
        + "    Named[]  n = b;\n"
        + "    Inner[]  c = { new Inner() };\n"
        + "    return Arrays.toString(i) + Arrays.asList(s) + o.length + n.length + c[0].x + Arrays.toString(t);\n"
        + "}\n"
        + "public static class Inner { int x = 1; }\n",

        // Overloads, inherited and interface methods, member types, boxing.
        ""
        + "public static String f() {\n"
        + "    Derived d = new Derived();\n"
        + "    Named   n = d;\n"
        + "    List<Object> l = new java.util.ArrayList<Object>();\n"
        + "    l.add(Integer.valueOf(1));\n"
        + "    l.add(n.name());\n"
        + "    StringBuilder sb = new StringBuilder();\n"
        + "    sb.append(d.describe(\"s\")).append(d.describe(new StringBuilder())).append(d.describe(1));\n"
        + "    sb.append(d.describe((Object) \"o\")).append(new Base.Member().value).append(l.size()).append(l);\n"
        + "    return sb.toString() + Math.max(1L, 2) + Character.isDigit('1');\n"
        + "}\n",

        // A loaded superclass, exceptions, and a member type of a JDK interface.
        ""
        + "public static String f() {\n"
        + "    java.util.Map.Entry<Object, Object> e = (\n"
        + "        new java.util.AbstractMap.SimpleEntry<Object, Object>(\"k\", \"v\")\n"
        + "    );\n"
        + "    try {\n"
        + "        Integer.parseInt(\"x\");\n"
        + "    } catch (NumberFormatException ex) {\n"
        + "        return e.getKey() + ex.getClass().getSimpleName() + new Sub().name();\n"
        + "    }\n"
        + "    return null;\n"
        + "}\n"
        + "public static class Sub extends Derived { public String name() { return \"sub\" + super.name(); } }\n",
    };

    /**
     * Compiles the same class bodies in eight threads at the same time, against the same new classes, and verifies
     * that the class files and the results are the same as with a compilation in one thread.
     */
    @Test public void
    testConcurrentCompilations() throws Exception {

        // The expected class files, compiled against one instance of the library.
        final List<Map<String, byte[]>> expected = SharedIClassCacheTest.compileAll(SharedIClassCacheTest.library());

        // Compiling again yields the same class files.
        SharedIClassCacheTest.assertSameClassFiles(expected, SharedIClassCacheTest.compileAll(
            SharedIClassCacheTest.library()
        ));

        for (int round = 0; round < 5; round++) {

            // New classes, whose IClasses no compilation has used yet.
            final ClassLoader library = SharedIClassCacheTest.library();

            final int           threadCount = 8;
            final CyclicBarrier start       = new CyclicBarrier(threadCount);
            ExecutorService     es          = Executors.newFixedThreadPool(threadCount);
            try {
                List<Future<List<Map<String, byte[]>>>> futures = new ArrayList<>();
                for (int i = 0; i < threadCount; i++) {
                    futures.add(es.submit(new Callable<List<Map<String, byte[]>>>() {

                        @Override public List<Map<String, byte[]>>
                        call() throws Exception {
                            start.await();
                            return SharedIClassCacheTest.compileAll(library);
                        }
                    }));
                }
                for (Future<List<Map<String, byte[]>>> f : futures) {
                    SharedIClassCacheTest.assertSameClassFiles(expected, f.get());
                }
            } finally {
                es.shutdownNow();
            }
        }
    }

    /**
     * A class in the package {@code java.lang} that does not exist is still reported after the first miss, and a class
     * in another package that a class loader finds only later is found then.
     */
    @Test public void
    testMissingClasses() throws Exception {

        String firstMessage = null;
        for (int i = 0; i < 2; i++) {
            try {
                new ClassBodyEvaluator("public java.lang.DoesNotExist x;");
                Assert.fail("CompileException expected");
            } catch (CompileException ce) {
                if (firstMessage == null) {
                    firstMessage = ce.getMessage();
                } else {
                    Assert.assertEquals(firstMessage, ce.getMessage());
                }
            }
        }

        SimpleCompiler sc = new SimpleCompiler();
        sc.cook("package pkg2; public class Late { public static int f() { return 42; } }");
        final byte[] late = sc.getBytecodes().get("pkg2.Late");

        final boolean[] visible = new boolean[1];
        ClassLoader     parent  = new ClassLoader(SharedIClassCacheTest.class.getClassLoader()) {

            @Override protected Class<?>
            findClass(String name) throws ClassNotFoundException {
                if (visible[0] && "pkg2.Late".equals(name)) return this.defineClass(name, late, 0, late.length);
                throw new ClassNotFoundException(name);
            }
        };

        try {
            SharedIClassCacheTest.invokeLate(parent);
            Assert.fail("CompileException expected");
        } catch (CompileException ce) {
            Assert.assertTrue(ce.getMessage(), ce.getMessage().contains("pkg2"));
        }

        visible[0] = true;
        Assert.assertEquals(42, SharedIClassCacheTest.invokeLate(parent));
    }

    private static Object
    invokeLate(ClassLoader parent) throws Exception {
        ClassBodyEvaluator cbe = new ClassBodyEvaluator();
        cbe.setParentClassLoader(parent);
        cbe.cook("public static int g() { return pkg2.Late.f(); }");
        return cbe.getClazz().getMethod("g").invoke(null);
    }

    /**
     * @return A class loader with new classes of the {@link #LIBRARY}
     */
    private static ClassLoader
    library() throws CompileException {
        SimpleCompiler sc = new SimpleCompiler();
        sc.setParentClassLoader(SharedIClassCacheTest.class.getClassLoader());
        sc.cook(SharedIClassCacheTest.LIBRARY);
        return sc.getClassLoader();
    }

    /**
     * Compiles each of the {@link #CLASS_BODIES} against the <var>library</var>, and verifies the results of their
     * methods "{@code f()}".
     *
     * @return The class files of each class body
     */
    private static List<Map<String, byte[]>>
    compileAll(ClassLoader library) throws Exception {
        List<Map<String, byte[]>> result = new ArrayList<>();
        for (int i = 0; i < SharedIClassCacheTest.CLASS_BODIES.length; i++) {
            ClassBodyEvaluator cbe = new ClassBodyEvaluator();
            cbe.setParentClassLoader(library);
            cbe.setDefaultImports(SharedIClassCacheTest.DEFAULT_IMPORTS);
            cbe.setClassName("pkg.C" + i);
            cbe.cook(SharedIClassCacheTest.CLASS_BODIES[i]);

            Method f = cbe.getClazz().getMethod("f");
            Assert.assertEquals(SharedIClassCacheTest.EXPECTED_RESULTS[i], f.invoke(null));

            result.add(cbe.getBytecodes());
        }
        return result;
    }

    private static final String[] EXPECTED_RESULTS = {
        "1,two,3x,y6901 xx[1, 2][x, y]",
        "[7, 7][a, b]221[a, b]",
        "stringcharsintobject32[1, derived]2true",
        "kNumberFormatExceptionsubderived",
    };

    private static void
    assertSameClassFiles(List<Map<String, byte[]>> expected, List<Map<String, byte[]>> actual) {
        Assert.assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            Map<String, byte[]> e = expected.get(i), a = actual.get(i);
            Assert.assertEquals(e.keySet(), a.keySet());
            for (Map.Entry<String, byte[]> me : e.entrySet()) {
                Assert.assertTrue(me.getKey(), Arrays.equals(me.getValue(), a.get(me.getKey())));
            }
        }
    }
}
