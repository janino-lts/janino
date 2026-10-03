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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * An allowlist of the fields, methods and constructors that sandboxed code may use, and of the classes and
 * interfaces that it may extend or implement. Everything that is not explicitly allowed is forbidden.
 * <p>
 *   A {@link SandboxPolicy} is immutable; use a {@link Builder} to create one:
 * </p>
 * <pre>
 *     SandboxPolicy policy = SandboxPolicy.builder()
 *         .include(SandboxPolicy.JAVA_LANG_BASIC)
 *         .include(SandboxPolicy.COLLECTIONS)
 *         .allowAllMembers("com.acme.ScriptApi")
 *         .build();
 * </pre>
 * <p>
 *   Rules always apply to the <em>declaring</em> class of a member; e.g. {@code ArrayList.toString()} is declared by
 *   {@code java.util.AbstractCollection}.
 * </p>
 * <p>
 *   Some members are <em>never allowed</em> (see {@link #isNeverAllowed(MemberRef)}), because they would allow
 *   sandboxed code to escape the sandbox or to affect the entire JVM, e.g. reflection, class loading, access control
 *   ({@code AccessController.doPrivileged()}), threads, file and network access, and system properties. Class-wide
 *   rules ({@link Builder#allowAllMembers(String...)}, {@link Builder#allowConstructors(String...)}) never enable
 *   them; a host that really wants to allow such a member must name it explicitly ({@link
 *   Builder#allowMethod(String, String, String)}, {@link Builder#allowMethods(String, String...)}, {@link
 *   Builder#allowField(String, String)}).
 * </p>
 */
public final
class SandboxPolicy {

    /**
     * Basic functionality of {@code java.lang}: {@code Object} (without {@code wait()} and {@code notify()}), {@code
     * Class.getName()} and {@code getSimpleName()}, {@code String}, {@code StringBuilder}, {@code Math}, the wrapper
     * types, {@code Enum}, the common exceptions, and {@code java.util.Objects}.
     */
    public static final SandboxPolicy JAVA_LANG_BASIC;

    /**
     * The core collections of {@code java.util}: the collection interfaces and their common implementations, {@code
     * Collections}, {@code Arrays} and {@code Optional}.
     */
    public static final SandboxPolicy COLLECTIONS;

    // The never-allowed members, see "isNeverAllowed()".
    private static final String[]                 NEVER_ALLOWED_PACKAGES;
    private static final Map<String, Set<String>> NEVER_ALLOWED_CLASSES;
    private static final Set<String>              NEVER_ALLOWED_METHODS;

    static {
        NEVER_ALLOWED_PACKAGES = new String[] {
            "com.sun.",
            "java.beans.",
            "java.lang.foreign.",
            "java.lang.instrument.",
            "java.lang.invoke.",
            "java.lang.management.",
            "java.lang.reflect.",
            "java.net.",
            "java.nio.channels.",
            "java.nio.file.",
            "java.rmi.",
            "java.util.logging.",
            "java.util.prefs.",
            "javax.management.",
            "javax.naming.",
            "javax.script.",
            "javax.tools.",
            "jdk.",
            "sun.",
        };

        // Class name => the names of the members that are exempt.
        Map<String, Set<String>> m = new HashMap<String, Set<String>>();
        SandboxPolicy.neverAllowed(m, "java.io.File");
        SandboxPolicy.neverAllowed(m, "java.io.FileDescriptor");
        SandboxPolicy.neverAllowed(m, "java.io.FileInputStream");
        SandboxPolicy.neverAllowed(m, "java.io.FileOutputStream");
        SandboxPolicy.neverAllowed(m, "java.io.FileReader");
        SandboxPolicy.neverAllowed(m, "java.io.FileWriter");
        SandboxPolicy.neverAllowed(m, "java.io.ObjectInputStream");
        SandboxPolicy.neverAllowed(m, "java.io.ObjectOutputStream");
        SandboxPolicy.neverAllowed(m, "java.io.PrintStream", "<init>"); // "PrintStream(String)" opens a file.
        SandboxPolicy.neverAllowed(m, "java.io.PrintWriter", "<init>"); // "PrintWriter(String)" opens a file.
        SandboxPolicy.neverAllowed(m, "java.io.RandomAccessFile");
        SandboxPolicy.neverAllowed(
            m,
            "java.lang.Class",
            "cast", "getName", "getSimpleName", "isInstance"
        );
        SandboxPolicy.neverAllowed(m, "java.lang.ClassLoader");
        SandboxPolicy.neverAllowed(m, "java.lang.InheritableThreadLocal");
        SandboxPolicy.neverAllowed(m, "java.lang.Module");
        SandboxPolicy.neverAllowed(m, "java.lang.ModuleLayer");
        SandboxPolicy.neverAllowed(m, "java.lang.Package");
        SandboxPolicy.neverAllowed(m, "java.lang.Process");
        SandboxPolicy.neverAllowed(m, "java.lang.ProcessBuilder");
        SandboxPolicy.neverAllowed(m, "java.lang.ProcessHandle");
        SandboxPolicy.neverAllowed(m, "java.lang.Runtime");
        SandboxPolicy.neverAllowed(m, "java.lang.SecurityManager");
        SandboxPolicy.neverAllowed(m, "java.lang.StackWalker");
        SandboxPolicy.neverAllowed(
            m,
            "java.lang.System",
            "arraycopy", "currentTimeMillis", "identityHashCode", "lineSeparator", "nanoTime"
        );
        SandboxPolicy.neverAllowed(m, "java.lang.Thread");
        SandboxPolicy.neverAllowed(m, "java.lang.ThreadGroup");
        SandboxPolicy.neverAllowed(m, "java.lang.ref.Cleaner");
        SandboxPolicy.neverAllowed(m, "java.security.AccessControlContext");
        SandboxPolicy.neverAllowed(m, "java.security.AccessController"); // "doPrivileged()" escapes from a "Sandbox".
        SandboxPolicy.neverAllowed(m, "java.security.Policy");
        SandboxPolicy.neverAllowed(m, "java.security.ProtectionDomain");
        SandboxPolicy.neverAllowed(m, "java.security.SecureClassLoader");
        SandboxPolicy.neverAllowed(m, "java.security.Security"); // Changes JVM-global state.
        SandboxPolicy.neverAllowed(m, "java.sql.DriverManager");
        SandboxPolicy.neverAllowed(m, "java.util.ServiceLoader");
        SandboxPolicy.neverAllowed(m, "java.util.Timer");
        SandboxPolicy.neverAllowed(m, "java.util.concurrent.CompletableFuture");
        SandboxPolicy.neverAllowed(m, "java.util.concurrent.Executors");
        SandboxPolicy.neverAllowed(m, "java.util.concurrent.ForkJoinPool");
        SandboxPolicy.neverAllowed(m, "java.util.concurrent.ForkJoinTask");
        SandboxPolicy.neverAllowed(m, "java.util.concurrent.ScheduledThreadPoolExecutor");
        SandboxPolicy.neverAllowed(m, "java.util.concurrent.ThreadPoolExecutor");
        SandboxPolicy.neverAllowed(m, "java.util.jar.JarFile");
        SandboxPolicy.neverAllowed(m, "java.util.zip.ZipFile");
        SandboxPolicy.neverAllowed(m, "javax.security.auth.Subject"); // "doAsPrivileged()" escapes from a "Sandbox".
        NEVER_ALLOWED_CLASSES = Collections.unmodifiableMap(m);

        // "className#methodName".
        NEVER_ALLOWED_METHODS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "java.lang.Boolean#getBoolean",          // Reads a system property.
            "java.lang.Integer#getInteger",          // Reads a system property.
            "java.lang.Long#getLong",                // Reads a system property.
            "java.lang.Object#notify",
            "java.lang.Object#notifyAll",
            "java.lang.Object#wait",
            "java.lang.Throwable#printStackTrace",   // Writes to "System.err".
            "java.util.Arrays#parallelPrefix",       // Uses the common fork/join pool.
            "java.util.Arrays#parallelSetAll",       // Uses the common fork/join pool.
            "java.util.Arrays#parallelSort",         // Uses the common fork/join pool.
            "java.util.Collection#parallelStream",   // Uses the common fork/join pool.
            "java.util.Locale#setDefault",           // Changes JVM-global state.
            "java.util.TimeZone#setDefault",         // Changes JVM-global state.
            "java.util.stream.BaseStream#parallel"   // Uses the common fork/join pool.
        )));

        JAVA_LANG_BASIC = SandboxPolicy.builder()
            .allowConstructors("java.lang.Object")
            .allowMethods("java.lang.Object", "equals", "getClass", "hashCode", "toString")
            .allowMethods("java.lang.Class", "getName", "getSimpleName")
            .allowAllMembers(
                "java.lang.AutoCloseable",
                "java.lang.Boolean",
                "java.lang.Byte",
                "java.lang.CharSequence",
                "java.lang.Character",
                "java.lang.Comparable",
                "java.lang.Double",
                "java.lang.Enum",
                "java.lang.Float",
                "java.lang.Integer",
                "java.lang.Iterable",
                "java.lang.Long",
                "java.lang.Math",
                "java.lang.Number",
                "java.lang.Runnable",
                "java.lang.Short",
                "java.lang.StrictMath",
                "java.lang.String",
                "java.lang.StringBuffer",
                "java.lang.StringBuilder",
                "java.util.Objects"
            )

            // The superclasses of "StringBuilder" and "StringBuffer" declare most of their methods.
            .allowAllMembers("java.lang.AbstractStringBuilder")

            .allowConstructors("java.lang.Throwable")
            .allowMethods(
                "java.lang.Throwable",
                "addSuppressed",
                "getCause",
                "getLocalizedMessage",
                "getMessage",
                "getSuppressed",
                "initCause",
                "toString"
            )
            .allowConstructors(
                "java.lang.ArithmeticException",
                "java.lang.ArrayIndexOutOfBoundsException",
                "java.lang.ArrayStoreException",
                "java.lang.AssertionError",
                "java.lang.ClassCastException",
                "java.lang.CloneNotSupportedException",
                "java.lang.Error",
                "java.lang.Exception",
                "java.lang.IllegalArgumentException",
                "java.lang.IllegalStateException",
                "java.lang.IndexOutOfBoundsException",
                "java.lang.NegativeArraySizeException",
                "java.lang.NullPointerException",
                "java.lang.NumberFormatException",
                "java.lang.RuntimeException",
                "java.lang.StringIndexOutOfBoundsException",
                "java.lang.UnsupportedOperationException"
            )
            .allowSubclassing(
                "java.io.Serializable",
                "java.lang.AutoCloseable",
                "java.lang.CharSequence",
                "java.lang.Cloneable",
                "java.lang.Comparable",
                "java.lang.Enum",
                "java.lang.Exception",
                "java.lang.Iterable",
                "java.lang.Object",
                "java.lang.Runnable",
                "java.lang.RuntimeException"
            )
            .build();

        COLLECTIONS = SandboxPolicy.builder()
            .allowAllMembers(
                "java.util.AbstractCollection",
                "java.util.AbstractList",
                "java.util.AbstractMap",
                "java.util.AbstractMap$SimpleEntry",
                "java.util.AbstractMap$SimpleImmutableEntry",
                "java.util.AbstractQueue",
                "java.util.AbstractSequentialList",
                "java.util.AbstractSet",
                "java.util.ArrayDeque",
                "java.util.ArrayList",
                "java.util.Arrays",
                "java.util.Collection",
                "java.util.Collections",
                "java.util.Comparator",
                "java.util.Deque",
                "java.util.EnumMap",
                "java.util.EnumSet",
                "java.util.HashMap",
                "java.util.HashSet",
                "java.util.Iterator",
                "java.util.LinkedHashMap",
                "java.util.LinkedHashSet",
                "java.util.LinkedList",
                "java.util.List",
                "java.util.ListIterator",
                "java.util.Map",
                "java.util.Map$Entry",
                "java.util.NavigableMap",
                "java.util.NavigableSet",
                "java.util.Optional",
                "java.util.OptionalDouble",
                "java.util.OptionalInt",
                "java.util.OptionalLong",
                "java.util.PriorityQueue",
                "java.util.Queue",
                "java.util.Set",
                "java.util.SortedMap",
                "java.util.SortedSet",
                "java.util.TreeMap",
                "java.util.TreeSet"
            )
            .allowConstructors(
                "java.util.ConcurrentModificationException",
                "java.util.NoSuchElementException"
            )
            .allowSubclassing(
                "java.util.AbstractCollection",
                "java.util.AbstractList",
                "java.util.AbstractMap",
                "java.util.AbstractSet",
                "java.util.Comparator",
                "java.util.Iterator"
            )
            .build();
    }

    private static void
    neverAllowed(Map<String, Set<String>> map, String className, String... exemptMemberNames) {
        map.put(className, Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(exemptMemberNames))));
    }

    // "className#name#descriptor".
    private final Set<String> methods;

    // "className#name"; all overloads.
    private final Set<String> methodNames;

    // "className#name".
    private final Set<String> fields;

    // "className".
    private final Set<String> constructorClasses;
    private final Set<String> allMembersClasses;
    private final Set<String> subclassableClasses;

    private
    SandboxPolicy(Builder builder) {
        this.methods             = Collections.unmodifiableSet(new HashSet<String>(builder.methods));
        this.methodNames         = Collections.unmodifiableSet(new HashSet<String>(builder.methodNames));
        this.fields              = Collections.unmodifiableSet(new HashSet<String>(builder.fields));
        this.constructorClasses  = Collections.unmodifiableSet(new HashSet<String>(builder.constructorClasses));
        this.allMembersClasses   = Collections.unmodifiableSet(new HashSet<String>(builder.allMembersClasses));
        this.subclassableClasses = Collections.unmodifiableSet(new HashSet<String>(builder.subclassableClasses));
    }

    /**
     * @return A new builder for an initially empty policy, i.e. a policy that allows nothing
     */
    public static Builder
    builder() { return new Builder(); }

    /**
     * @param member A field, method or constructor, identified by its <em>declaring</em> class
     * @return       Whether sandboxed code may use the <var>member</var>
     */
    public boolean
    isAllowed(MemberRef member) {

        String className = member.getClassName();
        String name      = member.getName();

        // Rules that name the member explicitly take precedence over the never-allowed members.
        if (member.getKind() == MemberRef.Kind.FIELD) {
            if (this.fields.contains(className + '#' + name)) return true;
        } else {
            if (this.methods.contains(className + '#' + name + '#' + member.getDescriptor())) return true;
            if (this.methodNames.contains(className + '#' + name)) return true;
        }

        if (SandboxPolicy.isNeverAllowed(member)) return false;

        if (this.allMembersClasses.contains(className)) return true;

        return member.getKind() == MemberRef.Kind.CONSTRUCTOR && this.constructorClasses.contains(className);
    }

    /**
     * @return Whether sandboxed code may declare classes that extend or implement the named class or interface
     */
    public boolean
    isSubclassingAllowed(String className) { return this.subclassableClasses.contains(className); }

    /**
     * @return Whether the <var>member</var> is one of the members that are never allowed unless a policy names them
     *         explicitly; see {@link SandboxPolicy the class documentation}
     */
    public static boolean
    isNeverAllowed(MemberRef member) {

        String className = member.getClassName();

        for (String packagePrefix : SandboxPolicy.NEVER_ALLOWED_PACKAGES) {
            if (className.startsWith(packagePrefix)) return true;
        }

        Set<String> exemptMemberNames = (Set<String>) SandboxPolicy.NEVER_ALLOWED_CLASSES.get(className);
        if (exemptMemberNames != null && !exemptMemberNames.contains(member.getName())) return true;

        return (
            member.getKind() != MemberRef.Kind.FIELD
            && SandboxPolicy.NEVER_ALLOWED_METHODS.contains(className + '#' + member.getName())
        );
    }

    /**
     * Creates {@link SandboxPolicy}s. All class names are binary names in the format of {@link Class#getName()},
     * e.g. {@code "java.util.Map$Entry"}.
     */
    public static final
    class Builder {

        private final Set<String> methods             = new HashSet<String>();
        private final Set<String> methodNames         = new HashSet<String>();
        private final Set<String> fields              = new HashSet<String>();
        private final Set<String> constructorClasses  = new HashSet<String>();
        private final Set<String> allMembersClasses   = new HashSet<String>();
        private final Set<String> subclassableClasses = new HashSet<String>();

        Builder() {}

        /**
         * Allows one method or constructor.
         *
         * @param name       The method name, or {@code "<init>"} for a constructor
         * @param descriptor The method descriptor, e.g. {@code "(Ljava/lang/String;)V"}
         */
        public Builder
        allowMethod(String className, String name, String descriptor) {
            Builder.checkClassName(className);
            if (!descriptor.startsWith("(")) {
                throw new IllegalArgumentException("Invalid method descriptor \"" + descriptor + "\"");
            }
            this.methods.add(className + '#' + name + '#' + descriptor);
            return this;
        }

        /**
         * Allows all overloads of the named methods of the class.
         */
        public Builder
        allowMethods(String className, String... names) {
            Builder.checkClassName(className);
            for (String name : names) this.methodNames.add(className + '#' + name);
            return this;
        }

        /**
         * Allows reading and writing the named field of the class. (Writing a {@code final} field fails anyway.)
         */
        public Builder
        allowField(String className, String name) {
            Builder.checkClassName(className);
            this.fields.add(className + '#' + name);
            return this;
        }

        /**
         * Allows all constructors of the given classes, except the never-allowed ones.
         */
        public Builder
        allowConstructors(String... classNames) {
            for (String className : classNames) {
                Builder.checkClassName(className);
                this.constructorClasses.add(className);
            }
            return this;
        }

        /**
         * Allows all fields, methods and constructors that the given classes declare, except the never-allowed ones.
         */
        public Builder
        allowAllMembers(String... classNames) {
            for (String className : classNames) {
                Builder.checkClassName(className);
                this.allMembersClasses.add(className);
            }
            return this;
        }

        /**
         * Allows sandboxed code to declare classes that extend or implement the given classes or interfaces.
         */
        public Builder
        allowSubclassing(String... classNames) {
            for (String className : classNames) {
                Builder.checkClassName(className);
                this.subclassableClasses.add(className);
            }
            return this;
        }

        /**
         * Adds all rules of the given <var>policy</var>, e.g. of {@link SandboxPolicy#JAVA_LANG_BASIC}.
         */
        public Builder
        include(SandboxPolicy policy) {
            this.methods.addAll(policy.methods);
            this.methodNames.addAll(policy.methodNames);
            this.fields.addAll(policy.fields);
            this.constructorClasses.addAll(policy.constructorClasses);
            this.allMembersClasses.addAll(policy.allMembersClasses);
            this.subclassableClasses.addAll(policy.subclassableClasses);
            return this;
        }

        public SandboxPolicy
        build() { return new SandboxPolicy(this); }

        private static void
        checkClassName(String className) {
            if (className.isEmpty() || className.indexOf('/') != -1 || className.indexOf('#') != -1) {
                throw new IllegalArgumentException(
                    "Invalid class name \"" + className + "\"; use the format of \"Class.getName()\""
                );
            }
        }
    }
}
