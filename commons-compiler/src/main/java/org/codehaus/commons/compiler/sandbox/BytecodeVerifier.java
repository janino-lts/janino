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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

import org.codehaus.commons.compiler.sandbox.ClassFileReader.DynamicReference;
import org.codehaus.commons.compiler.sandbox.ClassFileReader.MemberDeclaration;
import org.codehaus.commons.compiler.sandbox.ClassFileReader.MemberReference;
import org.codehaus.commons.compiler.util.Privileged;
import org.codehaus.commons.nullanalysis.Nullable;

/**
 * Verifies that compiled classes use only the fields, methods and constructors that a {@link SandboxPolicy} allows.
 * <p>
 *   For each class, the verifier checks
 * </p>
 * <ul>
 *   <li>its superclass and interfaces (see {@link SandboxPolicy#isSubclassingAllowed(String)}),</li>
 *   <li>that it declares no {@code native} methods,</li>
 *   <li>
 *     that it declares no {@code finalize()} method (which the JVM would call outside of any {@link
 *     SandboxExecutor}),
 *   </li>
 *   <li>
 *     every field and method reference in its constant pool (including those referenced by method handles), and
 *   </li>
 *   <li>the bootstrap method of every {@code invokedynamic} instruction and dynamically-computed constant.</li>
 * </ul>
 * <p>
 *   Each field and method reference is resolved as the JVM would resolve it (JVMS 5.4.3), and the policy is applied
 *   to the <em>declaring</em> class of the resolved member. Members declared by the verified classes themselves are
 *   always allowed, as are the members that compilers reference implicitly for language features like string
 *   concatenation, autoboxing, enhanced FOR statements, ASSERT statements, enums, lambdas and records, and the
 *   resource limit checks of {@link Guard}. References that cannot be resolved are rejected.
 * </p>
 * <p>
 *   Pure type references (e.g. for casts, {@code instanceof}, class literals and array creation) are not checked,
 *   because they do not execute any code of the referenced type.
 * </p>
 */
public final
class BytecodeVerifier {

    /**
     * Classes that every class may extend without a policy rule.
     */
    private static final Set<String> IMPLICITLY_EXTENDABLE = BytecodeVerifier.set(
        "java.lang.Enum",
        "java.lang.Object",
        "java.lang.Record"
    );

    /**
     * Interfaces that every class may implement without a policy rule.
     */
    private static final Set<String> IMPLICITLY_IMPLEMENTABLE = BytecodeVerifier.set(
        "java.lang.annotation.Annotation"
    );

    /**
     * The bootstrap methods that compilers use ("className#methodName").
     */
    private static final Set<String> BOOTSTRAP_METHODS = BytecodeVerifier.set(
        "java.lang.invoke.LambdaMetafactory#altMetafactory",
        "java.lang.invoke.LambdaMetafactory#metafactory",
        "java.lang.invoke.StringConcatFactory#makeConcat",
        "java.lang.invoke.StringConcatFactory#makeConcatWithConstants",
        "java.lang.runtime.ObjectMethods#bootstrap",
        "java.lang.runtime.SwitchBootstraps#enumSwitch",
        "java.lang.runtime.SwitchBootstraps#typeSwitch"
    );

    /**
     * The members that compilers reference implicitly ("className#memberName"), plus the {@link #BOOTSTRAP_METHODS}.
     * (The bootstrap methods also appear as method references in the constant pool.)
     */
    private static final Set<String> IMPLICIT_MEMBERS = BytecodeVerifier.union(
        BytecodeVerifier.BOOTSTRAP_METHODS,

        // Constructors of "Object", "Enum" and "Record" (super constructor invocations).
        "java.lang.Enum#<init>",
        "java.lang.Object#<init>",
        "java.lang.Record#<init>",

        // String concatenation.
        "java.lang.String#concat",
        "java.lang.String#valueOf",
        "java.lang.StringBuilder#<init>",
        "java.lang.StringBuilder#append",
        "java.lang.StringBuilder#toString",

        // Autoboxing and auto-unboxing.
        "java.lang.Boolean#booleanValue",
        "java.lang.Boolean#valueOf",
        "java.lang.Byte#byteValue",
        "java.lang.Byte#valueOf",
        "java.lang.Character#charValue",
        "java.lang.Character#valueOf",
        "java.lang.Double#doubleValue",
        "java.lang.Double#valueOf",
        "java.lang.Float#floatValue",
        "java.lang.Float#valueOf",
        "java.lang.Integer#intValue",
        "java.lang.Integer#valueOf",
        "java.lang.Long#longValue",
        "java.lang.Long#valueOf",
        "java.lang.Short#shortValue",
        "java.lang.Short#valueOf",

        // ASSERT statements.
        "java.lang.AssertionError#<init>",
        "java.lang.Class#desiredAssertionStatus",

        // Enhanced FOR statements.
        "java.lang.Iterable#iterator",
        "java.util.Iterator#hasNext",
        "java.util.Iterator#next",

        // SWITCH statements on strings and enums, and pattern matching.
        "java.lang.Enum#ordinal",
        "java.lang.IncompatibleClassChangeError#<init>",
        "java.lang.MatchException#<init>",
        "java.lang.String#equals",
        "java.lang.String#hashCode",

        // Enum declarations ("values()" and "valueOf()").
        "java.lang.Enum#valueOf",
        "java.lang.System#arraycopy",

        // TRY-with-resources statements.
        "java.lang.Throwable#addSuppressed",

        // Null checks generated by JAVAC (e.g. for qualified class instance creation and method references).
        "java.lang.Object#getClass",
        "java.util.Objects#requireNonNull",

        // The resource limit checks that JANINO inserts (see "Guard"); the other members of "Guard" are not public.
        "org.codehaus.commons.compiler.sandbox.Guard#arrayLength",
        "org.codehaus.commons.compiler.sandbox.Guard#arrayLengthStrict",
        "org.codehaus.commons.compiler.sandbox.Guard#newArray",
        "org.codehaus.commons.compiler.sandbox.Guard#newArrayStrict",
        "org.codehaus.commons.compiler.sandbox.Guard#tick",
        "org.codehaus.commons.compiler.sandbox.Guard#tickStrict"
    );

    private final SandboxPolicy policy;

    /**
     * @param policy The policy to verify classes against
     */
    public
    BytecodeVerifier(SandboxPolicy policy) { this.policy = policy; }

    /**
     * Verifies a set of classes that were compiled together, e.g. all classes generated by one compilation.
     *
     * @param classes         The class files to verify; the keys are ignored (they may be class names like {@code
     *                        "pkg.Foo"} or resource names like {@code "pkg/Foo.class"})
     * @param hostClassLoader Loads the classes that the verified classes refer to (other than themselves); typically
     *                        the parent class loader of the class loader that will define the verified classes
     * @return                All violations, ordered by source file and line (violations without a line number
     *                        first); empty if the classes comply with the policy
     * @throws ClassFormatError One of the <var>classes</var> is not a valid class file
     */
    public List<SandboxViolation>
    verify(Map<String, byte[]> classes, ClassLoader hostClassLoader) {
        return this.verify2(classes, hostClassLoader, null, Collections.<String, byte[]>emptyMap());
    }

    /**
     * Verifies classes that a class loader is about to define, typically a class loader that compiles classes on
     * demand (like a {@code JavaSourceClassLoader}), and thus defines the generated classes one at a time.
     * <p>
     *   Classes that the <var>definingClassLoader</var> has already defined, and the <var>pendingClasses</var>, are
     *   regarded as generated classes, like the <var>classes</var> themselves; i.e. their members are allowed, and
     *   they may be extended. This is safe as long as the class loader verifies every class before it defines it.
     * </p>
     *
     * @param classes             The class files to verify; the keys are ignored
     * @param definingClassLoader The class loader that will define the <var>classes</var>; classes that the verified
     *                            classes refer to (other than themselves and the <var>pendingClasses</var>) are loaded
     *                            through it
     * @param pendingClasses      Class name =&gt; class file; classes that the <var>definingClassLoader</var>
     *                            generated, but has not yet defined
     * @return                    All violations, ordered by source file and line (violations without a line
     *                            number first); empty if the classes comply with the policy
     * @throws ClassFormatError   One of the <var>classes</var> is not a valid class file
     */
    public List<SandboxViolation>
    verify(Map<String, byte[]> classes, ClassLoader definingClassLoader, Map<String, byte[]> pendingClasses) {
        return this.verify2(classes, definingClassLoader, definingClassLoader, pendingClasses);
    }

    private List<SandboxViolation>
    verify2(
        Map<String, byte[]>   classes,
        ClassLoader           classLoader,
        @Nullable ClassLoader generatedClassLoader,
        Map<String, byte[]>   pendingClasses
    ) {

        // Parse all class files; sort them by name to make the order of the violations deterministic.
        Map<String, ClassFileReader> classFiles = new TreeMap<String, ClassFileReader>();
        for (byte[] classFile : classes.values()) {
            ClassFileReader cfr = new ClassFileReader(classFile);
            classFiles.put(cfr.getClassName(), cfr);
        }

        Run run = new Run(classFiles, classLoader, generatedClassLoader, pendingClasses);
        for (ClassFileReader cfr : classFiles.values()) run.verify(cfr);

        // Order the violations by source file and line, so that e.g. the violations in an anonymous class appear
        // between those of the enclosing class. (A stable insertion sort; the lists are short.)
        List<SandboxViolation> result = new ArrayList<SandboxViolation>();
        for (SandboxViolation v : run.violations) {
            int i = result.size();
            while (i > 0 && BytecodeVerifier.compareLocations((SandboxViolation) result.get(i - 1), v) > 0) i--;
            result.add(i, v);
        }
        return result;
    }

    /**
     * Violations without a line number are "less" than violations with a line number; the latter are ordered by file
     * name and line number.
     */
    private static int
    compareLocations(SandboxViolation v1, SandboxViolation v2) {

        int l1 = v1.getLineNumber(), l2 = v2.getLineNumber();
        if (l1 == -1 || l2 == -1) return (l1 == -1 ? 0 : 1) - (l2 == -1 ? 0 : 1);

        String f1 = v1.getFileName(), f2 = v2.getFileName();
        if (f1 == null ? f2 != null : !f1.equals(f2)) {
            return f1 == null ? -1 : f2 == null ? 1 : f1.compareTo(f2);
        }

        return l1 < l2 ? -1 : l1 > l2 ? 1 : 0;
    }

    /**
     * The state of one {@link BytecodeVerifier#verify(Map, ClassLoader)} invocation.
     */
    private
    class Run {

        private final Map<String, ClassFileReader> classFiles;
        private final ClassLoader                  classLoader;
        @Nullable private final ClassLoader        generatedClassLoader;
        private final Map<String, byte[]>          pendingClasses;
        private final Map<String, TypeInfo>        typeInfos  = new HashMap<String, TypeInfo>();
        final List<SandboxViolation>               violations = new ArrayList<SandboxViolation>();

        Run(
            Map<String, ClassFileReader> classFiles,
            ClassLoader                  classLoader,
            @Nullable ClassLoader        generatedClassLoader,
            Map<String, byte[]>          pendingClasses
        ) {
            this.classFiles           = classFiles;
            this.classLoader          = classLoader;
            this.generatedClassLoader = generatedClassLoader;
            this.pendingClasses       = pendingClasses;
        }

        void
        verify(ClassFileReader cfr) {

            String className = cfr.getClassName();

            // Verify the superclass and the interfaces.
            String superclassName = cfr.getSuperclassName();
            if (
                superclassName != null
                && !BytecodeVerifier.IMPLICITLY_EXTENDABLE.contains(superclassName)
                && !BytecodeVerifier.this.policy.isSubclassingAllowed(superclassName)
                && !this.isGenerated(superclassName)
            ) {
                this.violation(className, "Extending " + superclassName + " is not permitted by the sandbox policy");
            }
            for (String interfaceName : cfr.getInterfaceNames()) {
                if (
                    !this.isGenerated(interfaceName)
                    && !BytecodeVerifier.IMPLICITLY_IMPLEMENTABLE.contains(interfaceName)
                    && !BytecodeVerifier.this.policy.isSubclassingAllowed(interfaceName)
                ) {
                    this.violation(
                        className,
                        "Implementing " + interfaceName + " is not permitted by the sandbox policy"
                    );
                }
            }

            // Native methods would execute code that is not subject to the policy.
            for (MemberDeclaration md : cfr.getMethods()) {
                if ((md.getAccessFlags() & Modifier.NATIVE) != 0) {
                    this.violation(className, "Native method " + md.getName() + " is not permitted");
                }
            }

            // The violations that concern the use of members are reported per source line.
            String      sourceFileName = cfr.getSourceFileName();
            Set<String> reported       = new HashSet<String>();

            // The JVM calls "finalize()" on its finalizer thread, i.e. outside of any "SandboxExecutor", so that the
            // resource limits would not apply, and a non-terminating finalizer would block finalization in the whole
            // JVM.
            for (MemberDeclaration md : cfr.getMethods()) {
                if (
                    "finalize".equals(md.getName())
                    && "()V".equals(md.getDescriptor())
                    && (md.getAccessFlags() & Modifier.STATIC) == 0
                ) {
                    SortedSet<Integer> lineNumbers = new TreeSet<Integer>();
                    if (md.getLineNumber() != -1) lineNumbers.add(Integer.valueOf(md.getLineNumber()));
                    this.violation(
                        className,
                        "Declaring finalize() is not permitted, because the JVM calls it outside of the sandbox",
                        sourceFileName,
                        lineNumbers,
                        reported
                    );
                }
            }

            // Verify all field and method references. (That includes the members referenced by method handles,
            // because these refer to the same constant pool entries.)
            for (MemberReference mr : cfr.getMemberReferences()) {
                String message = this.check(mr);
                if (message != null) {
                    this.violation(className, message, sourceFileName, cfr.getLineNumbers(mr), reported);
                }
            }

            // Verify the bootstrap methods of INVOKEDYNAMIC instructions and dynamically-computed constants.
            for (DynamicReference dr : cfr.getDynamicReferences()) {
                MemberReference bsm = cfr.getBootstrapMethod(dr).getMethod().getMember();
                if (!BytecodeVerifier.BOOTSTRAP_METHODS.contains(bsm.getClassName() + '#' + bsm.getName())) {
                    String message = (
                        "Bootstrap method "
                        + bsm.getClassName()
                        + '.'
                        + bsm.getName()
                        + " is not permitted by the sandbox policy"
                    );
                    this.violation(className, message, sourceFileName, cfr.getLineNumbers(dr), reported);
                }
            }
        }

        /**
         * Reports one violation for each of the <var>lineNumbers</var> (or one violation without a line number iff
         * the <var>lineNumbers</var> are empty), unless the same message was already reported for the same line.
         */
        private void
        violation(
            String             className,
            String             message,
            @Nullable String   fileName,
            SortedSet<Integer> lineNumbers,
            Set<String>        reported
        ) {
            if (lineNumbers.isEmpty()) {
                if (reported.add(-1 + ":" + message)) {
                    this.violations.add(new SandboxViolation(className, message, fileName, -1));
                }
                return;
            }
            for (Integer lineNumber : lineNumbers) {
                if (reported.add(lineNumber + ":" + message)) {
                    this.violations.add(new SandboxViolation(className, message, fileName, lineNumber.intValue()));
                }
            }
        }

        /**
         * @return A violation message, or {@code null} iff the reference is allowed
         */
        @Nullable private String
        check(MemberReference mr) {

            String  className = mr.getClassName();
            String  name      = mr.getName();
            String  desc      = mr.getDescriptor();
            boolean isField   = mr.getKind() == MemberReference.Kind.FIELD;

            // Array types have no fields, and their only own method is "clone()"; all other methods are those of
            // "java.lang.Object".
            if (className.startsWith("[")) {
                if (isField) return "Cannot resolve field " + BytecodeVerifier.format(mr);
                if ("clone".equals(name)) return null;
                className = "java.lang.Object";
            }

            String declaringClassName;
            if (isField) {
                declaringClassName = this.resolveField(className, name);
            } else
            if ("<init>".equals(name)) {
                declaringClassName = this.resolveConstructor(className, desc);
            } else
            if (mr.getKind() == MemberReference.Kind.INTERFACE_METHOD) {
                declaringClassName = this.resolveInterfaceMethod(className, name, desc);
            } else
            {
                declaringClassName = this.resolveMethod(className, name, desc);
            }
            if (declaringClassName == null) return "Cannot resolve " + BytecodeVerifier.format(mr);

            // Members of generated classes, e.g. of the verified classes themselves.
            if (this.isGenerated(declaringClassName)) return null;

            if (BytecodeVerifier.IMPLICIT_MEMBERS.contains(declaringClassName + '#' + name)) return null;

            MemberRef member = (
                isField
                ? MemberRef.field(declaringClassName, name, desc)
                : MemberRef.method(declaringClassName, name, desc)
            );
            if (BytecodeVerifier.this.policy.isAllowed(member)) return null;

            return "Access to " + BytecodeVerifier.format(member) + " is not permitted by the sandbox policy";
        }

        /**
         * Resolves a field like JVMS 5.4.3.2.
         *
         * @return The name of the declaring class, or {@code null}
         */
        @Nullable private String
        resolveField(String className, String name) {

            TypeInfo ti = this.getTypeInfo(className);
            if (ti == null) return null;

            if (ti.fields.contains(name)) return className;

            for (String interfaceName : ti.interfaceNames) {
                String result = this.resolveField(interfaceName, name);
                if (result != null) return result;
            }

            return ti.superclassName == null ? null : this.resolveField(ti.superclassName, name);
        }

        /**
         * @return The <var>className</var>, or {@code null} iff that class does not declare the constructor
         */
        @Nullable private String
        resolveConstructor(String className, String descriptor) {
            TypeInfo ti = this.getTypeInfo(className);
            return ti != null && ti.methods.contains("<init>" + descriptor) ? className : null;
        }

        /**
         * Resolves a method like JVMS 5.4.3.3.
         *
         * @return The name of the declaring class or interface, or {@code null}
         */
        @Nullable private String
        resolveMethod(String className, String name, String descriptor) {

            // Search the class and its superclasses.
            List<String> classNames = new ArrayList<String>();
            for (String cn = className; cn != null;) {
                TypeInfo ti = this.getTypeInfo(cn);
                if (ti == null) return null;
                if (ti.methods.contains(name + descriptor)) return cn;
                classNames.add(cn);
                cn = ti.superclassName;
            }

            // Search the superinterfaces.
            for (String cn : classNames) {
                TypeInfo ti = this.getTypeInfo(cn);
                assert ti != null;
                String result = this.resolveInSuperinterfaces(ti.interfaceNames, name, descriptor);
                if (result != null) return result;
            }

            return null;
        }

        /**
         * Resolves an interface method like JVMS 5.4.3.4.
         *
         * @return The name of the declaring interface or class, or {@code null}
         */
        @Nullable private String
        resolveInterfaceMethod(String interfaceName, String name, String descriptor) {

            TypeInfo ti = this.getTypeInfo(interfaceName);
            if (ti == null) return null;

            if (ti.methods.contains(name + descriptor)) return interfaceName;

            TypeInfo object = this.getTypeInfo("java.lang.Object");
            if (object != null && object.methods.contains(name + descriptor)) return "java.lang.Object";

            return this.resolveInSuperinterfaces(ti.interfaceNames, name, descriptor);
        }

        @Nullable private String
        resolveInSuperinterfaces(List<String> interfaceNames, String name, String descriptor) {
            for (String interfaceName : interfaceNames) {
                TypeInfo ti = this.getTypeInfo(interfaceName);
                if (ti == null) continue;
                if (ti.methods.contains(name + descriptor)) return interfaceName;
                String result = this.resolveInSuperinterfaces(ti.interfaceNames, name, descriptor);
                if (result != null) return result;
            }
            return null;
        }

        /**
         * @return The fields, methods and supertypes of the named class or interface, or {@code null} iff it cannot
         *         be loaded
         */
        @Nullable private TypeInfo
        getTypeInfo(final String className) {

            if (this.typeInfos.containsKey(className)) return (TypeInfo) this.typeInfos.get(className);

            TypeInfo result;

            ClassFileReader cfr     = (ClassFileReader) this.classFiles.get(className);
            byte[]          pending = (byte[]) this.pendingClasses.get(className);
            if (cfr != null) {
                result = TypeInfo.of(cfr);
            } else
            if (pending != null) {
                result = TypeInfo.of(new ClassFileReader(pending));
            } else
            {
                result = (TypeInfo) Privileged.run(new Supplier<TypeInfo>() {

                    @Override @Nullable public TypeInfo
                    get() {
                        try {
                            Class<?>    clazz = Class.forName(className, false, Run.this.classLoader);
                            ClassLoader gcl   = Run.this.generatedClassLoader;

                            // Notice: Classes loaded by the bootstrap class loader have a NULL class loader.
                            return TypeInfo.of(clazz, gcl != null && clazz.getClassLoader() == gcl);
                        } catch (ClassNotFoundException cnfe) {
                            return null;
                        } catch (LinkageError le) {
                            return null;
                        }
                    }
                });
            }

            this.typeInfos.put(className, result);
            return result;
        }

        /**
         * @return Whether the named class is a generated class, i.e. one of the verified classes, a pending class, or
         *         a class that the generated class loader defined
         */
        private boolean
        isGenerated(String className) {
            TypeInfo ti = this.getTypeInfo(className);
            return ti != null && ti.generated;
        }

        private void
        violation(String className, String message) {
            this.violations.add(new SandboxViolation(className, message, null, -1));
        }
    }

    /**
     * The names of the declared fields and methods, and the supertypes of a class or interface.
     */
    private static final
    class TypeInfo {

        final boolean          generated;
        @Nullable final String superclassName;
        final List<String>     interfaceNames;
        final Set<String>      fields;  // "name"
        final Set<String>      methods; // "name" + descriptor, including constructors ("<init>")

        TypeInfo(
            boolean          generated,
            @Nullable String superclassName,
            List<String>     interfaceNames,
            Set<String>      fields,
            Set<String>      methods
        ) {
            this.generated      = generated;
            this.superclassName = superclassName;
            this.interfaceNames = interfaceNames;
            this.fields         = fields;
            this.methods        = methods;
        }

        /**
         * @return The type info of a generated class
         */
        static TypeInfo
        of(ClassFileReader cfr) {

            Set<String> fields = new HashSet<String>();
            for (MemberDeclaration md : cfr.getFields()) fields.add(md.getName());

            Set<String> methods = new HashSet<String>();
            for (MemberDeclaration md : cfr.getMethods()) methods.add(md.getName() + md.getDescriptor());

            return new TypeInfo(true, cfr.getSuperclassName(), cfr.getInterfaceNames(), fields, methods);
        }

        static TypeInfo
        of(Class<?> clazz, boolean generated) {

            Set<String> fields = new HashSet<String>();
            for (Field f : clazz.getDeclaredFields()) fields.add(f.getName());

            Set<String> methods = new HashSet<String>();
            for (Method m : clazz.getDeclaredMethods()) {
                methods.add(
                    m.getName()
                    + BytecodeVerifier.methodDescriptor(m.getParameterTypes(), m.getReturnType())
                );
            }
            for (Constructor<?> c : clazz.getDeclaredConstructors()) {
                methods.add("<init>" + BytecodeVerifier.methodDescriptor(c.getParameterTypes(), void.class));
            }

            List<String> interfaceNames = new ArrayList<String>();
            for (Class<?> i : clazz.getInterfaces()) interfaceNames.add(i.getName());

            Class<?> superclass = clazz.getSuperclass();
            return new TypeInfo(
                generated,
                superclass == null ? null : superclass.getName(),
                interfaceNames,
                fields,
                methods
            );
        }
    }

    private static String
    methodDescriptor(Class<?>[] parameterTypes, Class<?> returnType) {
        StringBuilder sb = new StringBuilder("(");
        for (Class<?> pt : parameterTypes) sb.append(BytecodeVerifier.fieldDescriptor(pt));
        return sb.append(')').append(BytecodeVerifier.fieldDescriptor(returnType)).toString();
    }

    private static String
    fieldDescriptor(Class<?> type) {
        if (type.isArray()) return type.getName().replace('.', '/');
        if (!type.isPrimitive()) return 'L' + type.getName().replace('.', '/') + ';';
        return (
            type == int.class     ? "I" :
            type == long.class    ? "J" :
            type == boolean.class ? "Z" :
            type == byte.class    ? "B" :
            type == char.class    ? "C" :
            type == short.class   ? "S" :
            type == float.class   ? "F" :
            type == double.class  ? "D" :
            "V"
        );
    }

    /**
     * @return E.g. {@code "java.lang.System.out"}, {@code "java.lang.System.getProperty(java.lang.String)"} or
     *         {@code "new java.io.File(java.lang.String)"}
     */
    static String
    format(MemberRef member) {
        switch (member.getKind()) {
        case FIELD:
            return member.getClassName() + '.' + member.getName();
        case CONSTRUCTOR:
            return "new " + member.getClassName() + BytecodeVerifier.formatParameters(member.getDescriptor());
        default:
            return (
                member.getClassName()
                + '.'
                + member.getName()
                + BytecodeVerifier.formatParameters(member.getDescriptor())
            );
        }
    }

    private static String
    format(MemberReference mr) {
        return BytecodeVerifier.format(
            mr.getKind() == MemberReference.Kind.FIELD
            ? MemberRef.field(mr.getClassName(), mr.getName(), mr.getDescriptor())
            : MemberRef.method(mr.getClassName(), mr.getName(), mr.getDescriptor())
        );
    }

    /**
     * @param methodDescriptor E.g. {@code "(I[Ljava/lang/String;)V"}
     * @return                 E.g. {@code "(int, java.lang.String[])"}
     */
    private static String
    formatParameters(String methodDescriptor) {

        StringBuilder sb = new StringBuilder("(");
        int           i  = 1;
        while (i < methodDescriptor.length() && methodDescriptor.charAt(i) != ')') {
            if (i > 1) sb.append(", ");

            int dimensions = 0;
            while (methodDescriptor.charAt(i) == '[') {
                dimensions++;
                i++;
            }

            char c = methodDescriptor.charAt(i++);
            if (c == 'L') {
                int semicolon = methodDescriptor.indexOf(';', i);
                if (semicolon == -1) return methodDescriptor;
                sb.append(methodDescriptor.substring(i, semicolon).replace('/', '.'));
                i = semicolon + 1;
            } else {
                sb.append(
                    c == 'I' ? "int" :
                    c == 'J' ? "long" :
                    c == 'Z' ? "boolean" :
                    c == 'B' ? "byte" :
                    c == 'C' ? "char" :
                    c == 'S' ? "short" :
                    c == 'F' ? "float" :
                    c == 'D' ? "double" :
                    String.valueOf(c)
                );
            }

            for (; dimensions > 0; dimensions--) sb.append("[]");
        }
        return sb.append(')').toString();
    }

    private static Set<String>
    set(String... elements) {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(elements)));
    }

    private static Set<String>
    union(Set<String> set, String... elements) {
        Set<String> result = new LinkedHashSet<String>(set);
        result.addAll(Arrays.asList(elements));
        return Collections.unmodifiableSet(result);
    }
}
