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
import java.util.HashMap;
import java.util.Map;

import org.codehaus.commons.compiler.util.resource.MapResourceCreator;
import org.codehaus.commons.compiler.util.resource.Resource;
import org.codehaus.commons.compiler.util.resource.StringResource;
import org.codehaus.janino.ClassFileIClass;
import org.codehaus.janino.ClassLoaderIClassLoader;
import org.codehaus.janino.Compiler;
import org.codehaus.janino.IClass;
import org.codehaus.janino.IClass.IAnnotation;
import org.codehaus.janino.IClass.IField;
import org.codehaus.janino.util.ClassFile;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for {@link ClassFileIClass}, i.e. for the information that JANINO reads from class files.
 */
public
class ClassFileIClassTest {

    /**
     * The constant values of {@code boolean}, {@code byte}, {@code char} and {@code short} fields, and the element
     * values of annotations of these types, are stored as {@code int}s in the class file; they must have the types of
     * the fields and elements. See <a href="https://github.com/janino-lts/janino/issues/46">issue #46</a>.
     */
    @Test public void
    testConstantValueTypes() throws Exception {

        Map<String, byte[]> classes  = new HashMap<>();
        Compiler            compiler = new Compiler();
        compiler.setClassFileCreator(new MapResourceCreator(classes));
        compiler.compile(new Resource[] {
            new StringResource(
                "pkg/Ann.java",
                ""
                + "package pkg;\n"
                + "@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)\n"
                + "public @interface Ann { boolean z(); byte b(); char c(); short s(); int i(); }\n"
            ),
            new StringResource(
                "pkg/A.java",
                ""
                + "package pkg;\n"
                + "@Ann(z = true, b = (byte) -1, c = 'a', s = (short) -2, i = 7)\n"
                + "public class A {\n"
                + "    public static final boolean F = true, G = false;\n"
                + "    public static final byte    B = -1;\n"
                + "    public static final char    C = 97; // 'a'\n"
                + "    public static final short   S = -2;\n"
                + "    public static final int     I = 7;\n"
                + "}\n"
            ),
        });

        IClass a = new ClassFileIClass(
            new ClassFile(new ByteArrayInputStream(classes.get("pkg/A.class"))),
            new ClassLoaderIClassLoader(ClassFileIClassTest.class.getClassLoader())
        );

        Assert.assertEquals(Boolean.TRUE,              ClassFileIClassTest.constantValue(a, "F"));
        Assert.assertEquals(Boolean.FALSE,             ClassFileIClassTest.constantValue(a, "G"));
        Assert.assertEquals(Byte.valueOf((byte) -1),   ClassFileIClassTest.constantValue(a, "B"));
        Assert.assertEquals(Character.valueOf('a'),    ClassFileIClassTest.constantValue(a, "C"));
        Assert.assertEquals(Short.valueOf((short) -2), ClassFileIClassTest.constantValue(a, "S"));
        Assert.assertEquals(Integer.valueOf(7),        ClassFileIClassTest.constantValue(a, "I"));

        IAnnotation[] annotations = a.getIAnnotations();
        Assert.assertEquals(1, annotations.length);
        IAnnotation ann = annotations[0];
        Assert.assertEquals(Boolean.TRUE,              ann.getElementValue("z"));
        Assert.assertEquals(Byte.valueOf((byte) -1),   ann.getElementValue("b"));
        Assert.assertEquals(Character.valueOf('a'),    ann.getElementValue("c"));
        Assert.assertEquals(Short.valueOf((short) -2), ann.getElementValue("s"));
        Assert.assertEquals(Integer.valueOf(7),        ann.getElementValue("i"));
    }

    private static Object
    constantValue(IClass iClass, String fieldName) throws Exception {
        IField f = iClass.getDeclaredIField(fieldName);
        Assert.assertNotNull(fieldName, f);
        return f.getConstantValue();
    }
}
