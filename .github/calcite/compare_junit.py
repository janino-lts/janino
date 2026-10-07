#!/usr/bin/env python3
"""Compares the JUnit XML reports of two test runs, a baseline run and a candidate run.

Used by the workflow ".github/workflows/calcite.yml": the tests of Apache Calcite are executed with the JANINO that
Calcite declares (the baseline) and with the JANINO of this repository (the candidate). A test that passes with the
baseline and fails with the candidate is a regression; a test that fails with both is not.

Usage: compare_junit.py <baseline-dir> <candidate-dir>

Both directories are searched recursively for "*.xml" files in the JUnit XML format (as Gradle writes them to
"build/test-results"). A test is identified by its module (the path of the XML file relative to the directory, up
to "build", e.g. "core" or "example/csv"), its class, its name without identity hash codes, and the number of the
execution among equally named tests. The script prints a summary and the differences (also to the GitHub job
summary, if the
environment variable GITHUB_STEP_SUMMARY is set) and exits with status 1 if
 * a test passed with the baseline, but failed with the candidate,
 * a test that ran with the baseline did not run with the candidate (e.g. because the test JVM crashed), or
 * one of the runs contains no test at all.
"""

import os
import re
import sys
import xml.etree.ElementTree as ET

PASSED, FAILED, SKIPPED = "passed", "failed", "skipped"
MAX_LISTED = 100

# The display names of some parameterized tests contain the identity hash code of a parameter object, e.g.
# "[1] CAST, org.apache.calcite.test.SqlOperatorFixtureImpl@784bc074", which differs between the runs.
IDENTITY_HASH = re.compile(r"@[0-9a-f]{1,8}\b")


def module_name(directory, xml_dir):
    """Returns the path of "xml_dir" relative to "directory" up to (excluding) a component "build", with "/"."""
    parts = []
    for part in os.path.relpath(xml_dir, directory).split(os.sep):
        if part == "build" or part == os.curdir:
            break
        parts.append(part)
    return "/".join(parts)


def read_results(directory):
    """Returns {(module, class name, test name, n): status} for all JUnit XML files below the directory.

    "n" counts the executions of the same test name (0, 1, ...; e.g. parameterized tests whose display names are
    equal after the removal of identity hash codes), so that a missing execution is noticed.
    """
    results = {}
    executions = {}
    for root, _, files in os.walk(directory):
        module = module_name(directory, root)
        for name in sorted(files):
            if not name.endswith(".xml"):
                continue
            path = os.path.join(root, name)
            if os.name == "nt":
                path = "\\\\?\\" + os.path.abspath(path)  # paths longer than 260 characters on Windows
            try:
                tree = ET.parse(path)
            except ET.ParseError as e:
                print("WARNING: cannot parse " + path + ": " + str(e))
                continue
            for case in tree.getroot().iter("testcase"):
                name = (module, case.get("classname", ""), IDENTITY_HASH.sub("", case.get("name", "")))
                n = executions.get(name, 0)
                executions[name] = n + 1
                if case.find("failure") is not None or case.find("error") is not None:
                    status = FAILED
                elif case.find("skipped") is not None:
                    status = SKIPPED
                else:
                    status = PASSED
                results[name + (n,)] = status
    return results


def count(results, status):
    return sum(1 for s in results.values() if s == status)


def test_name(key):
    module, class_name, name, n = key
    return (module + ": " if module else "") + class_name + " > " + name + (" #" + str(n + 1) if n else "")


def listing(title, keys, lines):
    if not keys:
        return
    lines.append("")
    lines.append("### " + title + " (" + str(len(keys)) + ")")
    lines.append("")
    for key in sorted(keys)[:MAX_LISTED]:
        lines.append("- `" + test_name(key) + "`")
    if len(keys) > MAX_LISTED:
        lines.append("- ... and " + str(len(keys) - MAX_LISTED) + " more")


def main(argv):
    if len(argv) != 3:
        print("usage: compare_junit.py <baseline-dir> <candidate-dir>")
        return 2
    baseline = read_results(argv[1])
    candidate = read_results(argv[2])

    regressions = [k for k, s in candidate.items() if s == FAILED and baseline.get(k) == PASSED]
    missing = [k for k in baseline if k not in candidate]
    fixed = [k for k, s in candidate.items() if s == PASSED and baseline.get(k) == FAILED]
    failing_in_both = [k for k, s in candidate.items() if s == FAILED and baseline.get(k) == FAILED]
    extra = [k for k in candidate if k not in baseline]
    newly_skipped = [k for k, s in candidate.items() if s == SKIPPED and baseline.get(k) == PASSED]

    lines = ["## Calcite tests: candidate against baseline", ""]
    lines.append("| Run | Tests | Passed | Failed | Skipped |")
    lines.append("|---|---:|---:|---:|---:|")
    for name, results in (("baseline", baseline), ("candidate", candidate)):
        lines.append("| " + name + " | " + str(len(results)) + " | " + str(count(results, PASSED)) + " | "
                     + str(count(results, FAILED)) + " | " + str(count(results, SKIPPED)) + " |")

    problems = []
    if not baseline:
        problems.append("the baseline run contains no test")
    if not candidate:
        problems.append("the candidate run contains no test")
    if regressions:
        problems.append(str(len(regressions)) + " test(s) passed with the baseline, but failed with the candidate")
    if missing:
        problems.append(str(len(missing)) + " test(s) ran with the baseline, but not with the candidate")

    lines.append("")
    if problems:
        lines.append("**FAILED:** " + "; ".join(problems) + ".")
    else:
        lines.append("**OK:** every test that passed with the baseline passed with the candidate, and every test"
                     " that ran with the baseline ran with the candidate.")

    listing("Regressions: passed with the baseline, failed with the candidate", regressions, lines)
    listing("Missing: ran with the baseline, but not with the candidate", missing, lines)
    listing("Skipped with the candidate, passed with the baseline", newly_skipped, lines)
    listing("Failed with both", failing_in_both, lines)
    listing("Fixed: failed with the baseline, passed with the candidate", fixed, lines)
    listing("Only with the candidate", extra, lines)

    text = os.linesep.join(lines)
    print(text)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as f:
            print(text, file=f)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
