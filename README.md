This repository continues Janino, derived from [janino-compiler/janino](https://github.com/janino-compiler/janino)
after its author, Arno Unkrig, discontinued the development and archived the original repository. It is an attempt
to keep Janino alive: to fix bugs, to keep it working on current Java versions, and to stay compatible with
Janino 3.1.12, on which many projects depend. Janino requires Java 8 or later and is tested on Java 8, 17, 21 and 25.

Whether this succeeds depends on its users: if Janino matters to you, please report problems, try the releases, or
help with the work by creating an [issue](https://github.com/janino-lts/janino/issues) or a
[pull request](https://github.com/janino-lts/janino/pulls).

Janino is used by several large Apache projects, among others [Apache Spark](https://spark.apache.org/),
[Apache Calcite](https://calcite.apache.org/), [Apache Flink](https://flink.apache.org/) and
[Apache Hive](https://hive.apache.org/). (They use the original Janino 3.1.12 from Maven Central, not this fork.)

Please visit the [project homepage](https://janino-lts.github.io/janino/).

To restrict what untrusted code that is compiled at runtime may do, see
[Restricting Untrusted Code with a Sandbox Policy](SANDBOX.md).
