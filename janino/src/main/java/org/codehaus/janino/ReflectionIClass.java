
/*
 * Janino - An embedded Java[TM] compiler
 *
 * Copyright (c) 2001-2010 Arno Unkrig. All rights reserved.
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

package org.codehaus.janino;

import java.lang.annotation.Annotation;
import java.lang.ref.SoftReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.InternalCompilerException;
import org.codehaus.commons.compiler.Location;
import org.codehaus.commons.nullanalysis.Nullable;

/**
 * Wraps a {@link java.lang.Class} in an {@link org.codehaus.janino.IClass}.
 * <p>
 *   By default, there is one {@link ReflectionIClass} per {@link Class}, which all compilations share (see {@link
 *   #of(Class)}); it determines the classes that it references (superclass, interfaces, the types of its members)
 *   through the reflected {@link Class} objects, like the JVM links them. The system property {@code
 *   org.codehaus.janino.sharedIClassCache=false} switches that off; then each {@link ClassLoaderIClassLoader} creates
 *   its own {@link ReflectionIClass}es, which determine the classes that they reference by name through that {@link
 *   IClassLoader}.
 * </p>
 */
class ReflectionIClass extends IClass {

    /**
     * Whether the {@link ReflectionIClass}es are shared between compilations; see {@link #of(Class)}.
     */
    static final boolean SHARED = !"false".equals(System.getProperty("org.codehaus.janino.sharedIClassCache"));

    /**
     * The shared {@link ReflectionIClass} of each {@link Class}. The holder and the {@link SoftReference} are classes
     * of the JDK, so that the entry of a JDK class (e.g. {@link String}) does not keep the class loader of JANINO
     * alive, which may be a different one, e.g. in an application server. A cleared reference is replaced only when no
     * object references the {@link ReflectionIClass} strongly any more; thus a compilation never sees two {@link
     * ReflectionIClass}es for the same {@link Class}.
     */
    private static final ClassValue<AtomicReference<SoftReference<ReflectionIClass>>>
    SHARED_ICLASSES = new ClassValue<AtomicReference<SoftReference<ReflectionIClass>>>() {

        @Override protected AtomicReference<SoftReference<ReflectionIClass>>
        computeValue(Class<?> type) { return new AtomicReference<SoftReference<ReflectionIClass>>(); }
    };

    private final Class<?>               clazz;
    @Nullable private final IClassLoader iClassLoader;

    /**
     * @param iClassLoader Loads the other {@link IClass}es on {@code get...()}; {@code null} for a shared {@link
     *                     ReflectionIClass}, which gets them through {@link #of(Class)}
     */
    ReflectionIClass(Class<?> clazz, @Nullable IClassLoader iClassLoader) {
        this.clazz        = clazz;
        clazz.getTypeParameters();
        this.iClassLoader = iClassLoader;
    }

    /**
     * Returns the {@link IClass} of the <var>clazz</var> that all compilations share: {@link IClass#INT} etc. for a
     * primitive type, the {@link IClass#getArrayIClass(IClass) array type} of the shared component type for an array
     * class, and the shared {@link ReflectionIClass} otherwise. Its lifetime is that of the <var>clazz</var>; it does
     * not keep a class loader alive. It keeps copies of the {@link Method}s and {@link Field}s of the <var>clazz</var>
     * while it is used.
     */
    static IClass
    of(Class<?> clazz) {

        if (clazz.isPrimitive()) {
            return (
                clazz == void.class    ? IClass.VOID    :
                clazz == byte.class    ? IClass.BYTE    :
                clazz == char.class    ? IClass.CHAR    :
                clazz == double.class  ? IClass.DOUBLE  :
                clazz == float.class   ? IClass.FLOAT   :
                clazz == int.class     ? IClass.INT     :
                clazz == long.class    ? IClass.LONG    :
                clazz == short.class   ? IClass.SHORT   :
                clazz == boolean.class ? IClass.BOOLEAN :
                ReflectionIClass.unexpected(clazz)
            );
        }

        Class<?> componentType = clazz.getComponentType();
        if (componentType != null) {
            return ReflectionIClass.of(componentType).getArrayIClass(ReflectionIClass.of(Object.class));
        }

        AtomicReference<SoftReference<ReflectionIClass>>
        holder = (AtomicReference<SoftReference<ReflectionIClass>>) ReflectionIClass.SHARED_ICLASSES.get(clazz);
        for (;;) {
            @Nullable SoftReference<ReflectionIClass> ref = (SoftReference<ReflectionIClass>) holder.get();

            @Nullable ReflectionIClass result = ref == null ? null : (ReflectionIClass) ref.get();
            if (result != null) return result;

            result = new ReflectionIClass(clazz, null);
            if (holder.compareAndSet(ref, new SoftReference<ReflectionIClass>(result))) return result;
        }
    }

    private static IClass
    unexpected(Class<?> clazz) { throw new InternalCompilerException("Unexpected primitive type \"" + clazz + "\""); }

    /**
     * @return Whether the <var>iClass</var> is a {@link ReflectionIClass} that all compilations share
     */
    static boolean
    isShared(IClass iClass) {
        return iClass instanceof ReflectionIClass && ((ReflectionIClass) iClass).iClassLoader == null;
    }

    /**
     * The members of a loaded class never change, and all its supertypes are loaded classes.
     */
    @Override boolean
    hasFixedHierarchy() { return true; }

    @Override public ITypeVariable[]
    getITypeVariables2() {

        final TypeVariable<?>[] tps = this.clazz.getTypeParameters();

        ITypeVariable[] result = new ITypeVariable[tps.length];
        for (int i = 0; i < result.length; i++) result[i] = this.typeVariableToITypeVariable(tps[i]);

        return result;
    }

    private ITypeVariable
    typeVariableToITypeVariable(final TypeVariable<?> tv) {

        return new ITypeVariable() {

            @Override public String
            getName() { return tv.getName(); }

            @Override public ITypeVariableOrIClass[]
            getBounds() throws CompileException {
                IType[] tmp = ReflectionIClass.this.typesToITypes(tv.getBounds());
                return (ITypeVariableOrIClass[]) Arrays.copyOf(tmp, tmp.length, ITypeVariableOrIClass[].class);
            }
        };
    }

    private IType[]
    typesToITypes(Type[] types) throws CompileException {
        IType[] result = new IType[types.length];
        for (int i = 0; i < result.length; i++) result[i] = this.typeToIType(types[i]);
        return result;
    }

    private IType
    typeToIType(Type type) throws CompileException {
        if (type instanceof Class) {
            IClassLoader icl = this.iClassLoader;
            if (icl == null) return ReflectionIClass.of((Class<?>) type);

            IClass iClass;
            try {
                iClass = icl.loadIClass(Descriptor.fromClassName(((Class<?>) type).getName()));
            } catch (ClassNotFoundException cnfe) {
                throw new CompileException("Loading \"" + type + "\"", null, cnfe);
            }
            if (iClass == null) throw new CompileException("Could not load \"" + type + "\"", null);
            return iClass;
        } else
        if (type instanceof GenericArrayType) {
            throw new AssertionError("NYI");
        } else
        if (type instanceof ParameterizedType) {
            throw new AssertionError("NYI");
        } else
        if (type instanceof TypeVariable) {
            throw new AssertionError("NYI");
        } else
        if (type instanceof WildcardType) {
            throw new AssertionError("NYI");
        } else
        {
            throw new AssertionError(type.getClass());
        }
    }

    @Override protected IConstructor[]
    getDeclaredIConstructors2() {
        Constructor<?>[] constructors = this.clazz.getDeclaredConstructors();
        IConstructor[]   result       = new IConstructor[constructors.length];
        for (int i = 0; i < constructors.length; ++i) {
            result[i] = new ReflectionIConstructor(constructors[i]);
        }
        return result;
    }

    @Override protected IMethod[]
    getDeclaredIMethods2() {
        Method[] methods  = this.clazz.getDeclaredMethods();

        if (methods.length == 0 && this.clazz.isArray()) {

            // Arrays have ONE single method: "Object clone()".
            return new IMethod[] { new IMethod() {
                @Override public IAnnotation[] getAnnotations()       { return new IAnnotation[0]; }
                @Override public Access        getAccess()            { return Access.PUBLIC;      }
                @Override public boolean       isStatic()             { return false;              }
                @Override public boolean       isAbstract()           { return false;              }
                @Override public String        getName()              { return "clone";            }
                @Override public IClass[]      getParameterTypes2()   { return new IClass[0];      }
                @Override public boolean       isVarargs()            { return false;              }
                @Override public IClass[]      getThrownExceptions2() { return new IClass[0];      }

                @Override public IClass
                getReturnType() {
                    IClassLoader icl = ReflectionIClass.this.iClassLoader;
                    return icl == null ? ReflectionIClass.of(Object.class) : icl.TYPE_java_lang_Object;
                }
            } };
        }

        return this.methodsToIMethods(methods);
    }

    @Override protected IField[]
    getDeclaredIFields2() { return this.fieldsToIFields(this.clazz.getDeclaredFields()); }

    @Override protected IClass[]
    getDeclaredIClasses2() { return this.classesToIClasses(this.clazz.getDeclaredClasses()); }

    @Override @Nullable protected IClass
    getDeclaringIClass2() {

        Class<?> declaringClass = this.clazz.getDeclaringClass();
        return declaringClass == null ? null : this.classToIClass(declaringClass);
    }

    @Override @Nullable protected IClass
    getOuterIClass2() throws CompileException {

        if (Modifier.isStatic(this.clazz.getModifiers())) return null;

        return this.getDeclaringIClass();
    }

    @Override @Nullable protected IClass
    getSuperclass2() {

        Class<?> superclass = this.clazz.getSuperclass();
        return superclass == null ? null : this.classToIClass(superclass);
    }

    @Override @Nullable protected IClass
    getComponentType2() {
        Class<?> componentType = this.clazz.getComponentType();
        return componentType == null ? null : this.classToIClass(componentType);
    }

    @Override protected IClass[] getInterfaces2() { return this.classesToIClasses(this.clazz.getInterfaces()); }
    @Override protected String   getDescriptor2() { return Descriptor.fromClassName(this.clazz.getName());     }

    @Override public Access  getAccess()   { return ReflectionIClass.modifiers2Access(this.clazz.getModifiers()); }
    @Override public boolean isFinal()     { return Modifier.isFinal(this.clazz.getModifiers());                  }
    @Override public boolean isEnum()      { return this.clazz.isEnum();                                          }
    @Override public boolean isInterface() { return this.clazz.isInterface();                                     }
    @Override public boolean isAbstract()  { return Modifier.isAbstract(this.clazz.getModifiers());               }
    @Override public boolean isArray()     { return this.clazz.isArray();                                         }

    @Override public boolean
    isPrimitive() { return this.clazz.isPrimitive(); }

    @Override public boolean
    isPrimitiveNumeric() {
        return (
            this.clazz == byte.class
            || this.clazz == short.class
            || this.clazz == int.class
            || this.clazz == long.class
            || this.clazz == char.class
            || this.clazz == float.class
            || this.clazz == double.class
        );
    }

    @Override public IAnnotation[]
    getIAnnotations2() throws CompileException {

        Annotation[] as = this.clazz.getAnnotations();
        if (as.length == 0) return IClass.NO_ANNOTATIONS;

        IAnnotation[] result = new IAnnotation[as.length];
        for (int i = 0; i < as.length; i++) {
            final Annotation a = as[i];

            // Get annotation type IClass.
            final Class<? extends Annotation> annotationType = a.annotationType();
            final IClass                      annotationTypeIClass;
            final IClassLoader                icl                  = ReflectionIClass.this.iClassLoader;
            try {
                annotationTypeIClass = (
                    icl == null
                    ? ReflectionIClass.of(annotationType)
                    : icl.loadIClass(Descriptor.fromClassName(annotationType.getName()))
                );
            } catch (ClassNotFoundException cnfe) {
                throw new CompileException(
                    "Loading annotation type",
                    null, // location
                    cnfe
                );
            }
            if (annotationTypeIClass == null) {
                throw new CompileException("Could not load \"" + annotationType.getName() + "\"", null);
            }

            result[i] = new IAnnotation() {

                @Override public IClass
                getAnnotationType() { return annotationTypeIClass; }

                @Override public Object
                getElementValue(String name) throws CompileException {
                    try {
                        Object v = a.getClass().getMethod(name).invoke(a);

                        if (!Enum.class.isAssignableFrom(v.getClass())) return v;

                        Class<?> enumClass = v.getClass();

                        String enumConstantName = (String) enumClass.getMethod("name").invoke(v);

                        IClass enumIClass = ReflectionIClass.this.classToIClass(enumClass);

                        IField enumConstField = enumIClass.getDeclaredIField(enumConstantName);
                        if (enumConstField == null) {
                            throw new CompileException((
                                "Enum \""
                                + enumIClass
                                + "\" has no constant \""
                                + enumConstantName
                                + ""
                            ), null);
                        }
                        return enumConstField;
                    } catch (NoSuchMethodException e) {
                        throw new CompileException(
                            "Annotation \"" + annotationType.getName() + "\" has no element \"" + name + "\"",
                            null
                        );
                    } catch (Exception e) {
                        throw new AssertionError(e);
                    }
                }

                @Override public String
                toString() { return "@" + annotationTypeIClass; }
            };
        }

        return result;
    }

    /**
     * @return The underlying {@link Class java.lang.Class}
     */
    public Class<?>
    getClazz() { return this.clazz; }

    /**
     * @return E.g. {@code "int"}, {@code "int[][]"}, {@code "pkg1.pkg2.Outer$Inner[]"}
     */
    @Override public String
    toString() {
        int      brackets = 0;
        Class<?> c        = this.clazz;
        while (c.isArray()) {
            ++brackets;
            c = c.getComponentType();
        }
        String s = c.getName();

        // Must not strip "java.lang.", because some pieces of code rely on "toString()" returning the COMPLETE
        // qualified name.
//        if (s.startsWith("java.lang.")) s = s.substring(10);

        while (brackets-- > 0) s += "[]";
        return s;
    }

    private
    class ReflectionIConstructor extends IConstructor {

        ReflectionIConstructor(Constructor<?> constructor) { this.constructor = constructor; }

        // Implement IMember.
        @Override public Access
        getAccess() { return ReflectionIClass.modifiers2Access(this.constructor.getModifiers()); }

        @Override public IAnnotation[]
        getAnnotations() { return new IAnnotation[0]; }

        @Override public boolean
        isVarargs() {
            // TRANSIENT is identical with VARARGS.
            return Modifier.isTransient(this.constructor.getModifiers());
        }

        // Implement "IConstructor".
        @Override public IClass[]
        getParameterTypes2() throws CompileException {
            IClass[] parameterTypes = ReflectionIClass.this.classesToIClasses(this.constructor.getParameterTypes());

            // The JAVADOC of java.lang.reflect.Constructor does not document it, but "getParameterTypes()" includes
            // the synthetic "enclosing instance" parameter.
            IClass outerClass = ReflectionIClass.this.getOuterIClass();
            if (outerClass != null) {
                if (parameterTypes.length < 1) {
                    throw new CompileException(
                        "Constructor \"" + this.constructor + "\" lacks synthetic enclosing instance parameter",
                        null
                    );
                }
                if (parameterTypes[0] != outerClass) {
                    throw new CompileException((
                        "Enclosing instance parameter of constructor \""
                        + this.constructor
                        + "\" has wrong type -- \""
                        + parameterTypes[0]
                        + "\" vs. \""
                        + outerClass
                        + "\""
                    ), null);
                }
                IClass[] tmp = new IClass[parameterTypes.length - 1];
                System.arraycopy(parameterTypes, 1, tmp, 0, tmp.length);
                parameterTypes = tmp;
            }

            return parameterTypes;
        }

        @Override public MethodDescriptor
        getDescriptor2() {
            Class<?>[] parameterTypes       = this.constructor.getParameterTypes();
            String[]   parameterDescriptors = new String[parameterTypes.length];
            for (int i = 0; i < parameterDescriptors.length; ++i) {
                parameterDescriptors[i] = Descriptor.fromClassName(parameterTypes[i].getName());
            }
            return new MethodDescriptor(Descriptor.VOID, parameterDescriptors);
        }

        @Override public IClass[]
        getThrownExceptions2() {
            return ReflectionIClass.this.classesToIClasses(this.constructor.getExceptionTypes());
        }

        final Constructor<?> constructor;
    }

    public
    class ReflectionIMethod extends IMethod {

        ReflectionIMethod(Method method) { this.method = method; }

        // Implement IMember.
        @Override public Access
        getAccess() { return ReflectionIClass.modifiers2Access(this.method.getModifiers()); }

        @Override public IAnnotation[]
        getAnnotations() { return new IAnnotation[0]; }

        // Implement "IMethod".
        @Override public String
        getName() { return this.method.getName(); }

        @Override public boolean
        isVarargs() {

            // VARARGS is identical with TRANSIENT.
            return Modifier.isTransient(this.method.getModifiers());
        }

        @Override public IClass[]
        getParameterTypes2() { return ReflectionIClass.this.classesToIClasses(this.method.getParameterTypes()); }

        @Override public boolean
        isStatic() { return Modifier.isStatic(this.method.getModifiers()); }

        @Override public boolean
        isAbstract() { return Modifier.isAbstract(this.method.getModifiers()); }

        @Override boolean
        isFinal() { return Modifier.isFinal(this.method.getModifiers()); }

        @Override public IClass
        getReturnType() { return ReflectionIClass.this.classToIClass(this.method.getReturnType()); }

        @Override public IClass[]
        getThrownExceptions2() { return ReflectionIClass.this.classesToIClasses(this.method.getExceptionTypes()); }

        private final Method method;
    }

    private
    class ReflectionIField extends IField {

        ReflectionIField(Field field) { this.field = field; }

        // Implement IMember.
        @Override public Access
        getAccess() { return ReflectionIClass.modifiers2Access(this.field.getModifiers()); }

        @Override public IAnnotation[]
        getAnnotations() { return new IAnnotation[0]; }

        // Implement "IField".

        @Override public String
        getName() { return this.field.getName(); }

        @Override public boolean
        isStatic() { return Modifier.isStatic(this.field.getModifiers()); }

        @Override public IClass
        getType() { return ReflectionIClass.this.classToIClass(this.field.getType()); }

        @Override public String
        toString() {
            return Descriptor.toString(this.getDeclaringIClass().getDescriptor()) + "." + this.getName();
        }

        /**
         * This implementation of {@link IClass.IField#getConstantValue()} is not completely correct:
         * <ul>
         *   <li>
         *     It treats non-static fields as non-constant
         *   </li>
         *   <li>
         *     Even fields with a <em>non-constant</em> initializer are identified as constant. (The value of that
         *     field may be different in a different JVM instance -- the classical example is {@link
         *     java.io.File#separator}.)
         *   </li>
         * </ul>
         * <p>
         *   Notice that enum constants are <em>not</em> constant expression (despite the similarity of names).
         * </p>
         */
        @Override public Object
        getConstantValue() throws CompileException {
            int      mod   = this.field.getModifiers();
            Class<?> clazz = this.field.getType();
            if (
                Modifier.isStatic(mod)
                && Modifier.isFinal(mod)
                && (clazz.isPrimitive() || clazz == String.class)
            ) {
                try {
                    return this.field.get(null);
                } catch (IllegalAccessException ex) {

                    // A public field of a class that is not public, e.g. a constant that a public subclass inherits
                    // (issue #154): Reflection checks the accessibility of the declaring class, the compiler checks
                    // that of the class through which the field is accessed.
                    if (Modifier.isPublic(mod)) {
                        try {
                            this.field.setAccessible(true);
                            return this.field.get(null);
                        } catch (Exception ex2) { // SUPPRESS CHECKSTYLE IllegalCatch
                            ;
                        }
                    }
                    throw new CompileException( // SUPPRESS CHECKSTYLE AvoidHidingCause
                        "Field \"" + this.field.getName() + "\" is not accessible",
                        (Location) null
                    );
                }
            }
            return IClass.NOT_CONSTANT;
        }

        final Field field;
    }

    /**
     * Gets the shared {@link IClass} of the <var>c</var>, or, if this {@link ReflectionIClass} is not shared, loads the
     * {@link Class} by name through the {@link IClassLoader} to ensure unique {@link IClass}es.
     */
    private IClass
    classToIClass(Class<?> c) {
        IClassLoader icl = this.iClassLoader;
        if (icl == null) return ReflectionIClass.of(c);

        IClass iClass;
        try {
            iClass = icl.loadIClass(Descriptor.fromClassName(c.getName()));
        } catch (ClassNotFoundException ex) {
            throw new InternalCompilerException("Loading IClass \"" + c.getName() + "\": " + ex);
        }
        if (iClass == null) {
            throw new InternalCompilerException((
                "Cannot load class \"" + c.getName() + "\" through the given ClassLoader"
            ));
        }
        return iClass;
    }

    /**
     * @see #classToIClass(Class)
     */
    private IClass[]
    classesToIClasses(Class<?>[] cs) {

        IClass[] result = new IClass[cs.length];
        for (int i = 0; i < cs.length; ++i) result[i] = this.classToIClass(cs[i]);

        return result;
    }

    private IMethod[]
    methodsToIMethods(Method[] methods) {

        IMethod[] result = new IMethod[methods.length];
        for (int i = 0; i < result.length; i++) result[i] = new ReflectionIMethod(methods[i]);

        return result;
    }

    private IField[]
    fieldsToIFields(Field[] fields) {

        IField[] result = new IField[fields.length];
        for (int i = 0; i < fields.length; ++i) result[i] = new ReflectionIField(fields[i]);

        return result;
    }

    private static Access
    modifiers2Access(int modifiers) {
        return (
            Modifier.isPrivate(modifiers)   ? Access.PRIVATE   :
            Modifier.isProtected(modifiers) ? Access.PROTECTED :
            Modifier.isPublic(modifiers)    ? Access.PUBLIC    :
            Access.DEFAULT
        );
    }
}
