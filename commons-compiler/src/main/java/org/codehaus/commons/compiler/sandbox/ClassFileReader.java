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

package org.codehaus.commons.compiler.sandbox;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.codehaus.commons.nullanalysis.Nullable;

/**
 * A minimal parser for Java class files, as far as needed to determine which classes and members a class refers to.
 * <p>
 *   Parses the constant pool (all tags defined up to Java 25), the name, superclass and interfaces of the class, the
 *   access flags, names and descriptors of its fields and methods, and the {@code BootstrapMethods} and {@code
 *   SourceFile} attributes. The bytecode of the methods ({@code Code} attributes) is scanned only as far as needed to
 *   determine the source lines (from the {@code LineNumberTable} attributes) where the constant pool entries are
 *   used; see {@link #getLineNumbers(MemberReference)}. All other attributes are skipped.
 * </p>
 * <p>
 *   Class names are reported in the format of {@link Class#getName()}, i.e. with dots as package separators (e.g.
 *   {@code "java.lang.String"}, {@code "[Ljava.lang.String;"}); field and method descriptors are reported verbatim
 *   (e.g. {@code "(Ljava/lang/String;)V"}).
 * </p>
 * <p>
 *   Invalid or truncated class files cause a {@link ClassFormatError}.
 * </p>
 */
public final
class ClassFileReader {

    // Constant pool tags, see JVMS 4.4.
    private static final int CONSTANT_UTF8                 = 1;
    private static final int CONSTANT_INTEGER              = 3;
    private static final int CONSTANT_FLOAT                = 4;
    private static final int CONSTANT_LONG                 = 5;
    private static final int CONSTANT_DOUBLE               = 6;
    private static final int CONSTANT_CLASS                = 7;
    private static final int CONSTANT_STRING               = 8;
    private static final int CONSTANT_FIELDREF             = 9;
    private static final int CONSTANT_METHODREF            = 10;
    private static final int CONSTANT_INTERFACE_METHODREF  = 11;
    private static final int CONSTANT_NAME_AND_TYPE        = 12;
    private static final int CONSTANT_METHOD_HANDLE        = 15;
    private static final int CONSTANT_METHOD_TYPE          = 16;
    private static final int CONSTANT_DYNAMIC              = 17;
    private static final int CONSTANT_INVOKE_DYNAMIC       = 18;
    private static final int CONSTANT_MODULE               = 19;
    private static final int CONSTANT_PACKAGE              = 20;

    private static final int MAGIC = 0xCAFEBABE;

    // Opcodes, see JVMS 6.5.
    private static final int LDC           = 0x12;
    private static final int LDC_W         = 0x13;
    private static final int LDC2_W        = 0x14;
    private static final int IINC          = 0x84;
    private static final int TABLESWITCH   = 0xaa;
    private static final int GETSTATIC     = 0xb2;
    private static final int INVOKEDYNAMIC = 0xba;
    private static final int WIDE          = 0xc4;

    /**
     * The lengths of the instructions (including the opcode); 0 means "invalid opcode", and -1 means "variable
     * length" (see JVMS 6.5).
     */
    private static final byte[] INSTRUCTION_LENGTHS = new byte[256];
    static {
        byte[] l = ClassFileReader.INSTRUCTION_LENGTHS;
        Arrays.fill(l, 0x00, 0xca, (byte) 1); // "nop" through "jsr_w"; the multi-byte instructions follow.
        l[0x10] = 2;                          // bipush
        l[0x11] = 3;                          // sipush
        l[0x12] = 2;                          // ldc
        l[0x13] = 3;                          // ldc_w
        l[0x14] = 3;                          // ldc2_w
        Arrays.fill(l, 0x15, 0x1a, (byte) 2); // iload ... aload
        Arrays.fill(l, 0x36, 0x3b, (byte) 2); // istore ... astore
        l[0x84] = 3;                          // iinc
        Arrays.fill(l, 0x99, 0xa9, (byte) 3); // ifeq ... jsr
        l[0xa9] = 2;                          // ret
        l[0xaa] = -1;                         // tableswitch
        l[0xab] = -1;                         // lookupswitch
        Arrays.fill(l, 0xb2, 0xb9, (byte) 3); // getstatic ... invokestatic
        l[0xb9] = 5;                          // invokeinterface
        l[0xba] = 5;                          // invokedynamic
        l[0xbb] = 3;                          // new
        l[0xbc] = 2;                          // newarray
        l[0xbd] = 3;                          // anewarray
        l[0xc0] = 3;                          // checkcast
        l[0xc1] = 3;                          // instanceof
        l[0xc4] = -1;                         // wide
        l[0xc5] = 4;                          // multianewarray
        l[0xc6] = 3;                          // ifnull
        l[0xc7] = 3;                          // ifnonnull
        l[0xc8] = 5;                          // goto_w
        l[0xc9] = 5;                          // jsr_w
    }

    /**
     * A field or method declared by the class.
     */
    public static final
    class MemberDeclaration {

        private final int    accessFlags;
        private final String name;
        private final String descriptor;
        private final int    lineNumber;

        MemberDeclaration(int accessFlags, String name, String descriptor, int lineNumber) {
            this.accessFlags = accessFlags;
            this.name        = name;
            this.descriptor  = descriptor;
            this.lineNumber  = lineNumber;
        }

        /**
         * @return The access flags, e.g. {@link java.lang.reflect.Modifier#NATIVE}
         */
        public int getAccessFlags() { return this.accessFlags; }

        public String getName() { return this.name; }

        public String getDescriptor() { return this.descriptor; }

        /**
         * @return The first source line of the method's code, or -1 iff this is a field, the method has no code, or
         *         the class file has no line numbers
         */
        public int getLineNumber() { return this.lineNumber; }

        @Override public String
        toString() { return this.name + this.descriptor; }
    }

    /**
     * A {@code CONSTANT_Fieldref}, {@code CONSTANT_Methodref} or {@code CONSTANT_InterfaceMethodref} entry of the
     * constant pool.
     */
    public static final
    class MemberReference {

        /**
         * The kinds of member references.
         */
        public
        enum Kind { FIELD, METHOD, INTERFACE_METHOD }

        private final Kind   kind;
        private final String className;
        private final String name;
        private final String descriptor;

        MemberReference(Kind kind, String className, String name, String descriptor) {
            this.kind       = kind;
            this.className  = className;
            this.name       = name;
            this.descriptor = descriptor;
        }

        public Kind getKind() { return this.kind; }

        /**
         * @return The name of the class or interface (or the array type) that the reference names, e.g. {@code
         *         "java.lang.String"} or {@code "[I"}
         */
        public String getClassName() { return this.className; }

        public String getName() { return this.name; }

        public String getDescriptor() { return this.descriptor; }

        @Override public boolean
        equals(@Nullable Object o) {
            if (!(o instanceof MemberReference)) return false;
            MemberReference that = (MemberReference) o;
            return (
                this.kind == that.kind
                && this.className.equals(that.className)
                && this.name.equals(that.name)
                && this.descriptor.equals(that.descriptor)
            );
        }

        @Override public int
        hashCode() {
            return this.kind.hashCode() ^ this.className.hashCode() ^ this.name.hashCode() ^ this.descriptor.hashCode();
        }

        @Override public String
        toString() {
            return (
                this.className
                + '.'
                + this.name
                + (this.kind == Kind.FIELD ? ":" : "")
                + this.descriptor
            );
        }
    }

    /**
     * A {@code CONSTANT_MethodHandle} entry of the constant pool.
     */
    public static final
    class MethodHandleReference {

        private final int             referenceKind;
        private final MemberReference member;

        MethodHandleReference(int referenceKind, MemberReference member) {
            this.referenceKind = referenceKind;
            this.member        = member;
        }

        /**
         * @return The reference kind, e.g. {@code 6} for {@code REF_invokeStatic} (see JVMS 4.4.8)
         */
        public int getReferenceKind() { return this.referenceKind; }

        public MemberReference getMember() { return this.member; }

        @Override public String
        toString() { return "MethodHandle(" + this.referenceKind + ", " + this.member + ")"; }
    }

    /**
     * A {@code CONSTANT_Dynamic} or {@code CONSTANT_InvokeDynamic} entry of the constant pool.
     */
    public static final
    class DynamicReference {

        private final boolean invokeDynamic;
        private final int     bootstrapMethodIndex;
        private final String  name;
        private final String  descriptor;

        DynamicReference(boolean invokeDynamic, int bootstrapMethodIndex, String name, String descriptor) {
            this.invokeDynamic        = invokeDynamic;
            this.bootstrapMethodIndex = bootstrapMethodIndex;
            this.name                 = name;
            this.descriptor           = descriptor;
        }

        /**
         * @return {@code true} for a {@code CONSTANT_InvokeDynamic}, {@code false} for a {@code CONSTANT_Dynamic}
         */
        public boolean isInvokeDynamic() { return this.invokeDynamic; }

        /**
         * @return The index into the {@link ClassFileReader#getBootstrapMethods() bootstrap methods}
         */
        public int getBootstrapMethodIndex() { return this.bootstrapMethodIndex; }

        public String getName() { return this.name; }

        public String getDescriptor() { return this.descriptor; }

        @Override public String
        toString() {
            return (
                (this.invokeDynamic ? "InvokeDynamic(" : "Dynamic(")
                + this.bootstrapMethodIndex
                + ", "
                + this.name
                + this.descriptor
                + ")"
            );
        }
    }

    /**
     * An entry of the {@code BootstrapMethods} attribute.
     */
    public static final
    class BootstrapMethod {

        private final MethodHandleReference method;
        private final int                   methodIndex;
        private final int[]                 argumentIndexes;

        BootstrapMethod(MethodHandleReference method, int methodIndex, int[] argumentIndexes) {
            this.method          = method;
            this.methodIndex     = methodIndex;
            this.argumentIndexes = argumentIndexes;
        }

        public MethodHandleReference getMethod() { return this.method; }

        public int getArgumentCount() { return this.argumentIndexes.length; }

        @Override public String
        toString() { return "BootstrapMethod(" + this.method + ", " + this.argumentIndexes.length + " arguments)"; }
    }

    /**
     * The bytecode and the line number table of a method.
     */
    private static final
    class Code {

        final byte[]      bytecode;
        final List<int[]> lineNumbers = new ArrayList<int[]>(); // { start_pc, line_number }

        Code(byte[] bytecode) { this.bytecode = bytecode; }

        /**
         * @return The source line of the instruction at the given <var>pc</var>, or -1 iff unknown
         */
        int
        getLineNumber(int pc) {
            int result = -1, resultStartPc = -1;
            for (int[] entry : this.lineNumbers) {
                if (entry[0] <= pc && entry[0] > resultStartPc) {
                    resultStartPc = entry[0];
                    result        = entry[1];
                }
            }
            return result;
        }
    }

    // The constant pool. Index 0 and the slots following LONG and DOUBLE entries are unused (tag 0).
    private int[]    cpTags   = new int[0];
    private int[]    cpValue1 = new int[0];
    private int[]    cpValue2 = new int[0];
    private String[] cpUtf8s  = new String[0];

    private int    majorVersion;
    private int    minorVersion;
    private int    accessFlags;
    private String className = "";
    @Nullable private String superclassName;
    @Nullable private String sourceFileName;

    private final List<String>                interfaceNames   = new ArrayList<String>();
    private final List<MemberDeclaration>     fields           = new ArrayList<MemberDeclaration>();
    private final List<MemberDeclaration>     methods          = new ArrayList<MemberDeclaration>();
    private final List<String>                classReferences  = new ArrayList<String>();
    private final List<MemberReference>       memberReferences = new ArrayList<MemberReference>();
    private final List<MethodHandleReference> methodHandles    = new ArrayList<MethodHandleReference>();
    private final List<DynamicReference>      dynamics         = new ArrayList<DynamicReference>();
    private final List<BootstrapMethod>       bootstrapMethods = new ArrayList<BootstrapMethod>();
    private final List<Code>                  codes            = new ArrayList<Code>();

    // The constant pool indexes of the entries in "dynamics".
    private final List<Integer> dynamicIndexes = new ArrayList<Integer>();

    // The source lines where the member and dynamic references are used.
    private final Map<MemberReference, SortedSet<Integer>>  memberReferenceLines  = (
        new HashMap<MemberReference, SortedSet<Integer>>()
    );
    private final Map<DynamicReference, SortedSet<Integer>> dynamicReferenceLines = (
        new HashMap<DynamicReference, SortedSet<Integer>>()
    );

    /**
     * Parses the given class file.
     *
     * @throws ClassFormatError The <var>classFile</var> is invalid or truncated
     */
    public
    ClassFileReader(byte[] classFile) {
        try {
            this.parse(new DataInputStream(new ByteArrayInputStream(classFile)));
        } catch (EOFException eofe) {
            throw new ClassFormatError("Truncated class file");
        } catch (IOException ioe) {
            throw new ClassFormatError("Invalid class file: " + ioe.getMessage());
        }
    }

    public int getMajorVersion() { return this.majorVersion; }

    public int getMinorVersion() { return this.minorVersion; }

    /**
     * @return The access flags of the class, e.g. {@link java.lang.reflect.Modifier#INTERFACE}
     */
    public int getAccessFlags() { return this.accessFlags; }

    /**
     * @return The name of the class, e.g. {@code "pkg.Outer$Inner"}
     */
    public String getClassName() { return this.className; }

    /**
     * @return The name of the superclass, or {@code null} iff this is the class file of {@code java.lang.Object} (or
     *         of a module descriptor)
     */
    @Nullable public String getSuperclassName() { return this.superclassName; }

    public List<String> getInterfaceNames() { return Collections.unmodifiableList(this.interfaceNames); }

    public List<MemberDeclaration> getFields() { return Collections.unmodifiableList(this.fields); }

    public List<MemberDeclaration> getMethods() { return Collections.unmodifiableList(this.methods); }

    /**
     * @return The names of all classes, interfaces and array types referenced by {@code CONSTANT_Class} entries,
     *         including the class itself, its superclass and its interfaces
     */
    public List<String> getClassReferences() { return Collections.unmodifiableList(this.classReferences); }

    /**
     * @return All {@code CONSTANT_Fieldref}, {@code CONSTANT_Methodref} and {@code CONSTANT_InterfaceMethodref}
     *         entries of the constant pool
     */
    public List<MemberReference> getMemberReferences() { return Collections.unmodifiableList(this.memberReferences); }

    /**
     * @return All {@code CONSTANT_MethodHandle} entries of the constant pool
     */
    public List<MethodHandleReference> getMethodHandles() { return Collections.unmodifiableList(this.methodHandles); }

    /**
     * @return All {@code CONSTANT_Dynamic} and {@code CONSTANT_InvokeDynamic} entries of the constant pool
     */
    public List<DynamicReference> getDynamicReferences() { return Collections.unmodifiableList(this.dynamics); }

    /**
     * @return The entries of the {@code BootstrapMethods} attribute; empty if the class file has no such attribute
     */
    public List<BootstrapMethod> getBootstrapMethods() { return Collections.unmodifiableList(this.bootstrapMethods); }

    /**
     * @return The bootstrap method of the given <var>dynamicReference</var>
     */
    public BootstrapMethod
    getBootstrapMethod(DynamicReference dynamicReference) {
        return (BootstrapMethod) this.bootstrapMethods.get(dynamicReference.getBootstrapMethodIndex());
    }

    /**
     * @return The value of the {@code SourceFile} attribute (e.g. {@code "Foo.java"}), or {@code null} iff the class
     *         file has no such attribute
     */
    @Nullable public String getSourceFileName() { return this.sourceFileName; }

    /**
     * Returns the source lines of the instructions that use the given <var>memberReference</var>, i.e. of the field
     * access and method invocation instructions that refer to it, and of the instructions that use a method handle of
     * it, either directly ({@code ldc}), or as the bootstrap method or a bootstrap argument of an {@code
     * invokedynamic} instruction or a dynamically-computed constant (e.g. a method reference like {@code
     * Runtime::getRuntime}).
     *
     * @return The line numbers; empty iff the class file has no {@code LineNumberTable} attributes, or iff no
     *         instruction uses the <var>memberReference</var>
     */
    public SortedSet<Integer>
    getLineNumbers(MemberReference memberReference) {
        SortedSet<Integer> result = (SortedSet<Integer>) this.memberReferenceLines.get(memberReference);
        if (result == null) return Collections.emptySortedSet();
        return Collections.unmodifiableSortedSet(result);
    }

    /**
     * @return The source lines of the {@code invokedynamic} and {@code ldc} instructions that use the given
     *         <var>dynamicReference</var>; empty iff the class file has no {@code LineNumberTable} attributes, or iff
     *         no instruction uses the <var>dynamicReference</var>
     */
    public SortedSet<Integer>
    getLineNumbers(DynamicReference dynamicReference) {
        SortedSet<Integer> result = (SortedSet<Integer>) this.dynamicReferenceLines.get(dynamicReference);
        if (result == null) return Collections.emptySortedSet();
        return Collections.unmodifiableSortedSet(result);
    }

    private void
    parse(DataInputStream dis) throws IOException {

        if (dis.readInt() != ClassFileReader.MAGIC) throw new ClassFormatError("Invalid magic number");
        this.minorVersion = dis.readUnsignedShort();
        this.majorVersion = dis.readUnsignedShort();

        this.readConstantPool(dis);

        this.accessFlags = dis.readUnsignedShort();
        this.className   = this.getConstantClassName(dis.readUnsignedShort());

        int superclassIndex = dis.readUnsignedShort();
        this.superclassName = superclassIndex == 0 ? null : this.getConstantClassName(superclassIndex);

        for (int i = dis.readUnsignedShort(); i > 0; i--) {
            this.interfaceNames.add(this.getConstantClassName(dis.readUnsignedShort()));
        }

        this.readMembers(dis, this.fields, false);
        this.readMembers(dis, this.methods, true);

        for (int i = dis.readUnsignedShort(); i > 0; i--) {
            String attributeName   = this.getConstantUtf8(dis.readUnsignedShort());
            int    attributeLength = dis.readInt();
            if ("BootstrapMethods".equals(attributeName)) {
                DataInputStream dis2 = ClassFileReader.readAttribute(dis, attributeLength);
                this.readBootstrapMethods(dis2);
                if (dis2.read() != -1) throw new ClassFormatError("Invalid length of the BootstrapMethods attribute");
            } else
            if ("SourceFile".equals(attributeName)) {
                if (attributeLength != 2) throw new ClassFormatError("Invalid length of the SourceFile attribute");
                this.sourceFileName = this.getConstantUtf8(dis.readUnsignedShort());
            } else
            {
                ClassFileReader.skip(dis, attributeLength);
            }
        }

        if (dis.read() != -1) throw new ClassFormatError("Extra bytes after the end of the class file");

        this.resolveConstantPool();
        this.computeLineNumbers();
    }

    private void
    readConstantPool(DataInputStream dis) throws IOException {

        int count = dis.readUnsignedShort();

        this.cpTags   = new int[count];
        this.cpValue1 = new int[count];
        this.cpValue2 = new int[count];
        this.cpUtf8s  = new String[count];

        for (int i = 1; i < count; i++) {
            int tag = dis.readUnsignedByte();
            this.cpTags[i] = tag;
            switch (tag) {

            case CONSTANT_UTF8:
                this.cpUtf8s[i] = dis.readUTF();
                break;

            case CONSTANT_INTEGER:
            case CONSTANT_FLOAT:
                dis.readInt();
                break;

            case CONSTANT_LONG:
            case CONSTANT_DOUBLE:
                dis.readLong();
                i++; // Eight-byte constants take up two constant pool entries.
                break;

            case CONSTANT_CLASS:
            case CONSTANT_STRING:
            case CONSTANT_METHOD_TYPE:
            case CONSTANT_MODULE:
            case CONSTANT_PACKAGE:
                this.cpValue1[i] = dis.readUnsignedShort();
                break;

            case CONSTANT_FIELDREF:
            case CONSTANT_METHODREF:
            case CONSTANT_INTERFACE_METHODREF:
            case CONSTANT_NAME_AND_TYPE:
            case CONSTANT_DYNAMIC:
            case CONSTANT_INVOKE_DYNAMIC:
                this.cpValue1[i] = dis.readUnsignedShort();
                this.cpValue2[i] = dis.readUnsignedShort();
                break;

            case CONSTANT_METHOD_HANDLE:
                this.cpValue1[i] = dis.readUnsignedByte();
                this.cpValue2[i] = dis.readUnsignedShort();
                break;

            default:
                throw new ClassFormatError("Invalid constant pool tag " + tag + " at index " + i);
            }
        }
    }

    /**
     * @param methods Whether to read the {@code Code} attributes
     */
    private void
    readMembers(DataInputStream dis, List<MemberDeclaration> result, boolean methods) throws IOException {
        for (int i = dis.readUnsignedShort(); i > 0; i--) {
            int    accessFlags = dis.readUnsignedShort();
            String name        = this.getConstantUtf8(dis.readUnsignedShort());
            String descriptor  = this.getConstantUtf8(dis.readUnsignedShort());
            int    lineNumber  = -1;
            for (int j = dis.readUnsignedShort(); j > 0; j--) {
                String attributeName   = this.getConstantUtf8(dis.readUnsignedShort());
                int    attributeLength = dis.readInt();
                if (methods && "Code".equals(attributeName)) {
                    DataInputStream dis2 = ClassFileReader.readAttribute(dis, attributeLength);
                    Code            code = this.readCode(dis2);
                    if (dis2.read() != -1) throw new ClassFormatError("Invalid length of the Code attribute");
                    this.codes.add(code);
                    for (int[] entry : code.lineNumbers) {
                        if (lineNumber == -1 || entry[1] < lineNumber) lineNumber = entry[1];
                    }
                } else {
                    ClassFileReader.skip(dis, attributeLength);
                }
            }
            result.add(new MemberDeclaration(accessFlags, name, descriptor, lineNumber));
        }
    }

    /**
     * Reads the bytecode and the line number tables from a {@code Code} attribute; skips the exception table and all
     * other attributes.
     */
    private Code
    readCode(DataInputStream dis) throws IOException {

        dis.readUnsignedShort(); // max_stack
        dis.readUnsignedShort(); // max_locals

        int codeLength = dis.readInt();
        if (codeLength <= 0 || codeLength > 65535) throw new ClassFormatError("Invalid code length " + codeLength);
        byte[] bytecode = new byte[codeLength];
        dis.readFully(bytecode);

        ClassFileReader.skip(dis, 8 * dis.readUnsignedShort()); // exception_table

        Code result = new Code(bytecode);
        for (int i = dis.readUnsignedShort(); i > 0; i--) {
            String attributeName   = this.getConstantUtf8(dis.readUnsignedShort());
            int    attributeLength = dis.readInt();
            if (!"LineNumberTable".equals(attributeName)) {
                ClassFileReader.skip(dis, attributeLength);
                continue;
            }

            int count = dis.readUnsignedShort();
            if (attributeLength != 2 + 4 * count) {
                throw new ClassFormatError("Invalid length of the LineNumberTable attribute");
            }
            for (int j = 0; j < count; j++) {
                int startPc    = dis.readUnsignedShort();
                int lineNumber = dis.readUnsignedShort();
                result.lineNumbers.add(new int[] { startPc, lineNumber });
            }
        }
        return result;
    }

    private void
    readBootstrapMethods(DataInputStream dis) throws IOException {
        for (int i = dis.readUnsignedShort(); i > 0; i--) {
            int                   methodIndex     = dis.readUnsignedShort();
            MethodHandleReference method          = this.getConstantMethodHandle(methodIndex);
            int[]                 argumentIndexes = new int[dis.readUnsignedShort()];
            for (int j = 0; j < argumentIndexes.length; j++) {
                int index = dis.readUnsignedShort();
                if (index == 0 || index >= this.cpTags.length || this.cpTags[index] == 0) {
                    throw new ClassFormatError("Invalid bootstrap method argument index " + index);
                }
                argumentIndexes[j] = index;
            }
            this.bootstrapMethods.add(new BootstrapMethod(method, methodIndex, argumentIndexes));
        }
    }

    /**
     * Validates all constant pool entries and fills the lists of class, member, method handle and dynamic
     * references.
     */
    private void
    resolveConstantPool() {
        for (int i = 1; i < this.cpTags.length; i++) {
            switch (this.cpTags[i]) {

            case CONSTANT_CLASS:
                this.classReferences.add(this.getConstantClassName(i));
                break;

            case CONSTANT_STRING:
            case CONSTANT_METHOD_TYPE:
            case CONSTANT_MODULE:
            case CONSTANT_PACKAGE:
                this.getConstantUtf8(this.cpValue1[i]);
                break;

            case CONSTANT_NAME_AND_TYPE:
                this.getConstantUtf8(this.cpValue1[i]);
                this.getConstantUtf8(this.cpValue2[i]);
                break;

            case CONSTANT_FIELDREF:
            case CONSTANT_METHODREF:
            case CONSTANT_INTERFACE_METHODREF:
                this.memberReferences.add(this.getConstantMemberReference(i));
                break;

            case CONSTANT_METHOD_HANDLE:
                this.methodHandles.add(this.getConstantMethodHandle(i));
                break;

            case CONSTANT_DYNAMIC:
            case CONSTANT_INVOKE_DYNAMIC:
                int bootstrapMethodIndex = this.cpValue1[i];
                if (bootstrapMethodIndex >= this.bootstrapMethods.size()) {
                    throw new ClassFormatError(
                        "Invalid bootstrap method index " + bootstrapMethodIndex + " at constant pool index " + i
                    );
                }
                int nameAndTypeIndex = this.checkConstant(this.cpValue2[i], ClassFileReader.CONSTANT_NAME_AND_TYPE);
                this.dynamics.add(new DynamicReference(
                    this.cpTags[i] == ClassFileReader.CONSTANT_INVOKE_DYNAMIC,
                    bootstrapMethodIndex,
                    this.getConstantUtf8(this.cpValue1[nameAndTypeIndex]),
                    this.getConstantUtf8(this.cpValue2[nameAndTypeIndex])
                ));
                this.dynamicIndexes.add(Integer.valueOf(i));
                break;

            default:
                ;
            }
        }
    }

    /**
     * Scans the bytecode of all methods for instructions that refer to constant pool entries, and determines the
     * source lines where the member and dynamic references are used, directly or through method handles.
     */
    private void
    computeLineNumbers() {

        // Constant pool index => the source lines of the instructions that refer to it.
        Map<Integer, SortedSet<Integer>> lines = new HashMap<Integer, SortedSet<Integer>>();
        for (Code code : this.codes) this.scanCode(code, lines);

        // A dynamic entry passes its lines on to its bootstrap method and the bootstrap arguments. Repeat until
        // nothing changes, because a bootstrap argument may itself be a dynamic entry.
        for (boolean changed = true; changed;) {
            changed = false;
            for (Integer dynamicIndex : this.dynamicIndexes) {
                SortedSet<Integer> dl = (SortedSet<Integer>) lines.get(dynamicIndex);
                if (dl == null) continue;
                BootstrapMethod bm = (BootstrapMethod) this.bootstrapMethods.get(
                    this.cpValue1[dynamicIndex.intValue()]
                );
                changed |= ClassFileReader.addAll(lines, bm.methodIndex, dl);
                for (int argumentIndex : bm.argumentIndexes) {
                    changed |= ClassFileReader.addAll(lines, argumentIndex, dl);
                }
            }
        }

        // A method handle passes its lines on to the member that it refers to.
        for (int i = 1; i < this.cpTags.length; i++) {
            if (this.cpTags[i] != ClassFileReader.CONSTANT_METHOD_HANDLE) continue;
            SortedSet<Integer> mhl = (SortedSet<Integer>) lines.get(Integer.valueOf(i));
            if (mhl != null) ClassFileReader.addAll(lines, this.cpValue2[i], mhl);
        }

        for (int i = 1; i < this.cpTags.length; i++) {
            int tag = this.cpTags[i];
            if (
                tag != ClassFileReader.CONSTANT_FIELDREF
                && tag != ClassFileReader.CONSTANT_METHODREF
                && tag != ClassFileReader.CONSTANT_INTERFACE_METHODREF
            ) continue;
            SortedSet<Integer> ml = (SortedSet<Integer>) lines.get(Integer.valueOf(i));
            if (ml == null) continue;

            MemberReference    mr = this.getConstantMemberReference(i);
            SortedSet<Integer> l  = (SortedSet<Integer>) this.memberReferenceLines.get(mr);
            if (l == null) this.memberReferenceLines.put(mr, (l = new TreeSet<Integer>()));
            l.addAll(ml);
        }

        for (int i = 0; i < this.dynamics.size(); i++) {
            SortedSet<Integer> dl = (SortedSet<Integer>) lines.get(this.dynamicIndexes.get(i));
            if (dl != null) this.dynamicReferenceLines.put(this.dynamics.get(i), dl);
        }
    }

    /**
     * Adds the source lines of all instructions of the <var>code</var> that refer to constant pool entries to the
     * <var>result</var>.
     */
    private void
    scanCode(Code code, Map<Integer, SortedSet<Integer>> result) {

        byte[] bytecode = code.bytecode;
        for (int pc = 0; pc < bytecode.length;) {
            int opcode = 0xff & bytecode[pc];
            int length = ClassFileReader.instructionLength(bytecode, pc);
            if (length > bytecode.length - pc) throw new ClassFormatError("Truncated instruction at offset " + pc);

            int constantPoolIndex = (
                opcode == ClassFileReader.LDC
                ? 0xff & bytecode[pc + 1]
                : (
                    opcode == ClassFileReader.LDC_W
                    || opcode == ClassFileReader.LDC2_W
                    || (opcode >= ClassFileReader.GETSTATIC && opcode <= ClassFileReader.INVOKEDYNAMIC)
                )
                ? (0xff & bytecode[pc + 1]) << 8 | (0xff & bytecode[pc + 2])
                : 0
            );
            if (constantPoolIndex != 0) {
                if (constantPoolIndex >= this.cpTags.length) {
                    throw new ClassFormatError("Invalid constant pool index " + constantPoolIndex + " at offset " + pc);
                }
                int lineNumber = code.getLineNumber(pc);
                if (lineNumber != -1) {
                    ClassFileReader.addAll(
                        result,
                        constantPoolIndex,
                        new TreeSet<Integer>(Collections.singleton(Integer.valueOf(lineNumber)))
                    );
                }
            }

            pc += length;
        }
    }

    /**
     * @return                 The length of the instruction at the given <var>pc</var>, including the opcode
     * @throws ClassFormatError The opcode is invalid
     */
    private static int
    instructionLength(byte[] bytecode, int pc) {

        int opcode = 0xff & bytecode[pc];
        int length = ClassFileReader.INSTRUCTION_LENGTHS[opcode];
        if (length > 0) return length;
        if (length == 0) throw new ClassFormatError("Invalid opcode " + opcode + " at offset " + pc);

        if (opcode == ClassFileReader.WIDE) {
            if (pc + 1 >= bytecode.length) throw new ClassFormatError("Truncated instruction at offset " + pc);
            return (0xff & bytecode[pc + 1]) == ClassFileReader.IINC ? 6 : 4;
        }

        // TABLESWITCH and LOOKUPSWITCH: The operands start at the next offset that is a multiple of four.
        int operands = (pc + 4) & ~3;
        if (operands + 12 > bytecode.length) throw new ClassFormatError("Truncated instruction at offset " + pc);

        long end;
        if (opcode == ClassFileReader.TABLESWITCH) {
            int low  = ClassFileReader.readInt(bytecode, operands + 4);
            int high = ClassFileReader.readInt(bytecode, operands + 8);
            if (low > high) throw new ClassFormatError("Invalid tableswitch at offset " + pc);
            end = operands + 12 + 4L * ((long) high - low + 1);
        } else {
            int npairs = ClassFileReader.readInt(bytecode, operands + 4);
            if (npairs < 0) throw new ClassFormatError("Invalid lookupswitch at offset " + pc);
            end = operands + 8 + 8L * npairs;
        }
        if (end > bytecode.length) throw new ClassFormatError("Truncated instruction at offset " + pc);
        return (int) end - pc;
    }

    private static int
    readInt(byte[] ba, int offset) {
        return (
            (0xff & ba[offset]) << 24
            | (0xff & ba[offset + 1]) << 16
            | (0xff & ba[offset + 2]) << 8
            | (0xff & ba[offset + 3])
        );
    }

    /**
     * Adds the <var>lines</var> to the lines of the given constant pool entry.
     *
     * @return Whether lines were added
     */
    private static boolean
    addAll(Map<Integer, SortedSet<Integer>> map, int constantPoolIndex, SortedSet<Integer> lines) {
        SortedSet<Integer> l = (SortedSet<Integer>) map.get(Integer.valueOf(constantPoolIndex));
        if (l == null) map.put(Integer.valueOf(constantPoolIndex), (l = new TreeSet<Integer>()));
        return l.addAll(lines);
    }

    private MemberReference
    getConstantMemberReference(int index) {

        int tag = this.cpTags[index];

        MemberReference.Kind kind = (
            tag == ClassFileReader.CONSTANT_FIELDREF            ? MemberReference.Kind.FIELD :
            tag == ClassFileReader.CONSTANT_METHODREF           ? MemberReference.Kind.METHOD :
            tag == ClassFileReader.CONSTANT_INTERFACE_METHODREF ? MemberReference.Kind.INTERFACE_METHOD :
            null
        );
        if (kind == null) {
            throw new ClassFormatError("Constant pool entry " + index + " is not a field or method reference");
        }

        int nameAndTypeIndex = this.checkConstant(this.cpValue2[index], ClassFileReader.CONSTANT_NAME_AND_TYPE);
        return new MemberReference(
            kind,
            this.getConstantClassName(this.cpValue1[index]),
            this.getConstantUtf8(this.cpValue1[nameAndTypeIndex]),
            this.getConstantUtf8(this.cpValue2[nameAndTypeIndex])
        );
    }

    private MethodHandleReference
    getConstantMethodHandle(int index) {

        this.checkConstant(index, ClassFileReader.CONSTANT_METHOD_HANDLE);

        int referenceKind = this.cpValue1[index];
        if (referenceKind < 1 || referenceKind > 9) {
            throw new ClassFormatError("Invalid method handle reference kind " + referenceKind);
        }

        int referenceIndex = this.cpValue2[index];
        if (referenceIndex < 1 || referenceIndex >= this.cpTags.length) {
            throw new ClassFormatError("Invalid constant pool index " + referenceIndex);
        }

        return new MethodHandleReference(referenceKind, this.getConstantMemberReference(referenceIndex));
    }

    private String
    getConstantClassName(int index) {
        return this.getConstantUtf8(this.cpValue1[this.checkConstant(index, ClassFileReader.CONSTANT_CLASS)]).replace(
            '/',
            '.'
        );
    }

    private String
    getConstantUtf8(int index) {
        String result = this.cpUtf8s[this.checkConstant(index, ClassFileReader.CONSTANT_UTF8)];
        assert result != null;
        return result;
    }

    /**
     * @return                 The <var>index</var>
     * @throws ClassFormatError The <var>index</var> is out of range, or the entry does not have the
     *                          <var>expectedTag</var>
     */
    private int
    checkConstant(int index, int expectedTag) {
        if (index < 1 || index >= this.cpTags.length || this.cpTags[index] != expectedTag) {
            throw new ClassFormatError(
                "Constant pool index " + index + " does not refer to an entry with tag " + expectedTag
            );
        }
        return index;
    }

    private static void
    skip(DataInputStream dis, int n) throws IOException {
        if (n < 0 || dis.skipBytes(n) != n) throw new EOFException();
    }

    /**
     * Reads the data of an attribute.
     *
     * @return A stream that reads exactly the <var>attributeLength</var> bytes of the attribute data
     */
    private static DataInputStream
    readAttribute(DataInputStream dis, int attributeLength) throws IOException {
        if (attributeLength < 0) throw new ClassFormatError("Invalid attribute length " + attributeLength);
        byte[] attributeData = new byte[attributeLength];
        dis.readFully(attributeData);
        return new DataInputStream(new ByteArrayInputStream(attributeData));
    }
}
