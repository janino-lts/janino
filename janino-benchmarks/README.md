# Compile-time benchmarks

The module `janino-benchmarks` compares the compile time of two Janino versions on the same machine: a baseline
(3.1.12 from Maven Central by default, configurable) and the current build. It is built only with the Maven
profile `benchmarks` and never released. This page describes what is measured, how, and the results for the code
that Apache Spark generates; the details of the tool are in the
[package documentation](src/main/java/org/codehaus/janino/benchmarks/package-info.java).

## What is measured

Every operation of a workload compiles a fixed piece of code with a new compiler instance and loads the result,
so the verification of the class files by the JVM is included. The workloads (what one operation compiles):

| Workload | One operation |
|---|---|
| `TINY` | the expression `a * 3 + b` with an `ExpressionEvaluator`: the fixed costs of a compiler instance |
| `GENERATED` | a class with 15 larger methods (loops, `if` cascades, many JDK invocations), like SQL engines generate |
| `CONTROL_FLOW` | a class of `try`/`catch`/`finally`, try-with-resources, `synchronized` and labeled jumps |
| `SELF_COMPILE` | a large part of Janino's own sources, with the `Compiler` |
| `SPARK_TPCDS` | the code that Spark 4.2.0 generated for twelve TPC-DS queries, the way Spark compiles it (below) |

**`SPARK_TPCDS`** is the workload for Spark. Its corpus consists of 342 class bodies (3.6 MB of source): the
complete code that Spark 4.2.0 generated for the TPC-DS queries q1, q4, q9, q14a, q23a, q47, q64, q66, q72, q88,
q95 and q96, with broadcast hash joins for half of them and sort merge joins for the other half; 266 whole-stage
code generation classes (`GeneratedIteratorForCodegenStage…`), 53 unsafe projections, 15 orderings, 5 mutable and
3 safe projections. One operation compiles every class body exactly as Spark's `CodeGenerator` does: with a new
`ClassBodyEvaluator`, a parent class loader that sees the Spark runtime, Spark's twenty default imports, Spark's
`GeneratedClass` as the superclass and `cook("generated.java", body)`; then it loads the generated class and
instantiates it. Not measured is the execution of the generated code: that measures the JVM, not Janino.

## How it is measured

- **Interleaved.** Both versions are loaded into one JVM, each through its own class loader, and are measured in
  rounds of a fixed duration in alternating order: A then B, then B then A, and so on. The reported ratio B/A is the
  median of the ratios of the rounds, with the 10th and 90th percentiles; A is the baseline, B the current build,
  so B/A below 1 means that the current build is faster. Measuring one version after the other is not reliable:
  the speed of a machine drifts, e.g. while a laptop heats up, and the drift easily exceeds the differences to be
  measured. The absolute times per operation are reported too, but they are specific to the machine.
- **A/A control.** The current build is measured against itself (loaded through a second class loader). The spread
  of that comparison is the precision of the machine; differences within it are not measurable.
- **Allocation.** The bytes that one operation allocates (`ThreadMXBean`). With `-Xint`, the allocation is almost
  deterministic, so it shows small differences in the work reliably; the time with `-Xint` is the work without
  JIT effects.
- **Class files.** `CodeSizeReport` tells whether both versions generate the same class files, byte for byte, and
  how large they are. A difference in the class files explains a difference in the compile time; identical class
  files mean that the generated code, and therefore its runtime behavior, is unchanged.
- **Conditions.** No other work on the machine; the JDK is stated with the results.

## Running

```
mvn -f janino-parent/pom.xml -P benchmarks -DskipTests clean package   (online the first time: the Spark runtime)
java -jar janino-benchmarks/target/benchmarks.jar -aa -millis 5000 SPARK_TPCDS           (A/A: the precision)
java -jar janino-benchmarks/target/benchmarks.jar -millis 5000 SPARK_TPCDS               (baseline vs. current)
java -Xint -jar janino-benchmarks/target/benchmarks.jar -rounds 10 -warmup 2 -millis 25000 SPARK_TPCDS
java -cp janino-benchmarks/target/benchmarks.jar org.codehaus.janino.benchmarks.CodeSizeReport
```

Without a workload name, all workloads run. One operation of `SPARK_TPCDS` takes about one second with the JIT
compiler and 25 seconds with `-Xint`, hence the longer rounds. To compare with another baseline, build with
`-Djanino.baseline.version=3.1.20 -Djanino.baseline.groupId=io.github.janino-lts` (versions up to 3.1.14 have the
group ID `org.codehaus.janino`). JDK 24 and later warn about `sun.misc.Unsafe` when a class of Spark is loaded;
`--sun-misc-unsafe-memory-access=allow` silences that.

The module has one test, `SparkCorpusTest`: it compiles the corpus of `SPARK_TPCDS` with the current Janino in the
compatibility mode and in the compliance mode (`JaninoOption.JAVAC_COMPLIANCE`), loads and instantiates every
generated class, and fails with the list of the class bodies that do not compile or load in a mode. It also writes
the names of the class bodies whose class files differ between the two modes to
`target/spark-corpus-mode-differences.txt`. The job "Spark corpus in both modes" of the workflow "CI" runs it; locally:

```
mvn -f janino-parent/pom.xml install -DskipTests
mvn -f janino-benchmarks/pom.xml test
```

## Results: Janino 3.1.12 and 3.1.17 on the Spark corpus

Measured on a laptop with JDK 25.0.2; 3.1.17 contains all changes from 3.1.13 on. A is Janino
3.1.12, B is 3.1.17 (in the A/A control, A is 3.1.17, too); the times and the allocation are per operation, i.e.
per compilation of the whole corpus.

| Run | A [s] | B [s] | B/A | p10 | p90 | A [MB] | B [MB] | alloc B/A |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| A/A control, JIT, 20 rounds of 2 s | 0.933 | 0.965 | 1.023 | 0.997 | 1.067 | 718 | 743 | 1.036 |
| 3.1.12 vs. 3.1.17, JIT, 20 rounds of 2 s | 0.962 | 0.966 | 0.995 | 0.927 | 1.079 | 749 | 737 | 0.983 |
| 3.1.12 vs. 3.1.17, `-Xint`, 10 rounds of 4 s | 25.37 | 25.11 | 0.987 | 0.955 | 1.018 | 879 | 839 | 0.954 |

- With the JIT compiler, no difference is measurable: the A/A control spreads by about 7 %, and the comparison lies
  within that spread.
- Without the JIT compiler, 3.1.17 needs 1 % less time and allocates 4.6 % less memory per operation than
  3.1.12; the allocation is the most reliable of these numbers.
- The class files are the same size (2,125,722 bytes for the corpus) and differ only in the class file version
  (Java 6 with 3.1.12, Java 8 since 3.1.13, which raised the default target version); every instruction and
  attribute is identical.

## Results: Janino 3.1.16 and 3.1.17 on all workloads

Measured on the same laptop with JDK 25.0.2; A is Janino 3.1.16, B is 3.1.17. B/A is the median ratio of the
compile times (p10 and p90 in brackets, rounded), "A/A" the same for the control run of B against itself, "alloc"
the ratio of the memory that one operation allocates.

| Workload | JIT B/A | JIT A/A | JIT alloc | `-Xint` B/A | `-Xint` A/A | `-Xint` alloc |
|---|---|---|---:|---|---|---:|
| TINY | 0.976 (0.92..1.07) | 1.031 (0.90..1.09) | 1.001 | 1.008 (0.99..1.01) | 1.003 (1.00..1.02) | 1.002 |
| GENERATED | 1.092 (1.04..1.12) | 0.969 (0.93..0.99) | 1.014 | 1.008 (0.99..1.03) | 1.018 (0.70..1.35) | 1.001 |
| CONTROL_FLOW | 1.036 (0.97..1.06) | 1.052 (1.04..1.08) | 1.007 | 1.010 (0.90..1.04) | 0.998 (0.96..1.03) | 1.009 |
| SELF_COMPILE | 1.001 (0.96..1.03) | 0.966 (0.91..1.02) | 1.006 | 0.996 (0.94..1.01) | 0.999 (0.99..1.01) | 1.003 |
| SPARK_TPCDS | 1.014 (0.96..1.07) | 1.013 (0.95..1.04) | 0.995 | 0.992 (0.97..1.01) | 1.003 (0.95..1.02) | 0.992 |

- The compile time is unchanged within the measurement error on all workloads: the A/A control deviates by up to
  5 % in the median with the JIT compiler, and every B/A lies within that band, except GENERATED with the JIT,
  whose `-Xint` run shows the same work (1.008, allocation 1.001).
- The allocation, which is deterministic with `-Xint`, changes by less than 1 %: 0.1 to 0.9 % more on the
  synthetic workloads, 0.3 % more on Janino's own sources, 0.8 % less on the code that Spark generates.
- The class files are byte-identical for all workloads (`CodeSizeReport`).

## Results: Janino 3.1.20 and 3.1.21 on all workloads

3.1.21 shares the class model of the classes that it loads through reflection between the compilations, caches
the method lookup ([#193](https://github.com/janino-lts/janino/issues/193)), and has a faster scanner, caches the
name resolution and skips the trial compilation of literals ([#195](https://github.com/janino-lts/janino/issues/195)).
Measured on the same laptop; A is Janino 3.1.20, B is 3.1.21, the columns as above. JDK 25.0.2 with the JIT
compiler: 10 warm-up and 20 measured rounds of 3 s; with `-Xint`: 2 warm-up and 8 measured rounds of 5 s (25 s for
`SPARK_TPCDS`); JDK 17.0.8 with the JIT compiler, for two workloads: 6 warm-up and 12 measured rounds of 3 s.

| Workload | JIT B/A | JIT alloc | `-Xint` B/A | `-Xint` alloc | JDK 17 B/A | JDK 17 alloc |
|---|---|---:|---|---:|---|---:|
| TINY | 0.671 (0.64..0.70) | 0.238 | 0.574 (0.56..0.58) | 0.272 | | |
| GENERATED | 0.722 (0.65..0.98) | 0.729 | 0.735 (0.66..0.77) | 0.723 | 0.754 (0.71..0.78) | 0.720 |
| CONTROL_FLOW | 0.840 (0.77..1.02) | 0.830 | 0.828 (0.62..0.86) | 0.825 | | |
| SELF_COMPILE | 0.910 (0.76..1.13) | 0.868 | 0.866 (0.82..0.87) | 0.868 | | |
| SPARK_TPCDS | 0.708 (0.66..0.77) | 0.579 | 0.671 (0.63..0.67) | 0.591 | 0.676 (0.64..0.74) | 0.586 |

- On the code that Spark generates, 3.1.21 needs about 30 % less time and allocates about 41 % less memory per
  operation: with the JIT compiler 0.83 s instead of 1.16 s and 433 MB instead of 748 MB, with `-Xint` 19.9 s
  instead of 30.2 s and 508 MB instead of 860 MB. JDK 17 shows the same.
- The A/A control with the JIT compiler gives 0.937 (0.89..1.07) for `SPARK_TPCDS` and 0.927 (0.86..0.95) for
  `GENERATED`, with an allocation of 1.006 and 1.007; so the time ratios with the JIT compiler may overstate the
  gain by up to about 6 percentage points. The `-Xint` ratios and the allocation do not have this bias and show
  the same gains.
- `TINY`, the fixed costs of a compiler instance, gains the most: one operation allocates 24 KB instead of 100 KB,
  because the class model of the JDK classes that the expression needs is no longer built anew for each compiler
  instance.
- The class files are byte-identical for all workloads (`CodeSizeReport`).

The release notes of each version report its comparison with the previous version, on all workloads.

## The corpus

The class bodies were captured with [`tools/spark-corpus`](tools/spark-corpus): Spark 4.2.0 in local mode runs
the 103 TPC-DS query texts of its own test resources against the 24 TPC-DS tables, each a temporary view with 20
generated rows, adaptive query execution disabled, in two passes (with and without broadcast joins),
and logs every class body that it compiles; the line number prefixes of Spark's `CodeFormatter` are removed, the
indentation is the formatter's, duplicates are dropped. Of the 2,607 distinct class bodies (29.8 MB), the complete
code of the twelve queries above is kept.
See [the README of the corpus](src/main/resources/org/codehaus/janino/benchmarks/tpcds/README.txt) for the
details and the index of the files.

Two things to know about the setup: Janino's `ClassBodyEvaluator` loads the classes that the generated code
references through the parent class loader and introspects them, and their signatures pull in Hadoop, JSON and
logging classes; therefore the build copies the complete Spark runtime (spark-sql and its dependencies, about
250 MB, including the Janino 3.1.9 that Spark declares, which only serves this introspection) to
`target/spark-classpath`. And the generated code depends on the query plans, not on the data, so the tiny tables
do not make it less representative.
