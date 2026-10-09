[![Maven Central](https://img.shields.io/maven-central/v/io.github.janino-lts/janino.svg)](https://central.sonatype.com/artifact/io.github.janino-lts/janino)
[![javadoc.io](https://javadoc.io/badge2/io.github.janino-lts/janino/javadoc.svg)](https://javadoc.io/doc/io.github.janino-lts/janino)
[![license](https://img.shields.io/badge/license-BSD--3--Clause-blue.svg)](LICENSE)

This repository continues Janino, derived from [janino-compiler/janino](https://github.com/janino-compiler/janino)
after its author, Arno Unkrig, discontinued the development and archived the original repository. It is an attempt
to keep Janino alive: to fix bugs, to keep it working on current Java versions, and to stay compatible with
Janino 3.1.12, on which many projects depend. Janino requires Java 8 or later and is tested on Java 8, 17, 21 and 25.
[Changes since 3.1.12](CHANGES_SINCE_3.1.12.md) summarizes what an upgrade from 3.1.12 brings, and
[Compatibility](COMPATIBILITY.md) what stays the same. The 3.1.x line, the one for production use, is maintained as
long as projects depend on it (see [Release lines](COMPATIBILITY.md#release-lines)).

Whether this succeeds depends on its users: if Janino matters to you, please report problems, try the releases, or
help with the work by creating an [issue](https://github.com/janino-lts/janino/issues) or a
[pull request](https://github.com/janino-lts/janino/pulls).

Janino is used by several large Apache projects, among others [Apache Spark](https://spark.apache.org/),
[Apache Calcite](https://calcite.apache.org/), [Apache Flink](https://flink.apache.org/) and
[Apache Hive](https://hive.apache.org/). (They use the original Janino 3.1.12, not this continuation.)
Two workflows run the test suites of [Apache Calcite](.github/workflows/calcite.yml) (all modules) and of
[Spark Catalyst](.github/workflows/spark.yml) against this Janino, each twice: with the Janino that the project
declares and with this one, and compare the results. The [compile-time benchmarks](janino-benchmarks/README.md)
compare the compile time of two Janino versions, among others on the code that Apache Spark generates for the
TPC-DS queries; each release is measured against its predecessor.

Janino compiles Java source code like `javac`, but not in every respect: it accepts some invalid code, rejects some
valid code, and some valid code behaves differently. All known deviations are listed in
[Differences between Janino and javac](JAVAC_DIFFERENCES.md).

[Janino and javac: the compatibility mode and the compliance mode](JAVAC_COMPLIANCE.md) defines two modes: the
compatibility mode (the default), in which code that Janino has always accepted keeps compiling with its
established behavior, and the compliance mode, in which Janino behaves like `javac`. The development line has the
foundation of the compliance mode (an option, incomplete by design; see
[Development line](https://github.com/janino-lts/janino/blob/master/DEVELOPMENT_LINE.md)). When and as which version
it is released is open; the document is a roadmap, not a commitment.

To restrict what untrusted code that is compiled at runtime may do, see
[Restricting Untrusted Code with a Sandbox Policy](SANDBOX.md).

Please visit the [project homepage](https://janino-lts.github.io/janino/) for more documentation.

## Maven coordinates

From version 3.1.15 on, Janino is available on Maven Central under the group ID `io.github.janino-lts` (the original
versions up to 3.1.12 have the group ID `org.codehaus.janino`):

```xml
<dependency>
    <groupId>io.github.janino-lts</groupId>
    <artifactId>janino</artifactId>
    <version>3.1.18</version>
</dependency>
```

(`commons-compiler` comes in as a transitive dependency.) The Java packages are unchanged (`org.codehaus.janino`,
`org.codehaus.commons.compiler`), so existing code works without changes. Because the group ID is different, however,
Maven does not recognize the two as versions of the same artifact: if another dependency brings in
`org.codehaus.janino` artifacts, exclude them, otherwise the same classes are on the class path twice:

```xml
<dependency>
    <groupId>...</groupId>
    <artifactId>library-that-uses-janino</artifactId>
    <version>...</version>
    <exclusions>
        <exclusion>
            <groupId>org.codehaus.janino</groupId>
            <artifactId>*</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

(If the library uses `commons-compiler-jdk`, add `io.github.janino-lts:commons-compiler-jdk` as well.) The releases
up to 3.1.14 of this continuation are not on Maven Central; they are available as
[GitHub releases](https://github.com/janino-lts/janino/releases) with the group ID `org.codehaus.janino`.
