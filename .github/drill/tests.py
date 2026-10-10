#!/usr/bin/env python3
"""Selects the test classes of Apache Drill's "java-exec" module that the workflow ".github/workflows/drill.yml"
runs, and distributes them over the shards of the workflow.

The test classes are read from Drill's published tests jar ("drill-java-exec-<version>-tests.jar"). A class is
selected if surefire would run it by default (a top-level, concrete class whose name matches "Test*", "*Test",
"*Tests" or "*TestCase"), and unless
 * its name matches one of the patterns in EXCLUDED below, or the additional regular expression given, or
 * it is annotated with one of the JUnit categories in EXCLUDED_CATEGORIES (the POM also excludes them as groups,
   for annotated test methods).
The selected classes are distributed over the shards by their running time in "times.txt" (the largest first, each
to the shard with the least time so far); a class that is not listed there counts DEFAULT_SECONDS. The classes of
the requested shard are written to the output file, one per line ("org/apache/drill/.../TestX.java"), the format of
surefire's "includesFile".

The script fails if fewer than MIN_FOUND of the classes in "times.txt" are selected, e.g. because the tests jar of
another Drill version has a different structure: the selection would then silently shrink.

Usage: tests.py <tests-jar> <times-file> <excludes-regex or ""> <shard> <shards> <output-file>
"""

import re
import struct
import sys
import zipfile

# Tests that do not compile generated code, but take much time or need infrastructure (seconds on GitHub's runner).
EXCLUDED = [
    r"\.exec\.rpc\.",                                  # RPC and its security (Kerberos, SSL, SASL); 260 s
    r"\.exec\.coord\.",                                # cluster coordination (ZooKeeper)
    r"\.exec\.client\.",                               # client API
    r"\.exec\.testing\.",                              # fault injection
    r"\.exec\.impersonation\.",                        # needs an HDFS mini cluster
    r"\.exec\.server\.rest\.",                         # web server
    r"\.exec\.udf\.dynamic\.",                         # builds UDF jars with an embedded Maven; 55 s
    r"\.exec\.metastore\.",                            # metastore; 31 s
    r"\.exec\.server\.TestDrillbitResilience$",        # fault injection; 178 s
    r"\.test\.TestGracefulShutdown$",                  # shutdown of Drillbits; 29 s
    r"\.exec\.store\.TestTimedCallable$",              # thread pool timeouts; 119 s
    r"\.exec\.store\.parquet\.TestParquetMetadataCache$",  # metadata cache files; 49 s
    r"\.exec\.sql\.TestInfoSchemaWithMetastore$",      # metastore
    r"\.exec\.store\.(Dropbox|Box)FileSystemTest$",    # cloud file systems
    r"^org\.apache\.drill\.storage\.",                 # credential providers (Vault)
    r"\.exec\.TestSSLConfig$",                         # SSL configuration
    r"\.exec\.expr\.fn\.FunctionInitializerTest$",     # builds a UDF jar with an embedded Maven; 77 s
    # Fails when its upgrade file is on the class path twice (as a file and in the tests jar, see "pom.xml").
    r"\.exec\.store\.TestBootstrapLoader$",
]
EXCLUDED_CATEGORIES = ("SecurityTest", "MetastoreTest", "FlakyTest")
SUREFIRE_NAMES = re.compile(r"^(Test.*|.*Test|.*Tests|.*TestCase)$")
DEFAULT_SECONDS = 2.0
MIN_FOUND = 0.9

ACC_INTERFACE, ACC_ABSTRACT = 0x0200, 0x0400
CATEGORY = "Lorg/junit/experimental/categories/Category;"


class ClassFile:
    """The access flags and the class-level JUnit categories of a class file."""

    def __init__(self, data):
        self.data, self.pos = data, 10
        self.utf8 = {}
        count = self.u2(8)
        i = 1
        while i < count:
            tag = data[self.pos]
            self.pos += 1
            if tag == 1:
                length = self.u2()
                self.utf8[i] = data[self.pos:self.pos + length].decode("utf-8", "replace")
                self.pos += length
            elif tag in (5, 6):
                self.pos += 8
                i += 1
            elif tag in (7, 8, 16, 19, 20):
                self.pos += 2
            elif tag == 15:
                self.pos += 3
            elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
                self.pos += 4
            else:
                raise ValueError("unknown constant pool tag %d" % tag)
            i += 1
        self.access = self.u2()
        self.pos += 4
        interfaces = self.u2()
        self.pos += 2 * interfaces
        for _ in range(2):  # fields, methods
            for _ in range(self.u2()):
                self.pos += 6
                self.skip_attributes()
        self.categories = set()
        for _ in range(self.u2()):
            name, length = self.utf8.get(self.u2()), self.u4()
            end = self.pos + length
            if name == "RuntimeVisibleAnnotations":
                for _ in range(self.u2()):
                    self.annotation()
            self.pos = end

    def u2(self, pos=None):
        if pos is not None:
            self.pos = pos
        self.pos += 2
        return struct.unpack_from(">H", self.data, self.pos - 2)[0]

    def u4(self):
        self.pos += 4
        return struct.unpack_from(">I", self.data, self.pos - 4)[0]

    def skip_attributes(self):
        for _ in range(self.u2()):
            self.pos += 2
            length = self.u4()
            self.pos += length

    def annotation(self):
        is_category = self.utf8.get(self.u2()) == CATEGORY
        for _ in range(self.u2()):
            self.pos += 2
            self.element_value(is_category)

    def element_value(self, is_category):
        tag = chr(self.data[self.pos])
        self.pos += 1
        if tag == "c":
            descriptor = self.utf8.get(self.u2(), "")
            if is_category:
                self.categories.add(descriptor.rsplit("/", 1)[-1].rstrip(";"))
        elif tag == "e":
            self.pos += 4
        elif tag == "@":
            self.annotation()
        elif tag == "[":
            for _ in range(self.u2()):
                self.element_value(is_category)
        else:
            self.pos += 2


def test_classes(jar):
    """Returns the names of the classes in the jar that surefire would run, without excluded categories."""
    names = []
    with zipfile.ZipFile(jar) as z:
        for entry in z.namelist():
            if not entry.endswith(".class") or "$" in entry:
                continue
            name = entry[:-len(".class")].replace("/", ".")
            if not SUREFIRE_NAMES.match(name.rsplit(".", 1)[-1]):
                continue
            cf = ClassFile(z.read(entry))
            if cf.access & (ACC_INTERFACE | ACC_ABSTRACT) or cf.categories & set(EXCLUDED_CATEGORIES):
                continue
            names.append(name)
    return sorted(names)


def read_times(path):
    times = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#"):
                seconds, name = line.split()
                times[name] = float(seconds)
    return times


def main(argv):
    if len(argv) != 7:
        print("usage: tests.py <tests-jar> <times-file> <excludes-regex> <shard> <shards> <output-file>")
        return 2
    jar, times_file, extra, shard, shards, output = argv[1], argv[2], argv[3], int(argv[4]), int(argv[5]), argv[6]
    if not 1 <= shard <= shards:
        print("ERROR: shard %d of %d" % (shard, shards))
        return 2
    patterns = [re.compile(p) for p in EXCLUDED + ([extra] if extra else [])]
    times = read_times(times_file)
    selected = [n for n in test_classes(jar) if not any(p.search(n) for p in patterns)]
    found = sum(1 for n in times if n in selected)
    print("%d classes selected, %d of the %d classes in %s" % (len(selected), found, len(times), times_file))
    if not selected or found < MIN_FOUND * len(times):
        print("ERROR: the selection shrank; does the tests jar have the expected structure?")
        return 1

    loads = [[0.0, []] for _ in range(shards)]
    for name in sorted(selected, key=lambda n: (-times.get(n, DEFAULT_SECONDS), n)):
        load = min(loads, key=lambda l: l[0])
        load[0] += times.get(name, DEFAULT_SECONDS)
        load[1].append(name)
    for i, (seconds, names) in enumerate(loads, 1):
        print("shard %d: %d classes, about %.0f s" % (i, len(names), seconds))
    with open(output, "w", encoding="utf-8", newline="\n") as f:
        for name in sorted(loads[shard - 1][1]):
            f.write(name.replace(".", "/") + ".java\n")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
