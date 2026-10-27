# Differences between Janino and javac

Janino compiles Java source code like `javac`, but not in every respect: it accepts some invalid code, it rejects some
valid code, and some valid code behaves differently. This page is the register of all deviations that are known as
of version 3.1.20 and the development line, so that you can decide whether they matter for your code. Every
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
| S-08 | `getName()` of a local class `L` declared in a class `P` (see below) | `"P$1L"` | `"P$L"` | | open |
| S-11 | `P.this`, and the simple name of a `private` member of `P`, in `class Q extends P` that is declared in `P` (see below) | the enclosing instance of `Q` | `this` | [#101](https://github.com/janino-lts/janino/issues/101) | like `javac` (development line) |
| S-12 | `String s = "x"; boolean x = ("a" + (true ? "b" : s)) == "ab";` (see below) | `x == false` | `x == true` | | like `javac` (development line) |
| S-13 | `f(Object o)` and `f(int... i)`; `f(new int[] { 1 })` | invokes `f(int...)` | invokes `f(Object)` (a variable arity method is less specific than a fixed arity method) | [#126](https://github.com/janino-lts/janino/issues/126) | like `javac` (development line) |
| S-13 | `f(Object... a)`; `f(null)` | `a` is `null` | `a` is an array with one element `null` (the arguments of a variable arity method are always expanded) | [#126](https://github.com/janino-lts/janino/issues/126) | like `javac` (development line) |
| S-14 | `static boolean z;`, `boolean x = ("a" + (true \|\| z)) == "atrue";` (also `false && z`; see below) | `x == false` | `x == true` | [#140](https://github.com/janino-lts/janino/issues/140) | like `javac` (development line) |
| S-15 | `String t3(final int c) { class L { int c = 10; String l() { return "" + c; } } return new L().l(); }` (see below) | `t3(8)` is `"10"` (the field) | `t3(8)` is `"8"` (the parameter) | [#137](https://github.com/janino-lts/janino/issues/137) | like `javac` (development line) |
| S-16 | `String w = ""; boolean x = ("b" + w) == ("b" + w);` (see below) | `x == false` | `x == true` | | open |
| S-17 | `final int k = 5; byte b = 1; Object x = z ? b : k;` (see below) | `x` is a `Byte` | `x` is an `Integer` | [#139](https://github.com/janino-lts/janino/issues/139) | like `javac` (development line) |
| S-18 | `B.s("x")` with `static s(Object)` in a public class `B` and `static s(String)` in its package-private superclass `A`, from another package (see below) | invokes `A.s(String)` | invokes `B.s(Object)` (the methods of `A` are considered only if no accessible method is applicable) | [#154](https://github.com/janino-lts/janino/issues/154) | open |
| S-19 | `boolean x = ("a" + null) == "anull";` (also `null == null`; see below) | `x == false` | `x == true` | [#140](https://github.com/janino-lts/janino/issues/140) | like `javac` (development line) |

**S-08, the binary names of local classes** ([#143](https://github.com/janino-lts/janino/issues/143), fixed in
3.1.20): Janino names a local class `L` in a type `E` `E$L`, where `javac` inserts a number (`E$1L`, JLS 13.1). Since
3.1.20, Janino inserts a number only where `E$L` is already taken: by a member type `L` of `E` (the local class is
named `E$1L`), or by another local class `L` in `E` (the second one is named `E$2L`, the third one `E$3L`). Janino
3.1.19 and earlier gave all of them the name `E$L` and kept only one class file, so that the code that used another
one failed with a `NoSuchMethodError`, or used the wrong class. Both modes name local classes this way.

**S-05, constant expressions that are not folded** ([#47](https://github.com/janino-lts/janino/issues/47)): Janino
does not evaluate shifts, relational operators, `~`, operations with `char` operands, casts to and from `char`, `&`,
`|` and `^` with `boolean` operands, and casts to `String` at compile time (see section 2). Their values are correct,
but a `static final` field with such an initializer, e.g. `static final int X = 1 << 2;`, is not a constant variable:

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

**`super.m()` for a `private` method of the enclosing superclass**
([#136](https://github.com/janino-lts/janino/issues/136), fixed in 3.1.20): for
`class P { private String who() { ... } class Q extends P { ... super.who() ... } }`, also in a local, anonymous or
static member class that extends `P` and is declared in `P`, and for `Q.super.who()` from a class nested in `Q`,
Janino 3.1.19 and earlier invoke `who()` by its original name and descriptor, although they compile it as a static
method `who$(P)` (S-06, see above), and the invocation fails with a `NoSuchMethodError`. Since 3.1.20, Janino invokes
`who$(P)` with the instance of `Q`, and the invocation yields what `javac`'s code yields.

**`super.m()` for a `static` method of the superclass**
([#150](https://github.com/janino-lts/janino/issues/150), fixed in 3.1.20): for
`class B { static String s() { ... } } class P extends B { ... super.s() ... }`, Janino 3.1.19 and earlier load
`this` and invoke `s()` with `invokespecial`, which the JVM allows only for instance methods: the invocation fails
with an `IncompatibleClassChangeError`. `P.super.s()` from an inner class of `P` is an internal compiler error
(`ArrayIndexOutOfBoundsException`; Janino 3.1.12 rejects it, `Qualified superclass method invocation NYI`). Since
3.1.20, Janino invokes the method like the simple name `s()` does, with `invokestatic`.

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
(L-20, L-45) and `B.this` for a superclass `B` of an enclosing class (L-46), and it correctly compiles valid code for
which the compatibility mode generates a class that the JVM rejects (D-10). Before the development line, compiling
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

**S-14, `&&` and `||` with a constant left operand** ([#140](https://github.com/janino-lts/janino/issues/140)): Janino
treats `true || z` and `false && z` as constant expressions for any right operand `z`, because the left operand
decides the value; JLS 15.28 requires both operands to be constant. Such an expression is therefore a constant with
Janino, but not with `javac`: in a string concatenation, `"a" + (true || z)` is an interned string (`== "atrue"` is
`true`), and `static final boolean F = true || z;` gets a `ConstantValue` attribute instead of being initialized in
the static initializer. As a consequence, Janino also accepts such an expression where a constant expression is
required, and takes it for a constant in the reachability analysis (L-50). `z || true` and `z && false` are not
constant with either compiler. The compliance mode follows `javac` (development line); as a consequence, it rejects
such an expression where a constant expression is required, and `int f() { while (true || z) { return 1; } }` for the
missing return (L-50).

**S-15, a field of a local or anonymous class and a variable of the enclosing method**
([#137](https://github.com/janino-lts/janino/issues/137)): in a local or anonymous class, a simple name that denotes
both a field of the class, declared or inherited, and a parameter or local variable of the enclosing method denotes
the variable with Janino; by JLS 6.4.1, the field shadows the variable, and `javac` reads the field. For example,
`new P() { int d = 20; String x() { return "" + d; } }.x()` in a method of `P` with a parameter `final int d` reads
the parameter, and `new P() { String x() { return "" + d; } }.x()` reads the parameter instead of the field `d`
that the anonymous class inherits from `P`. As a consequence, Janino rejects such code if the variable is not
effectively final (D-13). The compliance mode follows `javac` (development line), also for a field that a class
inherits from an interface, in a field initializer of the class, and for a local class declared in a method of
another local class; a `private` field of a superclass is not inherited (JLS 8.2), so it does not shadow the
variable, with either compiler.

**S-16, the identity of a non-constant string concatenation**: JLS 15.18.1 requires the result of a string
concatenation that is not a constant expression to be a newly created `String`. Janino compiles a concatenation of up
to three operands (after merging adjacent constant operands) as `a.concat(b).concat(c)`, and `String.concat()`
returns its receiver if the argument is empty. A concatenation with an empty operand can therefore yield an existing
`String`: `("b" + w) == ("b" + w)` with `String w = ""` is `true`, where `javac` creates a new `String` each time. Only
a comparison of references can tell the difference.

**S-17, `final` local variables with a constant initializer** ([#139](https://github.com/janino-lts/janino/issues/139)):
by JLS 4.12.4, a `final` local variable with a constant initializer, e.g. `final int k = 5;`, is a constant variable,
and its simple name is a constant expression (JLS 15.28). Janino treats it like any other local variable, so valid
code behaves differently:

- the type of a conditional expression: `z ? b : k` with a `byte b` has the type `int` with Janino, the type `byte`
  with `javac` (JLS 15.25.2, see S-04), which determines the class of the boxed value, overload resolution and string
  conversion; with `static final int K = 5;` instead of `k`, both compilers give it the type `byte`;
- the identity of string concatenations: with `final String u = "a";`, `(u + "b") == "ab"` is `false` with Janino,
  `true` with `javac` (the concatenation is a constant, interned string).

As a consequence, Janino rejects valid code where a constant expression is required (D-09). The compliance mode
follows `javac` (development line), also in a local or anonymous class that uses the variable; the body of `while (y)`
with `final boolean y = false;` is then unreachable, which Janino accepts like `while (false)` (L-28), and after an
assignment to the variable (L-04) the compliance mode reads the constant value, the compatibility mode the assigned
value.

**S-19, the `null` literal in constant expressions** ([#140](https://github.com/janino-lts/janino/issues/140),
[#152](https://github.com/janino-lts/janino/issues/152) item 13): by JLS 15.28, a constant expression has a primitive
type or the type `String`, so the `null` literal is not a constant expression, and neither is an expression with a
`null` operand. Janino folds `==` and `!=` with `null` operands and string concatenations with a `null` operand:
`("x" + (null == null)) == "xtrue"` and `("a" + null) == "anull"` are `true` with Janino (the concatenations are
interned strings), `false` with `javac`. As a consequence, Janino rejects a statement after `while (null == null);` as
unreachable, where `javac` compiles it (D-11). The compliance mode follows `javac` (development line).

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
expected instead of '9.5'`), because they scan the digits after a leading `0` as an octal integer literal. Since
3.1.19, only a literal with an underscore directly before the first digit `8` or `9` is still rejected, e.g. `0_9.5`
or `01_8.5` (`';' expected instead of '_9'`): there, Janino keeps scanning `0_` as before, so that invalid octal
integer literals are reported with the same messages as before.

**D-05, a member type of a parameterized type** ([#23](https://github.com/janino-lts/janino/issues/23)): `O<String>.I`
is rejected (`IDENTIFIER expected instead of '.'`).

**D-07, a conditional expression with a wrapper operand and an `int` constant** in a context that requires the
unboxed type ([#38](https://github.com/janino-lts/janino/issues/38)): `byte x = z ? B : 5;` and `Byte x = z ? B : 5;`
with a `Byte B`, `Character x = true ? C : 98;` with a `Character C`, and `h(z ? S : 5)` with a `Short S` and a method
`h(short)` are rejected (`Assignment conversion not possible from type "int"`, `No applicable constructor/method
found`), because the type of the expression is `int` (S-04). The compatibility mode cannot accept this code without
giving up the type of S-04 that it keeps for legacy code; the compliance mode accepts it, like `javac`.

**D-08, an array for a variable arity parameter of a type to which only its elements convert**
([#127](https://github.com/janino-lts/janino/issues/127), found with
[#126](https://github.com/janino-lts/janino/issues/126), fixed in 3.1.20): Janino 3.1.19 and earlier reject `f(1, new
int[] { 1, 2 })` with `f(Integer i, Object... a)` (`Assignment conversion not possible from type "int[]" to type
"java.lang.Object[]"`), and likewise with a parameter `Serializable...`, because they pass the `int[]` as the array
itself instead of as its only element when another argument of the invocation needs boxing or unboxing; `javac` passes
`new Object[] { new int[] { 1, 2 } }`. Since 3.1.20, both modes accept it, like `javac`.

**D-09, a `final` local variable with a constant initializer where a constant expression is required**
([#139](https://github.com/janino-lts/janino/issues/139)): with `final int k = 3;` and `final String u = "a";`,
`case k:` (`Value of 'case' label does not pose a constant value`), `case u:` in a `switch` on a `String` (`Value of
'case' label is not a string constant`), `byte b = k;` (`Assignment conversion not possible from type "int" to type
"byte"`), and `int f() { final boolean y = true; while (y) { return 1; } }` (`Method must return a value`) are
rejected, because Janino does not treat the variable as a constant (S-17). The compatibility mode cannot accept this
code without giving up the rule of S-17 that it keeps for legacy code; the compliance mode accepts it, like `javac`
(development line).

**D-10, `P.this` or a `private` member of `P` before the superclass constructor of `Q` is invoked**
([#101](https://github.com/janino-lts/janino/issues/101)): in `class P { class Q extends P { ... } }`, the explicit
constructor invocations in `Q() { this(P.this.s); }`, `Q() { this(P.this.m()); }`, `Q() { super(P.this.s); }`, and
`Q() { this(s); }` with a `private` field `s` of `P`, use the instance of `Q` under construction instead of the
enclosing instance (S-11). The JVM does not allow that before the superclass constructor is invoked, and rejects the
class `Q` (`VerifyError: Bad type on operand stack`). The compatibility mode cannot compile this code correctly without
giving up the rule of S-11 that it keeps for legacy code; the compliance mode compiles it like `javac`.

**D-11, a statement after `while (null == null);`** ([#140](https://github.com/janino-lts/janino/issues/140),
[#152](https://github.com/janino-lts/janino/issues/152) item 13): `int f() { while (null == null); return 1; }` is
rejected (`Statement is unreachable`), because Janino takes `null == null` for a constant expression (S-19), so that
the `while` statement cannot complete normally (JLS 14.22). The compatibility mode cannot accept this code without
giving up the rule of S-19 that it keeps for legacy code; the compliance mode accepts it, like `javac` (development
line).

**D-12, a comparison with an expression of the null type** ([#166](https://github.com/janino-lts/janino/issues/166)):
`(z ? null : null) == (z ? null : null)` with a `boolean z` is rejected (`Cannot compare types "(null type)" and "(null
type)"`), and so are `(z ? null : null) != null` and `null == (z ? null : null)` (`Cannot compare primitive type "(null
type)" with "null"`), where `javac` accepts the comparisons (JLS 15.21.3): Janino treats the null type as a primitive
type here. Only a conditional expression whose operands are both `null` has the null type, so this code is rare. Both
modes, since 3.1.12; in the compliance mode also `(null == null ? null : null) == (null == null ? null : null)`, whose
conditions the compatibility mode folds (S-19).

**D-13, a field of a local or anonymous class with the name of a variable that is not effectively final**
([#137](https://github.com/janino-lts/janino/issues/137)): `void f(int c) { c = 2; class L { int c = 10; int g() {
return c; } } }` is rejected (`Cannot access non-final local variable "c" from inner class`), because Janino takes
`c` for the parameter (S-15); `javac` reads the field. The compatibility mode cannot accept this code without giving
up the rule of S-15 that it keeps for legacy code; the compliance mode accepts it, like `javac` (development line).

**D-14, `this(...)` in an enum constructor** ([#178](https://github.com/janino-lts/janino/issues/178), found with
[#177](https://github.com/janino-lts/janino/issues/177), fixed in 3.1.21): Janino 3.1.20 and earlier fail with an
internal compiler error on `enum E { A, B(2); E() { this(1); } E(int i) { } }` (`AssertionError: uninitializedThis`,
also without `-ea`), because they do not pass the synthetic name and ordinal parameters of the enum constant on to the
constructor that `this(...)` invokes; `javac` passes them on. Since 3.1.21, both modes compile it like `javac`, also a
chain of `this(...)` invocations and an enum constant with a class body.

**Language features that Janino does not implement, class F** (see
[Limitations](https://janino-lts.github.io/janino/#limitations); both modes reject them): F-01 lambda expressions
and method references; F-02 `switch` expressions, arrow labels and multiple labels in a `case`; F-03 pattern
matching (for `instanceof` and `switch`); F-04 records; F-05 sealed types; F-06 local variable type inference
(`var`); F-07 local enums and interfaces; F-08 type annotations; F-09 repeating annotations; F-10 `private` interface
methods; F-11 the diamond operator with an anonymous class.

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
Where `javac` applies a variable arity method by fixed arity, the compatibility mode still expands the arguments
(S-13), so that, e.g., `m(null)` with `m(Integer...)` and `m(Long, byte...)` is still rejected; since 3.1.20, with
the message `Invocation of constructor/method with argument type(s) "(null type)" is ambiguous`. The compliance
mode selects `m(Integer...)`, like `javac`.

**Two maximally specific variable arity methods with different numbers of parameters**
([#147](https://github.com/janino-lts/janino/issues/147), fixed in 3.1.20): Janino 3.1.19 and earlier fail with an
internal compiler error on `m(ia)` (`int[]`) with `m(int...)` and `m(int[], Serializable...)`
(`ArrayIndexOutOfBoundsException`, or `Two non-abstract methods ... have the same parameter types, declaring type
and return type` with the declarations in the opposite order), because they compare the parameter types of the
two methods without comparing their numbers. Since 3.1.20, the compatibility mode still rejects the invocation, with
the message `Invocation of constructor/method with argument type(s) "int[]" is ambiguous`: `javac` applies
`m(int...)` by fixed arity, the compatibility mode only by variable arity (S-13), where neither method is more
specific than the other. The compliance mode selects `m(int...)`, like `javac`.

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
unchanged, because it also selects methods that `javac` reports as ambiguous (L-49); the compliance mode of the
development line applies only the rule of JLS 15.12.2.5 ([#135](https://github.com/janino-lts/janino/issues/135)).

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
method is applicable; so `p.B.s("x")` still invokes `B.s(Object)` when `A` declares `s(String)` (S-18).
Still rejected: a `protected static` member of `A` accessed from an inner class of a subclass of `B` (the
synthetic accessor method references the member through `A`), and a resource of type `B` in a try-with-resources
statement when `close()` is declared in `A`: Janino selects `AutoCloseable.close()`, which throws `Exception`.

**`this(...)` in an enum constructor** (D-14, [#178](https://github.com/janino-lts/janino/issues/178), fixed in
3.1.21): Janino 3.1.20 and earlier fail with an internal compiler error on `enum E { A, B(2); E() { this(1); } E(int
i) { } }` (`AssertionError: uninitializedThis`, also without `-ea`), because they do not pass the synthetic name and
ordinal parameters of the enum constant on to the constructor that `this(...)` invokes. Since 3.1.21, they are passed
on, like with `javac`.

## 3. Invalid code that Janino accepts

Class L: `javac` rejects the following code, Janino compiles it, and the JVM loads the generated classes. Most of it
behaves as the source suggests (e.g. an assignment to a `final` local variable assigns it). See [issue
#33](https://github.com/janino-lts/janino/issues/33) for the compatibility considerations. The compatibility mode keeps
accepting all of it; the compliance mode rejects it once the respective check is implemented (today, it rejects L-20,
L-27 to L-29 and L-35 to L-37 ([#177](https://github.com/janino-lts/janino/issues/177)), L-45 and L-46, see S-11, L-47,
see S-04, L-48, see S-12, L-49, see S-02, and L-50, see S-14; all other entries of this section are open). One
exception, registered as A-05 (section 5): the access to a `protected` member of a class in another package through an
expression whose type is neither the accessing class nor a subclass of it (`((Object)
this).clone()`, `Object o = new P(); o.clone()`) is rejected since 3.1.16, like by `javac`, although the JVM loaded
such classes when the verifier could infer the type `P` from the bytecode
([#54](https://github.com/janino-lts/janino/issues/54)); it rejected
them for a parameter, a field or a method result of type `Object`.

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

- L-27: duplicate label: `L: while (true) { L: while (true) { break L; } }` (the compliance mode rejects it,
  [#177](https://github.com/janino-lts/janino/issues/177), development line; not in a `catch` clause of a `try`
  statement with an empty `try` block, which Janino does not compile, see L-32);
- L-28: unreachable body of `while (false) { ... }` and `for (; false; ) { ... }`, in the compliance mode also with a
  constant variable as the condition (`final boolean y = false; while (y) { ... }`, S-17) (the compliance mode rejects
  it, [#177](https://github.com/janino-lts/janino/issues/177), development line; not with a condition that Janino does
  not fold, S-05, e.g. `while (1 > 2)`);
- L-29: `synchronized (null) {}`, also with another expression of the null type, e.g. `b ? null : null` (the
  compliance mode rejects it, [#177](https://github.com/janino-lts/janino/issues/177), development line);
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
- L-35: case label out of the range of the switch type: `switch (b) { case 1000: }` with `byte b`, also with a
  selector of a wrapper type: `case 128:` with `Byte b`, and `case 'a':` with `Integer i` (`javac`: `char cannot be
  converted to Integer`) (the compliance mode rejects it, [#177](https://github.com/janino-lts/janino/issues/177),
  development line);
- L-36: case label of type `long`, `float` or `double`, whose value is truncated to `int`: `case 1L:`, `case 1.0:`,
  `case 4294967297L:` (which matches the value 1) (the compliance mode rejects it,
  [#177](https://github.com/janino-lts/janino/issues/177), development line);
- L-37: recursive constructor invocation: `P() { this(); }`, also through other constructors (`P() { this(1); }
  P(int i) { this(); }`) and in an enum (`enum E { ; E() { this(); } }`, which 3.1.20 and earlier do not compile,
  D-14) (the compliance mode rejects it, [#177](https://github.com/janino-lts/janino/issues/177), development line).

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
  rejects it, see S-12);
- L-49: an invocation that is ambiguous in phase 3 of JLS 15.12.2, because neither of two variable arity methods is more
  specific: `f(4)` with `f(int...)` and `f(Number...)`; Janino invokes `f(int...)`, because it applies variable arity
  methods before boxing (the compliance mode rejects it, see S-02); likewise `f(1, 2)` with `f(int...)` and
  `f(Integer...)`, and `f(1, 2)` with `f(int...)` and `f(Object...)`, `f(1)` with `f(int...)` and
  `f(Serializable...)`, and `f()` with `f(char...)` and `f(Object...)`, where Janino compares `Object` or
  `Serializable` with the array type `int[]` or `char[]`
  ([#156](https://github.com/janino-lts/janino/issues/156)), and `f(1)` with `f(Object...)` and
  `f(Object, Object...)`; the compliance mode rejects all of them, like `javac`
  ([#135](https://github.com/janino-lts/janino/issues/135), development line): like `javac`, it compares the
  variable arity parameter types of two such methods for as many parameters as the longer parameter list or the
  arguments have;
- L-50: `&&` or `||` with a constant left operand that decides the value and a right operand that is not constant
  (S-14), where a constant expression is required, or as the condition of a `while` statement that must not complete
  normally: `case (false && z) ? 1 : 2:` and `int f() { while (true || z) { return 1; } }` with a `boolean z`
  (`javac`: `constant expression required`, `missing return statement`; the compliance mode rejects it, see S-14;
  [#140](https://github.com/janino-lts/janino/issues/140)).

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
