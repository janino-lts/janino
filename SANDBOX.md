# Restricting Untrusted Code with a Sandbox Policy

JANINO compiles Java source code at runtime - expressions, scripts, class bodies and complete compilation units.
If that source code comes from users or other untrusted parties, you will want to restrict what it can do: it should
compute results, but it should not read files, open network connections, read system properties, start threads or
call `System.exit()`.

The **sandbox policy** mechanism does exactly that. You describe the APIs that the compiled code may use (an
*allowlist*), and JANINO verifies every class it generates against that allowlist **before the class is loaded**.
Code that uses anything else is rejected at compile time.

```java
IExpressionEvaluator ee = CompilerFactoryFactory.getDefaultCompilerFactory(classLoader).newExpressionEvaluator();
ee.setSandboxPolicy(SandboxPolicy.JAVA_LANG_BASIC);
ee.cook("System.getProperty(\"user.home\")");
// => CompileException: Sandbox violation:
//      SC: Access to java.lang.System.getProperty(java.lang.String) is not permitted by the sandbox policy
```

---

## 1. Requirements and availability

- **Java 8 or later.** The mechanism works identically on every JVM from Java 8 through the current releases
  (tested on Java 8, 17, 21 and 25). It does not depend on the Java security manager.
- **Both compiler implementations** are supported: JANINO itself (`org.codehaus.janino`) and the JDK-based
  implementation (`org.codehaus.commons.compiler.jdk`, which uses `javac`).
- **It is opt-in.** The mechanism is **never activated automatically**, on no Java version. As long as you do not
  set a policy, JANINO behaves exactly as before, and compiled code can use the entire Java API.

### Relationship to the legacy `Sandbox` class

JANINO also still offers the sandbox of earlier versions, `org.codehaus.commons.compiler.Sandbox`, which is based on
the Java security manager (see [section 7](#7-the-security-manager-based-sandbox)). The security manager has been
deprecated (Java 17) and permanently disabled (Java 24), so that class only works on older JVMs:

| JVM          | Legacy `Sandbox`                                                                  | Sandbox policy |
|--------------|-----------------------------------------------------------------------------------|----------------|
| Java 8 - 17  | Works                                                                             | Works          |
| Java 18 - 23 | Works only with `-Djava.security.manager=allow`                                   | Works          |
| Java 24+     | Not available: the constructor throws an `UnsupportedOperationException`          | Works          |

`Sandbox.isSupported()` tells whether the legacy sandbox can be used on the running JVM. For new code, and for any
code that must run on current JVMs, use the sandbox policy.

**Notice:** The legacy sandbox has a known weakness that lets code escape from it; see the warning in
[section 7](#7-the-security-manager-based-sandbox).

---

## 2. How it works

1. You create a `SandboxPolicy` that lists the fields, methods and constructors that compiled code may use.
2. You set it on the cookable (`setSandboxPolicy(...)`) or on the `JavaSourceClassLoader`.
3. JANINO compiles the source code as usual.
4. Before any generated class can be loaded, a **bytecode verifier** inspects it:
   - every field and method that the class refers to (including method references and lambdas),
   - its superclass and its interfaces,
   - `native` methods (always rejected),
   - the bootstrap methods of `invokedynamic` instructions (only those that compilers use for lambdas, string
     concatenation, records and pattern matching are accepted).
5. If anything is not allowed, cooking fails with a `CompileException` that lists **all** violations, and none of
   the generated classes can be loaded.

Some properties that are worth knowing:

- **The check is static.** It happens once, at compile time. At runtime, the compiled code runs at full speed;
  there is no per-call overhead.
- **Callbacks are covered.** Because the restriction is part of the compiled code itself, it also applies when
  your application calls the compiled code later - e.g. a `Runnable` or a `Comparator` that a script returned.
- **Rules apply to the declaring class.** A reference is checked against the class that actually *declares* the
  member, e.g. `myArrayList.toString()` is checked as `java.util.AbstractCollection.toString()`. The violation
  messages always name the declaring member, so you know exactly what to allow.
- **Compiler-generated code is always allowed.** Language features like string concatenation, autoboxing,
  enhanced `for` loops, `assert`, enums, `switch` on strings and enums, try-with-resources, lambdas, records and
  pattern matching work even with an empty policy, as far as the compiler generates the calls itself.
- **Type references are not restricted.** Code may *mention* any type (e.g. in declarations, casts, `instanceof`
  or class literals), because that does not execute code of that type. Only *using* its fields, methods and
  constructors is checked.

---

## 3. Using it

### 3.1 Expressions, scripts, class bodies and compilation units

All cookables (`IExpressionEvaluator`, `IScriptEvaluator`, `IClassBodyEvaluator`, `ISimpleCompiler`) have the
method `setSandboxPolicy(SandboxPolicy)`. Call it **before** `cook()`:

```java
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.CompilerFactoryFactory;
import org.codehaus.commons.compiler.IExpressionEvaluator;
import org.codehaus.commons.compiler.sandbox.SandboxPolicy;
import org.codehaus.commons.compiler.sandbox.SandboxViolation;
import org.codehaus.commons.compiler.sandbox.SandboxViolationException;

SandboxPolicy policy = SandboxPolicy.builder()
    .include(SandboxPolicy.JAVA_LANG_BASIC)
    .include(SandboxPolicy.COLLECTIONS)
    .build();

IExpressionEvaluator ee = CompilerFactoryFactory.getDefaultCompilerFactory(
    Thread.currentThread().getContextClassLoader()
).newExpressionEvaluator();
ee.setSandboxPolicy(policy);
ee.setParameters(new String[] { "a", "b" }, new Class<?>[] { int.class, int.class });
ee.setExpressionType(int.class);

try {
    ee.cook(userProvidedExpression);
} catch (CompileException ce) {
    if (ce.getCause() instanceof SandboxViolationException) {

        // The expression uses APIs that the policy does not allow.
        for (SandboxViolation v : ((SandboxViolationException) ce.getCause()).getViolations()) {
            System.err.println(v.getClassName() + ": " + v.getMessage());
        }
    } else {

        // An ordinary compilation error (syntax error, type error, ...).
        System.err.println(ce.getMessage());
    }
    return;
}

Object result = ee.evaluate(new Object[] { 3, 4 });
```

The message of the `CompileException` lists all violations, e.g.:

```
Sandbox violations:
  SC: Access to java.lang.System.getProperty(java.lang.String) is not permitted by the sandbox policy
  SC: Access to java.lang.Runtime.getRuntime() is not permitted by the sandbox policy
```

The class name (`SC` in this example) is the name of the generated class. Violations do not (yet) carry a source
line number.

**Extended classes and implemented interfaces.** If you let the generated class extend a class or implement
interfaces - through `setExtendedClass(...)`, `setImplementedInterfaces(...)`, or `createFastEvaluator(...)` with an
interface - the policy must allow that with `allowSubclassing(...)`, e.g.

```java
SandboxPolicy policy = SandboxPolicy.builder()
    .include(SandboxPolicy.JAVA_LANG_BASIC)
    .allowSubclassing("com.acme.script.Calculator")
    .build();
se.setSandboxPolicy(policy);
Calculator c = (Calculator) se.createFastEvaluator(script, Calculator.class, new String[] { "a", "b" });
```

Otherwise cooking fails with `Implementing com.acme.script.Calculator is not permitted by the sandbox policy`.
(`Runnable`, `Comparable`, `Iterable` and a few others are already allowed by `JAVA_LANG_BASIC`.)

### 3.2 Loading classes from source files

`JavaSourceClassLoader` (both implementations, including JANINO's `CachingJavaSourceClassLoader`) verifies each
class before it defines it. Set the policy before the first class is loaded:

```java
AbstractJavaSourceClassLoader jscl = compilerFactory.newJavaSourceClassLoader(parentClassLoader);
jscl.setSourcePath(new File[] { new File("scripts") });
jscl.setSandboxPolicy(policy);

Class<?> c = jscl.loadClass("pkg.MyScript");   // throws ClassNotFoundException on violations
```

If a class violates the policy, `loadClass()` throws a `ClassNotFoundException` whose cause is a
`SandboxViolationException`. A class that *uses* a rejected class cannot be loaded either.

`CachingJavaSourceClassLoader` also verifies classes that it loads from its class file cache, so a cache that was
populated without a policy (or with a more permissive one) cannot be used to bypass the policy.

### 3.3 Switching it on and off

| What                                    | How                                                                      |
|-----------------------------------------|--------------------------------------------------------------------------|
| Switch on                               | `setSandboxPolicy(policy)` before `cook()` / before the first `loadClass()` |
| Switch off (the default)                | Don't call `setSandboxPolicy()`, or call `setSandboxPolicy(null)`        |
| Scope                                   | Per cookable, resp. per `JavaSourceClassLoader` instance                 |

There is no global switch and no system property: each cookable and each class loader decides for itself. This
lets an application compile trusted code (e.g. its own templates) without restrictions and untrusted code with a
policy, side by side.

A `SandboxPolicy` is immutable and thread-safe; create it once and share it between all cookables.

### 3.4 Verifying class files directly

If you produce class files in another way, you can use the verifier directly:

```java
List<SandboxViolation> violations = new BytecodeVerifier(policy).verify(
    classFiles,         // Map<String, byte[]>: all class files that were compiled together
    parentClassLoader   // the class loader through which these classes will see the rest of the world
);
```

---

## 4. Configuring a policy

### 4.1 Deny by default

A policy allows **nothing** except what you add. `SandboxPolicy.builder().build()` creates a policy that rejects
every use of any field, method or constructor that is not generated by the compiler itself.

### 4.2 Presets

Two presets cover the most common needs; include them with `include(...)`:

| Preset            | Contents                                                                                       |
|-------------------|------------------------------------------------------------------------------------------------|
| `JAVA_LANG_BASIC` | `Object` (`equals`, `hashCode`, `toString`, `getClass`, constructor), `Class.getName()` and `getSimpleName()`, `String`, `StringBuilder`, `StringBuffer`, `CharSequence`, `Character`, `Math`, `StrictMath`, `Number` and the wrapper types (`Integer`, `Long`, ... - except the methods that read system properties), `Boolean`, `Enum`, `Comparable`, `Iterable`, `Runnable`, `AutoCloseable`, `java.util.Objects`, the constructors of the common exceptions and errors, and the basic methods of `Throwable` (`getMessage`, `getCause`, ...). Code may extend or implement `Object`, `Exception`, `RuntimeException`, `Enum`, `Comparable`, `Runnable`, `Iterable`, `CharSequence`, `AutoCloseable`, `Cloneable` and `Serializable`. |
| `COLLECTIONS`     | The collection interfaces of `java.util` (`Collection`, `List`, `Set`, `Map`, `Map.Entry`, `Queue`, `Deque`, `Iterator`, `Comparator`, the sorted and navigable variants) and their common implementations (`ArrayList`, `LinkedList`, `HashMap`, `LinkedHashMap`, `TreeMap`, `HashSet`, `LinkedHashSet`, `TreeSet`, `ArrayDeque`, `PriorityQueue`, `EnumMap`, `EnumSet` and their abstract base classes), `Collections`, `Arrays`, `Optional` (and its primitive variants), `NoSuchElementException`, `ConcurrentModificationException`. Code may extend `AbstractCollection`, `AbstractList`, `AbstractSet`, `AbstractMap` and implement `Comparator` and `Iterator`. |

`COLLECTIONS` does not include `JAVA_LANG_BASIC`; typically you include both.

### 4.3 Adding permissions

All class names are binary names as returned by `Class.getName()`, e.g. `"java.util.Map$Entry"` for a nested class.

| Builder method                                      | Allows                                                                  |
|-----------------------------------------------------|-------------------------------------------------------------------------|
| `allowAllMembers(String... classNames)`             | All fields, methods and constructors that the classes declare           |
| `allowMethods(String className, String... names)`   | All overloads of the named methods (`"<init>"` = all constructors)      |
| `allowMethod(String className, String name, String descriptor)` | Exactly one method or constructor                           |
| `allowField(String className, String name)`         | Reading and writing one field                                           |
| `allowConstructors(String... classNames)`           | All constructors of the classes                                         |
| `allowSubclassing(String... classNames)`            | Declaring classes that extend or implement the classes or interfaces    |
| `include(SandboxPolicy policy)`                     | All rules of another policy, e.g. a preset                              |

Method descriptors use the JVM format:

| Java signature                         | Descriptor                                    |
|----------------------------------------|-----------------------------------------------|
| `void run()`                           | `()V`                                         |
| `int max(int, int)`                    | `(II)I`                                       |
| `String format(String, Object...)`     | `(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;` |
| `Foo(long, double[])` (constructor)    | name `<init>`, descriptor `(J[D)V`            |

Example - give scripts access to your own API and to `java.time`:

```java
SandboxPolicy policy = SandboxPolicy.builder()
    .include(SandboxPolicy.JAVA_LANG_BASIC)
    .include(SandboxPolicy.COLLECTIONS)
    .allowAllMembers("com.acme.script.ScriptApi", "com.acme.script.Order")
    .allowSubclassing("com.acme.script.OrderFilter")          // scripts may implement this interface
    .allowAllMembers("java.time.LocalDate", "java.time.Duration")
    .allowMethods("java.time.Instant", "now", "toEpochMilli")
    .build();
```

Because rules apply to the *declaring* class, inherited members must be allowed where they are declared. If a
violation message says e.g. `Access to java.util.AbstractMap.toString() is not permitted`, allow that exact member
or class, not the subclass that your code used.

### 4.4 Recommended practice: capabilities instead of broad permissions

Rather than allowing powerful JDK APIs, give scripts a small object of your own that offers exactly what they need,
and allow only that class:

```java
public final class ScriptFiles {
    private final Path root;
    public ScriptFiles(Path root) { this.root = root; }

    /** Reads a file below the configured root directory. */
    public String read(String relativePath) throws IOException { ... /* validate the path, then read */ }
}

SandboxPolicy policy = SandboxPolicy.builder()
    .include(SandboxPolicy.JAVA_LANG_BASIC)
    .allowMethods("com.acme.ScriptFiles", "read")
    .build();
```

Pass an instance to the script (e.g. as a parameter of the expression). The script can call `read()`, but it cannot
create its own `ScriptFiles` object or touch `java.io` / `java.nio.file` directly. This is the replacement for the
parameterized permissions of the security manager (e.g. `FilePermission("/data/-", "read")`).

---

## 5. What is forbidden

Everything that your policy does not allow is forbidden. In addition, the following members are **never allowed**
by class-wide rules (`allowAllMembers`, `allowConstructors`), because they would let code escape the sandbox or affect
the whole JVM - even if a preset or your own rule allows "all members" of the class:

| Category                         | Examples                                                                              |
|----------------------------------|---------------------------------------------------------------------------------------|
| Reflection and dynamic invocation | `java.lang.reflect.*`, `java.lang.invoke.*`, `Class.forName()`, `Class.getDeclaredMethods()` and all other members of `Class` except `getName()`, `getSimpleName()`, `isInstance()` and `cast()` |
| Class loading and modules        | `ClassLoader`, `SecureClassLoader`, `Module`, `ModuleLayer`, `ServiceLoader`, `java.lang.instrument.*` |
| Threads and concurrency          | `Thread`, `ThreadGroup`, `InheritableThreadLocal`, executors, `ForkJoinPool`, `ForkJoinTask`, `CompletableFuture`, `Timer`, `Collection.parallelStream()`, `Arrays.parallelSort()` and friends, `Object.wait()`/`notify()` |
| Processes and the runtime        | `System` (except `nanoTime()`, `currentTimeMillis()`, `arraycopy()`, `identityHashCode()`, `lineSeparator()`), `Runtime`, `ProcessBuilder`, `Process`, `ProcessHandle`, `StackWalker`, `SecurityManager`, `java.lang.management.*` |
| Files and I/O                    | `File`, `FileInputStream`, `FileOutputStream`, `FileReader`, `FileWriter`, `RandomAccessFile`, `FileDescriptor`, the constructors of `PrintStream` and `PrintWriter`, `java.nio.file.*`, `java.nio.channels.*`, `ZipFile`, `JarFile` |
| Network and remote access        | `java.net.*`, `java.rmi.*`, `javax.naming.*` (JNDI), `java.sql.DriverManager`          |
| Serialization                    | `ObjectInputStream`, `ObjectOutputStream`, `java.beans.*`                              |
| JVM-global state                 | `Locale.setDefault()`, `TimeZone.setDefault()`, `java.util.logging.*`, `java.util.prefs.*`, `javax.management.*` |
| System properties                | `Integer.getInteger()`, `Long.getLong()`, `Boolean.getBoolean()` (and `System.getProperty()`, see above) |
| Other                            | `Throwable.printStackTrace()`, `javax.script.*`, `javax.tools.*`, `java.lang.foreign.*`, `java.lang.ref.Cleaner`, and all JDK-internal packages (`sun.*`, `jdk.*`, `com.sun.*`) |

`SandboxPolicy.isNeverAllowed(MemberRef)` tells whether a given member is on this list.

Independently of any policy, the verifier always rejects `native` methods and `invokedynamic` instructions with
bootstrap methods other than those that compilers generate.

### 5.1 Enabling a never-allowed member deliberately

If you really need one of these members, you can allow it by **naming it explicitly**:

```java
SandboxPolicy policy = SandboxPolicy.builder()
    .include(SandboxPolicy.JAVA_LANG_BASIC)
    .allowMethod("java.lang.System", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;")
    .build();
```

`allowMethod(...)`, `allowMethods(...)` and `allowField(...)` override the never-allowed list for exactly the
members they name; `allowAllMembers(...)` and `allowConstructors(...)` never do. This is deliberate: a class-wide
rule cannot open a hole by accident, but an explicit rule is your decision.

Be careful - each such rule weakens the sandbox. `System.getProperty(String)` for example lets scripts read *any*
system property, including paths and credentials that may be passed as properties. A capability object (see 4.4)
that returns only the values that scripts need is almost always the better solution. Members like
`Class.forName()`, `Thread.<init>` or anything in `java.lang.reflect` would effectively disable the sandbox.

---

## 6. Limitations

- **No resource limits (yet).** The policy restricts *which* APIs code may use, but not how much CPU time or memory
  it consumes. An infinite loop, or `new long[Integer.MAX_VALUE]`, is not prevented. If you run code from untrusted
  sources, execute it on a separate thread with a timeout, and consider running it in a separate JVM process with
  OS-level limits for hard guarantees.
- **Allowed APIs run unrestricted.** If you allow a method, everything that method does internally is allowed as
  well. Only allow APIs whose behavior you understand; prefer narrow capability objects.
- **Objects passed in by the host.** Code may call any *allowed* method on objects that your application passes to
  it. Since the checks apply to the declaring class, a method that your policy allows on an interface (e.g.
  `List.get()`) can be invoked on any implementation that you pass in.
- **No source locations.** Violations name the generated class and the member, but not the line in the source code.
- **Behavior change compared to the legacy sandbox:** `Class.forName(...)` is never allowed (the legacy sandbox
  allowed loading some classes this way). Class literals (`String.class`) remain usable.

### Recommended JVM options

When running untrusted code on current JVMs, also consider these options, which close further doors at the JVM
level:

- `--illegal-native-access=deny` (Java 24+): no JNI for code outside the modules you enable with
  `--enable-native-access=...`.
- `--sun-misc-unsafe-memory-access=deny` (Java 23+): no `sun.misc.Unsafe` memory access.
- `-XX:-EnableDynamicAgentLoading` (Java 9+): no agents attached at runtime.

---

## 7. The security-manager-based sandbox

Applications that run on JVMs with security manager support (Java 8 through 17, or Java 18 through 23 with
`-Djava.security.manager=allow`) can still use the sandbox of earlier versions. Instead of verifying the code at
compile time, it confines the code **at runtime** to a set of `java.security` permissions.

> **Warning: Code can escape from this sandbox.** Code that runs inside `Sandbox.confine()` can call
> `java.security.AccessController.doPrivileged(...)` itself, and thus perform actions that the sandbox's permissions
> do not allow - even with no permissions at all (reported as
> [issue #226](https://github.com/janino-compiler/janino/issues/226) of the original project). The reason is that
> the generated classes are defined with the protection domain of JANINO itself, and the sandbox grants all
> permissions to all code outside `confine()`. Therefore, do not rely on this sandbox alone to run untrusted code:
> combine it with a sandbox policy, which rejects such code at compile time (see the end of this section), or use a
> sandbox policy instead.

Example:

```java
import java.security.Permissions;
import java.security.PrivilegedAction;
import java.util.PropertyPermission;

import org.codehaus.commons.compiler.Sandbox;
import org.codehaus.janino.ScriptEvaluator;

// Allow reading the system property "foo", and forbid everything else.
Permissions permissions = new Permissions();
permissions.add(new PropertyPermission("foo", "read"));

ScriptEvaluator se = new ScriptEvaluator();
PrivilegedAction<?> pa = se.createFastEvaluator((
    "System.getProperty(\"foo\");\n" +
    "System.getProperty(\"bar\");\n" +
    "return null;\n"
), PrivilegedAction.class, new String[0]);

// Reading "foo" succeeds; reading "bar" throws
//    java.security.AccessControlException: access denied ("java.util.PropertyPermission" "bar" "read")
Sandbox sandbox = new Sandbox(permissions);
sandbox.confine(pa);
```

On JVMs without security manager support, the `Sandbox` constructor throws an `UnsupportedOperationException` that
explains the requirements; check `Sandbox.isSupported()` beforehand if your application runs on different JVMs.

How the two mechanisms differ:

| Aspect                         | `Sandbox` (security manager)                                 | Sandbox policy                                       |
|--------------------------------|--------------------------------------------------------------|------------------------------------------------------|
| JVMs                           | Java 8 - 17 (18 - 23 with `-Djava.security.manager=allow`)   | Java 8 and later                                     |
| When                           | At runtime, for each guarded operation                       | Once, at compile time                                |
| Granularity                    | Permissions, including parameters (e.g. file paths, hosts)   | Fields, methods and constructors                     |
| Scope                          | Only code executed *inside* `confine()`; threads started there inherit the restrictions | All code of the compiled classes, also when invoked later |
| Effect on the JVM              | Installs a security manager (with a permissive policy for all other code) when the class is first used | None                     |
| Memory, CPU time               | Not restricted                                               | Not restricted                                       |
| Known weaknesses               | Code can escape by calling `AccessController.doPrivileged()` (see the warning above) | Not affected: the presets do not allow `AccessController` |

Notice the scope: if a script returns an object (e.g. a `Runnable`), and the application invokes it later outside
of `confine()`, that code runs with the full permissions of the application.

On JVMs that support both, the two mechanisms can be combined: set a sandbox policy on the cookable, and execute the
compiled code inside `Sandbox.confine()`. The policy then rejects forbidden APIs at compile time, including
`AccessController.doPrivileged(...)`, which closes the escape described in the warning above; the permissions
restrict the allowed APIs at runtime. Because the example above lets the script implement `PrivilegedAction`, the
policy must allow that (see 3.1):

```java
SandboxPolicy policy = SandboxPolicy.builder()
    .include(SandboxPolicy.JAVA_LANG_BASIC)
    .allowMethod("java.lang.System", "getProperty", "(Ljava/lang/String;)Ljava/lang/String;")
    .allowSubclassing("java.security.PrivilegedAction")
    .build();

ScriptEvaluator se = new ScriptEvaluator();
se.setSandboxPolicy(policy);   // compile time: only JAVA_LANG_BASIC and System.getProperty(String)
PrivilegedAction<?> pa = se.createFastEvaluator(script, PrivilegedAction.class, new String[0]);
new Sandbox(permissions).confine(pa);   // runtime: only the system property "foo"
```

---

## 8. API reference (package `org.codehaus.commons.compiler.sandbox`)

| Type / method                                                    | Purpose                                                   |
|------------------------------------------------------------------|-----------------------------------------------------------|
| `SandboxPolicy`, `SandboxPolicy.builder()`, `SandboxPolicy.Builder` | The allowlist and its builder                          |
| `SandboxPolicy.JAVA_LANG_BASIC`, `SandboxPolicy.COLLECTIONS`     | Presets                                                   |
| `SandboxPolicy.isAllowed(MemberRef)`, `isSubclassingAllowed(String)` | Queries                                               |
| `SandboxPolicy.isNeverAllowed(MemberRef)`                        | Whether a member is on the never-allowed list             |
| `MemberRef.field(...)`, `MemberRef.method(...)`                  | Identifies a field, method or constructor                 |
| `ICookable.setSandboxPolicy(SandboxPolicy)`                      | Restricts expressions, scripts, class bodies, compilation units |
| `AbstractJavaSourceClassLoader.setSandboxPolicy(SandboxPolicy)`  | Restricts classes loaded from source files                |
| `BytecodeVerifier`                                               | Verifies class files against a policy                     |
| `SandboxViolation`                                               | One violation: class name and message                     |
| `SandboxViolationException`                                      | Cause of the `CompileException` / `ClassNotFoundException`; `getViolations()` |
| `org.codehaus.commons.compiler.Sandbox`, `Sandbox.isSupported()` | The security-manager-based sandbox (section 7)            |
