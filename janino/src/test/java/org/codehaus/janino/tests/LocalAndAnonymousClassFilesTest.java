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

import org.codehaus.commons.nullanalysis.Nullable;
import org.codehaus.janino.Mod;
import org.codehaus.janino.SimpleCompiler;
import org.codehaus.janino.util.ClassFile;
import org.codehaus.janino.util.ClassFile.ConstantNameAndTypeInfo;
import org.codehaus.janino.util.ClassFile.EnclosingMethodAttribute;
import org.codehaus.janino.util.ClassFile.InnerClassesAttribute;
import org.junit.Assert;
import org.junit.Test;

/**
 * The class files of local and anonymous classes (issue #73): the {@code InnerClasses} entries in the class file of
 * the class itself and of its enclosing class, the {@code EnclosingMethod} attribute, the class flags, and the
 * default {@code serialVersionUID}; and the flags of {@code strictfp} classes (issue #74). The values that the
 * reflection API reads from these attributes are recorded in the language support records ({@code
 * member-types.txt}).
 */
public
class LocalAndAnonymousClassFilesTest {

    private static final String SOURCE = (
        ""
        + "public class P implements java.io.Serializable {\n"
        + "    public Object run() {\n"
        + "        class L { }\n"
        + "        return new java.io.Serializable() { int y; };\n"
        + "    }\n"
        + "}\n"
    );

    /**
     * An anonymous class keeps the flag {@code ACC_FINAL} (unlike with JAVAC), so that its default {@code
     * serialVersionUID} is the same as with earlier versions of JANINO (3.1.12 through 3.1.18 compute this value for
     * the anonymous class of this source).
     */
    @Test public void
    testSerialVersionUidOfAnonymousClass() throws Exception {
        SimpleCompiler sc = new SimpleCompiler();
        sc.cook("public class P {\n    public Object f() { return new java.io.Serializable() { int y; }; }\n}\n");
        Class<?> c = sc.getClassLoader().loadClass("P$1");

        Assert.assertEquals(-4171947221928418234L, ObjectStreamClass.lookup(c).getSerialVersionUID());
        Assert.assertEquals(Mod.FINAL, c.getModifiers());
        Assert.assertTrue(c.isAnonymousClass());
    }

    /**
     * The {@code InnerClasses} entries and the {@code EnclosingMethod} attribute, like JAVAC writes them; the class
     * flags {@code ACC_SUPER}, and {@code ACC_FINAL} for the anonymous class.
     */
    @Test public void
    testClassFileAttributes() throws Exception {
        SimpleCompiler sc = new SimpleCompiler();
        sc.cook(LocalAndAnonymousClassFilesTest.SOURCE);
        Map<String, ClassFile> cfs = LocalAndAnonymousClassFilesTest.classFiles(sc.getBytecodes());

        ClassFile p = (ClassFile) cfs.get("P");
        ClassFile l = (ClassFile) cfs.get("P$L");
        ClassFile a = (ClassFile) cfs.get("P$1");
        Assert.assertNotNull(cfs.keySet().toString(), l);
        Assert.assertNotNull(cfs.keySet().toString(), a);

        // The enclosing class has an entry for each local and anonymous class; without it, the JVM throws an
        // "IncompatibleClassChangeError" in "Class.getDeclaringClass()".
        Assert.assertEquals(Mod.NONE,  LocalAndAnonymousClassFilesTest.innerClassFlags(p, "P$L", "L"));
        Assert.assertEquals(Mod.FINAL, LocalAndAnonymousClassFilesTest.innerClassFlags(p, "P$1", null));
        Assert.assertNull(p.getEnclosingMethodAttribute());

        // The local and the anonymous class have an entry for themselves, and an "EnclosingMethod" attribute.
        Assert.assertEquals(Mod.NONE,  LocalAndAnonymousClassFilesTest.innerClassFlags(l, "P$L", "L"));
        Assert.assertEquals(Mod.FINAL, LocalAndAnonymousClassFilesTest.innerClassFlags(a, "P$1", null));
        LocalAndAnonymousClassFilesTest.assertEnclosingMethod(l, "P", "run", "()Ljava/lang/Object;");
        LocalAndAnonymousClassFilesTest.assertEnclosingMethod(a, "P", "run", "()Ljava/lang/Object;");

        // "ACC_SUPER", like with JAVAC; "ACC_FINAL" for the anonymous class; never "ACC_PRIVATE" (not a class flag).
        Assert.assertEquals(Mod.SUPER, l.accessFlags);
        Assert.assertEquals(Mod.SUPER | Mod.FINAL, a.accessFlags);
    }

    /**
     * A class in a field initializer is not enclosed by a method or constructor: the method index of the {@code
     * EnclosingMethod} attribute is 0 (JVMS 4.7.7).
     */
    @Test public void
    testEnclosingMethodOfClassInFieldInitializer() throws Exception {
        SimpleCompiler sc = new SimpleCompiler();
        sc.cook("public class P {\n    Object o = new Object() { };\n}\n");
        ClassFile a = (ClassFile) LocalAndAnonymousClassFilesTest.classFiles(sc.getBytecodes()).get("P$1");

        LocalAndAnonymousClassFilesTest.assertEnclosingMethod(a, "P", null, null);
    }

    /**
     * {@code ACC_STRICT} is a method flag; a {@code strictfp} class does not have it (issue #74).
     */
    @Test public void
    testStrictfpClassFlags() throws Exception {
        SimpleCompiler sc = new SimpleCompiler();
        sc.cook(
            ""
            + "public strictfp class P {\n"
            + "    strictfp class M { }\n"
            + "    void m() { strictfp class L { } }\n"
            + "}\n"
        );
        Map<String, ClassFile> cfs = LocalAndAnonymousClassFilesTest.classFiles(sc.getBytecodes());

        Assert.assertEquals(3, cfs.size());
        for (Map.Entry<String, ClassFile> e : cfs.entrySet()) {
            Assert.assertEquals(e.getKey(), 0, ((ClassFile) e.getValue()).accessFlags & Mod.STRICTFP);
        }
    }

    private static Map<String, ClassFile>
    classFiles(Map<String, byte[]> bytecodes) throws Exception {
        Map<String, ClassFile> result = new HashMap<String, ClassFile>();
        for (Map.Entry<String, byte[]> e : bytecodes.entrySet()) {
            result.put(e.getKey(), new ClassFile(new ByteArrayInputStream((byte[]) e.getValue())));
        }
        return result;
    }

    /**
     * @return The {@code inner_class_access_flags} of the {@code InnerClasses} entry of the class file <var>cf</var>
     *         for the class <var>innerClassName</var>, which must have no outer class and the <var>innerName</var>
     */
    private static short
    innerClassFlags(ClassFile cf, String innerClassName, @Nullable String innerName) {
        InnerClassesAttribute ica = cf.getInnerClassesAttribute();
        Assert.assertNotNull(cf.getThisClassName(), ica);
        for (InnerClassesAttribute.Entry e : ica.getEntries()) {
            if (!innerClassName.equals(cf.getConstantClassInfo(e.innerClassInfoIndex).getName(cf))) continue;
            Assert.assertEquals(0, e.outerClassInfoIndex);
            Assert.assertEquals(innerName, e.innerNameIndex == 0 ? null : cf.getConstantUtf8(e.innerNameIndex));
            return e.innerClassAccessFlags;
        }
        Assert.fail("No entry for \"" + innerClassName + "\" in " + cf.getThisClassName());
        return 0;
    }

    private static void
    assertEnclosingMethod(
        ClassFile        cf,
        String           className,
        @Nullable String methodName,
        @Nullable String methodDescriptor
    ) {
        EnclosingMethodAttribute ema = cf.getEnclosingMethodAttribute();
        Assert.assertNotNull(cf.getThisClassName(), ema);
        Assert.assertEquals(className, cf.getConstantClassInfo(ema.getClassIndex()).getName(cf));
        if (methodName == null) {
            Assert.assertEquals(0, ema.getMethodIndex());
        } else {
            ConstantNameAndTypeInfo nat = (ConstantNameAndTypeInfo) cf.getConstantPoolInfo(ema.getMethodIndex());
            Assert.assertEquals(methodName, nat.getName(cf));
            Assert.assertEquals(methodDescriptor, nat.getDescriptor(cf));
        }
    }
}
