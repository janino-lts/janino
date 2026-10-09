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
| [#73](https://github.com/janino-lts/janino/issues/73) | Local and anonymous classes as `javac` describes them | changed | reflection results |
| [#74](https://github.com/janino-lts/janino/issues/74) | No `ACC_STRICT` in the flags of `strictfp` classes | changed | unchanged |
| [#76](https://github.com/janino-lts/janino/issues/76) | `AbstractTraverser` descends into initializers | unchanged | subclasses of `AbstractTraverser` |
| [#87](https://github.com/janino-lts/janino/issues/87) | Class flags of member types as `javac` writes them | changed | unchanged |
| [#88](https://github.com/janino-lts/janino/issues/88) | `AbstractTraverser` visits enum constants | unchanged | subclasses of `AbstractTraverser` |

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
