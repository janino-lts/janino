# Differences between Janino and javac

Janino compiles Java source code like `javac`, but not in every respect: it accepts some invalid code, it rejects some
valid code, and some valid code behaves differently. This page lists all deviations that are known as of version
3.1.15, so that you can decide whether they matter for your code.

Language features that Janino does not implement at all (e.g. lambda expressions, `switch` expressions, records) are
listed under [Limitations](https://janino-lts.github.io/janino/#limitations) on the project homepage, and are not
repeated here.

Each deviation was verified against `javac` (JDK 25, with and without `--release 8`) and Janino 3.1.15. Unless stated
otherwise, Janino 3.1.12 behaves the same way (with target version 8 where the code requires it, e.g. for static
interface methods).

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
| `"" + (true ? 1 : 2.0)` | `"1.0"` | `"1"` (the constant value has the type of the selected operand; fixed in the main line) | [#55](https://github.com/janino-lts/janino/issues/55) |
| `enum E { A, B { String n() { return "b"; } }; String n() { return "a"; } }`; `E.B.n()` | `"b"` | `"a"` (class bodies of enum constants are ignored; fixed in the main line) | [#44](https://github.com/janino-lts/janino/issues/44) |
| `class P { @Retention(RUNTIME) @interface A {} @A static class Q {} }`; `Q.class.isAnnotationPresent(A.class)` | `true` | `false` (annotation types declared as member types; top-level annotation types work; fixed in the main line) | [#43](https://github.com/janino-lts/janino/issues/43) |
| `new Object() {}.getClass().getModifiers()` | `0` (JDK 9 and later) | `0x10` (`final`) | |

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

**Parameter annotations** (in the main line, [#61](https://github.com/janino-lts/janino/issues/61); 3.1.15 does not
write them at all): Janino records the annotations of the parameters against the parameters of the method
descriptor, which include the parameters that the compiler prepends to the declared ones: the captured local
variables of a local class constructor, and the instance of a `private` instance method (see above). `javac` records
them against the declared parameters. `Constructor.getParameterAnnotations()` of a local class that captures local
variables, and `Method.getParameterAnnotations()` of a `private` instance method, therefore return arrays that are
longer by the number of prepended parameters, with each annotation at the index of its actual parameter.

## 2. Valid code that Janino rejects

**Constant expressions** ([#47](https://github.com/janino-lts/janino/issues/47)): wherever the language requires a
constant expression, the expressions that Janino does not fold (see section 1) are rejected:

- `case A:` with `static final int A = 1 << 0;`, `case 'a' + 1:`, `case MASK:` with `static final int MASK = ~0xFF;`;
- `byte b = 1 << 2;`, `char c = 'a' + 1;`, `byte b = -'a';`;
- `byte b = MAX > 5 ? 1 : 2;` with `static final int MAX = 10;`;
- `int f() { while (MAX > 0) { } }` ("Method must return a value", because the condition is not constant).

**Conditional expressions with operands of different narrow numeric types** (in 3.1.15; fixed in the main line,
[#51](https://github.com/janino-lts/janino/issues/51) and [#56](https://github.com/janino-lts/janino/issues/56)):

- an `int` constant with an operand of type `byte`, `short` or `char`: `byte b = 1; byte x = z ? b : 5;`,
  `char c = 'a'; Object x = z ? c : 66;`;
- two operands of different types, one of them of type `byte`, `short` or `char`: `z ? s : c`,
  `z ? (short) -1 : c`, `z ? c : (short) 66`, `Long J = 5L; short s = 1; Object x = z ? J : s;`;
- an `int` constant that is not representable in the type of the other operand: `byte b = 1; Object x = z ? b : 500;`;
- `z ? c : (short) -1`, also with a constant condition (3.1.12 compiled `true ? c : (short) -1` with the wrong type
  `char`, and `false ? c : (short) -1` into code that throws an `ArrayIndexOutOfBoundsException`).

In the main line, these expressions have the types of `javac`. The types of such expressions that compile in 3.1.15
are unchanged (section 1, #38).

**Arrays** ([#58](https://github.com/janino-lts/janino/issues/58)): `a.clone()` of an array `a` has the type
`Object` instead of the array type (JLS 10.7): `int[] b = a.clone();` is rejected; with a cast, `(int[]) a.clone()`,
it compiles.

**Inner classes** ([#59](https://github.com/janino-lts/janino/issues/59)): a `protected` member that an enclosing
class inherits from a class in another package, accessed from an inner class (`in` or `P.this.in` in
`class P extends FilterInputStream { class Q { ... } }`, or `P.this.clone()`): the code compiles, but the generated
class throws an `IllegalAccessError` or fails to verify. `javac` generates an accessor method.

**Local classes** ([#60](https://github.com/janino-lts/janino/issues/60)): a local class declaration with a modifier
or an annotation (`final class L {}`, `abstract class L {}`, `@A class L {}`) is rejected (`IDENTIFIER expected
instead of 'class'`).

## 3. Invalid code that Janino accepts

`javac` rejects the following code, Janino compiles it, and the JVM loads the generated classes. Most of it behaves
as the source suggests (e.g. an assignment to a `final` local variable assigns it). See
[issue #33](https://github.com/janino-lts/janino/issues/33) for the compatibility considerations. One exception: the
access to a `protected` member of a class in another package through an expression whose type is neither the
accessing class nor a subclass of it (`((Object) this).clone()`, `Object o = new P(); o.clone()`) is rejected since
3.1.16, like by `javac`, although the JVM loaded such classes when the verifier could infer the type `P` from the
bytecode ([#54](https://github.com/janino-lts/janino/issues/54)); it rejected them for a parameter, a field or a
method result of type `Object`.

**`final` variables and definite assignment:**

- assignment to a `final` field: `final int x = 1; void f() { x = 2; }`;
- assignment to a blank `final` field outside of a constructor: `final int x; P() { x = 1; } void f() { x = 2; }`;
- blank `final` fields that are never assigned: `final int x;`, `static final int X;`;
- assignment to a `final` local variable, also twice to a blank one: `final int x = 1; x = 2;`,
  `final int x; x = 1; x = 2;`;
- assignment to a blank `final` local variable in a loop: `final int x; for (;;) { x = 1; }`;
- assignment to a `final` parameter or a `final` variable of an enhanced `for` statement;
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
- `native strictfp` method (the JVM accepts the combination);
- `static default` interface method; interface field without initializer: `interface I { int X; }`;
- default method that overrides a method of `Object`: `default boolean equals(Object o) { ... }`;
- a class that inherits two default methods with the same signature from two interfaces;
- `public` enum constructor; `final` modifier on an enum declaration (ignored);
- an unqualified invocation of a `private` instance method of the enum from the class body of an enum constant
  (`enum E { A { String n() { return p(); } }; private String p() { ... } abstract String n(); }`, which `javac`
  rejects as a reference from a static context; Janino invokes the method on the constant);
- annotation type element with parameters (`int value(int i);`), or of a type that is not allowed (`Object value();`).

**Annotations:**

- an annotation without a value for an element that has no default: `@interface X { int value(); } @X class P {}`;
- an annotation on a kind of declaration that its `@Target` does not allow, e.g. `@Override` on a class;
- `@FunctionalInterface` on an interface that is not functional;
- `@SafeVarargs` on an instance method that is not `final`: `@SafeVarargs void f(String... s) {}`.

**Statements:**

- duplicate label: `L: while (true) { L: while (true) { break L; } }`;
- unreachable body of `while (false) { ... }` and `for (; false; ) { ... }`;
- `synchronized (null) {}`;
- `catch` of a checked exception that the `try` block cannot throw: `try { } catch (java.io.IOException e) { }`;
- `catch` clause after a `catch` of a superclass: `catch (Exception e) { } catch (RuntimeException e) { }`;
- `catch` of a type that is not a `Throwable`: `catch (String e)`;
- `catch` parameter that redeclares a local variable: `int e = 0; try { } catch (RuntimeException e) { }`;
- case label out of the range of the switch type: `switch (b) { case 1000: }` with `byte b`;
- case label of type `long`, `float` or `double`, whose value is truncated to `int`: `case 1L:`, `case 1.0:`,
  `case 4294967297L:` (which matches the value 1);
- recursive constructor invocation: `P() { this(); }`.

**Expressions, names and imports:**

- illegal forward reference in a field initializer: `int a = b; int b = 1;`;
- a constant conditional expression of type `long`, `float` or `double` assigned to a variable of a narrower type:
  `byte b = true ? 1 : 2L;` (rejected in the main line, [#55](https://github.com/janino-lts/janino/issues/55));
- `instanceof` with a `final` class and an interface that it does not implement: `"x" instanceof Runnable`;
- invocation of a static interface method as if it were inherited (JLS 8.4.8): through an instance
  (`comparator.naturalOrder()`), through an implementing class or a subinterface (`P.s()`, `J.s()`), or unqualified
  from an implementing class (`s()`);
- `private` member type of another top-level class, also of a JDK class: `java.util.ArrayList.Itr x;`;
- on-demand import of a package that does not exist: `import foo.*;`;
- static import of a member that does not exist, or is not static: `import static java.lang.Math.foo;`,
  `import static java.lang.String.length;`.

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
