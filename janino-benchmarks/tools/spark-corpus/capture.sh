#!/bin/bash
# Captures the Java code that Apache Spark generates for the TPC-DS queries: the class bodies that Spark's
# CodeGenerator compiles with JANINO. They are the corpus of the workload "SPARK_TPCDS" of "janino-benchmarks"
# (see "src/main/resources/org/codehaus/janino/benchmarks/tpcds/README.txt" for how the corpus is selected from
# the captured code).
#
# Usage: capture.sh [output directory]      (default: "out", in this directory)
#
# Needs "mvn" and a JDK 17 or 21 ("java" and "javac" on the PATH, or JAVA_HOME). Runs on Linux and under Git Bash
# on Windows. Resolves the class path of Spark (see "pom.xml"; online the first time), compiles and runs
# "CaptureTpcds" (local mode, two passes: with and without broadcast joins), which writes the log of the generated
# code to "work/codegen.log" and then the class bodies and an index to the output directory.

set -u
cd "$(dirname "$0")" || exit 1
out=${1:-out}
java=${JAVA_HOME:+$JAVA_HOME/bin/java}; java=${java:-java}
javac=${JAVA_HOME:+$JAVA_HOME/bin/javac}; javac=${javac:-javac}
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) sep=';' ;; *) sep=':' ;; esac
mkdir -p work

# 1. The class path of Spark ("|" as separator, because ":" is part of Windows paths); it goes into an argument
# file, because the command line of Windows is limited to 32 K characters.
mvn -B -q -f pom.xml dependency:build-classpath -Dmdep.outputFile=work/cp.txt -Dmdep.pathSeparator='|' \
    > work/mvn.log 2>&1 || { echo "mvn failed, see work/mvn.log"; exit 1; }
entries=$(tr '|' '\n' < work/cp.txt | tr '\\' '/')
echo "-cp \"work/classes$sep$(echo "$entries" | paste -s -d "$sep" -)\"" > work/cp.args
echo "$entries" | grep -E '/(spark-sql_2\.13-[^/]*|janino-[0-9][^/]*)\.jar$' | sed 's/^/CLASSPATH /'

# 2. Compile the capture program against that class path.
rm -rf work/classes; mkdir -p work/classes
"$javac" "@work/cp.args" -proc:none -Xlint:-options -d work/classes CaptureTpcds.java || exit 1

# 3. Run it. The JVM options are those of Spark's own test runs ("extraJavaTestArgs" in the spark-parent POM).
rm -f work/codegen.log
start=$(date +%s)
"$java" "@work/cp.args" -Xmx4g -XX:+IgnoreUnrecognizedVMOptions \
    --add-modules=jdk.incubator.vector \
    --add-opens=java.base/java.lang=ALL-UNNAMED \
    --add-opens=java.base/java.lang.invoke=ALL-UNNAMED \
    --add-opens=java.base/java.lang.reflect=ALL-UNNAMED \
    --add-opens=java.base/java.io=ALL-UNNAMED \
    --add-opens=java.base/java.net=ALL-UNNAMED \
    --add-opens=java.base/java.nio=ALL-UNNAMED \
    --add-opens=java.base/java.util=ALL-UNNAMED \
    --add-opens=java.base/java.util.concurrent=ALL-UNNAMED \
    --add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED \
    --add-opens=java.base/jdk.internal.ref=ALL-UNNAMED \
    --add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
    --add-opens=java.base/sun.nio.cs=ALL-UNNAMED \
    --add-opens=java.base/sun.security.action=ALL-UNNAMED \
    --add-opens=java.base/sun.util.calendar=ALL-UNNAMED \
    -Djdk.reflect.useDirectMethodHandle=false \
    -Dio.netty.tryReflectionSetAccessible=true \
    --sun-misc-unsafe-memory-access=allow \
    --enable-native-access=ALL-UNNAMED \
    -Dlog4j2.configurationFile=log4j2.properties -Dspark.corpus.log=work/codegen.log \
    -Duser.timezone=UTC -Dfile.encoding=UTF-8 \
    org.codehaus.janino.benchmarks.tools.CaptureTpcds "$out" 2> work/capture.err
rc=$?
minutes=$(( ($(date +%s) - start) / 60 ))
echo "CaptureTpcds finished with status $rc after $minutes minutes; messages in work/capture.err"
exit $rc
