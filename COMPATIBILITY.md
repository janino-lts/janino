# Compatibility

What stays the same from one release of Janino to the next, and how that is checked. The goal: a project that
depends on Janino 3.1.12, the last release of the original project, can move to any later release without changes
to its own code, to the code that it generates, or to the behavior of the generated code. The projects that compile
generated code with Janino, among others Apache Spark and Apache Calcite, test their code generators against
Janino, not against `javac`; this page is written for them.

## What stays the same

- **Packages and API.** The Java packages (`org.codehaus.janino`, `org.codehaus.commons.compiler`, ...), the
  OSGi bundle symbolic names and the automatic module names are those of 3.1.12. The public classes, methods and
  fields of 3.1.12 remain, with their signatures and their meaning; nothing is removed, also nothing that is
  deprecated (the security-manager-based `Sandbox` is deprecated since 3.1.13, but still there). New API is only
  added. A comparison of the public and protected members of the 3.1.12 JARs with the current ones (`javap`)
  finds one exception, which came in with the last upstream pull request: the two constructors of the AST class
  `Java.Wildcard` gained a parameter for the annotations of the wildcard (3.1.13). The other thing that changed
  is the Maven group ID, `io.github.janino-lts` since 3.1.15 (see the [README](README.md) for the consequences).
- **Java 8.** Janino runs on Java 8 and later (3.1.12: Java 7) and is tested on Java 8, 17, 21 and 25. The class
  files that it generates have the class file version of Java 8 by default (since 3.1.13; before, Java 6); the
  target version can be set, as before.
- **Code that compiles keeps compiling.** Code that Janino 3.1.12 accepts is still accepted, including the invalid
  code that `javac` rejects; see [Differences between Janino and javac](JAVAC_DIFFERENCES.md), section 3, and
  [issue #33](https://github.com/janino-lts/janino/issues/33). Exceptions are made only for code that compiled into
  class files that the JVM rejects, or that failed at run time anyway, and every such case is listed in the
  "Compatibility" entry of the release in the [change log](https://janino-lts.github.io/janino/changelog.html).
- **Generated code behaves the same.** The behavior of code that compiled correctly before changes only where it
  was clearly wrong: a wrong value, an exception, a class file that the JVM rejects. Otherwise the class files
  are unchanged, byte for byte; where a fix changes them, the change log says so, and the release notes report the
  result of the class file comparison of the [benchmarks](janino-benchmarks/README.md) (`CodeSizeReport`). For the
  code that Apache Spark 4.2.0 generates for twelve TPC-DS queries, the class files of 3.1.19 differ from
  those of 3.1.12 only in the class file version, and are identical to those of 3.1.18.
- **Class resolution.** The compiler resolves the classes that the compiled code references through the parent
  class loader that the application sets (`setParentClassLoader()`), by reflection: also classes that exist only
  in that class loader and nowhere as a file, e.g. the classes of a REPL session, and also by their binary names
  (`foo.package$Bar`). Spark relies on both (the REPL and Connect sessions, and classes nested in Scala package
  objects, are the cases that Spark's alternative compiler cannot handle and routes to Janino); this stays.
- **Compile time.** Every release is measured against its predecessor, interleaved in one JVM, on synthetic
  workloads and on the code that Spark generates; the result is part of the release notes.

## Release lines

Janino is maintained in two lines:

- **3.1.x**, the maintenance line, on the branch `3.1.x`. The releases 3.1.18, 3.1.19, ... are made from it. They
  contain only fixes of valid code that is rejected, that crashes the compiler, that compiles into class files
  that the JVM rejects, or that behaves wrongly, and everything that this page promises applies to them: no API
  changes, the code that 3.1.12 accepts stays accepted, Java 8 and the class files of code that compiled
  correctly before stay the same, except where the change log names the reason. The 3.1.x line is maintained as
  long as projects depend on it. For production use, take the latest 3.1.x release.
- **`master`**, the development line. Changes that do not fit the rules of the 3.1.x line are developed there;
  when and as which version they are released is open. Nothing of it reaches a 3.1.x release. On `master`,
  [Development line](DEVELOPMENT_LINE.md) lists these changes.

A fix that both lines need is made on `3.1.x` first and then merged into `master`, so `master` always contains all
fixes of the 3.1.x line. Issues carry the label of the 3.1.x release that is to contain the fix.

## The API that Apache Spark uses

Spark compiles the code that it generates for SQL queries with Janino (`CodeCompiler.scala` in Spark 4.3 and
later, `CodeGenerator.scala` in Spark 4.2 and earlier, `QueryExecutionErrors.scala`; state of 2026-10-07). The
members that it uses are fixed points of the API:

| Member | Used for |
|---|---|
| `ClassBodyEvaluator()` | one evaluator per generated class |
| `ClassBodyEvaluator.setParentClassLoader(ClassLoader)` | the class loader that sees Spark and the user's classes |
| `ClassBodyEvaluator.setClassName(String)` | `org.apache.spark.sql.catalyst.expressions.GeneratedClass` |
| `ClassBodyEvaluator.setDefaultImports(String...)` | twenty classes of Spark |
| `ClassBodyEvaluator.setExtendedClass(Class)` | Spark's `GeneratedClass` as the superclass |
| `ClassBodyEvaluator.setDebuggingInformation(boolean, boolean, boolean)` | when the generated code is logged |
| `ClassBodyEvaluator.cook(String, String)` | `cook("generated.java", body)` |
| `ClassBodyEvaluator.getBytecodes()` | the class files, for the bytecode statistics |
| `ClassBodyEvaluator.getClazz()` | the generated class, instantiated with its no-arg constructor |
| `ClassFile(InputStream)`, `getThisClassName()`, `getConstantPoolSize()` | the bytecode statistics |
| `ClassFile.methodInfos`, `MethodInfo.getName()`, `MethodInfo.getAttributes()` | the methods |
| `CodeAttribute.code` | the code size of every method |
| `CompileException(String, Location)`, `CompileException.getLocation()` | a compile error, rethrown |
| `InternalCompilerException(String, Throwable)` | an internal error, rethrown |

The test `DownstreamApiTest` in the module `janino` uses these members exactly as Spark does, statically typed:
a change of one of them breaks the build. Spark's generated code also relies on four leniencies of the compiler,
which `javac` does not have ([SPARK-58437](https://issues.apache.org/jira/browse/SPARK-58437)): the assignment to a
`final` local variable, the binary name of a nested class (`ArrayBuilder$ofInt`) in source position, generic array
creation (`new Foo<X>[n]`) and the assignment of a parameterized type to a field with a different type argument.
They are recorded in the negative tests (`InvalidCodeTest`) and stay; see
[Differences between Janino and javac](JAVAC_DIFFERENCES.md), section 3.

## The API that Apache Calcite uses

Calcite compiles the code that it generates for queries, metadata handlers and expressions with Janino, mostly
through the `commons-compiler` interfaces (Calcite 1.42.0: `EnumerableInterpretable`, `JaninoRelMetadataProvider`,
`JaninoRexCompiler`, `RexExecutable`, `JaninoCompiler`; state of 2026-10-08). The members that it uses are fixed
points of the API as well:

| Member | Used for |
|---|---|
| `CompilerFactoryFactory.getDefaultCompilerFactory(ClassLoader)` | finding the Janino implementation |
| `ICompilerFactory.newSimpleCompiler()`, `newClassBodyEvaluator()` | one compiler per generated class |
| `ISimpleCompiler.setParentClassLoader(ClassLoader)`, `cook(String)` | compiling a complete class |
| `ISimpleCompiler.getClassLoader()` | loading the class, instantiated with its constructor |
| `IClassBodyEvaluator.setClassName(String)`, `setExtendedClass(Class)` | the name and the superclass |
| `IClassBodyEvaluator.setImplementedInterfaces(Class[])` | the interfaces of the generated class |
| `IClassBodyEvaluator.setParentClassLoader(ClassLoader)` | the class loader that sees Calcite |
| `IClassBodyEvaluator.createInstance(Reader)` | compiling and instantiating in one step |
| `setDebuggingInformation(boolean, boolean, boolean)` | line numbers in debug mode |
| `ClassBodyEvaluator()`, `cook(Scanner)`, `getClazz()`; `Scanner(String, Reader)` | the same, with the Janino classes |
| `JavaSourceClassLoader(ClassLoader, ResourceFinder, String)`, `setDebuggingInfo(...)` | a source class loader |
| `JavaSourceClassLoader.generateBytecodes(String)`, overridden | counting the bytes of the class files |
| `MapResourceFinder(Map)`, `ClassFile.getSourceResourceName(String)` | the source, kept in memory |
| `CompileException` | a compile error, caught |

`DownstreamApiTest` covers these members as well, each of Calcite's uses in one test method.

## How it is checked

- **Downstream test suites.** Two workflows run the complete test suite of
  [Apache Calcite](.github/workflows/calcite.yml) and the 349 test suites of
  [Spark Catalyst](.github/workflows/spark.yml) against the current sources every week, and against every
  release candidate before the release: once with the Janino that the project declares and once with this one,
  and compare the results. No release without both being green.
- **Recorded behavior.** The negative tests (`InvalidCodeTest`, 453 cases of invalid code) and the
  characterization tests (`LanguageSupportTest`, 553 cases of valid code) record Janino's actual behavior, so that
  every change of it, intended or not, fails a test; on the development line, every case runs in both the
  compatibility mode and the compliance mode, and `LegacyDifferentialTest` compares every case with Janino 3.1.12,
  the reference of the compatibility mode. Differential tests compile generated expressions and control flow with
  Janino and with `javac` and compare the results.
- **Class files.** `CodeSizeReport` of the benchmarks compares the class files that two versions generate, byte
  for byte, for all workloads, including the code that Spark generates.
- **Java versions.** The test suite runs on Java 8, 17, 21 and 25 in CI.
