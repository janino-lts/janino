#!/usr/bin/env python3
"""Lists the ScalaTest suites of a package in a tests jar, as "-s <suite>" arguments for the ScalaTest Runner.

Usage: suites.py <tests jar> <package prefix> [regex of excluded simple class names]

Classes named "*Suite" that are JUnit tests rather than ScalaTest suites (Spark has a few, e.g. "XXH64Suite")
are left out, because the Runner aborts on them. The number of suites goes to stderr.
"""

import re
import sys
import zipfile


def main(argv):
    if len(argv) < 3:
        print("usage: suites.py <tests jar> <package prefix> [excluded regex]")
        return 2
    jar, prefix = argv[1], argv[2]
    excluded = re.compile(argv[3]) if len(argv) > 3 and argv[3] else None
    names = []
    with zipfile.ZipFile(jar) as z:
        for entry in z.namelist():
            if not entry.endswith("Suite.class") or "$" in entry:
                continue
            name = entry[:-len(".class")].replace("/", ".")
            if not name.startswith(prefix + "."):
                continue
            if excluded and excluded.search(name.split(".")[-1]):
                continue
            data = z.read(entry)
            if b"org/scalatest" not in data and b"Lorg/junit/" in data:
                continue
            names.append(name)
    print(" ".join("-s " + name for name in sorted(names)))
    print(str(len(names)) + " suites", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
