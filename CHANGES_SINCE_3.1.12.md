# Changes since 3.1.12

For projects that depend on Janino 3.1.12, the last release of the original project, and consider an upgrade:
what the releases 3.1.13 to 3.1.19 of this continuation change, in short. The
[change log](https://janino-lts.github.io/janino/changelog.html) has the complete entries, with details and
examples; this page summarizes it. State: 3.1.19, released on 2026-10-10. The entries of 3.1.17 and later are
marked with the version.

## What stays the same

- The Java packages and the public API of 3.1.12, the OSGi bundle symbolic names and the automatic module names;
  nothing was removed, with one exception: the two constructors of the AST class `Java.Wildcard` gained a
  parameter for the annotations of the wildcard (upstream pull request #214, in 3.1.13). See
  [Compatibility](COMPATIBILITY.md).
- Invalid code that 3.1.12 accepts is still accepted ([JAVAC_DIFFERENCES.md](JAVAC_DIFFERENCES.md), section 3;
  [#33]), with the exceptions listed under "Code that is now rejected" below.
- Code that compiled correctly with 3.1.12 compiles into the same class files, except where a fix changes them
  (the change log names these cases) and except for the class file version (below). For the code that Apache
  Spark 4.2.0 generates for twelve TPC-DS queries, the class files of 3.1.19 differ from those of 3.1.12
  only in the class file version ([benchmarks](janino-benchmarks/README.md)).

## What changes for every user

- **Java 8 or later** is required since 3.1.13 (3.1.12: Java 7). The releases are tested on Java 8, 17, 21 and 25.
- **Default target version 8** (was 6) since 3.1.13: the generated class files have the version 52 instead of 50,
  and default methods and static interface methods compile without `setTargetVersion(8)`. The old default can be
  restored with `-Dorg.codehaus.janino.UnitCompiler.defaultTargetVersion=6`. A side effect: the JVM no longer
  falls back to its old bytecode verifier for the generated classes, which exposed defects of the code
  generation for `try` statements; they are fixed (3.1.14, below).
- **Maven group ID** `io.github.janino-lts` since 3.1.15 (3.1.13 and 3.1.14 are GitHub releases only). The
  packages are unchanged. Where another dependency brings in the original `org.codehaus.janino` artifacts, exclude
  them; see the [README](README.md).
- **The security-manager-based `Sandbox`** is deprecated since 3.1.13, but still present. Its replacement, sandbox
  policies and the `SandboxExecutor`, works on Java 8 and later without a security manager; see
  [Restricting Untrusted Code with a Sandbox Policy](SANDBOX.md).

## Fixes

All of these defects exist in 3.1.12, unless noted otherwise.

**Code generation for `try` statements** (3.1.13, 3.1.14, 3.1.16):

- `VerifyError`s and internal compiler errors when a `finally` block cannot complete normally, or when a
  `return`, `break` or `continue` leaves a `try` block whose `finally` block contains a `try` or `synchronized`
  statement or a jump ([#2]); after a `try` block that cannot complete normally ([#7]); for a `do` statement whose
  body ends with `continue` (3.1.13).
- A `finally` block was executed twice when a `catch` clause handled an exception, when the copy of the block
  before a jump threw ([#3]), or when a jump left a `synchronized` statement inside a `try` statement ([#5]).
- Try-with-resources statements now close the resources on `return`, `break` and `continue`, add the exceptions
  from `close()` as suppressed exceptions, and close the resources before the `catch` clauses and the `finally`
  block run (JLS 14.20.3, [#4]).
- Since 3.1.13, a local variable assigned in the body of a `do` statement, or in the right operand of `&&` or
  `||`, compiled into class files that the JVM rejects ([#57]); a variable assigned in the condition of a loop or
  of a conditional expression could not be read in the body or the operands (all versions).

**Constants and conversions** (3.1.15):

- The value of a compound assignment used as an expression, e.g. `a[i] += x` or `I += 2` with an `Integer I`,
  was wrong, failed verification or crashed the compiler ([#35]).
- The bitwise and shift operators did not unbox their operands when determining the result type ([#36]).
- Narrowing conversions to `char` and from `char` to `short` generated wrong opcodes ([#37]).
- The constant folding of the unary `+` and `-` did not promote `byte`, `short` and `char` to `int` ([#39]).
- `final` fields of type `char`, and of a wrapper or other reference type, with a constant initializer were
  rejected or caused a `ClassFormatError` ([#45]).
- `boolean`, `byte`, `char` and `short` constants of fields from class files were treated as `int` ([#46]).
- The constant folding of a string concatenation with `null` crashed the compiler ([#49]).

**Conditional expressions** (3.1.16, 3.1.19):

- With a `byte`, `short` or `char` operand and an operand of another numeric type, the expression was rejected,
  had the wrong type or compiled into code that failed at run time ([#51], a regression of 3.1.15; [#56]).
- The constant value of a conditional expression with a constant condition had the type of the selected operand
  instead of the type of the expression: `"" + (true ? 1 : 2.0)` was `"1"` ([#55]).
- 3.1.19: A conditional expression with a constant condition in the initializer of a
  `static final` field of a class, e.g. `static final int K = DEBUG ? 1 : 2;`, was an internal compiler error; as
  the value of an annotation element, `@A(true ? 1 : 2)`, it was rejected, and so was `@B(X || Y)` ([#113]).

**Annotations, enums and member types** (3.1.15, 3.1.16):

- Annotation element values and default values whose type differs from the element type could not be read through
  reflection ([#48]).
- Annotations of member annotation types were invisible at run time, and `Class.getModifiers()`,
  `isMemberClass()`, `getDeclaringClass()` and `getSimpleName()` of member types were incomplete ([#43]).
- Annotations on method and constructor parameters were not written to the class files ([#61]).
- The member types of an interface were not `public` and `static`, and an `enum` in an interface was a syntax
  error ([#62]).
- The class bodies of enum constants were ignored ([#44]).

**Inner classes, scopes and access** (3.1.16, 3.1.17, 3.1.19):

- An anonymous or local class in a `catch` clause could access neither the `catch` parameter nor a local variable
  declared before the `try` statement ([#63]).
- `Outer.super.fld` from an inner class was rejected ([#66]).
- A class that implements an interface with a static method was rejected when the interface was declared in the
  compiled code ([#53]).
- 3.1.17: A `protected` member that an enclosing class inherits from a class in another package, accessed from an
  inner class, compiled into a class that throws an `IllegalAccessError`; Janino now generates a synthetic accessor
  like `javac` ([#59]). The resource variable of a try-with-resources statement was not accessible ([#64]). The
  `final` loop variable of a basic `for` statement, captured by an inner class, was an internal compiler error
  ([#75]). A type in a `throws` clause whose name consists of uppercase letters only was taken for a type
  parameter ([#65]).
- 3.1.19: A `protected` member type (class, interface or enum) could not be used from a subclass of
  the enclosing type in another package: the JVM threw an `IllegalAccessError`. Its class file now has the flag
  `ACC_PUBLIC`, like with `javac` ([#87]); the class files of all other code are unchanged. An inner class whose
  superclass is an inner class that extends the enclosing class, e.g. `class R extends Q` with `class Q extends P`
  in `P`, compiled into a class that the JVM rejects (`VerifyError`); the constructor passed its own, still
  uninitialized instance to the constructor of the superclass instead of the enclosing instance ([#97]).
  `Outer.super.m()` in an inner class that itself extends `Outer`, in a class nested in it, or in an anonymous or
  local subclass of `Outer`, invoked the superclass method on the instance of the inner class instead of the
  enclosing instance ([#110], since 3.1.16).

**Arrays, expressions and literals** (3.1.17, 3.1.19, 3.1.20):

- `clone()` of an array had the type `Object` instead of the array type ([#58]).
- A subscript on an expression that is not an array was an internal compiler error when the expression was a
  method or constructor argument ([#71]).
- 3.1.19: A floating-point literal with a leading zero, e.g. `09.5`, `07e1` or `07f`, was
  rejected ([#96]); except for `0_9.5` and the like, with an underscore directly before the first digit `8` or `9`.
  An integer literal with a leading zero and the digit `8` or `9`, e.g. `09`, is still rejected with the same message.
- 3.1.20 (in development): An array of a primitive type as the only variable arity argument of a parameter
  `Object...` or `Serializable...` was rejected when another argument needs boxing, e.g. `f(1, new int[] { 1, 2 })`
  with `f(Integer i, Object... a)` ([#127]); now the array is the only element of `a`, like with `javac`.

**Invalid code that crashed the compiler or compiled into class files that the JVM rejects** is rejected with a
compile error now: overriding a `final` method, extending a `final` class, duplicate fields or member types,
`int x; x++;` ([#30]); illegal combinations of modifiers, the same interface twice in an `implements` clause,
`protected` access from another package through an expression of another type, a `protected` constructor invoked
from another package, a local variable that is not definitely assigned ([#54]); and various internal compiler
errors, also after a first compile error when the error handler does not throw ([#32], [#34]).

**API and tools** (3.1.13, 3.1.15, 3.1.18):

- The `cookFiles(...)` methods of `IMultiCookable` did not name the files in compile errors, and
  `cookFiles(String[])` failed with a `NullPointerException` ([#19]); a `NullPointerException` when a directory
  could not be listed ([#20]).
- New overloads `StringResource(String, String, Charset)` and `MapResourceFinder.addResource(String, String,
  Charset)`; the "jdk" implementation no longer replaces characters that the platform charset cannot represent
  ([#6]).
- The command line compiler reports the options `-g:lines` and `-g:vars` correctly with `-verbose`;
  `DeepCopier` can be compiled by Janino itself.
- 3.1.18: `ExpressionEvaluator.guessParameterNames()` and `ScriptEvaluator.guessParameterNames()` did not find
  the names in the initializers of variables and fields and in array initializers ([#76]).

## New language support

- Multi-catch clauses, `catch (IOException | SQLException e)` ([#21], 3.1.16); before, they were parsed but
  rejected ("NYI").
- Qualified superclass method invocations, `Interface.super.meth()` and `Outer.super.meth()` ([#22], 3.1.16).
- 3.1.17: Effectively final local variables and parameters can be accessed from local and anonymous classes (JLS
  8.1.3, [#24]), with a conservative rule described in [JAVAC_DIFFERENCES.md](JAVAC_DIFFERENCES.md), section 2;
  local classes with modifiers and annotations, `final class L {}` ([#60]); the resource variable of a
  try-with-resources statement is in scope in the block ([#64]).

## Code that is now rejected

Some code that 3.1.12 accepted, but `javac` rejects, is rejected now. Unless noted, the JVM rejected the class
files that 3.1.12 generated for it, so the code never ran:

- 3.1.15: the code of [#30] above; `byte b = -Byte.MIN_VALUE;`, which compiled with a wrong value ([#39]); a
  statement after `while (A.F) {}` with a `boolean` constant `true` from a class file ([#46]).
- 3.1.16: the code of [#54] above, including the access to a `protected` member through an expression that is
  cast to the superclass, `((Object) this).clone()`, which the JVM loaded in some cases; a constant conditional
  expression of type `long`, `float` or `double` where a narrower type is required, `byte b = true ? 1 : 2L;`,
  which compiled with the value of the selected operand ([#55]); the forms of a member annotation type that only an
  ordinary interface permits, e.g. type parameters or an `extends` clause ([#43]); a `catch` parameter whose type is
  not a `Throwable` ([#21]).
- 3.1.15 only: the valid expressions `true ? c : (short) -1` and `false ? c : (short) -1` with a `char c` were
  rejected as a side effect of [#45]; fixed in 3.1.16 ([#51]).

## Sandbox

3.1.13 replaced the security-manager-based sandbox, which does not work on Java 24 and later, with sandbox policies
(`ICookable.setSandboxPolicy()`: which classes and members the compiled code may use, checked at compile time and
by a bytecode verifier, with presets) and the `SandboxExecutor`, which runs the code under resource limits (time,
"ticks", allocated memory). Policy violations carry their source location. See
[Restricting Untrusted Code with a Sandbox Policy](SANDBOX.md). Applications that do not use the sandbox are not
affected.

## Performance and tests

- The compilation of Janino's own sources is about 6 % faster than with 3.1.12 and allocates about 16 % less
  memory ([#26], 3.1.15). Code with many nested `try` / `finally` statements compiles somewhat slower, because the
  code that Janino generates for them since 3.1.14 is correct, but larger.
- On the code that Apache Spark 4.2.0 generates for twelve TPC-DS queries, 3.1.19 and 3.1.12 compile
  equally fast within the measurement error; without the JIT compiler, 3.1.19 needs 1.8 % less time and
  allocates 4.6 % less memory. See the [benchmarks](janino-benchmarks/README.md) for the method and the numbers
  of 3.1.17; every release is measured against its predecessor the same way.
- New tests: characterization tests that record the correct result and Janino's actual behavior for 491 language
  constructs, negative tests with 443 cases of invalid code, differential tests that compile generated expressions
  and control flow with Janino and with `javac` and compare the results, and two workflows that run the test suites
  of Apache Calcite and of Spark Catalyst every week and before every release ([Compatibility](COMPATIBILITY.md)).

[#2]: https://github.com/janino-lts/janino/issues/2
[#3]: https://github.com/janino-lts/janino/issues/3
[#4]: https://github.com/janino-lts/janino/issues/4
[#5]: https://github.com/janino-lts/janino/issues/5
[#6]: https://github.com/janino-lts/janino/issues/6
[#7]: https://github.com/janino-lts/janino/issues/7
[#19]: https://github.com/janino-lts/janino/issues/19
[#20]: https://github.com/janino-lts/janino/issues/20
[#21]: https://github.com/janino-lts/janino/issues/21
[#22]: https://github.com/janino-lts/janino/issues/22
[#24]: https://github.com/janino-lts/janino/issues/24
[#26]: https://github.com/janino-lts/janino/issues/26
[#30]: https://github.com/janino-lts/janino/issues/30
[#32]: https://github.com/janino-lts/janino/issues/32
[#33]: https://github.com/janino-lts/janino/issues/33
[#34]: https://github.com/janino-lts/janino/issues/34
[#35]: https://github.com/janino-lts/janino/issues/35
[#36]: https://github.com/janino-lts/janino/issues/36
[#37]: https://github.com/janino-lts/janino/issues/37
[#39]: https://github.com/janino-lts/janino/issues/39
[#43]: https://github.com/janino-lts/janino/issues/43
[#44]: https://github.com/janino-lts/janino/issues/44
[#45]: https://github.com/janino-lts/janino/issues/45
[#46]: https://github.com/janino-lts/janino/issues/46
[#48]: https://github.com/janino-lts/janino/issues/48
[#49]: https://github.com/janino-lts/janino/issues/49
[#51]: https://github.com/janino-lts/janino/issues/51
[#53]: https://github.com/janino-lts/janino/issues/53
[#54]: https://github.com/janino-lts/janino/issues/54
[#55]: https://github.com/janino-lts/janino/issues/55
[#56]: https://github.com/janino-lts/janino/issues/56
[#57]: https://github.com/janino-lts/janino/issues/57
[#58]: https://github.com/janino-lts/janino/issues/58
[#59]: https://github.com/janino-lts/janino/issues/59
[#60]: https://github.com/janino-lts/janino/issues/60
[#61]: https://github.com/janino-lts/janino/issues/61
[#62]: https://github.com/janino-lts/janino/issues/62
[#63]: https://github.com/janino-lts/janino/issues/63
[#64]: https://github.com/janino-lts/janino/issues/64
[#65]: https://github.com/janino-lts/janino/issues/65
[#66]: https://github.com/janino-lts/janino/issues/66
[#71]: https://github.com/janino-lts/janino/issues/71
[#75]: https://github.com/janino-lts/janino/issues/75
[#76]: https://github.com/janino-lts/janino/issues/76
[#87]: https://github.com/janino-lts/janino/issues/87
[#96]: https://github.com/janino-lts/janino/issues/96
[#97]: https://github.com/janino-lts/janino/issues/97
[#110]: https://github.com/janino-lts/janino/issues/110
[#113]: https://github.com/janino-lts/janino/issues/113
[#127]: https://github.com/janino-lts/janino/issues/127
