This repository is derived from [janino-compiler/janino](https://github.com/janino-compiler/janino).
It is an experimental fork - a proof of concept, not a commitment to maintain JANINO. If you are interested in
continuing the project together, please [create an issue](https://github.com/stefan-zobel/janino/issues).

The experiment: JANINO's sandbox relies on the Java security manager, which is permanently disabled since Java 24.
This fork replaces it with a mechanism that works on all current JREs: a *sandbox policy* (an allowlist of the APIs
that compiled code may use, enforced by a bytecode verifier before the classes are loaded), plus *resource limits*
(CPU time and memory) for executing the compiled code. JANINO requires Java 8 or later and is tested on Java 8, 17,
21 and 25.

JANINO is used by several large Apache projects, among others [Apache Spark](https://spark.apache.org/),
[Apache Calcite](https://calcite.apache.org/), [Apache Flink](https://flink.apache.org/) and
[Apache Hive](https://hive.apache.org/). (They use the original JANINO 3.1.12 from Maven Central, not this fork.)

Please visit the [project homepage](https://stefan-zobel.github.io/janino/).

To restrict what untrusted code that is compiled at runtime may do, see
[Restricting Untrusted Code with a Sandbox Policy](SANDBOX.md).
