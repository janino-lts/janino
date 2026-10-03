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
import java.util.Collections;
import java.util.List;

import org.codehaus.commons.nullanalysis.Nullable;

/**
 * A minimal parser for Java class files, as far as needed to determine which classes and members a class refers to.
 * <p>
 *   Parses the constant pool (all tags defined up to Java 25), the name, superclass and interfaces of the class, the
 *   access flags, names and descriptors of its fields and methods, and the {@code BootstrapMethods} attribute. All
 *   other attributes (including the {@code Code} attribute) are skipped.
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

    /**
     * A field or method declared by the class.
     */
    public static final
    class MemberDeclaration {

        private final int    accessFlags;
        private final String name;
        private final String descriptor;

        MemberDeclaration(int accessFlags, String name, String descriptor) {
            this.accessFlags = accessFlags;
            this.name        = name;
            this.descriptor  = descriptor;
        }

        /**
         * @return The access flags, e.g. {@link java.lang.reflect.Modifier#NATIVE}
         */
        public int getAccessFlags() { return this.accessFlags; }

        public String getName() { return this.name; }

        public String getDescriptor() { return this.descriptor; }

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
        private final int                   argumentCount;

        BootstrapMethod(MethodHandleReference method, int argumentCount) {
            this.method        = method;
            this.argumentCount = argumentCount;
        }

        public MethodHandleReference getMethod() { return this.method; }

        public int getArgumentCount() { return this.argumentCount; }

        @Override public String
        toString() { return "BootstrapMethod(" + this.method + ", " + this.argumentCount + " arguments)"; }
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

    private final List<String>                interfaceNames   = new ArrayList<String>();
    private final List<MemberDeclaration>     fields           = new ArrayList<MemberDeclaration>();
    private final List<MemberDeclaration>     methods          = new ArrayList<MemberDeclaration>();
    private final List<String>                classReferences  = new ArrayList<String>();
    private final List<MemberReference>       memberReferences = new ArrayList<MemberReference>();
    private final List<MethodHandleReference> methodHandles    = new ArrayList<MethodHandleReference>();
    private final List<DynamicReference>      dynamics         = new ArrayList<DynamicReference>();
    private final List<BootstrapMethod>       bootstrapMethods = new ArrayList<BootstrapMethod>();

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
        return this.bootstrapMethods.get(dynamicReference.getBootstrapMethodIndex());
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

        this.readMembers(dis, this.fields);
        this.readMembers(dis, this.methods);

        for (int i = dis.readUnsignedShort(); i > 0; i--) {
            String attributeName   = this.getConstantUtf8(dis.readUnsignedShort());
            int    attributeLength = dis.readInt();
            if (!"BootstrapMethods".equals(attributeName)) {
                ClassFileReader.skip(dis, attributeLength);
                continue;
            }

            if (attributeLength < 0) throw new ClassFormatError("Invalid attribute length " + attributeLength);
            byte[] attributeData = new byte[attributeLength];
            dis.readFully(attributeData);

            DataInputStream dis2 = new DataInputStream(new ByteArrayInputStream(attributeData));
            this.readBootstrapMethods(dis2);
            if (dis2.read() != -1) throw new ClassFormatError("Invalid length of the BootstrapMethods attribute");
        }

        if (dis.read() != -1) throw new ClassFormatError("Extra bytes after the end of the class file");

        this.resolveConstantPool();
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

    private void
    readMembers(DataInputStream dis, List<MemberDeclaration> result) throws IOException {
        for (int i = dis.readUnsignedShort(); i > 0; i--) {
            int    accessFlags = dis.readUnsignedShort();
            String name        = this.getConstantUtf8(dis.readUnsignedShort());
            String descriptor  = this.getConstantUtf8(dis.readUnsignedShort());
            for (int j = dis.readUnsignedShort(); j > 0; j--) {
                dis.readUnsignedShort();
                ClassFileReader.skip(dis, dis.readInt());
            }
            result.add(new MemberDeclaration(accessFlags, name, descriptor));
        }
    }

    private void
    readBootstrapMethods(DataInputStream dis) throws IOException {
        for (int i = dis.readUnsignedShort(); i > 0; i--) {
            MethodHandleReference method        = this.getConstantMethodHandle(dis.readUnsignedShort());
            int                   argumentCount = dis.readUnsignedShort();
            for (int j = 0; j < argumentCount; j++) {
                int index = dis.readUnsignedShort();
                if (index == 0 || index >= this.cpTags.length || this.cpTags[index] == 0) {
                    throw new ClassFormatError("Invalid bootstrap method argument index " + index);
                }
            }
            this.bootstrapMethods.add(new BootstrapMethod(method, argumentCount));
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
                break;

            default:
                ;
            }
        }
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
}
