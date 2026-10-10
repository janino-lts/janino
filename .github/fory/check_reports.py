#!/usr/bin/env python3
"""Checks which JANINO the tests of Apache Fory actually ran with.

Used by the workflow ".github/workflows/fory.yml" after the tests: surefire records the system properties of the
test JVM in each report ("<module>/target/surefire-reports/TEST-*.xml"), among them the class path of the tests
("surefire.test.class.path"). The script fails unless
 * every report that has a JANINO jar ("janino-*.jar" or "commons-compiler-*.jar") on its class path has exactly
   "janino-<version>.jar" and "commons-compiler-<version>.jar" of the given group, from the local Maven repository,
   and nothing else of JANINO, and
 * at least one report has them (Fory's modules without code generation do not need JANINO).
This makes sure that the baseline and the candidate run are what they claim to be, e.g. that the candidate run did
not silently use the JANINO that Fory declares.

Usage: check_reports.py <fory-java-dir> <group-id> <version>
"""

import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

JANINO_JAR = re.compile(r"^(janino|commons-compiler)-.*\.jar$")


def check(path, group, version):
    """Returns (problems, whether the report has the expected JANINO) for one report."""
    properties = {p.get("name"): p.get("value") for p in ET.parse(path).getroot().iter("property")}
    class_path = properties.get("surefire.test.class.path") or properties.get("java.class.path")
    if not class_path:
        return ["no class path recorded"], False
    separator = properties.get("path.separator", os.pathsep)
    jars = [e.replace("\\", "/") for e in class_path.split(separator)
            if JANINO_JAR.match(os.path.basename(e.replace("\\", "/")))]
    if not jars:
        return [], False
    expected = ["/" + group.replace(".", "/") + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".jar"
                for artifact in ("janino", "commons-compiler")]
    problems = ["unexpected " + jar for jar in jars if not any(jar.endswith(e) for e in expected)]
    problems += ["missing " + e[1:] for e in expected if not any(jar.endswith(e) for jar in jars)]
    return problems, not problems


def main(argv):
    if len(argv) != 4:
        print("usage: check_reports.py <fory-java-dir> <group-id> <version>")
        return 2
    directory, group, version = argv[1:]
    paths = sorted(glob.glob(os.path.join(directory, "*", "target", "surefire-reports", "TEST-*.xml")))
    if not paths:
        print("ERROR: no surefire reports below " + directory)
        return 1
    ok = 0
    failed = False
    for path in paths:
        name = os.path.relpath(path, directory)
        if os.name == "nt":
            path = "\\\\?\\" + os.path.abspath(path)  # paths longer than 260 characters on Windows
        problems, has_janino = check(path, group, version)
        if problems:
            failed = True
            for problem in problems:
                print("ERROR: " + name + ": " + problem)
        elif has_janino:
            ok += 1
            print(name + ": " + group + ":janino:" + version)
        else:
            print(name + ": no JANINO on the class path")
    if not ok:
        print("ERROR: no report has " + group + ":janino:" + version + " on its class path")
        failed = True
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
