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

package org.codehaus.commons.compiler.sandbox.tests;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Supplier;

import org.codehaus.commons.compiler.sandbox.ClassFileReader;
import org.codehaus.commons.compiler.sandbox.ClassFileReader.BootstrapMethod;
import org.codehaus.commons.compiler.sandbox.ClassFileReader.DynamicReference;
import org.codehaus.commons.compiler.sandbox.ClassFileReader.MemberReference;
import org.codehaus.commons.compiler.sandbox.ClassFileReader.MethodHandleReference;
import org.codehaus.commons.nullanalysis.Nullable;
import org.junit.Assert;
import org.junit.Test;

// SUPPRESS CHECKSTYLE Javadoc:9999

public
class ClassFileReaderTest {

    /**
     * A class whose class file (compiled by the test build) contains lambdas, method references and eight-byte
     * constants.
     */
    static
    class Sample {

        static final long   LONG_CONSTANT   = 0x123456789ABCDEFL;
        static final double DOUBLE_CONSTANT = 3.14159;

        Runnable
        lambda() { return () -> System.out.println("x"); }

        Supplier<String>
        constructorReference() { return String::new; }
    }

    /**
     * A class whose class file (compiled by the test build) contains a TABLESWITCH and a LOOKUPSWITCH instruction
     * (which have alignment padding), and member references on known source lines, which are marked with "LINE-x"
     * comments.
     */
    static
    class LineSample {

        static Object
        sample(int x) {
            switch (x) {
            case 1: case 2: case 3: x++; break;
            default: break;
            }
            switch (x) {
            case 10: case 1000: case 100000: x--; break;
            default: break;
            }
            String            a = System.getProperty("a");  // LINE-A
            Object            o = System.out;               // LINE-B
            String            b = System.getProperty("b");  // LINE-C
            Supplier<Runtime> r = Runtime::getRuntime;      // LINE-D
            return a + o + b + r + x;
        }
    }

    @SuppressWarnings("static-method") @Test public void
    testString() throws Exception {

        ClassFileReader cfr = new ClassFileReader(ClassFileReaderTest.readSystemClassFile("java.lang.String"));

        Assert.assertEquals("java.lang.String", cfr.getClassName());
        Assert.assertEquals("java.lang.Object", cfr.getSuperclassName());
        Assert.assertTrue(cfr.getInterfaceNames().contains("java.lang.CharSequence"));
        Assert.assertTrue(cfr.getInterfaceNames().contains("java.lang.Comparable"));
        Assert.assertTrue(cfr.getMajorVersion() >= 52);
        Assert.assertTrue(cfr.getClassReferences().contains("java.lang.String"));

        boolean found = false;
        for (ClassFileReader.MemberDeclaration md : cfr.getMethods()) {
            if ("length".equals(md.getName()) && "()I".equals(md.getDescriptor())) found = true;
        }
        Assert.assertTrue(found);
    }

    @SuppressWarnings("static-method") @Test public void
    testObject() throws Exception {
        ClassFileReader cfr = new ClassFileReader(ClassFileReaderTest.readSystemClassFile("java.lang.Object"));
        Assert.assertEquals("java.lang.Object", cfr.getClassName());
        Assert.assertNull(cfr.getSuperclassName());
    }

    /**
     * Parses a number of complex JDK classes, which, depending on the JDK version, contain all sorts of constant pool
     * entries and attributes.
     */
    @SuppressWarnings("static-method") @Test public void
    testJdkClasses() throws Exception {
        for (String className : new String[] {
            "java.lang.Character",
            "java.lang.Class",
            "java.lang.ClassLoader",
            "java.lang.Integer",
            "java.lang.Thread",
            "java.lang.invoke.LambdaMetafactory",
            "java.lang.invoke.MethodHandles",
            "java.util.ArrayList",
            "java.util.Collections",
            "java.util.HashMap",
            "java.util.stream.Collectors",
        }) {
            ClassFileReader cfr = new ClassFileReader(ClassFileReaderTest.readSystemClassFile(className));
            Assert.assertEquals(className, cfr.getClassName());
            Assert.assertFalse(className, cfr.getMemberReferences().isEmpty());
        }
    }

    @SuppressWarnings("static-method") @Test public void
    testLambdasAndConstants() throws Exception {

        ClassFileReader cfr = new ClassFileReader(ClassFileReaderTest.readClassFile(Sample.class));

        Assert.assertEquals(Sample.class.getName(), cfr.getClassName());

        // The lambda and the constructor reference are compiled into INVOKEDYNAMIC instructions.
        List<String> indyNames = new ArrayList<String>();
        for (DynamicReference dr : cfr.getDynamicReferences()) {
            Assert.assertTrue(dr.isInvokeDynamic());
            indyNames.add(dr.getName() + dr.getDescriptor());

            MemberReference bsm = cfr.getBootstrapMethod(dr).getMethod().getMember();
            Assert.assertEquals("java.lang.invoke.LambdaMetafactory", bsm.getClassName());
            Assert.assertEquals("metafactory", bsm.getName());
        }
        Assert.assertTrue(indyNames.toString(), indyNames.contains("run()Ljava/lang/Runnable;"));
        Assert.assertTrue(indyNames.toString(), indyNames.contains("get()Ljava/util/function/Supplier;"));

        // The method handle of the constructor reference ("REF_newInvokeSpecial").
        boolean found = false;
        for (MethodHandleReference mhr : cfr.getMethodHandles()) {
            MemberReference mr = mhr.getMember();
            if ("java.lang.String".equals(mr.getClassName()) && "<init>".equals(mr.getName())) {
                Assert.assertEquals(8, mhr.getReferenceKind());
                found = true;
            }
        }
        Assert.assertTrue(cfr.getMethodHandles().toString(), found);

        // The member references in the body of the lambda.
        Assert.assertTrue(cfr.getMemberReferences().toString(), ClassFileReaderTest.containsMemberReference(
            cfr,
            MemberReference.Kind.FIELD,
            "java.lang.System.out:Ljava/io/PrintStream;"
        ));
        Assert.assertTrue(cfr.getMemberReferences().toString(), ClassFileReaderTest.containsMemberReference(
            cfr,
            MemberReference.Kind.METHOD,
            "java.io.PrintStream.println(Ljava/lang/String;)V"
        ));
    }

    @SuppressWarnings("static-method") @Test public void
    testLineNumbers() throws Exception {

        ClassFileReader cfr = new ClassFileReader(ClassFileReaderTest.readClassFile(LineSample.class));

        Assert.assertEquals("ClassFileReaderTest.java", cfr.getSourceFileName());

        // Notice: The marker strings are concatenated, so that only the comments in "LineSample" match.
        int lineA = ClassFileReaderTest.lineOf("// LINE-" + "A");
        int lineB = ClassFileReaderTest.lineOf("// LINE-" + "B");
        int lineC = ClassFileReaderTest.lineOf("// LINE-" + "C");
        int lineD = ClassFileReaderTest.lineOf("// LINE-" + "D");

        ClassFileReaderTest.assertLineNumbers(
            cfr,
            MemberReference.Kind.METHOD,
            "java.lang.System.getProperty(Ljava/lang/String;)Ljava/lang/String;",
            lineA,
            lineC
        );
        ClassFileReaderTest.assertLineNumbers(
            cfr,
            MemberReference.Kind.FIELD,
            "java.lang.System.out:Ljava/io/PrintStream;",
            lineB
        );

        // The method reference is a bootstrap argument of an INVOKEDYNAMIC instruction.
        ClassFileReaderTest.assertLineNumbers(
            cfr,
            MemberReference.Kind.METHOD,
            "java.lang.Runtime.getRuntime()Ljava/lang/Runtime;",
            lineD
        );
        Assert.assertEquals(1, cfr.getDynamicReferences().size());
        Assert.assertEquals(
            new TreeSet<Integer>(Arrays.asList(lineD)),
            cfr.getLineNumbers(cfr.getDynamicReferences().get(0))
        );
    }

    @SuppressWarnings("static-method") @Test public void
    testHandCraftedCode() {

        // INVOKESTATIC Foo.m(), RETURN
        byte[] invokestatic = { (byte) 0xb8, 0, 8, (byte) 0xb1 };

        ClassFileReader cfr = new ClassFileReader(ClassFileReaderTest.codeClassFile(invokestatic, 0, 0, 7));
        Assert.assertEquals("Foo.java", cfr.getSourceFileName());
        Assert.assertEquals(
            new TreeSet<Integer>(Arrays.asList(7)),
            cfr.getLineNumbers(cfr.getMemberReferences().get(0))
        );

        // Without a line number table.
        cfr = new ClassFileReader(ClassFileReaderTest.codeClassFile(invokestatic, 0));
        Assert.assertTrue(cfr.getLineNumbers(cfr.getMemberReferences().get(0)).isEmpty());

        // WIDE IINC 1 1, INVOKESTATIC Foo.m(), RETURN
        cfr = new ClassFileReader(ClassFileReaderTest.codeClassFile(
            new byte[] { (byte) 0xc4, (byte) 0x84, 0, 1, 0, 1, (byte) 0xb8, 0, 8, (byte) 0xb1 },
            0,
            0, 7,
            6, 9
        ));
        Assert.assertEquals(
            new TreeSet<Integer>(Arrays.asList(9)),
            cfr.getLineNumbers(cfr.getMemberReferences().get(0))
        );
    }

    @SuppressWarnings("static-method") @Test public void
    testInvalidCode() {

        // Invalid opcode.
        ClassFileReaderTest.assertClassFormatError(ClassFileReaderTest.codeClassFile(
            new byte[] { (byte) 0xcb, (byte) 0xb1 },
            0
        ));

        // Truncated instruction.
        ClassFileReaderTest.assertClassFormatError(ClassFileReaderTest.codeClassFile(new byte[] { (byte) 0xb8, 0 }, 0));

        // Truncated TABLESWITCH.
        ClassFileReaderTest.assertClassFormatError(ClassFileReaderTest.codeClassFile(
            new byte[] { (byte) 0xaa, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1 },
            0
        ));

        // Constant pool index out of range.
        ClassFileReaderTest.assertClassFormatError(ClassFileReaderTest.codeClassFile(
            new byte[] { (byte) 0xb8, 0, 99, (byte) 0xb1 },
            0
        ));

        // Extra byte in the "Code" attribute.
        ClassFileReaderTest.assertClassFormatError(ClassFileReaderTest.codeClassFile(
            new byte[] { (byte) 0xb8, 0, 8, (byte) 0xb1 },
            1
        ));
    }

    @SuppressWarnings("static-method") @Test public void
    testDynamicModuleAndPackageConstants() {

        ClassFileReader cfr = new ClassFileReader(ClassFileReaderTest.dynamicClassFile(0, 6, false));

        Assert.assertEquals("Foo", cfr.getClassName());
        Assert.assertEquals("java.lang.Object", cfr.getSuperclassName());
        Assert.assertEquals(Arrays.asList("Foo", "java.lang.Object", "Boot"), cfr.getClassReferences());

        Assert.assertEquals(1, cfr.getDynamicReferences().size());
        DynamicReference dr = cfr.getDynamicReferences().get(0);
        Assert.assertFalse(dr.isInvokeDynamic());
        Assert.assertEquals("c", dr.getName());
        Assert.assertEquals("Ljava/lang/Object;", dr.getDescriptor());

        BootstrapMethod bm = cfr.getBootstrapMethod(dr);
        Assert.assertEquals(1, bm.getArgumentCount());
        Assert.assertEquals(6, bm.getMethod().getReferenceKind());
        Assert.assertEquals("Boot", bm.getMethod().getMember().getClassName());
        Assert.assertEquals("bsm", bm.getMethod().getMember().getName());

        Assert.assertEquals(1, cfr.getMethodHandles().size());
        Assert.assertEquals(1, cfr.getMemberReferences().size());
    }

    @SuppressWarnings("static-method") @Test public void
    testInvalidClassFiles() throws Exception {

        // Invalid magic number.
        ClassFileReaderTest.assertClassFormatError(new byte[] { 1, 2, 3, 4, 0, 0, 0, 52 });

        // Truncated class file.
        byte[] ba = ClassFileReaderTest.readSystemClassFile("java.lang.String");
        ClassFileReaderTest.assertClassFormatError(Arrays.copyOf(ba, ba.length / 2));

        // Extra bytes at the end.
        ClassFileReaderTest.assertClassFormatError(ClassFileReaderTest.dynamicClassFile(0, 6, true));

        // Bootstrap method index out of range.
        ClassFileReaderTest.assertClassFormatError(ClassFileReaderTest.dynamicClassFile(1, 6, false));

        // "CONSTANT_Methodref" refers to a "CONSTANT_Utf8" instead of a "CONSTANT_Class".
        ClassFileReaderTest.assertClassFormatError(ClassFileReaderTest.dynamicClassFile(0, 1, false));
    }

    private static void
    assertClassFormatError(byte[] classFile) {
        try {
            new ClassFileReader(classFile);
            Assert.fail("ClassFormatError expected");
        } catch (ClassFormatError cfe) {
            ;
        }
    }

    /**
     * Asserts that the given member reference is used on exactly the <var>expectedLines</var>.
     */
    private static void
    assertLineNumbers(ClassFileReader cfr, MemberReference.Kind kind, String memberReference, Integer... expectedLines) {
        for (MemberReference mr : cfr.getMemberReferences()) {
            if (mr.getKind() == kind && mr.toString().equals(memberReference)) {
                Assert.assertEquals(
                    memberReference,
                    new TreeSet<Integer>(Arrays.asList(expectedLines)),
                    cfr.getLineNumbers(mr)
                );
                return;
            }
        }
        Assert.fail(memberReference + " not found in " + cfr.getMemberReferences());
    }

    /**
     * @return The (one-based) number of the first line of the source file of this class that contains the given
     *         <var>text</var>
     */
    private static int
    lineOf(String text) throws IOException {
        List<String> lines = Files.readAllLines(
            Paths.get("src/test/java", ClassFileReaderTest.class.getName().replace('.', '/') + ".java"),
            StandardCharsets.UTF_8
        );
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(text)) return i + 1;
        }
        throw new AssertionError("\"" + text + "\" not found");
    }

    /**
     * @param memberReference E.g. {@code "java.lang.System.out:Ljava/io/PrintStream;"}, see {@link
     *                        MemberReference#toString()}
     */
    private static boolean
    containsMemberReference(ClassFileReader cfr, MemberReference.Kind kind, String memberReference) {
        for (MemberReference mr : cfr.getMemberReferences()) {
            if (mr.getKind() == kind && mr.toString().equals(memberReference)) return true;
        }
        return false;
    }

    /**
     * Creates a minimal class file with a {@code CONSTANT_Dynamic}, a {@code CONSTANT_Module}, a {@code
     * CONSTANT_Package}, a {@code CONSTANT_MethodType} and a {@code CONSTANT_Long} entry.
     *
     * @param bootstrapMethodIndex The bootstrap method index of the {@code CONSTANT_Dynamic} entry
     * @param bootClassIndex       The class index of the {@code CONSTANT_Methodref} entry; 6 (class {@code "Boot"}) is
     *                             valid
     * @param extraByte            Whether to append an extra byte to the class file
     */
    private static byte[]
    dynamicClassFile(int bootstrapMethodIndex, int bootClassIndex, boolean extraByte) {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream      dos  = new DataOutputStream(baos);
        try {
            dos.writeInt(0xCAFEBABE);
            dos.writeShort(0);  // minor_version
            dos.writeShort(55); // major_version

            dos.writeShort(26); // constant_pool_count
            ClassFileReaderTest.utf8(dos, "Foo");                             // #1
            ClassFileReaderTest.u1u2(dos, 7, 1);                              // #2  Class "Foo"
            ClassFileReaderTest.utf8(dos, "java/lang/Object");                // #3
            ClassFileReaderTest.u1u2(dos, 7, 3);                              // #4  Class "java/lang/Object"
            ClassFileReaderTest.utf8(dos, "Boot");                            // #5
            ClassFileReaderTest.u1u2(dos, 7, 5);                              // #6  Class "Boot"
            ClassFileReaderTest.utf8(dos, "bsm");                             // #7
            ClassFileReaderTest.utf8(dos, (                                   // #8
                "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;"
            ));
            ClassFileReaderTest.u1u2u2(dos, 12, 7, 8);                        // #9  NameAndType
            ClassFileReaderTest.u1u2u2(dos, 10, bootClassIndex, 9);           // #10 Methodref "Boot.bsm"
            dos.writeByte(15);                                                // #11 MethodHandle REF_invokeStatic
            dos.writeByte(6);
            dos.writeShort(10);
            ClassFileReaderTest.utf8(dos, "c");                               // #12
            ClassFileReaderTest.utf8(dos, "Ljava/lang/Object;");              // #13
            ClassFileReaderTest.u1u2u2(dos, 12, 12, 13);                      // #14 NameAndType
            ClassFileReaderTest.u1u2u2(dos, 17, bootstrapMethodIndex, 14);    // #15 Dynamic
            ClassFileReaderTest.utf8(dos, "mod");                             // #16
            ClassFileReaderTest.u1u2(dos, 19, 16);                            // #17 Module
            ClassFileReaderTest.utf8(dos, "pkg");                             // #18
            ClassFileReaderTest.u1u2(dos, 20, 18);                            // #19 Package
            ClassFileReaderTest.utf8(dos, "()V");                             // #20
            ClassFileReaderTest.u1u2(dos, 16, 20);                            // #21 MethodType
            dos.writeByte(5);                                                 // #22 Long (two entries)
            dos.writeLong(42L);
            ClassFileReaderTest.utf8(dos, "BootstrapMethods");                // #24
            ClassFileReaderTest.utf8(dos, "unused");                          // #25

            dos.writeShort(0x0021); // access_flags
            dos.writeShort(2);      // this_class
            dos.writeShort(4);      // super_class
            dos.writeShort(0);      // interfaces_count
            dos.writeShort(0);      // fields_count
            dos.writeShort(0);      // methods_count

            dos.writeShort(1);      // attributes_count
            dos.writeShort(24);     // attribute_name_index
            dos.writeInt(8);        // attribute_length
            dos.writeShort(1);      // num_bootstrap_methods
            dos.writeShort(11);     // bootstrap_method_ref
            dos.writeShort(1);      // num_bootstrap_arguments
            dos.writeShort(21);     // bootstrap_argument

            if (extraByte) dos.writeByte(0);

            dos.flush();
        } catch (IOException ioe) {
            throw new AssertionError(ioe);
        }
        return baos.toByteArray();
    }

    /**
     * Creates a minimal class file with one static method {@code "m()V"}, a {@code CONSTANT_Methodref} entry for
     * that method (constant pool index 8), and a {@code SourceFile} attribute ({@code "Foo.java"}).
     *
     * @param code            The bytecode of the method
     * @param extraBytes      The number of extra bytes to insert at the end of the {@code Code} attribute
     * @param lineNumberTable Pairs of {@code start_pc} and {@code line_number}; if empty, then the {@code Code}
     *                        attribute has no {@code LineNumberTable} attribute
     */
    private static byte[]
    codeClassFile(byte[] code, int extraBytes, int... lineNumberTable) {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream      dos  = new DataOutputStream(baos);
        try {
            dos.writeInt(0xCAFEBABE);
            dos.writeShort(0);  // minor_version
            dos.writeShort(52); // major_version

            dos.writeShort(13); // constant_pool_count
            ClassFileReaderTest.utf8(dos, "Foo");               // #1
            ClassFileReaderTest.u1u2(dos, 7, 1);                // #2  Class "Foo"
            ClassFileReaderTest.utf8(dos, "java/lang/Object");  // #3
            ClassFileReaderTest.u1u2(dos, 7, 3);                // #4  Class "java/lang/Object"
            ClassFileReaderTest.utf8(dos, "m");                 // #5
            ClassFileReaderTest.utf8(dos, "()V");               // #6
            ClassFileReaderTest.u1u2u2(dos, 12, 5, 6);          // #7  NameAndType
            ClassFileReaderTest.u1u2u2(dos, 10, 2, 7);          // #8  Methodref "Foo.m()V"
            ClassFileReaderTest.utf8(dos, "Code");              // #9
            ClassFileReaderTest.utf8(dos, "LineNumberTable");   // #10
            ClassFileReaderTest.utf8(dos, "SourceFile");        // #11
            ClassFileReaderTest.utf8(dos, "Foo.java");          // #12

            dos.writeShort(0x0021); // access_flags
            dos.writeShort(2);      // this_class
            dos.writeShort(4);      // super_class
            dos.writeShort(0);      // interfaces_count
            dos.writeShort(0);      // fields_count

            dos.writeShort(1);      // methods_count
            dos.writeShort(0x0009); // access_flags
            dos.writeShort(5);      // name_index
            dos.writeShort(6);      // descriptor_index
            dos.writeShort(1);      // attributes_count

            int lineNumberTableLength = lineNumberTable.length == 0 ? 0 : 6 + 2 + 2 * lineNumberTable.length;
            dos.writeShort(9);                                                  // attribute_name_index ("Code")
            dos.writeInt(12 + code.length + lineNumberTableLength + extraBytes); // attribute_length
            dos.writeShort(0);                                                  // max_stack
            dos.writeShort(0);                                                  // max_locals
            dos.writeInt(code.length);                                          // code_length
            dos.write(code);
            dos.writeShort(0);                                                  // exception_table_length
            if (lineNumberTable.length == 0) {
                dos.writeShort(0);                                              // attributes_count
            } else {
                dos.writeShort(1);                                              // attributes_count
                dos.writeShort(10);                                             // attribute_name_index
                dos.writeInt(2 + 2 * lineNumberTable.length);                   // attribute_length
                dos.writeShort(lineNumberTable.length / 2);                     // line_number_table_length
                for (int i : lineNumberTable) dos.writeShort(i);
            }
            for (int i = 0; i < extraBytes; i++) dos.writeByte(0);

            dos.writeShort(1);      // attributes_count
            dos.writeShort(11);     // attribute_name_index ("SourceFile")
            dos.writeInt(2);        // attribute_length
            dos.writeShort(12);     // sourcefile_index

            dos.flush();
        } catch (IOException ioe) {
            throw new AssertionError(ioe);
        }
        return baos.toByteArray();
    }

    private static void
    utf8(DataOutputStream dos, String s) throws IOException {
        dos.writeByte(1);
        dos.writeUTF(s);
    }

    private static void
    u1u2(DataOutputStream dos, int tag, int value) throws IOException {
        dos.writeByte(tag);
        dos.writeShort(value);
    }

    private static void
    u1u2u2(DataOutputStream dos, int tag, int value1, int value2) throws IOException {
        dos.writeByte(tag);
        dos.writeShort(value1);
        dos.writeShort(value2);
    }

    private static byte[]
    readSystemClassFile(String className) throws IOException {
        return ClassFileReaderTest.readFully(
            ClassLoader.getSystemResourceAsStream(className.replace('.', '/') + ".class"),
            className
        );
    }

    private static byte[]
    readClassFile(Class<?> clazz) throws IOException {
        String className = clazz.getName();
        return ClassFileReaderTest.readFully(
            clazz.getResourceAsStream('/' + className.replace('.', '/') + ".class"),
            className
        );
    }

    private static byte[]
    readFully(@Nullable InputStream is, String className) throws IOException {

        if (is == null) throw new AssertionError("Class file of \"" + className + "\" not found");

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[]                buffer = new byte[8192];
            for (int n = is.read(buffer); n != -1; n = is.read(buffer)) baos.write(buffer, 0, n);
            return baos.toByteArray();
        } finally {
            is.close();
        }
    }
}
