# Development line

This page exists only on `master`, the development line (see [Release lines](COMPATIBILITY.md#release-lines)). It
lists every change on `master` that the 3.1.x maintenance line does not have, and what it means for a project that
moves from a 3.1.x release to a build of `master`. When and as which version these changes are released is open.

`master` contains all fixes of the 3.1.x line (they are made on `3.1.x` first and then merged into `master`); they
are not listed here, but in the entries of the 3.1.x releases in the
[change log](https://janino-lts.github.io/janino/changelog.html).

## What is different from the rules of 3.1.x

What [Compatibility](COMPATIBILITY.md) promises applies to `master` as well, with one exception: the class files of
code that compiled correctly before, and the results of the reflection API for the classes in them, may change
where Janino deviates from `javac`. Every such change is listed below, with what changes in the class files, what
changes in behavior and what stays the same; the class file comparison of the
[benchmarks](janino-benchmarks/README.md) (`CodeSizeReport`) is run against the latest 3.1.x release.

## Changes

| Issue | Change | Class files | Behavior |
|---|---|---|---|
| [#31](https://github.com/janino-lts/janino/issues/31) | Compile error messages explain the error | unchanged | message texts |
| [#73](https://github.com/janino-lts/janino/issues/73) | Local and anonymous classes as `javac` describes them | changed | reflection results |
| [#74](https://github.com/janino-lts/janino/issues/74) | No `ACC_STRICT` in the flags of `strictfp` classes | changed | unchanged |
| [#76](https://github.com/janino-lts/janino/issues/76) | `AbstractTraverser` descends into initializers | unchanged | subclasses of `AbstractTraverser` |
| [#87](https://github.com/janino-lts/janino/issues/87) | Class flags of member types as `javac` writes them | changed | unchanged |
| [#88](https://github.com/janino-lts/janino/issues/88) | `AbstractTraverser` visits enum constants | unchanged | subclasses of `AbstractTraverser` |
| [#103](https://github.com/janino-lts/janino/issues/103) | The compliance mode: the option `JAVAC_COMPLIANCE` (S-01 to S-03) | unchanged | only with the option |
| [#40](https://github.com/janino-lts/janino/issues/40) | The compliance mode: `Z \|\| true` and `Z && false` unbox `Z` (S-01) | unchanged | only with the option |
| [#101](https://github.com/janino-lts/janino/issues/101) | The compliance mode: enclosing instances like `javac` (S-11) | unchanged | only with the option |
| [#38](https://github.com/janino-lts/janino/issues/38) | The compliance mode: conditional expressions like `javac` (S-04, S-12) | unchanged | only with the option |

### Compile error messages (#31)

**Behavior.** Some compile error messages pointed in the wrong direction; now they explain the error. Janino rejects
the same code as before, at the same locations. Applications and their tests match message texts, so each message
keeps the text that it had in 3.1.x, at its beginning, and the explanation is appended (`...` in the table); a check
with `contains()` or `startsWith()` still matches. The exceptions are a typo and cyclic inheritance (see below).

| Code | 3.1.x | `master` |
|---|---|---|
| `public public void f() {}` | `Duplication access modifier "public"` | `Duplicate access modifier "public"` |
| `int i = 09;` | `';' expected instead of '9'` | `...; digit '9' not allowed in octal literal` |
| `interface I { static { } }` | `IDENTIFIER expected instead of '{'` | `...; an interface cannot declare an initializer` |
| `this(1);` or `super(1);` not as the first statement of a constructor | `Expression "this()" is not an rvalue` | `...; an explicit constructor invocation is only allowed as the first statement of a constructor body` |
| `int x; static int f() { return x; }` | `Expression "P" is not an rvalue` | `...; non-static field "x" cannot be referenced from a static context` |
| `java.foo.Bar x;` | `Cannot determine simple type name "java"` | `...; no type "java.foo.Bar" found` |
| `class A extends B {} class B extends A {}` | `Compilation unit is nested too deeply` | `Class circularity detected for "A"` |

The message about a simple name that denotes no type, e.g. `Cannot determine simple type name "Foo"`, is unchanged.

For cyclic inheritance, the check for a circularity used to recurse until a `StackOverflowError`, which Janino
reported as `Compilation unit is nested too deeply` (`ClassBodyEvaluator` and `ScriptEvaluator`: `Script is nested
too deeply`; `JavaSourceClassLoader` threw the `StackOverflowError`). Now the check detects the cycle and reports the
message that Janino already had for it, `Class circularity detected for "A"` (for interfaces: `Interface circularity
detected for "I"`), without a location, like the old message; `JavaSourceClassLoader` throws a
`ClassNotFoundException` with this message. The old text is not kept, because it described the stack overflow and
depended on the entry point. Cycles that Janino did not detect before are compiled as before: an empty cycle of
interfaces (the JVM rejects the class files) and a class that extends its own member type.

**Class files.** Unchanged.

### Local and anonymous classes (#73)

**Class files.** Like `javac`, Janino now writes an `InnerClasses` entry for every local and anonymous class, both
into its own class file and into that of the enclosing class, and an `EnclosingMethod` attribute into its class
file. The class flags of local and anonymous classes are `ACC_SUPER`, like with `javac`, without the bit `0x0002`,
which is not a class flag (the JVM ignores both). The instructions are unchanged. Measured against 3.1.18: the class
files of the code that Apache Spark 4.2.0 generates for twelve TPC-DS queries grow by 0.4 %, those of Janino's own
sources by 1.5 %; those of the synthetic workloads are identical.

**Behavior.** The reflection API now recognizes local and anonymous classes. For `class L {}` and
`new Object() {}` in a method `run()` of a class `P`:

| | 3.1.x | `master` (like `javac`) |
|---|---|---|
| `getSimpleName()` | `"P$L"`, `"P$1"` | `"L"`, `""` |
| `getCanonicalName()` | `"P$L"`, `"P$1"` | `null` |
| `isLocalClass()`, `isAnonymousClass()` | `false` | `true` (`L`), `true` (`P$1`) |
| `getEnclosingClass()` | `null` | `P` |
| `getEnclosingMethod()` | `null` | `run` |

The same holds for classes in constructors (`getEnclosingConstructor()`), in field initializers and initializers
(enclosing class only), in other local and anonymous classes, in the class bodies of enum constants and in the
constants of interfaces. Code that relies on the old results, for example on `getSimpleName()` of an anonymous
class not being empty, sees the new ones.

**What stays the same.**

- An anonymous class keeps the flag `ACC_FINAL` (`javac` 9 and later: none), so `getModifiers()` still returns
  `final`, and the default `serialVersionUID` of a serializable anonymous class is the same as with 3.1.12 to
  3.1.18; serialized instances stay readable. The test `LocalAndAnonymousClassFilesTest` fixes the value.
- The binary names of local classes stay `P$L` (`javac`: `P$1L`).
- For a class in a `private` instance method `p()`, `getEnclosingMethod()` returns `p$`, the static method that
  Janino compiles `p()` into (`javac`: `p`).

The three deviations from `javac` are listed in [Differences between Janino and javac](JAVAC_DIFFERENCES.md),
section 1.

### `strictfp` classes (#74)

**Class files.** The class file of a `strictfp` class no longer has the bit `0x0800` (`ACC_STRICT`, a method
flag) in its class flags, like with `javac`.

**Behavior.** Unchanged; the JVM ignored the bit. Only tools that read class files see the difference.

### `AbstractTraverser` descends into initializers (#76)

**Behavior.** `org.codehaus.janino.util.AbstractTraverser` (public API) now descends into the initializers of local
variables and fields and into the values of array initializers. In 3.1.x, it passes such an rvalue to `traverseRvalue()`
only, so that a subclass sees neither the specific `traverse*()` method of the rvalue (e.g.
`traverseMethodInvocation()`) nor its subordinate nodes: for `int y = x + 1;`, the `traverseAmbiguousName()` of a
subclass is not invoked for `x` in 3.1.x and is on `master`. A subclass that counts or collects nodes sees more of them.
A subclass that overrides `traverseArrayInitializerOrRvalue()` to descend itself, as 3.1.x does in
`guessParameterNames()`, works as before and no longer needs the override.

**Class files.** Unchanged. The compiler uses `AbstractTraverser` to find the variables that are not effectively
final, which descended into initializers already, and to set the enclosing scope of rvalues, which does not reach
the nodes that are new to the traversal.

**What stays the same.** The results of `ExpressionEvaluator.guessParameterNames()` and
`ScriptEvaluator.guessParameterNames()` are the same as with 3.1.18.

### Class flags of member types (#87)

The 3.1.x line fixes the part of #87 that changes behavior (3.1.19): a `protected` member type gets `ACC_PUBLIC`, so
that a subclass in another package can use it. `master` has that fix, too, and also the rest:

**Class files.** The `access_flags` of a member type (a class, interface, enum or annotation type declared in the
body of another type) no longer contain the bits `0x0002` (`private`) and `0x0008` (`static`), which are not class
flags (JVMS 4.1) and which only the `InnerClasses` entry records, and a member class (not an interface) has the flag
`ACC_SUPER`, like with `javac`. For example, `static class S` has the flags `0x0020` (3.1.x: `0x0008`), `public
class I` `0x0021` (3.1.x: `0x0001`), `private static class P` `0x0020` (3.1.x: `0x000a`), and `interface J`
`0x0600` (3.1.x: `0x0608`). The size of the class files does not change.

**Behavior.** Unchanged. The JVM ignores the bits (and treats `ACC_SUPER` as set in every class file since Java 8);
the reflection API, and with it the default `serialVersionUID` of a serializable member class, reads the modifiers
from the `InnerClasses` entry, which is unchanged. When Janino compiles against such a class file, it reads only the
access bits, `ACC_FINAL`, `ACC_ABSTRACT`, `ACC_ENUM` and `ACC_INTERFACE` from these flags, and accepts the same code
as before.

**What stays the same.** A `protected` member type keeps `0x0004` (`ACC_PROTECTED`) in addition to `ACC_PUBLIC`
(`javac`: `ACC_PUBLIC` only), like in 3.1.x: Janino determines the accessibility of a type that it reads from a class
file from these flags, and with both bits it still sees a `protected` type. See
[Differences between Janino and javac](JAVAC_DIFFERENCES.md), section 1.

### `AbstractTraverser` visits enum constants (#88)

**Behavior.** `org.codehaus.janino.util.AbstractTraverser` (public API) now visits the constants of an enum
declaration: it invokes `traverseEnumConstant()`, which 3.1.x never invokes, and descends into the arguments of a
constant, its class body (including its fields and initializers) and its annotations. For
`enum E { @Deprecated A(f(x)) { void m() { int y = z; } }, B; ... }`, a subclass sees `A`, `B`, `@Deprecated`, `x`,
`m` and `z` on `master`, and none of them in 3.1.x. A subclass that counts or collects nodes sees more of them; the
sample `DeclarationCounter` counts the class bodies of enum constants and their fields and local variables.

**Class files.** Unchanged.

**What stays the same.** Janino's own traversers do not look into enum constants, so that their results are the
same as before: the effectively final analysis (an assignment to a field in the class body of an enum constant
would otherwise count against a local variable of the same name, and code that compiles in 3.1.x would be rejected),
and `ExpressionEvaluator.guessParameterNames()` and `ScriptEvaluator.guessParameterNames()` (a name in an enum
constant cannot denote a parameter).

### The compliance mode (#103)

[Janino and javac: the compatibility mode and the compliance mode](JAVAC_COMPLIANCE.md) describes two modes: the
compatibility mode, in which code that Janino has always accepted keeps compiling with its established behavior,
and the compliance mode, in which Janino behaves like `javac`. `master` has the foundation of the compliance mode
([#103](https://github.com/janino-lts/janino/issues/103)).

**The option.** `JaninoOption.JAVAC_COMPLIANCE`, set with `options(...)` of `SimpleCompiler`, `Compiler`,
`JavaSourceIClassLoader` or the evaluators. The system property `org.codehaus.janino.javacCompliance=true` adds the
option to the initial options of every compiler that is created afterwards (`JaninoOption.defaultOptions()`), so
that a whole application can be checked without code changes; an explicit `options(...)` call replaces the initial
options, so that a library keeps control of its own compilers. The default is the compatibility mode, as before.

**Behavior.** The compliance mode is incomplete by design: the register of deviations in [Differences between Janino
and javac](JAVAC_DIFFERENCES.md) is authoritative for what it corrects, so "compiles in the compliance mode" does not
mean "is valid Java". Language features that Janino does not implement, and everything that depends on the typing of
generics, remain outside its scope; the other options apply in both modes. So far, the compliance mode corrects
six deviations, each a consistent rule of Janino's that programs could rely on (class S of the contract):

| ID | Code | Compatibility mode | Compliance mode (like `javac`) |
|---|---|---|---|
| S-01 | `Boolean Z = null; boolean x = Z \|\| true;` (also `Z && false`) | `x == true`, no exception | `NullPointerException` ([#40](https://github.com/janino-lts/janino/issues/40)) |
| S-02 | `f(Object o)` and `f(int... i)`; `f(1)` | invokes `f(int...)` | invokes `f(Object)` (variable arity methods only in phase 3 of JLS 15.12.2) |
| S-03 | `assert false;` | always throws | throws only if assertions are enabled for the class |
| S-04 | `Byte B = 1; Object x = z ? B : 5;`; `char c = 'a'; Object x = false ? c : (short) 66;` | `x` is an `Integer`; `x` is a `Character` | `x` is a `Byte`; `x` is an `Integer` ([#38](https://github.com/janino-lts/janino/issues/38)) |
| S-11 | `P.this`, and the simple name of a `private` member of `P`, in `class Q extends P` declared in `P` | `this` | the enclosing instance of `Q` ([#101](https://github.com/janino-lts/janino/issues/101)) |
| S-12 | `String s = "x"; boolean x = ("a" + (true ? "b" : s)) == "ab";` | `x == true` | `x == false` (the expression is not a constant expression) |

For S-03, the compliance mode declares a synthetic field `static final boolean $assertionsDisabled` in every class
that contains an `assert` statement, initialized with `!Outermost.class.desiredAssertionStatus()` as the first
statement of the class initializer, like `javac`; an `assert true;` generates no code and no field, like with
`javac`. Two consequences: the default `serialVersionUID` of a serializable class with an `assert` statement is the
one that `javac` computes (it includes the field), and differs from the compatibility mode's; and for an interface,
the field is a `public static final` field of the interface itself, where `javac` declares it in a synthetic
class.

For S-11, the compliance mode resolves `T.this` to the lexically enclosing instance of class `T` (JLS 15.8.4), also in
`P.this.f`, `P.this.m()`, `P.super.f`, in anonymous and local subclasses of `P` and in classes nested in `Q`; and the
`private` members of a superclass are not inherited (JLS 8.2), so that their simple names denote the members of the
enclosing instance. As a consequence, the compliance mode rejects three forms of invalid code that the compatibility
mode accepts, like `javac`: the access to a `private` member of a superclass through the subclass (`this.secret` in
`Q`, L-45, and the invocation of a `private` enum method from the body of an enum constant, L-20), and `B.this` for a
superclass `B` of an enclosing class (L-46). In both modes, compiling `P.this` as a value of type `P` no longer fails
with an `InternalCompilerException` when the compiler's assertions are enabled (`-ea`).

For S-04, the compliance mode determines the type of a conditional expression with numeric operands of different
types like `javac` (JLS 15.25.2): an operand of type `Byte`, `Short` or `Character` and a constant of type `int` that
is representable in its unboxed type give the unboxed type (`z ? B : 5` is a `byte`), and with a constant condition,
the operands are subject to binary numeric promotion, like with a variable condition (`true ? s : c` is an `int`,
not a `short`). The type determines the class of the boxed value, overload resolution and string conversion. As a
consequence, the compliance mode rejects code that is valid only with the type of the compatibility mode
(`short x = true ? s : c;`, L-47), and accepts valid code that the compatibility mode rejects because of the type `int`
(`byte x = z ? B : 5;`, D-07). For S-12, found with S-04, a conditional expression with a constant condition is a
constant expression only if all three operands are (JLS 15.28), so that `"a" + (true ? "b" : s)` with a variable `s`
is not an interned constant, and `byte x = false ? i : 5;` and `case false ? i : 5:` with an `int i` are rejected
(L-48).

**Class files.** Unchanged in the compatibility mode.

**Tests.** The record tests (`InvalidCodeTest`, `LanguageSupportTest`) run every case in both modes: a record
describes the compatibility mode (`janino:`) and, where the compliance mode differs, the compliance mode
(`compliant:`, with the `id:` of the deviation). `ExpressionDifferentialTest` and `ControlFlowDifferentialTest` run
in both modes as well, with the known differences of each mode in its own file.

The reference of the compatibility mode is Janino 3.1.12 (see the contract in `JAVAC_COMPLIANCE.md`): code that
3.1.12 compiled into loadable classes keeps its behavior, except where 3.1.12 miscompiled it. `LegacyDifferentialTest`
makes this machine-checkable: it compiles every recorded case with 3.1.12 (loaded from Maven Central into a class
loader of its own, with target version 8) and compares its behavior with the recorded behavior of the compatibility
mode. Where the two differ, the record states the behavior of 3.1.12 (`legacy:`) and the correction that explains the
difference (`id:`, an issue number or an ID of the register); a difference that no record states, e.g. a regression,
fails the test. Today, 395 of the 1004 recorded cases differ from 3.1.12, each with its correction: the fixes of
classes D and V since 3.1.13, the registered exceptions A-04, A-05 and A-06, and the language features that 3.1.12
did not compile (multi-catch, qualified superclass method invocations, effectively final variables, local classes
with modifiers).

**The register.** [Differences between Janino and javac](JAVAC_DIFFERENCES.md) states the contract of the two modes
and lists every known deviation with a stable ID and its class: S (a consistent rule of Janino's, kept in the
compatibility mode), D (not compiled by 3.1.12 either, fixed in both modes), L (invalid code that is accepted; the
compliance mode will reject it), G and F (generics and unimplemented features, outside the scope of both modes), and
A (registered exceptions, frozen). Every test record that documents a deviation names its ID (`id:`), so that the
cases of an ID can be found. When the register was created, `LegacyDifferentialTest` showed two released corrections
of 3.1.16 and 3.1.17 that also reject a form of invalid code which 3.1.12 compiled; they are registered as the
exceptions A-07 (a cast of the clone of an array to an unrelated array type, #58) and A-08 (a duplicate annotation on
a parameter, #61).
