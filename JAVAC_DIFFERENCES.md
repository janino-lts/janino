# Differences between Janino and javac

Janino compiles Java source code like `javac`, but not in every respect: it accepts some invalid code, it rejects some
valid code, and some valid code behaves differently. This page is the register of all deviations that are known as
of version 3.1.18 and the development line, so that you can decide whether they matter for your code. Every
deviation has an ID: its class (see "The two modes" below) and a number, e.g. `L-07`. IDs are stable: a number is
never reused or reassigned, and the test records refer to them. A deviation that is not in the register is an
unknown defect; please report it as an issue.

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

## The two modes

[Janino and javac: the compatibility mode and the compliance mode](JAVAC_COMPLIANCE.md) defines a contract with two
modes, whose foundation the development line (`master`) implements:

- **The compatibility mode** (the default): code that Janino 3.1.12 compiled into loadable classes ("legacy code")
  keeps working with the same behavior, except where 3.1.12 miscompiled it; everything else behaves like `javac`, or
  is rejected. In particular, the compatibility mode accepts a program if and only if 3.1.12 accepts it into loadable
  classes, or `javac` accepts it within the scope: it never rejects what either of the two accepts, and it never
  accepts what neither accepts.
- **The compliance mode** (`JaninoOption.JAVAC_COMPLIANCE`, or the system property
  `org.codehaus.janino.javacCompliance=true`, which sets the initial options of every compiler that is created
  afterwards; an explicit `options(...)` call replaces them): Janino behaves like `javac` within the scope of the
  language that it implements. The compliance mode is incomplete by design: this register is authoritative for what
  it corrects, so "compiles in the compliance mode" does not mean "is valid Java".

Every deviation belongs to one class, which determines what each mode does with it:

| Class | 3.1.12 | `javac` | Compatibility mode | Compliance mode |
|---|---|---|---|---|
| **S** (section 1) | loadable, a consistent rule | accepts | the 3.1.12 behavior | like `javac` |
| **V** | loadable, miscompiled | accepts | like `javac` (corrected in both modes) | like `javac` |
| **D** (section 2) | rejected, internal error or not loadable | accepts or rejects | like `javac` (corrected in both modes) | like `javac` |
| **L** (section 3) | loadable | rejects | the 3.1.12 behavior | rejected |
| **F** (section 2) | any | accepts | rejected (not implemented) | rejected (not implemented) |
| **G** (sections 1 and 3) | any | any | erased types (outside the scope) | erased types (outside the scope) |
| **A** (section 5) | loadable | any | as registered | like `javac` |

Deviations of class V are corrected in both modes as soon as they are found, so the register holds none. Where
the compliance mode already corrects a deviation, its entry says so; everywhere else, the compliance mode behaves
like the compatibility mode (an open entry).

## 1. Valid code that behaves differently

Class S: Janino applies a consistent rule of its own, which a program can rely on; the compatibility mode keeps it,
the compliance mode follows `javac` (where the column says so; "open" means not yet).

| ID | Code | `javac` | Janino | Issue | Compliance mode |
|---|---|---|---|---|---|
| S-01 | `Boolean Z = null; boolean x = Z \|\| true;` (also `Z && false`) | `NullPointerException` | `x == true`, no exception | [#40](https://github.com/janino-lts/janino/issues/40) | like `javac` (development line) |
| S-02 | `f(Object o)` and `f(int... i)`; `f(1)` | invokes `f(Object)` | invokes `f(int...)` (variable arity methods before boxing) | | like `javac` (development line) |
| S-03 | `assert false;` | throws only if assertions are enabled (`-ea`) | always throws | | like `javac` (development line) |
| S-04 | `Byte B = 1; Object x = z ? B : 5;` (see below) | `x` is a `Byte` | `x` is an `Integer` | [#38](https://github.com/janino-lts/janino/issues/38) | like `javac` (development line) |
| S-04 | `char c = 'a'; Object x = false ? c : (short) 66;` | `x` is an `Integer` | `x` is a `Character` | [#38](https://github.com/janino-lts/janino/issues/38) | like `javac` (development line) |
| S-07 | `new Object() {}.getClass().getModifiers()` | `0` (JDK 9 and later) | `0x10` (`final`), kept for the default `serialVersionUID` | [#73](https://github.com/janino-lts/janino/issues/73) | open |
| S-06 | `getEnclosingMethod()` of a class declared in a `private` instance method `p()` | `p` | `p$` (Janino compiles the method as a static method `p$`, see below) | | open |
| S-08 | `getName()` of a local class `L` declared in a class `P` | `"P$1L"` | `"P$L"` | | open |
| S-11 | `P.this`, and the simple name of a `private` member of `P`, in `class Q extends P` that is declared in `P` (see below) | the enclosing instance of `Q` | `this` | [#101](https://github.com/janino-lts/janino/issues/101) | like `javac` (development line) |
| S-12 | `String s = "x"; boolean x = ("a" + (true ? "b" : s)) == "ab";` (see below) | `x == false` | `x == true` | | like `javac` (development line) |

**S-05, constant expressions that are not folded** ([#47](https://github.com/janino-lts/janino/issues/47)): Janino
does not evaluate shifts, relational operators, `~`, operations with `char` operands and casts to and from `char` at
compile time (see section 2). Their values are correct, but a `static final` field with such an initializer, e.g.
`static final int X = 1 << 2;`, is not a constant variable:

- it has no `ConstantValue` attribute and is initialized in the static initializer;
- reading it from another class initializes the declaring class, which `javac`'s code does not (JLS 12.4.1).

Likewise, such an operand of a conditional expression is not a constant: `z ? b : (1 << 2)` with a `byte b` has the
type `int` (JLS 15.25.2 gives it the type `byte`, see S-04).

**Generics, class G** (type arguments are parsed, but otherwise ignored, see
[Limitations](https://janino-lts.github.io/janino/#limitations); everything that depends on the typing of generics is
outside the scope of both modes):

- G-01: expressions of a parameterized type have their erased type, so a cast is required where `javac` infers the
  type argument: `String s = list.get(0);` with a `List<String> list` is rejected (`Assignment conversion not
  possible`), as are `for (Map.Entry<String, Integer> e : map.entrySet())`, `int i = map.get("k");` and
  `list.get(0).length()`; with a cast, they compile;
- G-02: overload resolution uses the erased types: with `f(Object)` and `f(String)`, `f(list.get(0))` invokes
  `f(Object)` for a `List<String> list`;
- G-03: no bridge methods: if `class B extends A<String>` overrides `m(T)` with `m(String)`, an invocation through
  `A` still invokes `A.m`, and a class that implements `Comparable<Foo>` with `compareTo(Foo)` is rejected (`must
  implement method compareTo(Object)`);
- G-04: no `Signature` attributes: `Field.getGenericType()` returns a `Class`, not a `ParameterizedType`.

**S-06, private members in the class files:** Janino compiles `private` fields, methods and constructors without the
`private` flag (package access), and a `private` instance method `m(...)` as a static method `m$(P, ...)` with the
instance as the first parameter. Reflection shows these modifiers and names, and code in the same package that is
compiled against the class file can access these members.

**S-09, protected member types in the class files** ([#87](https://github.com/janino-lts/janino/issues/87)): the
`access_flags` of a `protected` member type contain `ACC_PROTECTED` (`0x0004`) in addition to `ACC_PUBLIC`; `javac`
writes `ACC_PUBLIC` only. Janino keeps the bit because it determines the accessibility of a type that it reads from a
class file from these flags. The JVM ignores it, and the reflection API reads the modifiers from the `InnerClasses`
attribute, which is the same as with `javac`.

**S-10, parameter annotations** (since 3.1.16, [#61](https://github.com/janino-lts/janino/issues/61); before, they were
not written at all): Janino records the annotations of the parameters against the parameters of the method
descriptor, which include the parameters that the compiler prepends to the declared ones: the captured local
variables of a local class constructor, and the instance of a `private` instance method (see above). `javac` records
them against the declared parameters. `Constructor.getParameterAnnotations()` of a local class that captures local
variables, and `Method.getParameterAnnotations()` of a `private` instance method, therefore return arrays that are
longer by the number of prepended parameters, with each annotation at the index of its actual parameter.

**An inner class whose superclass is an inner class that extends the enclosing class** (class D,
[#97](https://github.com/janino-lts/janino/issues/97), fixed in 3.1.19): for
`class P { class Q extends P {} class R extends Q {} }`, Janino 3.1.18 and earlier pass the uninitialized instance
of `R`, instead of the enclosing instance of `R`, as the enclosing instance to the constructor of `Q`, and the JVM
rejects the class `P$R` (`VerifyError`).

**S-11, an inner class that extends its enclosing class** ([#101](https://github.com/janino-lts/janino/issues/101)):
in `class P { class Q extends P { ... } }`, and likewise in an anonymous or local subclass of `P` that is declared in
`P` (`return new P(...) { ... };`), expressions in `Q` denote `Q` itself (`this`) instead of the enclosing instance of
`Q`:

- `P.this`, also in `P.this.f`, `P.this.m()` and `P.super.f`, and in a class nested in `Q`: Janino takes the first
  class, starting with `Q`, that is a subclass of `P`, while `javac` takes the lexically enclosing class `P`;
- the simple name of a `private` field or method of `P`: Janino finds it through the superclass of `Q`, although
  `private` members are not inherited.

Members that `Q` inherits from `P`, and `((P) this).f`, denote `this` with both compilers. The compliance mode
follows `javac`; as a consequence, it rejects the access to a `private` member of a superclass through the subclass
(L-20, L-45) and `B.this` for a superclass `B` of an enclosing class (L-46). Before the development line, compiling
`P.this` as a value of type `P` (e.g. `Object o = P.this;`) failed with an `InternalCompilerException` when the
compiler's assertions were enabled (`-ea`).

**S-04, the type of a conditional expression with numeric operands of different types**
([#38](https://github.com/janino-lts/janino/issues/38)):

- Janino does not implement the rule of JLS 15.25.2 for an operand of type `Byte`, `Short` or `Character` and a
  constant of type `int` that is representable in its unboxed type: `z ? B : 5` has the type `int` with Janino, the
  type `byte` with `javac`;
- with a constant condition, Janino gives the expression the type of its operand of type `byte`, `short` or `char`
  where `javac` applies binary numeric promotion: `false ? c : (short) 66` has the type `char` with Janino, `int` with
  `javac`; `false ? b : c` with `c = (char) 200` is the `byte` -56 with Janino, the `int` 200 with `javac`.

The type determines the class of the boxed value, overload resolution (`f(z ? B : 5)` invokes `f(int)` instead of
`f(byte)`) and string conversion (`"" + (true ? 'a' : (short) 1)` is `"a"` instead of `"97"`). The compliance mode
follows `javac`; as a consequence, it rejects code that is valid only with Janino's type (L-47), and it accepts valid
code that the compatibility mode rejects because of the type `int` (D-07).

**S-12, conditional expressions as constant expressions**: Janino treats a conditional expression with a constant
condition as a constant expression if the selected operand is constant, also when the other operand is not; JLS
15.28 requires all three operands to be constant. `"a" + (true ? "b" : s)` with a variable `s` is therefore a
constant, interned string with Janino (`== "ab"` is `true`), but not with `javac`. The compliance mode follows
`javac`; as a consequence, it rejects such an expression where a constant expression is required (L-48).

## 2. Valid code that Janino rejects

Class D: 3.1.12 did not compile the code into loadable classes either, so no program depends on it; a fix applies to
both modes.

**D-01, constant expressions** ([#47](https://github.com/janino-lts/janino/issues/47)): wherever the language requires a
constant expression, the expressions that Janino does not fold (see section 1) are rejected:

- `case A:` with `static final int A = 1 << 0;`, `case 'a' + 1:`, `case MASK:` with `static final int MASK = ~0xFF;`;
- `byte b = 1 << 2;`, `char c = 'a' + 1;`, `byte b = -'a';`;
- `byte b = MAX > 5 ? 1 : 2;` with `static final int MAX = 10;`;
- `int f() { while (MAX > 0) { } }` ("Method must return a value", because the condition is not constant).

**D-02, multi-catch** ([#21](https://github.com/janino-lts/janino/issues/21), since 3.1.16): the type of the parameter
of `catch (A | B e)` is the nearest common superclass of the alternatives, not their least upper bound with the
interfaces that all alternatives implement: `e.n()` with a method `n()` of an interface that `A` and `B` implement,
but not their common superclass, is rejected (`A method named "n" is not declared in any enclosing class nor any
supertype`); with a cast, `((I) e).n()`, it compiles.

**D-03, effectively final local variables** ([#24](https://github.com/janino-lts/janino/issues/24), since 3.1.17;
before, a local variable had to be declared `final` to be accessed from a local or anonymous class): Janino finds a
local variable, a parameter, a `catch` parameter or the variable of an enhanced `for` statement effectively final by a
conservative rule: the variable has an initializer, or is a parameter, and its name is not assigned, incremented or
decremented anywhere in the method, constructor or initializer that declares it, including nested classes. Janino
therefore rejects (`Cannot access non-final local variable "x" from inner class`):

- a variable without an initializer that is assigned exactly once: `int x; x = 1; new Runnable() { ... x ... }`
  (JLS 4.12.4 requires definite assignment analysis here, which Janino does not have);
- a variable whose name is assigned in another block or in a nested class:
  `{ int x = 1; x = 2; } int x = 3; new Runnable() { ... x ... }`.

**D-04, floating-point literals with a leading zero** ([#96](https://github.com/janino-lts/janino/issues/96), fixed
in 3.1.19): Janino 3.1.18 and earlier reject `09.5`, `00.5`, `0123.5`, `07e1`, `07f`, `09d` and `08.` (e.g. `';'
expected instead of '9.5'`), because they scan the digits after a leading `0` as an octal integer literal.

**D-05, a member type of a parameterized type** ([#23](https://github.com/janino-lts/janino/issues/23)): `O<String>.I`
is rejected (`IDENTIFIER expected instead of '.'`).

**D-07, a conditional expression with a wrapper operand and an `int` constant** in a context that requires the
unboxed type ([#38](https://github.com/janino-lts/janino/issues/38)): `byte x = z ? B : 5;` and `Byte x = z ? B : 5;`
with a `Byte B`, `Character x = true ? C : 98;` with a `Character C`, and `h(z ? S : 5)` with a `Short S` and a method
`h(short)` are rejected (`Assignment conversion not possible from type "int"`, `No applicable constructor/method
found`), because the type of the expression is `int` (S-04). The compatibility mode cannot accept this code without
giving up the type of S-04 that it keeps for legacy code; the compliance mode accepts it, like `javac`.

**Language features that Janino does not implement, class F** (see
[Limitations](https://janino-lts.github.io/janino/#limitations); both modes reject them): F-01 lambda expressions
and method references; F-02 `switch` expressions, arrow labels and multiple labels in a `case`; F-03 pattern
matching (for `instanceof` and `switch`); F-04 records; F-05 sealed types; F-06 local variable type inference
(`var`); F-07 local enums and interfaces; F-08 type annotations; F-09 repeating annotations; F-10 `private` interface
methods; F-11 the diamond operator with an anonymous class.

## 3. Invalid code that Janino accepts

Class L: `javac` rejects the following code, Janino compiles it, and the JVM loads the generated classes. Most of it
behaves as the source suggests (e.g. an assignment to a `final` local variable assigns it). See
[issue #33](https://github.com/janino-lts/janino/issues/33) for the compatibility considerations. The compatibility
mode keeps accepting all of it; the compliance mode rejects it once the respective check is implemented (today, it
rejects L-20, L-45 and L-46, see S-11, L-47, see S-04, and L-48, see S-12; all other entries of this section are
open). One exception, registered as A-05 (section 5): the access to a `protected` member of a class in another
package through an expression whose type is neither the accessing class nor a subclass of it
(`((Object) this).clone()`, `Object o = new P(); o.clone()`) is rejected since 3.1.16, like by `javac`, although the
JVM loaded such classes when the verifier could infer the type `P` from the bytecode
([#54](https://github.com/janino-lts/janino/issues/54)); it rejected them for a parameter, a field or a method result
of type `Object`.

Code generators rely on some of these leniencies. The code that Apache Spark generates for SQL queries, for
example, assigns to a `final` local variable, names nested classes by their binary names, creates generic arrays
and assigns a parameterized type to a field with a different type argument
([SPARK-58437](https://issues.apache.org/jira/browse/SPARK-58437)); such code keeps compiling.

**`final` variables and definite assignment:**

- L-01: assignment to a `final` field: `final int x = 1; void f() { x = 2; }`;
- L-02: assignment to a blank `final` field outside of a constructor: `final int x; P() { x = 1; } void f() { x = 2; }`;
- L-03: blank `final` fields that are never assigned: `final int x;`, `static final int X;`;
- L-04: assignment to a `final` local variable, also twice to a blank one: `final int x = 1; x = 2;`,
  `final int x; x = 1; x = 2;`;
- L-05: assignment to a blank `final` local variable in a loop: `final int x; for (;;) { x = 1; }`;
- L-06: assignment to a `final` parameter, a `final` variable of an enhanced `for` statement, or the (implicitly
  `final`) resource variable of a try-with-resources statement: `try (R r = new R()) { r = null; }` (the resource is
  then not closed);
- L-07: assignment to a `static final` field of another class, e.g. an interface field (`I.X = 2;`) or an enum constant
  (`E.A = null;`): this compiles, but throws an `IllegalAccessError` when it is executed.

**Declarations:**

- L-08: override with weaker access, also of an interface method: `class P implements Runnable { void run() {} }`;
- L-09: a static method that hides an instance method, an instance method that overrides a static method, a static
  method that hides a `static final` method;
- L-10: a private method with the signature of a `final` method of the superclass;
- L-11: override that throws a broader checked exception, or a checked exception that the overridden method does not
  throw;
- L-12: `throws` clause with a type that is not a `Throwable`: `void f() throws String {}`;
- L-13: two fields with the same name and different types: `int x; long x;`;
- L-14: a `catch` parameter, the variable of an enhanced `for` statement or the resource variable of a
  try-with-resources statement with the name of a local variable or parameter in scope
  (`int e = 1; try { ... } catch (Exception e) {}`, `try (R r = ...)` with a local variable `r`), two resources with
  the same name, or a local variable in the block of a try-with-resources statement with the name of a resource
  variable; the later declaration shadows the earlier one in its scope;
- L-15: `native strictfp` method (the JVM accepts the combination);
- L-16: `static default` interface method; interface field without initializer: `interface I { int X; }`;
- L-17: default method that overrides a method of `Object`: `default boolean equals(Object o) { ... }`;
- L-18: a class that inherits two default methods with the same signature from two interfaces;
- L-19: `public` enum constructor; `final` modifier on an enum declaration (ignored);
- L-20: an unqualified invocation of a `private` instance method of the enum from the class body of an enum constant
  (`enum E { A { String n() { return p(); } }; private String p() { ... } abstract String n(); }`, which `javac`
  rejects as a reference from a static context; Janino invokes the method on the constant; the compliance mode
  rejects it, see S-11);
- L-21: annotation type element with parameters (`int value(int i);`), or of a type that is not allowed
  (`Object value();`);
- L-22: in a script (`IScriptEvaluator`) only ([#72](https://github.com/janino-lts/janino/issues/72)): any modifier on a
  local variable declaration (`static int x = 1;`, `public int x;`, `abstract int x;`); the modifiers are ignored.
  In a method body, only `final` and annotations are accepted, like by `javac`.

**Annotations:**

- L-23: an annotation without a value for an element that has no default: `@interface X { int value(); } @X class P {}`,
  or with a value of the wrong type: `@X("x")`;
- L-24: an annotation on a kind of declaration that its `@Target` does not allow, e.g. `@Override` on a class;
- L-25: `@FunctionalInterface` on an interface that is not functional;
- L-26: `@SafeVarargs` on an instance method that is not `final`: `@SafeVarargs void f(String... s) {}`.

**Statements:**

- L-27: duplicate label: `L: while (true) { L: while (true) { break L; } }`;
- L-28: unreachable body of `while (false) { ... }` and `for (; false; ) { ... }`;
- L-29: `synchronized (null) {}`;
- L-30: `catch` of a checked exception that the `try` block cannot throw: `try { } catch (java.io.IOException e) { }`,
  also as an alternative of a multi-catch clause: `catch (IOException | SQLException e)` with a `try` block that
  throws only `IOException`;
- L-31: `catch` clause after a `catch` of a superclass: `catch (Exception e) { } catch (RuntimeException e) { }`, also
  of a superclass of an alternative of a multi-catch clause: `catch (Exception e) { } catch (IOException |
  SQLException e) { }`;
- L-32: `catch` of a type that is not a `Throwable` with an empty `try` block: `try { } catch (String e) { }` (with a
  non-empty `try` block, this is rejected);
- `catch` parameter that redeclares a local variable: `int e = 0; try { } catch (RuntimeException e) { }`, also a
  multi-catch parameter (L-14);
- L-33: a checked exception that is thrown in a `catch` or `finally` clause and that a `catch` clause of the same `try`
  statement catches: `try { } catch (Exception e) { throw new IOException(); }`, also the rethrow of the parameter:
  `catch (IOException | SQLException e) { throw e; }` in a method that declares neither exception, and
  `catch (Exception e) { throw e; }` in a method that does not declare the checked exceptions of the `try` block;
- L-34: an invocation of a method that declares a checked exception whose class name is all uppercase (`void m() throws
  X`, `throws IOEXC`), without catching or declaring the exception
  ([#65](https://github.com/janino-lts/janino/issues/65); before 3.1.17, such a type was taken for a type parameter
  and ignored; a `throw` statement and a constructor invocation are checked); a `throws` clause with an
  all-uppercase name that denotes no type (`void m() throws T` without a type parameter `T`), which is ignored;
- L-35: case label out of the range of the switch type: `switch (b) { case 1000: }` with `byte b`;
- L-36: case label of type `long`, `float` or `double`, whose value is truncated to `int`: `case 1L:`, `case 1.0:`,
  `case 4294967297L:` (which matches the value 1);
- L-37: recursive constructor invocation: `P() { this(); }`.

**Expressions, names and imports:**

- L-38: illegal forward reference in a field initializer: `int a = b; int b = 1;`;
- L-39: `instanceof` with a `final` class and an interface that it does not implement: `"x" instanceof Runnable`;
- L-40: invocation of a static interface method as if it were inherited (JLS 8.4.8): through an instance
  (`comparator.naturalOrder()`), through an implementing class or a subinterface (`P.s()`, `J.s()`), or unqualified
  from an implementing class (`s()`);
- L-41: `private` member type of another top-level class, also of a JDK class: `java.util.ArrayList.Itr x;`; for a class
  in another package, the JVM throws an `IllegalAccessError` when it resolves the reference, e.g. for
  `java.util.ArrayList.Itr.class` ([#33](https://github.com/janino-lts/janino/issues/33));
- L-42: the binary name of a nested class (with `$`) in source position: `java.util.Map$Entry e;`,
  `new java.util.AbstractMap$SimpleEntry<String, String>("a", "b")`;
- L-43: on-demand import of a package that does not exist: `import foo.*;`;
- L-44: static import of a member that does not exist, or is not static: `import static java.lang.Math.foo;`,
  `import static java.lang.String.length;`;
- L-45: access to a `private` member of a superclass through the subclass, which does not inherit it (JLS 8.2):
  `this.secret` and `this.sec()` in `class Q extends P` that is declared in `P`, or the simple name `secret` in a
  `static class S extends P` (where `P` has no instance); Janino accesses the member of the subclass instance
  ([#101](https://github.com/janino-lts/janino/issues/101); the compliance mode rejects it, see S-11);
- L-46: `B.this` for a superclass `B` of an enclosing class `P`, which is not an enclosing class itself
  (`class P extends B { class Q { ... B.this.f ... } }`); Janino takes the instance of `P`
  ([#101](https://github.com/janino-lts/janino/issues/101); the compliance mode rejects it, see S-11);
- L-47: an assignment or a method argument that is valid only with the type that Janino gives a conditional
  expression with a constant condition (S-04): `short x = true ? s : c;`, `Short x = true ? s : c;`,
  `byte x = true ? b : c;`, `char x = true ? c : (byte) 1;`, `h(true ? s : c)` with a method `h(short)` (`javac`:
  the type is `int`; [#38](https://github.com/janino-lts/janino/issues/38); the compliance mode rejects it, see S-04);
- L-48: a conditional expression with a constant condition and an operand that is not constant, where a constant
  expression is required: `byte x = false ? i : 5;` and `case false ? i : 5:` with an `int i` (the compliance mode
  rejects it, see S-12).

**Generics, class G** (type arguments are not checked; outside the scope of both modes):

- G-05: wrong number of type arguments: `List<String, String> l;`;
- G-06: type arguments for a type that is not generic: `String<Integer> s;`;
- G-07: incompatible parameterized types: `List<String> l = new ArrayList<Integer>();`, also a parameterized field
  assigned a value with a different type argument;
- G-08: unknown type as a type argument: `List<Foo> l;`;
- G-09: generic array creation: `new List<String>[1]`.

**Invalid code that compiles into classes that the JVM rejects** (class D; not legacy code, because the classes do
not load): D-06, an inner class whose superclass is an inner class that extends the enclosing class, in a static
context (`class P { class Q extends P {} static Object f() { return new Q() { }; } }`, also a local or a static member
class that extends `Q`; [#97](https://github.com/janino-lts/janino/issues/97)); `javac` rejects the code, Janino
compiles it into classes that fail with a `VerifyError`.

## 4. Optional deviations

`org.codehaus.janino.JaninoOption` contains options that deviate from the JLS on purpose. Neither is enabled by
default:

- `EXPRESSIONS_IN_TRY_WITH_RESOURCES_ALLOWED`: allow any expression as a resource of a try-with-resources statement;
- `PRIVATE_MEMBERS_OF_ENCLOSING_AND_ENCLOSED_TYPES_INACCESSIBLE`: disallow access to `private` members of enclosing
  and enclosed types.

Set them with `options(...)` of `SimpleCompiler`, `Compiler`, `JavaSourceIClassLoader` or the evaluators.

The development line (`master`) has a third option, `JAVAC_COMPLIANCE`, the compliance mode (see "The two modes"
above and [Development line](https://github.com/janino-lts/janino/blob/master/DEVELOPMENT_LINE.md)).

## 5. Registered exceptions

Class A: changes of the behavior of legacy code against the contract, made before the contract existed or decided
under its exception rule; each is frozen, with the version since which it applies. The justifications are in
[Janino and javac: the compatibility mode and the compliance mode](JAVAC_COMPLIANCE.md), section 8.

| ID | Change | Since | Compatibility mode per contract |
|---|---|---|---|
| A-01 | `byte b = -Byte.MIN_VALUE;` and `short s = -Short.MIN_VALUE;` are rejected ([#39](https://github.com/janino-lts/janino/issues/39)) | 3.1.15 | accepted, with the values -128 and -32768 |
| A-02 | when compiling against class files, a statement after `while (A.F) { }` with a `boolean` constant `true` is rejected ([#46](https://github.com/janino-lts/janino/issues/46)) | 3.1.15 | accepted |
| A-03 | `true ? c : (short) -1` (a `char c`, a constant condition, a negative `byte` or `short` constant) has the type `int` ([#51](https://github.com/janino-lts/janino/issues/51)); 3.1.12 gave it the type `char`, 3.1.15 rejected it | 3.1.15 | type `char`, as in 3.1.12 |
| A-04 | a constant conditional expression of type `long`, `float` or `double` is rejected where a `byte`, `short` or `char` value is required: `byte b = true ? 1 : 2L;` ([#55](https://github.com/janino-lts/janino/issues/55)); 3.1.12 accepted it with the value of the selected operand | 3.1.16 | accepted, with that value |
| A-05 | the access to a `protected` instance member of a class in another package through an expression whose static type is neither the accessing class (or an enclosing class) nor a subclass of it is rejected ([#54](https://github.com/janino-lts/janino/issues/54), [#59](https://github.com/janino-lts/janino/issues/59)): `((Object) this).clone()`, `Object o = new P(); o.clone()`, `FilterInputStream s = new P(); s.in`, also from an inner class; 3.1.12 accepted it, and the JVM loads the class iff its verifier infers a subclass type from the bytecode (it does not for a parameter, a field or a method result) | 3.1.16, 3.1.17 | accepted where the JVM loads the class |
| A-06 | a member annotation type is parsed like a top-level one ([#43](https://github.com/janino-lts/janino/issues/43)): type parameters (`class P { @interface A<T> {} }`), an `extends` clause, and `default` or `static` methods are rejected, and a class that implements a member annotation type must implement `annotationType()`; 3.1.12 compiled these declarations as ordinary interfaces | 3.1.16 | accepted, as an ordinary interface |
| A-07 | a cast of the clone of an array to an unrelated array type, `(String[]) intArray.clone()`, is rejected ([#58](https://github.com/janino-lts/janino/issues/58)); 3.1.12 compiled it (the clone had the type `Object`) into code that throws a `ClassCastException` | 3.1.17 | accepted, throwing at run time |
| A-08 | a duplicate annotation on a parameter is rejected ([#61](https://github.com/janino-lts/janino/issues/61)); 3.1.12 did not write parameter annotations at all, so it accepted the duplicate | 3.1.16 | accepted |

A-07 and A-08 were found by `LegacyDifferentialTest` (below) when the register was created; they are released
corrections of class D that also rejected a form of invalid legacy code, and are registered like A-01 to A-03.

## Tests

The deviations are recorded in the test suite, so that every change of Janino's behavior, intended or not, makes a
test fail:

- invalid code: [`InvalidCodeTest`](commons-compiler-tests/src/test/resources/invalidCode) (a record `ACCEPTED`
  means that Janino accepts the code);
- valid code: [`LanguageSupportTest`](commons-compiler-tests/src/test/resources/languageSupport) (the result that
  the language requires, and Janino's actual behavior).

Each record names the ID of its deviation (`id:`), so that the records of an ID can be found. On the development
line, each record states the behavior of the compatibility mode (`janino:`) and, where it differs, of the compliance
mode (`compliant:`), and both modes are tested; where the behavior differs from that of Janino 3.1.12, the record
states the behavior of 3.1.12 (`legacy:`) and the correction that explains the difference, and
`LegacyDifferentialTest` verifies it against 3.1.12.

If you find a deviation that is not listed here, please create an
[issue](https://github.com/janino-lts/janino/issues).
