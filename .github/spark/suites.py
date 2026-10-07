#!/usr/bin/env python3
"""Lists the ScalaTest suites of a package in a tests jar, as "-s <suite>" arguments for the ScalaTest Runner.

Usage: suites.py <tests jar> <package prefix> [regex of excluded simple class names]

Only classes that the Runner can run are listed: public, concrete, with a public no-argument constructor (the
jar also contains abstract base suites, and a few JUnit tests named "*Suite", e.g. "XXH64Suite"; the Runner
aborts on both). The number of suites goes to stderr.
"""

import re
import struct
import sys
import zipfile

ACC_PUBLIC, ACC_INTERFACE, ACC_ABSTRACT = 0x0001, 0x0200, 0x0400
# The sizes of the constant pool entries by tag, excluding the tag byte (CONSTANT_Utf8 has a variable size).
CONSTANT_SIZES = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4,
                  19: 2, 20: 2}


def is_runnable(data):
    """Returns whether the class file is a public, concrete class with a public no-argument constructor."""
    utf8 = {}
    pos = 10
    count = struct.unpack_from(">H", data, 8)[0]
    index = 1
    while index < count:
        tag = data[pos]
        if tag == 1:
            length = struct.unpack_from(">H", data, pos + 1)[0]
            utf8[index] = data[pos + 3:pos + 3 + length]
            pos += 3 + length
        else:
            pos += 1 + CONSTANT_SIZES[tag]
        index += 2 if tag in (5, 6) else 1
    flags = struct.unpack_from(">H", data, pos)[0]
    if not flags & ACC_PUBLIC or flags & (ACC_INTERFACE | ACC_ABSTRACT):
        return False
    pos += 6  # access_flags, this_class, super_class
    pos += 2 + 2 * struct.unpack_from(">H", data, pos)[0]  # interfaces
    for _ in range(2):  # fields, then methods
        n = struct.unpack_from(">H", data, pos)[0]
        pos += 2
        for _ in range(n):
            access, name, descriptor, attributes = struct.unpack_from(">HHHH", data, pos)
            pos += 8
            if utf8.get(name) == b"<init>" and utf8.get(descriptor) == b"()V" and access & ACC_PUBLIC:
                return True
            for _ in range(attributes):
                pos += 6 + struct.unpack_from(">I", data, pos + 2)[0]
    return False


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
                continue  # a JUnit test class, not a ScalaTest suite
            if not is_runnable(data):
                continue
            names.append(name)
    print(" ".join("-s " + name for name in sorted(names)))
    print(str(len(names)) + " suites", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
