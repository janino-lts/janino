The corpus of the workload SPARK_TPCDS (see "SparkCodegen" and "Workload"): the Java class bodies that Apache
Spark 4.2.0 generated for twelve queries of the TPC-DS benchmark, exactly the texts that Spark passed to JANINO's
ClassBodyEvaluator (whole-stage code generation, unsafe projections, orderings, mutable and safe projections). One
operation of the workload compiles all of them, like Spark does: each with a new ClassBodyEvaluator, Spark's
default imports and Spark's GeneratedClass as the superclass, then loads the generated class and instantiates it.

Origin: captured on 2026-10-07 with "tools/spark-corpus/capture.sh" (see there): Spark 4.2.0 in local mode runs
the 103 TPC-DS query texts of its own test resources ("tpcds/q*.sql" in the "tests" JAR of spark-sql) against the
24 TPC-DS tables, each a temporary view with 20 generated rows (CHAR and VARCHAR columns as STRING), adaptive query
execution disabled, in two passes: with the default broadcast threshold (broadcast hash joins) and with broadcast
joins disabled (sort merge joins). Spark logs every class body that it compiles (logger CodeGenerator, formatted by
its CodeFormatter: the line number prefixes are removed here, the indentation is the formatter's); duplicates are
dropped. From the 2,607 distinct class bodies (29.8 MB), "tools/spark-corpus/select.py" takes the complete code of
these queries and passes (the set covers aggregation, both join kinds, window functions, rollups, set operations
and subqueries, and includes the queries with the largest generated classes):

  q1 broadcast, q4 sortmerge, q9 broadcast, q14a broadcast, q23a broadcast, q47 broadcast, q64 sortmerge,
  q66 broadcast, q72 sortmerge, q88 broadcast, q95 sortmerge, q96 broadcast

"index.txt" lists the files (file|query|pass|class|lines|bytes); the file name is
"<query>-<pass>-<number>-<class>.java.txt". The generated code is the output of Apache Spark (Apache License 2.0)
for the publicly specified TPC-DS queries; it is reproduced here only to measure the compiler.

To capture the corpus again, e.g. for another version of Spark: run "capture.sh" (needs the Spark runtime from
Maven Central, online the first time) and then "select.py" in "tools/spark-corpus", and update this file.
