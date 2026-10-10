#!/usr/bin/env python3
"""Installs the candidate JANINO into the local Maven repository under the coordinates that Apache Fory declares.

Used by the workflow ".github/workflows/fory.yml": Fory declares "org.codehaus.janino:janino" (with the version in
the property "janino.version") and shades it into its own jars, selected by that group ID. The artifacts of this
repository have the group ID "io.github.janino-lts". Rather than editing Fory's POMs, this script installs the jars
of "io.github.janino-lts:janino" and "io.github.janino-lts:commons-compiler" of the given version as
"org.codehaus.janino:janino" and "org.codehaus.janino:commons-compiler" of the same version, each with a minimal POM
(janino depends on commons-compiler, as in the original POMs). Fory's build then uses the candidate with
"-Djanino.version=<version>", in its tests and in the shaded jars that its other modules test against.

Usage: install_candidate.py <version> [<local repository>]

The jars of "io.github.janino-lts" must be in the local repository (installed by the build of this repository, or
downloaded by "mvn dependency:get" if missing). The script refuses a version that already exists under
"org.codehaus.janino" in the local repository, e.g. a release of the original project, which it would overwrite.
"""

import os
import subprocess
import sys
import tempfile

POM = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.codehaus.janino</groupId>
  <artifactId>{artifact}</artifactId>
  <version>{version}</version>
  <description>io.github.janino-lts:{artifact}:{version}, installed under the coordinates of the original</description>
{dependencies}</project>
"""
JANINO_DEPENDENCIES = """  <dependencies>
    <dependency>
      <groupId>org.codehaus.janino</groupId>
      <artifactId>commons-compiler</artifactId>
      <version>{version}</version>
    </dependency>
  </dependencies>
"""


def mvn(repository, *args):
    command = ["mvn", "-B", "-ntp", "-Dmaven.repo.local=" + repository] + list(args)
    print(" ".join(command), flush=True)
    subprocess.run(command, check=True, shell=os.name == "nt")


def main(argv):
    if len(argv) not in (2, 3):
        print("usage: install_candidate.py <version> [<local repository>]")
        return 2
    version = argv[1]
    repository = os.path.abspath(argv[2] if len(argv) == 3 else os.path.expanduser("~/.m2/repository"))
    target = os.path.join(repository, "org", "codehaus", "janino", "janino", version)
    if os.path.exists(target):
        print("ERROR: " + target + " exists already; refusing to overwrite it")
        return 1

    def jar(artifact):
        return os.path.join(repository, "io", "github", "janino-lts", artifact, version,
                            artifact + "-" + version + ".jar")

    if not (os.path.isfile(jar("janino")) and os.path.isfile(jar("commons-compiler"))):
        mvn(repository, "dependency:get", "-Dartifact=io.github.janino-lts:janino:" + version)

    with tempfile.TemporaryDirectory() as directory:
        for artifact in ("commons-compiler", "janino"):
            dependencies = JANINO_DEPENDENCIES.format(version=version) if artifact == "janino" else ""
            pom = os.path.join(directory, artifact + ".pom")
            with open(pom, "w", encoding="utf-8") as f:
                f.write(POM.format(artifact=artifact, version=version, dependencies=dependencies))
            mvn(repository, "install:install-file", "-Dfile=" + jar(artifact), "-DpomFile=" + pom)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
