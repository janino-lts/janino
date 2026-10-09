# Janino and javac: the compatibility mode and the compliance mode

Status: revision 3, 2026-10-09. The foundation of the compliance mode (section 9) is implemented on the development
line; the open points of revision 2 are decided (section 10).

## Summary

Janino deviates from `javac` in a number of ways: it accepts some invalid code, rejects some valid code, and compiles
some valid code into something that behaves differently (see
[Differences between Janino and javac](JAVAC_DIFFERENCES.md)). Applications that generate code at run time depend
on some of these deviations; other users want Janino to behave exactly like `javac`. Both could be served by one code
base with two modes:

- **Compatibility mode** (the default in 3.x): code that Janino 3.1.12 compiled into loadable classes keeps its
  3.1.12 behavior, except where 3.1.12 miscompiled it; everything else behaves like `javac`, or is rejected.
- **Compliance mode** (an option on the development line, incomplete by design; the default in a far away 4.0):
  Janino behaves like `javac`.

This document defines what each mode would promise (the contract), how every deviation is classified, how the
promises are verified, and in which order the work would be done. It replaces decisions made case by case.

## 1. Why a contract

Until now, every finding was decided on its own: fix it or not, in the default or not. Three consequences:

- Fixes have rejected or retyped code that 3.1.12 compiled, each with its own reasoning: #39 rejects
  `byte b = -Byte.MIN_VALUE;`, #46 rejects a statement after `while (A.F) { }` when compiling against class files,
  #51 gives `true ? c : (short) -1` the type `int` instead of `char`. Fixes of wrong values (#3, #5, #35, #37, #45)
  changed the behavior of such code as well, but those followed a rule: a wrong value is corrected. The rejections
  did not.
- A fix created a new defect: the first version of #51 turned code that was rejected before (`true ? 'a' : 1L`) into
  class files that the JVM rejects. The rule that would have caught it (the "ratchet", section 3.4) did not exist.
- "What may the default change?" was discussed anew every time, and the answer depended on how the issue was worded.

Three things are needed: a contract that determines the behavior of each mode for every program; a procedure that
classifies every finding with a few yes/no questions; and tests that enforce the contract on every change.

## 2. The oracles

**`javac`** is the reference for the compliance mode, not the text of the Java Language Specification: `javac` can be
run, and the tests compare with it already (the JDK-based implementation of `commons-compiler`, the differential
tests). Where `javac` deviates from the specification, `javac` wins. Precisely:

- the `javac` of every JDK in the test matrix (currently 8, 17, 21 and 25) must agree; a case on which they disagree
  is outside the contract, or is restricted to the JDKs on which it holds (`minJava` in the test records);
- `javac` is invoked with `--release` equal to the target version that Janino compiles for (the default is 8).

**Janino 3.1.12** is the reference for the compatibility mode, because it is the version that the users of Janino come
from. Precisely: 3.1.12 with **target version 8**, on HotSpot (Java 8 or later) with the default bytecode
verification. The target version matters: 3.1.12's default was 6, and some code that 3.1.12 accepts with target
version 8 (e.g. default and static interface methods, also in invalid combinations) it rejects with target version 6.
Target version 8 is the default of Janino since 3.1.13, and it is the more inclusive choice: it keeps more code
working.

## 3. The contract

### 3.1 Definitions

- **U**: a compilation unit together with everything that determines its compilation: the class path, the API that
  is used (`Compiler`, `SimpleCompiler`, the evaluators), the options and the target version.
- **Behavior** of a compiler on U: whether U is accepted or rejected, and, if it is accepted, everything a program
  can observe: the values and types of expressions, which method an invocation selects, which exceptions are thrown,
  when a class is initialized, and the modifiers, names and annotations that reflection shows. **Not** behavior:
  the wording of error messages, the instruction sequence of the generated code, the debug information, and the
  compile time.
- **Legacy code**: U is legacy if 3.1.12 accepts it and compiles it into classes that the JVM loads, i.e. without a
  `LinkageError` (`ClassFormatError`, `VerifyError`, ...) when the classes are loaded. Code that loads and fails only
  when it is executed (e.g. with an `IllegalAccessError`) is legacy.
- **Scope**: the language that Janino implements (section 6). Language features that Janino does not implement, and
  everything that depends on the typing of generics, are outside the scope.

### 3.2 The two modes

```
compliant(U) = javac(U)        within the scope

compat(U)    = 3.1.12(U)       if U is legacy, except for the defects of class V (section 4.3),
                               which are corrected toward javac(U)
             = javac(U)        if U is not legacy, javac accepts U, and U is within the scope
             = rejected        otherwise
```

### 3.3 The union rule

A direct consequence: **the compatibility mode accepts U if and only if 3.1.12 accepts U into loadable classes, or
`javac` accepts U within the scope.** It never rejects what either of the two accepts, and it never accepts what
neither accepts. This is the single most useful test oracle for the compatibility mode: leniency is preserved for
legacy code, and never created.

Examples, with `static final int MAX = 10;`:

- `void m() { while (MAX > 0) { } int x = 1; }`: 3.1.12 accepts it (`MAX > 0` is not folded, so the statement is
  reachable), `javac` rejects it. The compatibility mode accepts it.
- `int f() { while (MAX > 0) { } }`: 3.1.12 rejects it ("Method must return a value"), `javac` accepts it. The
  compatibility mode accepts it, too (issue #47).

### 3.4 Invariants

These replace the case-by-case decisions:

1. **The compatibility mode never rejects legacy code.** Not even when the rejection would only be the consequence of
   a correct fix (see the exception rule in section 4.3).
2. **The compatibility mode never accepts new code with a behavior other than `javac`'s.** For code that is not
   legacy, acceptance implies `javac` behavior. This also applies to new language features (#21 to #24): they are
   implemented strictly, in both modes.
3. **The compatibility mode changes legacy code only for defects of class V**, and only toward `javac`.
4. **The compliance mode behaves like `javac` within the scope.** Every remaining deviation is a registered open item
   (section 7.5), not a design decision.
5. **The ratchet:** the compatibility mode of a release never behaves differently from the previous release, except
   by a change that the change log records together with its class (D, V or A). A released correction, and a
   registered exception, are frozen; neither is reverted later.

A consequence of invariants 1 and 2 is that the same defect can be treated differently depending on its form:
`z ? B : 5` (with a `Byte B`; legacy, type `Integer`) keeps the type `Integer` in the compatibility mode, whereas
`z ? b : 5` (with a `byte b`; rejected by 3.1.12) gets the type `byte` when #38 is fixed, like with `javac`. This is
intended: the compatibility mode is "3.1.12 where 3.1.12 worked; `javac` otherwise".

### 3.5 The public promise

"Code that Janino 3.1.12 compiled into loadable classes keeps working with the same behavior, except where 3.1.12
miscompiled it. Everything else behaves like `javac`, or is rejected."

## 4. The classes of deviations

Every deviation from `javac` belongs to exactly one class. The class determines what each mode does.

### 4.1 Class L: invalid code that is legacy

`javac` rejects the code; 3.1.12 compiles it into loadable classes. The compatibility mode keeps accepting it, with
the 3.1.12 behavior, also when the code fails at run time (e.g. an assignment to a `static final` field of another
class, which throws an `IllegalAccessError`). The compliance mode rejects it.

Today: the cases recorded as `ACCEPTED` in `InvalidCodeTest` (#33 and its comments, and the findings of the work
since 3.1.16), except for the generics cases among them (class G).

### 4.2 Class S: valid legacy code with a consistent, non-`javac` rule

`javac` accepts the code; 3.1.12 compiles it into loadable classes; the behavior differs from `javac`'s by a rule that
Janino applies consistently, so that a program can be written to rely on it. The compatibility mode keeps the 3.1.12
behavior. The compliance mode behaves like `javac`.

| ID | Deviation | Issue |
|---|---|---|
| S-01 | `Z \|\| true` and `Z && false` do not unbox `Z`: no `NullPointerException` for a `null` `Boolean` | #40 |
| S-02 | overload resolution prefers a variable arity method over boxing: `f(1)` invokes `f(int...)`, not `f(Object)` | |
| S-03 | `assert` is always enabled, regardless of `-ea` | |
| S-04 | the type of a conditional expression with operands of different primitive or wrapper types, where the code compiles today: `z ? B : 5` is an `Integer`; `false ? c : (short) 66` is a `Character` | #38 |
| S-05 | the consequences of the incomplete constant folding: which fields are constant variables (`ConstantValue` attribute, class initialization), and the reachability analysis | #47 |
| S-06 | `private` members are compiled without the `private` flag, `private` instance methods as static methods `m$(P, ...)` | |

### 4.3 Class V: valid legacy code that is miscompiled

`javac` accepts the code; 3.1.12 compiles it into loadable classes; the behavior contradicts the program's own text,
so that no program can rely on it intentionally. **Both modes** correct it toward `javac`.

The four forms of V are the definition; the question "could a program rely on this?" is only the intuition behind
them:

| | Form | Examples |
|---|---|---|
| V1 | the value does not fit the type that Janino itself determines for the expression | `(int) (char) b` is -1 (#37); a `char` field with a `Short` value (#45); `"" + (true ? 1 : 2.0)` is `"1"` although the type is `double` |
| V2 | two of Janino's own paths disagree: folded at compile time vs. computed at run time; `SimpleCompiler` vs. `Compiler`; source code vs. class file | `-Byte.MIN_VALUE` is -128 when folded, 128 at run time (#39); `boolean` constants from class files (#46) |
| V3 | the generated code does not do what the source says: a wrong opcode, a wrong order, a block executed twice or never, a declaration ignored | `finally` executed twice (#3, #5); the value of a compound assignment (#35); class bodies of enum constants ignored (#44); annotations of member annotation types invisible (#43) |
| V4 | the generated code of valid code fails at run time because of its own invalidity: wrong values, unreadable metadata, errors thrown from Janino's own code | `false ? c : (short) -1` throws an `ArrayIndexOutOfBoundsException` (#51); annotation element values that cannot be read (#48) |

**Exception rule.** When the corrected behavior would make `javac` reject the code (e.g. the corrected constant
128 does not fit a `byte`), invariant 1 applies: the compatibility mode keeps the 3.1.12 behavior for exactly that
code. If that is unreasonable to implement (it may require a constant whose value depends on the context), the
correction is registered as an exception of class A, with the justification; this is decided in the issue.

### 4.4 Class D: code that 3.1.12 does not compile into loadable classes

3.1.12 rejects the code, fails with an internal error, or compiles it into classes that the JVM does not load. Such
code is not legacy. When it is fixed, **both modes** behave like `javac`: valid code is accepted with `javac`
behavior, invalid code is rejected. Examples: #30, #32, #34, #53, #54, #56, the rejected forms of #47, and the new
language features #21 to #24.

A fix of class D can uncover a leniency of class L: the same rule of the compiler accepts the code on another path
already (e.g. with interfaces loaded from class files), and only the error that is now fixed hid it. The leniency
then applies on both paths, and the cases are recorded as class L: invariant 2 concerns the rules of the compiler,
not the paths on which they are reached (compare class V2). Example: #53 made the static method of an interface
declared in source code non-abstract, so that the existing lookup rule, which treats static interface methods as
inherited, now also applies to such interfaces (`P.s()` with a class `P` that implements the interface).

### 4.5 Classes F and G: outside the scope

- **F**: language features that Janino does not implement. Both modes reject them; the goal is a clear message.
- **G**: everything that depends on the typing of generics (section 6).

### 4.6 Class A: registered exceptions

Changes of legacy code against the contract, made before the contract existed or decided under the exception rule.
Each has an ID, the version since which it applies, and a justification (section 8).

### 4.7 Decision table

| Class | 3.1.12 | `javac` | Compatibility mode | Compliance mode |
|---|---|---|---|---|
| L | loadable | rejects | 3.1.12 behavior | rejected |
| S | loadable, consistent rule | accepts | 3.1.12 behavior | `javac` |
| V | loadable, miscompiled | accepts | `javac` (exception rule) | `javac` |
| D | rejected / internal error / not loadable | accepts or rejects | `javac` | `javac` |
| F, G | any | any | rejected / erased types | rejected / erased types |
| A | loadable | any | as registered | `javac` |

## 5. The classification procedure

Every finding (a failing test, an issue, a probe) is classified with these questions before any code changes, and
the classification is recorded in the issue:

0. **Does the current version behave differently from the last release in the compatibility mode, without a
   recorded change?** Then it is a regression; it is fixed regardless of everything below (the ratchet).
1. **What does `javac` do?** This is the target of the compliance mode; it is recorded as a test that also runs with
   the JDK-based implementation.
2. **What does 3.1.12 do (target version 8)?** Rejected, internal error, or classes that do not load: **class D**,
   continue with question 5. Loadable classes: legacy, continue with question 3.
3. **Does `javac` reject the code?** Yes: **class L**.
4. **Does the behavior differ from `javac`'s?** No: there is nothing to do, and the change must not touch it. Yes:
   one of the four forms of section 4.3 applies: **class V**; otherwise **class S**.
5. **Does the fix depend on generics or on a feature that Janino does not implement?** Then it is **class G** or
   **F**, outside the scope.

**Checks for a fix of class D.** Code that becomes compilable must behave like with `javac` in every context: as a
statement, as a constant expression, in string concatenation, with boxing, as a method argument, and in a comparison.
(The first version of #51 lacked this check.) And because the fix loosens a check, the invalid neighbors of the new
code must stay rejected: they are added to `InvalidCodeTest` as `REJECTED`.

## 6. The scope

The compliance mode promises `javac` behavior only within the language that Janino implements:

- **Not implemented** (class F): lambda expressions, method references, `switch` expressions and arrow labels,
  pattern matching, records, sealed types, local variable type inference, local enums and interfaces, repeating and
  type annotations. Both modes reject them. See the limitations on the project homepage.
- **Generics** (class G): type arguments are parsed, but otherwise ignored; all expressions have their erased types.
  Overload resolution with generic types, bridge methods, `Signature` attributes, and the checks of type arguments
  (the five generics cases of #33) are therefore outside the scope, in both modes, until Janino implements generic
  typing. This is stated in the documentation of the option.
- **Not behavior**: error messages, the instruction sequence, debug information, compile time.
- **Explicit options override the mode, in both directions**: `EXPRESSIONS_IN_TRY_WITH_RESOURCES_ALLOWED` makes
  the compliance mode accept a non-`javac` construct, `PRIVATE_MEMBERS_OF_ENCLOSING_AND_ENCLOSED_TYPES_INACCESSIBLE`
  makes the compatibility mode stricter than 3.1.12. Whoever sets them wants them.
- **The target version** is part of U: code that requires a higher target version than the one in effect is rejected
  in both modes, like by `javac` with the corresponding `--release`.

## 7. Implementation

### 7.1 The option

`JaninoOption.JAVAC_COMPLIANCE`, opt-in (implemented on the development line). The alternative, lenient options
with a non-empty default set, is ruled out: an application that calls `options(EnumSet.of(...))` today would
silently lose the leniency.

The documentation of the option states that the compliance mode is incomplete by design, and that the register
(section 7.5) is authoritative: "compiles in the compliance mode" does not mean "is valid Java".

### 7.2 The pattern: legacy logic first, `javac` logic on its error path

The contract "3.1.12 where 3.1.12 worked, `javac` otherwise" is implemented with one pattern, wherever a fix of
class D touches an area of class S: the existing logic runs first; only where it would report an error, the
`javac` logic takes over. Examples:

- #51: the rule "the constant is representable in the type of the other operand" is checked as before; only when the
  constant is not representable (an error before), the type is determined by binary numeric promotion.
- #47: the reachability analysis treats conditions as before; only when that leads to an error ("Method must return a
  value"), it is repeated with the constant conditions of `javac`.

Two consequences follow. First, the compliance mode falls out of the pattern for free: it is the `javac` logic
alone. Every fix of class D in an area of class S thus implements both modes at once. Second, in the compatibility
mode, properties like "is a constant expression" become dependent on the context; this is the price of the contract,
and it is documented in the register. The pattern also tells when a finding is not of class D: when the existing
logic has no error path, but silently does something else, the finding is of class S or V.

### 7.3 In the compiler

- One method `compliant()` in `UnitCompiler`; every mode-dependent place is guarded by it, so that the compatibility
  mode does not spend time on checks of the compliance mode.
- Every mode-dependent place carries a comment with the ID of the deviation (`// Compliance S-01: ...`), so that each
  ID can be found in the code.
- IDs are stable: a number is never reused or reassigned; new cases get the next number of their class.
- Implemented so far: S-01, S-02 and S-03 (the development line); see
  [Development line](https://github.com/janino-lts/janino/blob/master/DEVELOPMENT_LINE.md) for what they do.

### 7.4 The system property

`org.codehaus.janino.javacCompliance=true` enables the compliance mode for all compilers in the JVM, like
`org.codehaus.janino.UnitCompiler.defaultTargetVersion` sets the target version. It allows a whole application to be
checked without code changes, and it is the only way for users of the generic `commons-compiler` API, which has no
Janino-specific options. It is global, and therefore coarse: in one JVM, a library that depends on the leniency and
the application's own code share it. Precedence (P4): the property determines the initial options of every compiler
that is created afterwards (`JaninoOption.defaultOptions()`, read when the compiler is created); an explicit
`options(...)` call replaces them, so that a library keeps control of its own compilers.

### 7.5 The register

`JAVAC_DIFFERENCES.md` is the register: every deviation with its ID, its class, its behavior in each mode, and, where
the compliance mode already corrects it, the version since which it does. The test records refer to the IDs. The
register is the public list; a deviation that is not in it is an unknown defect, to be reported as an issue.

### 7.6 Tests

- `InvalidCodeTest` and `LanguageSupportTest` record, per case, the expected behavior of each mode: `janino:` (the
  compatibility mode, the default) and `compliant:` (if `compliant:` is missing, it equals `janino:`), and `id:`, the
  ID of the deviation that the case documents (or the issue number of a correction); both modes run for every case.
  The module `commons-compiler-tests` compiles only against the `commons-compiler` API, so it sets the option
  through reflection (`TestUtil.setJavacCompliance()`).
- `ExpressionDifferentialTest` and `ControlFlowDifferentialTest` run in both modes; in the compliance mode, the
  generator does not avoid the constructs of the defects that the mode corrects (`Defect`), and each mode has its own
  file of known differences.
- `LegacyDifferentialTest` runs the recorded cases through 3.1.12, loaded from Maven Central into a class loader of
  its own (as `janino-benchmarks` loads its baseline), and compares its behavior with the recorded behavior of the
  compatibility mode. Where the two differ, the record states the behavior of 3.1.12 (`legacy:`) and the correction
  that explains the difference (`id:`); an unrecorded difference fails the test. This is the test that makes "legacy"
  machine-checkable; without it, the records only protect against accidental changes. Today, 372 of the 925 recorded
  cases differ from 3.1.12.
- Open: every other test class that compiles with Janino runs in the compatibility mode only; a second run of the
  whole suite in the compliance mode needs the mode-dependent expectations of those tests first.
- `CodeSizeReport` (`janino-benchmarks`): in the compatibility mode, the class files of all workloads are byte for
  byte identical to those of the last release, except where a fix of class V or D explains the difference.

### 7.7 Progress

The primary measure is the number of open IDs in the register, per class. The secondary measure is the share of the
recorded cases on which the compliance mode agrees with `javac`. Both are stated in the change log of every release.

The development line, 2026-10-09: S, 10 IDs, 3 corrected in the compliance mode; L, 44 open; D, 6 open; G, 9 and F,
11 (outside the scope); A, 8. Of the 925 recorded cases, the compliance mode agrees with `javac` on 781 (the
compatibility mode on 771).

## 8. Registered exceptions

| ID | Change | Since | Compatibility mode per contract |
|---|---|---|---|
| A-01 | `byte b = -Byte.MIN_VALUE;` and `short s = -Short.MIN_VALUE;` are rejected (#39) | 3.1.15 | accepted, with the values -128 and -32768 |
| A-02 | when compiling against class files, a statement after `while (A.F) { }` with a `boolean` constant `true` is rejected (#46) | 3.1.15 | accepted |
| A-03 | `true ? c : (short) -1` (a `char c`, a constant condition, a negative `byte` or `short` constant) has the type `int` (#51); 3.1.12 gave it the type `char`, 3.1.15 rejected it | 3.1.15 | type `char`, as in 3.1.12 |
| A-04 | a constant conditional expression of type `long`, `float` or `double` is rejected where a `byte`, `short` or `char` value is required: `byte b = true ? 1 : 2L;` (#55); 3.1.12 accepted it with the value of the selected operand | 3.1.16 | accepted, with that value |
| A-05 | the access to a `protected` instance member of a class in another package through an expression whose static type is neither the accessing class (or an enclosing class) nor a subclass of it is rejected (#54): `((Object) this).clone()`, `Object o = new P(); o.clone()`, `FilterInputStream s = new P(); s.in`; 3.1.12 accepted it, and the JVM loads the class iff its verifier infers a subclass type from the bytecode (it does not for a parameter, a field or a method result) | 3.1.16 | accepted where the JVM loads the class |
| A-06 | a member annotation type is parsed like a top-level one (#43): type parameters (`class P { @interface A<T> {} }`), an `extends` clause, and `default` or `static` methods are rejected, and a class that implements a member annotation type must implement `annotationType()`; 3.1.12 compiled these declarations as ordinary interfaces | 3.1.16 | accepted, as an ordinary interface |
| A-07 | a cast of the clone of an array to an unrelated array type, `(String[]) intArray.clone()`, is rejected (#58); 3.1.12 compiled it (the clone had the type `Object`) into code that throws a `ClassCastException` | 3.1.17 | accepted, throwing at run time |
| A-08 | a duplicate annotation on a parameter is rejected (#61); 3.1.12 did not write parameter annotations at all, so it accepted the duplicate | 3.1.16 | accepted |

Justification for keeping them: A-01 and A-02 are released; they correspond to `javac`; reverting them would itself
change the behavior of 3.1.15. A-03 concerns an expression that no code generator produces (a constant condition
with a negative constant of a narrow type); 3.1.15 rejects it, so no user of 3.1.15 depends on it. Note that A-03 is
only the `true` form: `false ? c : (short) -1` threw an exception at run time (class V4), and `true ? 'a' : 1L` was
rejected by 3.1.12 (class D); both are corrected within the contract.
A-04 is the case that the exception rule of section 4.3 names: the V1 correction of #55 (the constant value has the
type of the expression) could keep the 3.1.12 behavior for these forms only with a constant whose value depends on
the context in which it is used (the value of the selected operand in narrowing contexts, the converted value
elsewhere). The code is not something a code generator produces, and no other form of #55 is affected.
A-05: whether the JVM loads the class depends on the data flow of the bytecode (the type that the verifier infers for
the receiver between two stack map frames), not on the types of the source code; a compiler can follow this only by
reimplementing the verifier's inference. The rule of JLS 6.6.2.1 (the static type of the receiver) is what `javac`
checks, and what the JVM checks where it cannot infer more. The loadable forms narrow an expression that would be
accepted without the cast or the intermediate variable; no code generator produces them.
A-06: the member annotation type was not an annotation type at all (an ordinary interface, without the
`ACC_ANNOTATION` flag and without the superinterface `Annotation`), which is the V3 defect of #43; the fix cannot
keep the forms that only an ordinary interface permits. None of them is Java (`javac` rejects all of them), and
top-level annotation types rejected them in all versions.
A-07 and A-08 were found by `LegacyDifferentialTest` when the register was created (2026-10-09): both are released
corrections of class D (#58, the type of `a.clone()`; #61, parameter annotations) that also reject a form of invalid
legacy code which could only fail, or be ignored, at run time. They are registered like A-01 to A-03: reverting them
would change the behavior of a released version. (A-05 also covers the forms of #59, the access from an inner class.)

## 9. The plan

**3.1.16 (released): fixes of classes D and V only**, each classified per section 5: #43 (V3, with the exception A-06; the
parameter annotations, the implicit modifiers of member types in the `InnerClasses` attribute and the member types of
interfaces are V3 and D as well), #44 (V3); #51 (V4, D); #53, #54 (D, with the exception A-05); #55, the constant
value of conditional expressions (V1, V4); #56, the rejected forms of #38 (D); #57, the stack map frames of loops and
short-circuit operators (D: valid code that 3.1.13 to 3.1.15 compiled into unloadable classes, and that 3.1.12
compiled only with target version 6). The new cases of #33 and #54 are recorded with their current behavior first.
The new language features #21 and #22 are class D (section 4.4), like #61 and #62 (V3, D), which were found with #43,
and #63 (D), which was found with #21.
#47 stays out: its rejected forms are entangled with S-05.

**3.1.17 (released): fixes of classes D and V only, like 3.1.16.** All were found during the 3.1.16 work and are
present in 3.1.12: #58, the type of `a.clone()` (D); #60, local classes with modifiers and annotations (D, parser);
#71, a subscript on a non-array as a method argument, found during the work (D: an internal compiler error);
#64, the resource variable of a try-with-resources statement (D); #59, a `protected` member of the superclass of
an enclosing class, accessed from an inner class (V4 and D: a synthetic accessor method, like `javac`); #65, the D
part only: a `throws` clause with an all-uppercase type name no longer declares a type parameter, so the `catch`
of such an exception is reachable and the `Exceptions` attribute is complete; the L part, that an invocation
need neither catch nor declare such an exception, stays under invariant 1 (section 3 of `JAVAC_DIFFERENCES.md`);
#24, effectively final local variables in the conservative variant (D); #75, the loop variable of a basic `for`
statement captured by an inner class, found with #24 (D: an internal compiler error). #23, `O<String>.I`, was
taken out: low benefit, and a parser change with the highest risk for existing code; it stays open without a
target version. The class files of code that compiled before are unchanged, except for one more `checkcast`
instruction where the clone of an array was used as an `Object` (#58) and for the `Exceptions` attribute of a
method with an all-uppercase exception type (#65).

**3.1.18 (released): no change of the compiler.** #76, in part: `guessParameterNames()` of the expression and
script evaluators did not find the names in the initializers of variables and fields and in array initializers (D).
Since 3.1.18, the 3.1.x line is a maintenance line (see "Release lines" in [Compatibility](COMPATIBILITY.md)): it
takes fixes of classes D and V that leave the class files of all other code byte for byte identical. Fixes that
change the class files of correct code, or the behavior of public API such as `AbstractTraverser`, go to the
development line.

**3.1.19 (in development):** #87, part 1: a `protected` member type could not be used from a subclass of the
enclosing type in another package; the JVM threw an `IllegalAccessError` (V4 and D, like #59). Its class file now
has the flag `ACC_PUBLIC`; the class files of all other code are unchanged. A new case of class L for #33: a
`private` member type of a class in another package, which compiles into a class that fails when it resolves the
reference. #97, found with #31: an inner class whose superclass is an inner class that extends the enclosing class
compiled into a class that the JVM rejects (D: the constructor passed its uninitialized `this` as the enclosing
instance); only these class files change.

**The development line (`master`, not released)**, see
[Development line](https://github.com/janino-lts/janino/blob/master/DEVELOPMENT_LINE.md): #73, the `InnerClasses`
entries and the `EnclosingMethod` attribute of local and anonymous classes, so that the reflection API recognizes
them (V4; it changes the class files of correct code); #74, the `ACC_STRICT` bit of `strictfp` classes, and #87,
part 2, the other class flags of member types (class file defects without behavior); #76, completely, and #88:
`AbstractTraverser` descends into initializers and visits enum constants (public API, not the compiler); #31,
misleading compile error messages: each improved message keeps its old text and adds an explanation, except for a
typo and for cyclic inheritance, which was reported as a stack overflow ("Compilation unit is nested too deeply") and
is now reported as a class circularity (the same code is rejected as before).

**Backlog without a target version:** #23 (above); #96, a floating-point literal with a leading zero, e.g. `09.5`,
is rejected (D, found with #31).

Open: #38, #40 and #47 (class S) and #33 (class L) belong to the compliance mode. A compile error message is not a
change of behavior in the sense of section 3.1, but applications and their tests match message texts (the tests of
Apache Spark expect `Cannot determine simple type name "..."`); an improved message therefore keeps the old text and
adds to it, as with #31 on the development line. The gates are those of 3.1.16.

**The compliance mode: the foundation** (done on the development line, 2026-10-09, #103; the version is open):

1. The contract, as the section "The two modes" of `JAVAC_DIFFERENCES.md`.
2. The option, the system property, `compliant()`.
3. The test infrastructure for two modes: the record format (`janino:`, `compliant:`, `legacy:`, `id:`), the
   parameterized record and differential tests, `LegacyDifferentialTest` against 3.1.12.
4. The register: every known deviation with its ID and class (S-01 to S-10, D-01 to D-06, L-01 to L-44, G-01 to
   G-09, F-01 to F-11, A-01 to A-08), and the IDs in the records.
5. The first checks of the compliance mode: S-01, S-02 and S-03 (each is one place in the compiler, and they are
   what users of the compliance mode notice first: the behavior of generated code).

**Then:** the cases of class L that need no data flow analysis, by area, one branch each: modifiers, declarations and
annotations; overrides, hiding and `throws` clauses; `catch` clauses; imports and access; statements. In each branch,
the `compliant:` entries of the area change from `ACCEPTED` to `REJECTED`. Then S-04 and S-05 in the compliance mode;
the remaining areas of class L.

**Definite assignment and definite unassignment** (JLS 16) for the `final` cases of class L: the largest single piece
of work. Definite assignment analysis might not be implemented at all.

**Generic typing**: the precondition for compliance with generics. A separate project with its own plan. Will likely never be done.

**4.0:** the compliance mode becomes the default; the compatibility mode remains available through an option. When
this happens depends on the number of open IDs, not on the calendar.

**Gates for every change** (the checklist of a pull request):

- the classification per section 5 in the issue;
- compatibility mode: all `compat:` records unchanged, except those that a fix of class D or V changes deliberately;
  `CodeSizeReport` against the last release byte for byte identical, except where such a fix explains it (on the
  development line also where `DEVELOPMENT_LINE.md` lists the change);
- compliance mode: the affected `compliant:` records agree with `javac` (the JDK-based implementation runs the same
  cases);
- newly compilable code: like `javac` in every context, and its invalid neighbors stay rejected (section 5);
- the test matrix: Java 8, 17, 21 and 25.

## 10. Open points

| | Question | Proposal |
|---|---|---|
| P1 | the contract (sections 3 and 4), including the reference 3.1.12 with target version 8 and the four forms of V | accept |
| P2 | the exceptions A-01 to A-03 | keep, as registered |
| P3 | the names | `JAVAC_COMPLIANCE`, `org.codehaus.janino.javacCompliance` (decided, implemented) |
| P4 | precedence of the system property and explicit options | the property sets the initial options of every compiler; an explicit `options(...)` call replaces them, so that a library keeps control of its own compilers (decided, implemented) |
| P5 | the `javac` reference: all JDKs of the test matrix must agree, `--release` equals the target version | accept |
| P6 | the line of the foundation | the development line, because of the new API; the version is open (decided) |
| P7 | the order of the first checks of the compliance mode | S-01 to S-03 first (done), then the areas of class L (decided) |
| P8 | the record key of the compatibility mode | `janino:` keeps its name (it is the default mode; 895 records stay unchanged) instead of `compat:` (decided) |
