# Differences between Janino and javac

Janino compiles Java source code like `javac`, but not in every respect: it accepts some invalid code, it rejects some
valid code, and some valid code behaves differently. This page lists all deviations that are known as of version
3.1.20, so that you can decide whether they matter for your code.

Language features that Janino does not implement at all (e.g. lambda expressions, `switch` expressions, records) are
listed under [Limitations](https://janino-lts.github.io/janino/#limitations) on the project homepage, and are not
repeated here.

Each deviation was verified against `javac` (JDK 25, with and without `--release 8`) and Janino 3.1.16, or the later
version in which it was added. Unless stated otherwise, Janino 3.1.12 behaves the same way (with target version 8 where
the code requires it, e.g. for static interface methods).

## Compatibility policy

Many applications generate Java code at run time and compile it with Janino; they test their code generators against
Janino, not against `javac`. Therefore, Janino keeps accepting invalid code that it has always accepted (see
[issue #33](https://github.com/janino-lts/janino/issues/33)), and changes the behavior of code that compiles today only
where the old behavior is clearly broken, e.g. a wrong value or a class file that the JVM rejects. The deviations
below remain for that reason, or because they have not been fixed yet.

## 1. Valid code that behaves differently

| Code | `javac` | Janino | Issue |
|---|---|---|---|
| `f(Object o)` and `f(int... i)`; `f(1)` | invokes `f(Object)` | invokes `f(int...)` | |
| `assert false;` | throws only if assertions are enabled (`-ea`) | always throws | |
| `Boolean Z = null; boolean x = Z \|\| true;` (also `Z && false`) | `NullPointerException` | `x == true`, no exception | [#40](https://github.com/janino-lts/janino/issues/40) |
| `Byte B = 1; Object x = z ? B : 5;` | `x` is a `Byte` | `x` is an `Integer` | [#38](https://github.com/janino-lts/janino/issues/38) |
| `char c = 'a'; Object x = false ? c : (short) 66;` | `x` is an `Integer` | `x` is a `Character` | [#38](https://github.com/janino-lts/janino/issues/38) |
| `new Object() {}.getClass().getModifiers()` | `0` (JDK 9 and later) | `0x10` (`final`) | [#73](https://github.com/janino-lts/janino/issues/73) |
| `getName()` of a local class `L` declared in a class `P` (see below) | `"P$1L"` | `"P$L"` | |
| `B.s("x")` with `static s(Object)` in a public class `B` and `static s(String)` in its package-private superclass `A`, from another package (see section 2) | invokes `A.s(String)` | invokes `B.s(Object)` | [#154](https://github.com/janino-lts/janino/issues/154) |
| `J.f()` with `static String f()` in an interface `J` and in its superinterface `I` (also the simple name `f()` in the body of `J`) | invokes `J.f()` | invokes `I.f()` | [#200](https://github.com/janino-lts/janino/issues/200) |

**The binary names of local classes** ([#143](https://github.com/janino-lts/janino/issues/143), fixed in 3.1.20):
Janino names a local class `L` in a type `E` `E$L`, where `javac` inserts a number (`E$1L`, JLS 13.1). Since 3.1.20,
Janino inserts a number only where `E$L` is already taken: by a member type `L` of `E` (the local class is named
`E$1L`), or by another local class `L` in `E` (the second one is named `E$2L`, the third one `E$3L`). Janino 3.1.19
and earlier gave all of them the name `E$L` and kept only one class file, so that the code that used another one
failed with a `NoSuchMethodError`, or used the wrong class.

**Local and anonymous classes in the reflection API** ([#73](https://github.com/janino-lts/janino/issues/73)): their
class files have no `InnerClasses` entry and no `EnclosingMethod` attribute, so the reflection API does not recognize
them. For `class L {}` and `new Object() {}` in a method `run()` of a class `P`:

| Method | `javac` | Janino |
|---|---|---|
| `getSimpleName()` | `"L"`, `""` | `"P$L"`, `"P$1"` |
| `getCanonicalName()` | `null` | `"P$L"`, `"P$1"` |
| `isLocalClass()`, `isAnonymousClass()` | `true` (`L`), `true` (`P$1`) | `false` |
| `getEnclosingClass()`, `getEnclosingMethod()` | `P`, `run` | `null` |

The development line fixes this (see
[Development line](https://github.com/janino-lts/janino/blob/master/DEVELOPMENT_LINE.md)); the 3.1.x line keeps it,
because the fix changes the class files of correct code.

**Constant expressions that are not folded** ([#47](https://github.com/janino-lts/janino/issues/47)): Janino does not
evaluate shifts, relational operators, `~`, operations with `char` operands and casts to and from `char` at compile
time (see section 2). Their values are correct, but a `static final` field with such an initializer, e.g.
`static final int X = 1 << 2;`, is not a constant variable:

- it has no `ConstantValue` attribute and is initialized in the static initializer;
- reading it from another class initializes the declaring class, which `javac`'s code does not (JLS 12.4.1).

**Generics** (type arguments are parsed, but otherwise ignored, see
[Limitations](https://janino-lts.github.io/janino/#limitations)):

- overload resolution uses the erased types: with `f(Object)` and `f(String)`, `f(list.get(0))` invokes `f(Object)`
  for a `List<String> list`;
- no bridge methods: if `class B extends A<String>` overrides `m(T)` with `m(String)`, an invocation through `A` still
  invokes `A.m`;
- no `Signature` attributes: `Field.getGenericType()` returns a `Class`, not a `ParameterizedType`.

**Private members in the class files:** Janino compiles `private` fields, methods and constructors without the
`private` flag (package access), and a `private` instance method `m(...)` as a static method `m$(P, ...)` with the
instance as the first parameter. Reflection shows these modifiers and names, and code in the same package that is
compiled against the class file can access these members.

**Parameter annotations** (since 3.1.16, [#61](https://github.com/janino-lts/janino/issues/61); before, they were
not written at all): Janino records the annotations of the parameters against the parameters of the method
descriptor, which include the parameters that the compiler prepends to the declared ones: the captured local
variables of a local class constructor, and the instance of a `private` instance method (see above). `javac` records
them against the declared parameters. `Constructor.getParameterAnnotations()` of a local class that captures local
variables, and `Method.getParameterAnnotations()` of a `private` instance method, therefore return arrays that are
longer by the number of prepended parameters, with each annotation at the index of its actual parameter.

**An inner class whose superclass is an inner class that extends the enclosing class**
([#97](https://github.com/janino-lts/janino/issues/97), fixed in 3.1.19): for
`class P { class Q extends P {} class R extends Q {} }`, Janino 3.1.18 and earlier pass the uninitialized instance
of `R`, instead of the enclosing instance of `R`, as the enclosing instance to the constructor of `Q`, and the JVM
rejects the class `P$R` (`VerifyError`).

**`super.m()` for a `private` method of the enclosing superclass**
([#136](https://github.com/janino-lts/janino/issues/136), fixed in 3.1.20): for
`class P { private String who() { ... } class Q extends P { ... super.who() ... } }`, also in a local, anonymous or
static member class that extends `P` and is declared in `P`, and for `Q.super.who()` from a class nested in `Q`,
Janino 3.1.19 and earlier invoke `who()` by its original name and descriptor, although they compile it as a static
method `who$(P)` (see above), and the invocation fails with a `NoSuchMethodError`. Since 3.1.20, Janino invokes
`who$(P)` with the instance of `Q`, and the invocation yields what `javac`'s code yields.

**`super.m()` for a `static` method of the superclass**
([#150](https://github.com/janino-lts/janino/issues/150), fixed in 3.1.20): for
`class B { static String s() { ... } } class P extends B { ... super.s() ... }`, Janino 3.1.19 and earlier load
`this` and invoke `s()` with `invokespecial`, which the JVM allows only for instance methods: the invocation fails
with an `IncompatibleClassChangeError`. `P.super.s()` from an inner class of `P` is an internal compiler error
(`ArrayIndexOutOfBoundsException`; Janino 3.1.12 rejects it, `Qualified superclass method invocation NYI`). Since
3.1.20, Janino invokes the method like the simple name `s()` does, with `invokestatic`.

**An inner class that extends its enclosing class** ([#101](https://github.com/janino-lts/janino/issues/101)):
in `class P { class Q extends P { ... } }`, two expressions in `Q` denote `Q` itself (`this`) instead of the
enclosing instance of `Q`:

- `P.this`: Janino takes the first class, starting with `Q`, that is a subclass of `P`; with assertions enabled
  (`-ea`), compiling it fails with an `InternalCompilerException`;
- the simple name of a `private` member of `P`: Janino finds it through the superclass of `Q`, although `private`
  members are not inherited.

Members that `Q` inherits from `P` denote `this` with both compilers.

## 2. Valid code that Janino rejects

**Constant expressions** ([#47](https://github.com/janino-lts/janino/issues/47)): wherever the language requires a
constant expression, the expressions that Janino does not fold (see section 1) are rejected:

- `case A:` with `static final int A = 1 << 0;`, `case 'a' + 1:`, `case MASK:` with `static final int MASK = ~0xFF;`;
- `byte b = 1 << 2;`, `char c = 'a' + 1;`, `byte b = -'a';`;
- `byte b = MAX > 5 ? 1 : 2;` with `static final int MAX = 10;`;
- `int f() { while (MAX > 0) { } }` ("Method must return a value", because the condition is not constant).

**Multi-catch** ([#21](https://github.com/janino-lts/janino/issues/21), since 3.1.16): the type of the parameter
of `catch (A | B e)` is the nearest common superclass of the alternatives, not their least upper bound with the
interfaces that all alternatives implement: `e.n()` with a method `n()` of an interface that `A` and `B` implement,
but not their common superclass, is rejected (`A method named "n" is not declared in any enclosing class nor any
supertype`); with a cast, `((I) e).n()`, it compiles.

**Effectively final local variables** ([#24](https://github.com/janino-lts/janino/issues/24), since 3.1.17; before,
a local variable had to be declared `final` to be accessed from a local or anonymous class): Janino finds a local
variable, a parameter, a `catch` parameter or the variable of an enhanced `for` statement effectively final by a
conservative rule: the variable has an initializer, or is a parameter, and its name is not assigned, incremented or
decremented anywhere in the method, constructor or initializer that declares it, including nested classes. Janino
therefore rejects (`Cannot access non-final local variable "x" from inner class`):

- a variable without an initializer that is assigned exactly once: `int x; x = 1; new Runnable() { ... x ... }`
  (JLS 4.12.4 requires definite assignment analysis here, which Janino does not have);
- a variable whose name is assigned in another block or in a nested class:
  `{ int x = 1; x = 2; } int x = 3; new Runnable() { ... x ... }`.

**Floating-point literals with a leading zero** ([#96](https://github.com/janino-lts/janino/issues/96), fixed in
3.1.19): Janino 3.1.18 and earlier reject `09.5`, `00.5`, `0123.5`, `07e1`, `07f`, `09d` and `08.` (e.g. `';'
expected instead of '9.5'`), because they scan the digits after a leading `0` as an octal integer literal. Since
3.1.19, only a literal with an underscore directly before the first digit `8` or `9` is still rejected, e.g. `0_9.5`
or `01_8.5` (`';' expected instead of '_9'`): there, Janino keeps scanning `0_` as before, so that invalid octal
integer literals are reported with the same messages as before.

**An array for a variable arity parameter** ([#127](https://github.com/janino-lts/janino/issues/127), fixed in
3.1.20): Janino 3.1.19 and earlier reject `f(1, new int[] { 1, 2 })` with `f(Integer i, Object... a)` (`Assignment
conversion not possible from type "int[]" to type "java.lang.Object[]"`), and likewise with a parameter
`Serializable...`, because they pass the `int[]` as the array itself instead of as its only element when another
argument of the invocation needs boxing or unboxing.

**An array type of a parameterized type** ([#138](https://github.com/janino-lts/janino/issues/138), fixed in
3.1.20): Janino 3.1.19 and earlier reject `List<Object>[]` as the type of a local variable in a script
(`IDENTIFIER expected instead of '['`), in the first statement of a constructor and in a `for` statement
(`Expression "java.util.List<Object>" is not an rvalue`), as the target type of a cast (`')' expected instead of
'['`), and as the first type argument of the type of a local variable or of a cast, e.g.
`Map<List<String>[], String> m` (`One of '> ,' expected instead of '['`), because the parser does not read the
brackets after the type arguments in these places.

**A static method imported twice** ([#133](https://github.com/janino-lts/janino/issues/133), fixed in 3.1.20):
Janino 3.1.19 and earlier fail with an internal compiler error (`Two non-abstract methods "public static int
java.lang.Math.abs(int)" have the same parameter types, declaring type and return type`) when the code invokes a
static method that the compilation unit imports twice, with two single-static-import declarations
(`import static java.lang.Math.abs;` twice) or with two static-import-on-demand declarations of the same class,
because they count the method once per declaration.

**Invocations with a `null` argument** ([#134](https://github.com/janino-lts/janino/issues/134), fixed in 3.1.20):
Janino 3.1.19 and earlier fail with an internal compiler error on `m(null, (short) 1, 'c')` with `m(Integer)`,
`m(int...)` and `m(Integer[], Short, int)` (`ArrayIndexOutOfBoundsException`), because they count `m(int...)` as
applicable against the first argument only; since 3.1.20, the invocation selects `m(Integer[], Short, int)`, like
`javac`. They also fail while reporting an ambiguity with a `null` argument (`StringIndexOutOfBoundsException`).
Where `javac` applies a variable arity method by fixed arity, Janino still expands the arguments, so that, e.g.,
`m(null)` with `m(Integer...)` and `m(Long, byte...)` is still rejected; since 3.1.20, with the message
`Invocation of constructor/method with argument type(s) "(null type)" is ambiguous`.

**Two maximally specific variable arity methods with different numbers of parameters**
([#147](https://github.com/janino-lts/janino/issues/147), fixed in 3.1.20): Janino 3.1.19 and earlier fail with an
internal compiler error on `m(ia)` (`int[]`) with `m(int...)` and `m(int[], Serializable...)`
(`ArrayIndexOutOfBoundsException`, or `Two non-abstract methods ... have the same parameter types, declaring type
and return type` with the declarations in the opposite order), because they compare the parameter types of the
two methods without comparing their numbers. Since 3.1.20, the invocation is still rejected, with the message
`Invocation of constructor/method with argument type(s) "int[]" is ambiguous`: `javac` applies `m(int...)` by fixed
arity, Janino only by variable arity, where neither method is more specific than the other.

**`super.m(...)` for a variable arity method** ([#157](https://github.com/janino-lts/janino/issues/157), fixed in
3.1.20): with `void f(String... s)` in the superclass, Janino 3.1.19 and earlier reject `super.f("a", "b")`
(`Assignment conversion not possible from type "String" to type "String[]"`), and `super.f()` is an internal
compiler error (`Operand stack underflow`), because they pass the arguments without collecting the variable arity
arguments into an array; likewise `Outer.super.f(...)` and `Interface.super.f(...)`. Only an array as the variable
arity argument compiles. Since 3.1.20, the arguments are collected into an array, like with `javac`; `super.f(null)`
passes `null` as the array, as before and as with `javac`.

**`super.m(...)` for a variable arity method that the superclass implements from an interface**
([#160](https://github.com/janino-lts/janino/issues/160), fixed in 3.1.20): for
`interface I { void a(Object o, Object... v); } class B implements I { public void a(Object o, Object... v) { ... } }`
and `super.a(o, v)` in a subclass of `B`, also with a default method `a` in `I`, Janino 3.1.19 and earlier select
the method of `I` instead of the method of `B`, because they find each of two variable arity methods with the same
parameter types more specific than the other; `super.a(o, v)` then invokes `I.a` with `invokespecial`, and the JVM
of Java 25 rejects the class (`VerifyError: Bad invokespecial instruction: interface method to invoke is not in a
direct superinterface`), older JVMs throw an `IncompatibleClassChangeError` at the invocation. Since 3.1.20, the
method of the class is selected, like with fixed arity and with `javac`.

**`if (b) {}` with a condition of type `Boolean`** ([#153](https://github.com/janino-lts/janino/issues/153), fixed
in 3.1.20): Janino 3.1.19 and earlier reject an `if` statement whose branches are empty, e.g. `if (b) {}`, `if (b);`
or `if (b) {} else {}` with `Boolean b` (`Not a boolean expression`), because they evaluate the condition only for
its side effects and do not unbox it; with a non-empty branch, the condition is unboxed. Since 3.1.20, it is
unboxed in both cases, like with `javac`; with `b == null`, the statement throws a `NullPointerException`.

**Two variable arity methods, one of which is more specific**
([#156](https://github.com/janino-lts/janino/issues/156), fixed in 3.1.20): Janino 3.1.19 and earlier reject
`g(1, 2, 3)` with `g(int...)` and `g(double...)`, `f("s", 1)` with `f(Object, int...)` and `f(String, int...)`,
and the like (`Invocation of constructor/method with argument type(s) "int, int, int" is ambiguous`), because
they compare the component type of the variable arity parameter of one method with the array type of the other.
Since 3.1.20, when this comparison finds no most specific method, the rule of JLS 15.12.2.5 for an invocation
by variable arity selects it, like with `javac`: it compares the variable arity parameter types for as many
parameters as the longer parameter list or the arguments have, so that `m(1)` with `m(int, Object...)` and
`m(int...)` stays ambiguous ([#169](https://github.com/janino-lts/janino/issues/169)). The comparison itself is
unchanged, because it also selects methods that `javac` reports as ambiguous (section 3).

**A member of a class that is not accessible, inherited through an accessible subclass**
([#154](https://github.com/janino-lts/janino/issues/154), fixed in 3.1.20): for `public class B extends A` with a
package-private class `A` in a package `p`, Janino 3.1.19 and earlier reject the access to the `public` and
`protected` members of `A` through `B` from another package, e.g. `p.B.F`, `p.B.m()`, `new p.B().f`, `super.m()`,
the simple name `F` in a subclass of `B`, and `X.super.m()` (`"p.A" is inaccessible from this package`, `No
applicable constructor/method found`), because they require the class that declares the member to be accessible.
By JLS 6.6.1, the type through which the member is accessed must be accessible. Since 3.1.20, Janino checks that
type when the declaring class is not accessible, and references the member through it in the class file (JLS
13.1), like `javac`; otherwise, the JVM would throw an `IllegalAccessError`. An instance method of `A` worked before
when `B` was read from a class file that `javac` generated, because `javac` adds a public bridge method to `B`. To
keep the selection among the overloads that compiled before, the methods of `A` are considered only if no accessible
method is applicable; so `p.B.s("x")` still invokes `B.s(Object)` when `A` declares `s(String)` (section 1).
Still rejected: a `protected static` member of `A` accessed from an inner class of a subclass of `B` (the
synthetic accessor method references the member through `A`), and a resource of type `B` in a try-with-resources
statement when `close()` is declared in `A`: Janino selects `AutoCloseable.close()`, which throws `Exception`.

**`this(...)` in an enum constructor** ([#178](https://github.com/janino-lts/janino/issues/178), fixed in 3.1.21):
Janino 3.1.20 and earlier fail with an internal compiler error on `enum E { A, B(2); E() { this(1); } E(int i) { } }`
(`AssertionError: uninitializedThis`, also without `-ea`), because they do not pass the synthetic name and ordinal
parameters of the enum constant on to the constructor that `this(...)` invokes. Since 3.1.21, they are passed on, like
with `javac`.

**A `catch` clause for a superclass after a `catch` clause for the thrown exception**
([#181](https://github.com/janino-lts/janino/issues/181), fixed in 3.1.21): Janino 3.1.20 and earlier reject
`try { new FileInputStream(file); } catch (FileNotFoundException e) { } catch (IOException e) { }`, where the `try`
block throws only `FileNotFoundException` (`Catch clause is unreachable`), because they mark only the first `catch`
clause that catches a thrown exception as reachable. By JLS 14.21, the second clause is reachable: the exception is
assignable to its type, and no preceding clause catches `IOException` or a superclass of it; `javac` only warns.
Since 3.1.21, such a clause is accepted, like by `javac`.

**A static import of a name with static and instance methods**
([#191](https://github.com/janino-lts/janino/issues/191), fixed in 3.1.21): Janino 3.1.20 and earlier reject
`isNaN(d)` with `import static java.lang.Double.*;` or `import static java.lang.Double.isNaN;`
(`method "public boolean java.lang.Double.isNaN()" of "java.lang.Double" must be static`), and `abs(-1)` with
`import static java.lang.Math.*;` and `import static java.math.BigInteger.*;`, because they report every instance
method with the name as soon as they look the name up through a static import. By JLS 7.5.3 and 7.5.4, a static
import imports only the static members; the instance members are simply not imported. Since 3.1.21, they are not
imported, like with `javac`; a name that the static imports provide only as an instance member is still reported.

**A static import of an inherited static member** ([#201](https://github.com/janino-lts/janino/issues/201)): a
single static import `import static T.n;` and a static import on demand `import static T.*;` import the accessible
static members of `T`, including the inherited ones (JLS 7.5.3, 7.5.4). Janino imports the inherited member types,
but only the static fields and static methods that `T` declares itself, and therefore rejects, e.g.:

- `short s = STREAM_MAGIC;` with `import static java.io.ObjectOutputStream.STREAM_MAGIC;` or
  `import static java.io.ObjectOutputStream.*;` (the field is declared in the superinterface `ObjectStreamConstants`;
  `Expression "STREAM_MAGIC" is not an rvalue`);
- `getDefaultLocale()` with `import static javax.swing.JPanel.getDefaultLocale;` (declared in the superclass
  `JComponent`; `A method named "getDefaultLocale" is not declared in any enclosing class nor any supertype, nor
  through a static import`);
- `m(1)` with `import static p.A.m;`, `static m(String)` in `A` and `static m(int)` in its superclass (`No applicable
  constructor/method found`);
- a `public` constant of a package-private class, imported through its public subclass
  ([#154](https://github.com/janino-lts/janino/issues/154), see above).

When another static import provides the same name, Janino uses that one instead: with `import static p.A.x;` and
`import static p.B.*;`, where `A` inherits `x` and `B` declares `x`, the name `x` denotes `B.x`, where `javac` takes
`A.x` (the single static import shadows the static import on demand, JLS 6.4.1); likewise for a static method.

## 3. Invalid code that Janino accepts

`javac` rejects the following code, Janino compiles it, and the JVM loads the generated classes. Most of it behaves
as the source suggests (e.g. an assignment to a `final` local variable assigns it). See
[issue #33](https://github.com/janino-lts/janino/issues/33) for the compatibility considerations. One exception: the
access to a `protected` member of a class in another package through an expression whose type is neither the
accessing class nor a subclass of it (`((Object) this).clone()`, `Object o = new P(); o.clone()`) is rejected since
3.1.16, like by `javac`, although the JVM loaded such classes when the verifier could infer the type `P` from the
bytecode ([#54](https://github.com/janino-lts/janino/issues/54)); it rejected them for a parameter, a field or a
method result of type `Object`.

Code generators rely on some of these leniencies. The code that Apache Spark generates for SQL queries, for
example, assigns to a `final` local variable, names nested classes by their binary names, creates generic arrays
and assigns a parameterized type to a field with a different type argument
([SPARK-58437](https://issues.apache.org/jira/browse/SPARK-58437)); such code keeps compiling.

**`final` variables and definite assignment:**

- assignment to a `final` field: `final int x = 1; void f() { x = 2; }`;
- assignment to a blank `final` field outside of a constructor: `final int x; P() { x = 1; } void f() { x = 2; }`;
- blank `final` fields that are never assigned: `final int x;`, `static final int X;`;
- assignment to a `final` local variable, also twice to a blank one: `final int x = 1; x = 2;`,
  `final int x; x = 1; x = 2;`;
- assignment to a blank `final` local variable in a loop: `final int x; for (;;) { x = 1; }`;
- assignment to a `final` parameter, a `final` variable of an enhanced `for` statement, or the (implicitly `final`)
  resource variable of a try-with-resources statement: `try (R r = new R()) { r = null; }` (the resource is then
  not closed);
- assignment to a `static final` field of another class, e.g. an interface field (`I.X = 2;`) or an enum constant
  (`E.A = null;`): this compiles, but throws an `IllegalAccessError` when it is executed.

**Declarations:**

- override with weaker access, also of an interface method: `class P implements Runnable { void run() {} }`;
- a static method that hides an instance method, an instance method that overrides a static method, a static method
  that hides a `static final` method;
- a private method with the signature of a `final` method of the superclass;
- override that throws a broader checked exception, or a checked exception that the overridden method does not throw;
- `throws` clause with a type that is not a `Throwable`: `void f() throws String {}`;
- two fields with the same name and different types: `int x; long x;`;
- a `catch` parameter, the variable of an enhanced `for` statement or the resource variable of a try-with-resources
  statement with the name of a local variable or parameter in scope (`int e = 1; try { ... } catch (Exception e) {}`,
  `try (R r = ...)` with a local variable `r`), two resources with the same name, or a local variable in the block
  of a try-with-resources statement with the name of a resource variable; the later declaration shadows the earlier
  one in its scope;
- `native strictfp` method (the JVM accepts the combination);
- `static default` interface method; interface field without initializer: `interface I { int X; }`;
- default method that overrides a method of `Object`: `default boolean equals(Object o) { ... }`;
- a class that inherits two default methods with the same signature from two interfaces;
- `public` enum constructor; `final` modifier on an enum declaration (ignored);
- an unqualified invocation of a `private` instance method of the enum from the class body of an enum constant
  (`enum E { A { String n() { return p(); } }; private String p() { ... } abstract String n(); }`, which `javac`
  rejects as a reference from a static context; Janino invokes the method on the constant);
- annotation type element with parameters (`int value(int i);`), or of a type that is not allowed (`Object value();`);
- in a script (`IScriptEvaluator`) only ([#72](https://github.com/janino-lts/janino/issues/72)): any modifier on a
  local variable declaration (`static int x = 1;`, `public int x;`, `abstract int x;`); the modifiers are ignored.
  In a method body, only `final` and annotations are accepted, like by `javac`.

**Annotations:**

- an annotation without a value for an element that has no default: `@interface X { int value(); } @X class P {}`;
- an annotation on a kind of declaration that its `@Target` does not allow, e.g. `@Override` on a class;
- `@FunctionalInterface` on an interface that is not functional;
- `@SafeVarargs` on an instance method that is not `final`: `@SafeVarargs void f(String... s) {}`.

**Statements:**

- duplicate label: `L: while (true) { L: while (true) { break L; } }`;
- unreachable body of `while (false) { ... }` and `for (; false; ) { ... }`;
- `synchronized (null) {}`;
- `catch` of a checked exception that the `try` block cannot throw: `try { } catch (java.io.IOException e) { }`,
  also as an alternative of a multi-catch clause: `catch (IOException | SQLException e)` with a `try` block that
  throws only `IOException`;
- `catch` clause after a `catch` of a superclass: `catch (Exception e) { } catch (RuntimeException e) { }`, also
  of a superclass of an alternative of a multi-catch clause: `catch (Exception e) { } catch (IOException |
  SQLException e) { }`;
- `catch` of a type that is not a `Throwable` with an empty `try` block: `try { } catch (String e) { }` (with a
  non-empty `try` block, this is rejected);
- `catch` parameter that redeclares a local variable: `int e = 0; try { } catch (RuntimeException e) { }`, also a
  multi-catch parameter;
- a checked exception that is thrown in a `catch` or `finally` clause and that a `catch` clause of the same `try`
  statement catches: `try { } catch (Exception e) { throw new IOException(); }`, also the rethrow of the parameter:
  `catch (IOException | SQLException e) { throw e; }` in a method that declares neither exception, and
  `catch (Exception e) { throw e; }` in a method that does not declare the checked exceptions of the `try` block;
- an invocation of a method that declares a checked exception whose class name is all uppercase (`void m() throws
  X`, `throws IOEXC`), without catching or declaring the exception
  ([#65](https://github.com/janino-lts/janino/issues/65); before 3.1.17, such a type was taken for a type parameter
  and ignored; a `throw` statement and a constructor invocation are checked); a `throws` clause with an
  all-uppercase name that denotes no type (`void m() throws T` without a type parameter `T`), which is ignored;
- case label out of the range of the switch type: `switch (b) { case 1000: }` with `byte b`;
- case label of type `long`, `float` or `double`, whose value is truncated to `int`: `case 1L:`, `case 1.0:`,
  `case 4294967297L:` (which matches the value 1);
- recursive constructor invocation: `P() { this(); }`.

**Expressions, names and imports:**

- illegal forward reference in a field initializer: `int a = b; int b = 1;`;
- `instanceof` with a `final` class and an interface that it does not implement: `"x" instanceof Runnable`;
- invocation of a static interface method as if it were inherited (JLS 8.4.8): through an instance
  (`comparator.naturalOrder()`), through an implementing class or a subinterface (`P.s()`, `J.s()`), or unqualified
  from an implementing class (`s()`);
- `private` member type of another top-level class, also of a JDK class: `java.util.ArrayList.Itr x;`; for a class
  in another package, the JVM throws an `IllegalAccessError` when it resolves the reference, e.g. for
  `java.util.ArrayList.Itr.class` ([#33](https://github.com/janino-lts/janino/issues/33));
- the binary name of a nested class (with `$`) in source position: `java.util.Map$Entry e;`,
  `new java.util.AbstractMap$SimpleEntry<String, String>("a", "b")`;
- on-demand import of a package that does not exist: `import foo.*;`;
- static import of a member that does not exist, or is not static: `import static java.lang.Math.foo;`,
  `import static java.lang.String.length;`;
- an invocation of variable arity methods that `javac` reports as ambiguous: `f(1, 2)` with `f(int...)` and
  `f(Object...)` (`Object` is not a supertype of `int`, but Janino compares it with the array type `int[]`), or
  with `f(int...)` and `f(Integer...)` (without boxing, only `f(int...)` is applicable); Janino invokes
  `f(int...)` ([#156](https://github.com/janino-lts/janino/issues/156)).

**Generics** (type arguments are not checked):

- wrong number of type arguments: `List<String, String> l;`;
- type arguments for a type that is not generic: `String<Integer> s;`;
- incompatible parameterized types: `List<String> l = new ArrayList<Integer>();`;
- unknown type as a type argument: `List<Foo> l;`;
- generic array creation: `new List<String>[1]`.

## 4. Optional deviations

`org.codehaus.janino.JaninoOption` contains options that deviate from the JLS on purpose. Neither is enabled by
default:

- `EXPRESSIONS_IN_TRY_WITH_RESOURCES_ALLOWED`: allow any expression as a resource of a try-with-resources statement;
- `PRIVATE_MEMBERS_OF_ENCLOSING_AND_ENCLOSED_TYPES_INACCESSIBLE`: disallow access to `private` members of enclosing
  and enclosed types.

Set them with `options(...)` of `SimpleCompiler`, `Compiler`, `JavaSourceIClassLoader` or the evaluators.

## Tests

The deviations are recorded in the test suite, so that every change of Janino's behavior, intended or not, makes a
test fail:

- invalid code: [`InvalidCodeTest`](commons-compiler-tests/src/test/resources/invalidCode) (a record `ACCEPTED`
  means that Janino accepts the code);
- valid code: [`LanguageSupportTest`](commons-compiler-tests/src/test/resources/languageSupport) (the result that
  the language requires, and Janino's actual behavior).

If you find a deviation that is not listed here, please create an
[issue](https://github.com/janino-lts/janino/issues).
