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
import java.io.ObjectStreamClass;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.util.reflect.ByteArrayClassLoader;
import org.codehaus.commons.compiler.util.resource.MapResourceCreator;
import org.codehaus.commons.compiler.util.resource.MapResourceFinder;
import org.codehaus.commons.compiler.util.resource.Resource;
import org.codehaus.commons.nullanalysis.Nullable;
import org.codehaus.janino.ClassLoaderIClassLoader;
import org.codehaus.janino.Compiler;
import org.codehaus.janino.IClassLoader;
import org.codehaus.janino.Mod;
import org.codehaus.janino.ResourceFinderIClassLoader;
import org.codehaus.janino.SimpleCompiler;
import org.codehaus.janino.util.ClassFile;
import org.junit.Assert;
import org.junit.Test;

/**
 * A {@code protected} member type is accessible from a subclass of the enclosing type in another package (issue #87):
 * its class file has the flag {@code ACC_PUBLIC}, without which the JVM throws an {@link IllegalAccessError}.
 */
public
class ProtectedMemberTypeTest {

    private static final String SOURCE_A = (
        ""
        + "package a;\n"
        + "\n"
        + "public class A {\n"
        + "    protected static class ProtS { public ProtS() { } }\n"
        + "    protected class ProtI { public ProtI() { } }\n"
        + "    protected interface PI { }\n"
        + "    protected enum PE { X }\n"
        + "    public static class PubS { }\n"
        + "    static class PkgS { }\n"
        + "    private static class PrivS { }\n"
        + "    interface I { }\n"
        + "    void use() { new PrivS(); }\n"
        + "}\n"
    );

    /**
     * A subclass in another package uses each {@code protected} member type; JANINO compiles it against the class
     * files of {@code A}, which it compiled itself.
     */
    @Test public void
    testSubclassInAnotherPackage() throws Exception {
        Map<String, byte[]> classesA = ProtectedMemberTypeTest.compileA();
        Map<String, byte[]> classesB = ProtectedMemberTypeTest.compile(classesA, "b/B.java", (
            ""
            + "package b;\n"
            + "\n"
            + "public class B extends a.A {\n"
            + "    public static Object[] run() {\n"
            + "        return new Object[] {\n"
            + "            new ProtS(),\n"
            + "            new B().new ProtI(),\n"
            + "            ProtS.class,\n"
            + "            new ProtS() { },\n"
            + "            new PI() { },\n"
            + "            PE.X,\n"
            + "        };\n"
            + "    }\n"
            + "}\n"
        ));

        Map<String, byte[]> classes = new HashMap<String, byte[]>(classesA);
        classes.putAll(classesB);
        ClassLoader cl = new ByteArrayClassLoader(classes, this.getClass().getClassLoader());
        Object[] result = (Object[]) cl.loadClass("b.B").getMethod("run").invoke(null);

        Assert.assertEquals("a.A$ProtS", result[0].getClass().getName());
        Assert.assertEquals("a.A$ProtI", result[1].getClass().getName());
        Assert.assertEquals("a.A$ProtS", ((Class<?>) result[2]).getName());
        Assert.assertEquals("a.A$ProtS", result[3].getClass().getSuperclass().getName());
        Assert.assertEquals("a.A$PI",    result[4].getClass().getInterfaces()[0].getName());
        Assert.assertEquals("X",         result[5].toString());

        // The reflection API reads the modifiers from the "InnerClasses" attribute; they are unchanged.
        Assert.assertEquals(Mod.PROTECTED | Mod.STATIC, cl.loadClass("a.A$ProtS").getModifiers());
        Assert.assertEquals(Mod.PROTECTED,              cl.loadClass("a.A$ProtI").getModifiers());
    }

    /**
     * A class in another package that is not a subclass cannot access a {@code protected} member type, also when
     * JANINO reads the type from a class file that it compiled.
     */
    @Test public void
    testNonSubclassInAnotherPackage() throws Exception {
        Map<String, byte[]> classesA = ProtectedMemberTypeTest.compileA();
        try {
            ProtectedMemberTypeTest.compile(classesA, "c/C.java", (
                ""
                + "package c;\n"
                + "\n"
                + "public class C {\n"
                + "    public static Object run() { return new a.A.ProtS(); }\n"
                + "}\n"
            ));
            Assert.fail("CompileException expected");
        } catch (CompileException ce) {
            Assert.assertTrue(ce.getMessage(), ce.getMessage().contains("Protected member cannot be accessed"));
        }
    }

    /**
     * The flags of member types are those of JAVAC (issue #87): {@code ACC_PUBLIC} for the {@code protected} member
     * types, {@code ACC_SUPER} for classes, and neither {@code ACC_PRIVATE} nor {@code ACC_STATIC}. Unlike with JAVAC,
     * {@code ACC_PROTECTED} stays, so that JANINO, when it reads the class file, still sees a {@code protected} type;
     * the JVM ignores it.
     */
    @Test public void
    testAccessFlags() throws Exception {
        Map<String, byte[]> classes = ProtectedMemberTypeTest.compileA();

        Assert.assertEquals(Mod.PUBLIC | Mod.PROTECTED | Mod.SUPER, ProtectedMemberTypeTest.flags(classes, "ProtS"));
        Assert.assertEquals(Mod.PUBLIC | Mod.PROTECTED | Mod.SUPER, ProtectedMemberTypeTest.flags(classes, "ProtI"));
        Assert.assertEquals(
            Mod.PUBLIC | Mod.PROTECTED | Mod.INTERFACE | Mod.ABSTRACT,
            ProtectedMemberTypeTest.flags(classes, "PI")
        );
        Assert.assertEquals(
            Mod.PUBLIC | Mod.PROTECTED | Mod.FINAL | Mod.SUPER | Mod.ENUM,
            ProtectedMemberTypeTest.flags(classes, "PE")
        );

        Assert.assertEquals(Mod.PUBLIC | Mod.SUPER,        ProtectedMemberTypeTest.flags(classes, "PubS"));
        Assert.assertEquals(Mod.SUPER,                     ProtectedMemberTypeTest.flags(classes, "PkgS"));
        Assert.assertEquals(Mod.SUPER,                     ProtectedMemberTypeTest.flags(classes, "PrivS"));
        Assert.assertEquals(Mod.INTERFACE | Mod.ABSTRACT, ProtectedMemberTypeTest.flags(classes, "I"));
    }

    /**
     * The modifiers that the reflection API reports, and therefore the default {@code serialVersionUID} of a
     * serializable member class, come from the {@code InnerClasses} attribute; they are the same as with 3.1.18,
     * which wrote other {@code access_flags} (issue #87).
     */
    @Test public void
    testSerialVersionUidOfMemberClasses() throws Exception {
        SimpleCompiler sc = new SimpleCompiler();
        sc.cook(
            ""
            + "public class P {\n"
            + "    public static class S implements java.io.Serializable { int x; }\n"
            + "    protected static class T implements java.io.Serializable { int y; }\n"
            + "    private class U implements java.io.Serializable { int z; }\n"
            + "}\n"
        );
        ClassLoader cl = sc.getClassLoader();

        Assert.assertEquals(-7416770703760539819L, ObjectStreamClass.lookup(cl.loadClass("P$S")).getSerialVersionUID());
        Assert.assertEquals(514917891290028149L,   ObjectStreamClass.lookup(cl.loadClass("P$T")).getSerialVersionUID());
        Assert.assertEquals(5541701940790853378L,  ObjectStreamClass.lookup(cl.loadClass("P$U")).getSerialVersionUID());
        Assert.assertEquals(Mod.PUBLIC | Mod.STATIC,    cl.loadClass("P$S").getModifiers());
        Assert.assertEquals(Mod.PROTECTED | Mod.STATIC, cl.loadClass("P$T").getModifiers());
        Assert.assertEquals(Mod.PRIVATE,                cl.loadClass("P$U").getModifiers());
    }

    private static Map<String, byte[]>
    compileA() throws Exception {
        return ProtectedMemberTypeTest.compile(null, "a/A.java", ProtectedMemberTypeTest.SOURCE_A);
    }

    /**
     * Compiles one compilation unit with the {@link Compiler}, which reads the types of <var>classFiles</var> from
     * their class files (with {@code ClassFileIClass}).
     *
     * @return The generated class files, by resource name
     */
    private static Map<String, byte[]>
    compile(@Nullable Map<String, byte[]> classFiles, String fileName, String source) throws Exception {

        MapResourceFinder sourceFinder = new MapResourceFinder();
        sourceFinder.addResource(fileName, source);

        IClassLoader icl = new ClassLoaderIClassLoader(ProtectedMemberTypeTest.class.getClassLoader());
        if (classFiles != null) icl = new ResourceFinderIClassLoader(new MapResourceFinder(classFiles), icl);

        Map<String, byte[]> result = new HashMap<String, byte[]>();

        Compiler compiler = new Compiler();
        compiler.setSourceFinder(sourceFinder);
        compiler.setIClassLoader(icl);
        compiler.setClassFileCreator(new MapResourceCreator(result));
        compiler.compile(sourceFinder.resources().toArray(new Resource[0]));

        return result;
    }

    /**
     * @return The {@code access_flags} of the class file of the member type <var>memberTypeName</var> of {@code A}
     */
    private static int
    flags(Map<String, byte[]> classes, String memberTypeName) throws Exception {
        byte[] bytes = (byte[]) classes.get("a/A$" + memberTypeName + ".class");
        Assert.assertNotNull(classes.keySet().toString(), bytes);
        return new ClassFile(new ByteArrayInputStream(bytes)).accessFlags;
    }
}
