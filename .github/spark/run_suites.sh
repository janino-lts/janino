#!/bin/bash
# Runs the published test suites of Spark Catalyst with a selectable JANINO (see "pom.xml" in this directory and
# the workflow ".github/workflows/spark.yml").
#
# Usage: run_suites.sh <label> <janino groupId> <janino version> <package prefix> [regex of excluded suites]
#   e.g. run_suites.sh candidate io.github.janino-lts 3.1.17-SNAPSHOT org.apache.spark 'SparkSubmit|SubExpr'
#
# Needs "mvn", "python3" (or "python") and a JDK 17 or later ("java" on the PATH, or JAVA_HOME). Runs on Linux
# and under Git Bash on Windows. Writes the JUnit XML reports to "reports/<label>/", the log to "logs/<label>.log"
# and the resolved class path to "cp-<label>.txt", all in this directory.
#
# With the environment variable JANINO_COMPLIANCE=true, the suites run with JANINO's compliance mode (the system
# property "org.codehaus.janino.javacCompliance"; the development line only).
#
# Exit status: 0 = the run completed and all tests passed; 3 = the run completed, but tests failed (the comparison
# decides); 2 = the ScalaTest run aborted, e.g. because of a LinkageError in a suite (the aborting suite is printed;
# exclude it); 1 = wrong arguments, or the class path does not contain exactly the expected JANINO.

set -u
cd "$(dirname "$0")" || exit 1
if [ $# -lt 4 ]; then echo "usage: $0 <label> <groupId> <version> <package prefix> [excluded regex]"; exit 1; fi
label=$1; group=$2; version=$3; prefix=$4; excluded=${5:-}

py=$(command -v python3 || command -v python) || { echo "python not found"; exit 1; }
java=${JAVA_HOME:+$JAVA_HOME/bin/java}; java=${java:-java}
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) sep=';' ;; *) sep=':' ;; esac
mkdir -p reports logs home

# 1. The class path with the selected JANINO ("|" as separator, because ":" is part of Windows paths).
mvn -B -q -f pom.xml -Djanino.groupId="$group" -Djanino.version="$version" dependency:build-classpath \
    -Dmdep.outputFile="cp-$label.txt" -Dmdep.pathSeparator='|' > "logs/$label-mvn.log" 2>&1 \
    || { echo "mvn failed, see logs/$label-mvn.log"; exit 1; }
entries=$(tr '|' '\n' < "cp-$label.txt" | tr '\\' '/')
jars=$(echo "$entries" | grep -E '/(janino|commons-compiler)-[0-9][^/]*\.jar$')
echo "$jars" | sed 's/^/JANINO_CLASSPATH /'
[ "$(echo "$jars" | grep -c .)" = 2 ] || { echo "expected exactly two JANINO jars on the class path"; exit 1; }
pattern=$(echo "$group" | sed 's/\./[.\/]/g')
for artifact in janino commons-compiler; do
    echo "$jars" | grep -E -q "$pattern/.*/$artifact-$version\.jar$" \
        || { echo "$artifact-$version.jar of $group is not on the class path"; exit 1; }
done
if [ "${JANINO_COMPLIANCE:-}" = true ]; then compliance=true; mode=true; else compliance=false; mode=off; fi
echo "JANINO_COMPLIANCE $mode"

# 2. The tests jar as the runpath for the suite discovery; copied, because the runpath is split at spaces.
tests_jar=$(echo "$entries" | grep -E '/spark-catalyst_2\.13-[^/]*-tests\.jar$' | head -1)
[ -n "$tests_jar" ] || { echo "spark-catalyst tests jar not found on the class path"; exit 1; }
cp "$tests_jar" catalyst-tests.jar
suites=$("$py" suites.py catalyst-tests.jar "$prefix" "$excluded") || exit 1
[ -n "$suites" ] || { echo "no suites for the package prefix $prefix"; exit 1; }

# 3. The run. The class path goes into an argument file (the command line of Windows is limited to 32 K
# characters); the JVM options are those of Spark's own test runs ("extraJavaTestArgs" in the spark-parent POM),
# plus the mode of JANINO (which the baseline ignores).
echo "-cp \"$(echo "$entries" | paste -s -d "$sep" -)\"" > "cp-$label.args"
rm -rf "reports/$label"; mkdir -p "reports/$label"
start=$(date +%s)
"$java" "@cp-$label.args" -Xmx4g -XX:+IgnoreUnrecognizedVMOptions \
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
    -Dio.netty.tryReflectionSetAccessible=true \
    --sun-misc-unsafe-memory-access=allow \
    --enable-native-access=ALL-UNNAMED \
    -XX:+EnableDynamicAgentLoading \
    -Dspark.test.home="$PWD/home" -Dspark.testing=true -Duser.timezone=UTC -Dfile.encoding=UTF-8 \
    -Dorg.codehaus.janino.javacCompliance=$compliance \
    org.scalatest.tools.Runner -R catalyst-tests.jar -u "reports/$label" -oD $suites > "logs/$label.log" 2>&1
rc=$?
sed 's/\x1b\[[0-9;]*m//g' "logs/$label.log" > "logs/$label-plain.log"
echo "ScalaTest Runner finished with status $rc after $(( ($(date +%s) - start) / 60 )) minutes"
grep -E '^(Total number of tests run|Suites: completed|Tests: succeeded)' "logs/$label-plain.log"

# 4. An aborted run (e.g. a LinkageError in a suite) ends the whole run; report the suite and the cause.
if grep -q 'RUN ABORTED' "logs/$label-plain.log"; then
    suite=$(awk '/^[A-Za-z0-9_.]*Suite:$/ { s = $0 } /RUN ABORTED/ { print s; exit }' "logs/$label-plain.log")
    echo "THE RUN ABORTED in suite ${suite%:}"
    grep -A1 'RUN ABORTED' "logs/$label-plain.log" | tail -1
    exit 2
fi
grep -q -E '^\*\*\* [0-9]+ TESTS? FAILED' "logs/$label-plain.log" && exit 3
[ $rc = 0 ] || { echo "the ScalaTest Runner failed, see logs/$label.log"; exit 2; }
exit 0
