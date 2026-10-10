#!/usr/bin/env python3
"""Checks which JANINO the tests of Apache Fory or Apache Drill actually ran with.

Used by the workflows ".github/workflows/fory.yml" and "drill.yml" after the tests: surefire records the system
properties of the test JVM in each report ("<module>/target/surefire-reports/TEST-*.xml", where "<module>" is a
subdirectory of the given directory, e.g. "fory-core", or "drill" below ".github"), among them the class path of
the tests ("surefire.test.class.path"). The script fails unless
 * every report that has a JANINO jar ("janino-*.jar" or "commons-compiler-*.jar") on its class path has exactly
   "janino-<version>.jar" and "commons-compiler-<version>.jar" of the given group, from the local Maven repository,
   and nothing else of JANINO, and
 * at least one report has them (Fory's modules without code generation do not need JANINO), and
 * with the mode "true" (the development line), every report has the given system properties with the value "true";
   with the mode "off" (the default), no report has any of them with the value "true". By default, these are the
   property of the compliance mode under its name and under the name that Fory's shading relocates it to; Drill does
   not shade JANINO and passes the name only.
This makes sure that the baseline and the candidate run are what they claim to be, e.g. that the candidate run did
not silently use the JANINO that the project declares.

Usage: check_reports.py <directory> <group-id> <version> [true|off [<property>...]]
"""

import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

JANINO_JAR = re.compile(r"^(janino|commons-compiler)-.*\.jar$")
COMPLIANCE = ("org.codehaus.janino.javacCompliance", "org.apache.fory.shaded.org.codehaus.janino.javacCompliance")


def check(path, group, version, mode, compliance):
    """Returns (problems, whether the report has the expected JANINO) for one report."""
    properties = {p.get("name"): p.get("value") for p in ET.parse(path).getroot().iter("property")}
    problems = [name + " is " + str(properties.get(name)) for name in compliance
                if (properties.get(name) == "true") != (mode == "true")]
    class_path = properties.get("surefire.test.class.path") or properties.get("java.class.path")
    if not class_path:
        return problems + ["no class path recorded"], False
    separator = properties.get("path.separator", os.pathsep)
    jars = [e.replace("\\", "/") for e in class_path.split(separator)
            if JANINO_JAR.match(os.path.basename(e.replace("\\", "/")))]
    if not jars:
        return problems, False
    expected = ["/" + group.replace(".", "/") + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".jar"
                for artifact in ("janino", "commons-compiler")]
    problems += ["unexpected " + jar for jar in jars if not any(jar.endswith(e) for e in expected)]
    problems += ["missing " + e[1:] for e in expected if not any(jar.endswith(e) for jar in jars)]
    return problems, not problems


def main(argv):
    if len(argv) < 4 or len(argv) > 4 and argv[4] not in ("true", "off"):
        print("usage: check_reports.py <directory> <group-id> <version> [true|off [<property>...]]")
        return 2
    directory, group, version = argv[1:4]
    mode = argv[4] if len(argv) > 4 else "off"
    compliance = argv[5:] or COMPLIANCE
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
        problems, has_janino = check(path, group, version, mode, compliance)
        if problems:
            failed = True
            for problem in problems:
                print("ERROR: " + name + ": " + problem)
        elif has_janino:
            ok += 1
            print(name + ": " + group + ":janino:" + version + ", compliance mode " + mode)
        else:
            print(name + ": no JANINO on the class path")
    if not ok:
        print("ERROR: no report has " + group + ":janino:" + version + " on its class path")
        failed = True
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
