/*
 * Janino - An embedded Java[TM] compiler
 *
 * Copyright (c) 2026 Stefan Zobel. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 *
 *    1. Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *       following disclaimer.
 *    2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *       following disclaimer in the documentation and/or other materials provided with the distribution.
 *    3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote
 *       products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/**
 * Benchmarks that compare the compile time of the current build of JANINO with a baseline version.
 * <p>
 *   Both versions are loaded into the same JVM, each through its own class loader (see {@link
 *   org.codehaus.janino.benchmarks.JaninoVersion}), and are measured <em>interleaved</em>, in alternating order (see
 *   {@link org.codehaus.janino.benchmarks.Compare}). Measuring one version after the other is not reliable: the speed of
 *   a machine drifts, e.g. while a laptop heats up, and the drift easily exceeds the differences to be measured. Look
 *   at the <em>ratios</em>, not at the absolute numbers: they depend on the machine.
 * </p>
 *
 * <h2>Building</h2>
 * <pre>
 *   mvn -f janino-parent/pom.xml -P benchmarks -DskipTests clean package
 * </pre>
 * <p>
 *   This creates "janino-benchmarks/target/benchmarks.jar", and copies the JAR files of the current build to
 *   "janino-benchmarks/target/janino-current" and those of the baseline version (3.1.12 by default; from Maven Central)
 *   to "janino-benchmarks/target/janino-baseline". To compare with another version, add
 *   {@code -Djanino.baseline.version=}<var>version</var>.
 * </p>
 *
 * <h2>Running</h2>
 * <pre>
 *   java -jar janino-benchmarks/target/benchmarks.jar
 * </pre>
 * <p>
 *   compares the two versions for each {@link org.codehaus.janino.benchmarks.Workload} (a few minutes). Useful variants:
 * </p>
 * <pre>
 *   java -jar janino-benchmarks/target/benchmarks.jar -aa             (current with itself: the precision)
 *   java -jar janino-benchmarks/target/benchmarks.jar CONTROL_FLOW    (one workload)
 *   java -Xint -jar janino-benchmarks/target/benchmarks.jar           (interpreter only: the work, without JIT effects)
 *   java -jar janino-benchmarks/target/benchmarks.jar -h              (all options)
 * </pre>
 * <p>
 *   Besides the times, the memory that one operation allocates is reported; with "-Xint", it is almost deterministic,
 *   so it shows even small differences reliably. The size of the generated class files is deterministic, and often
 *   explains differences of the compile time; the report also tells whether both versions generate the same class
 *   files:
 * </p>
 * <pre>
 *   java -cp janino-benchmarks/target/benchmarks.jar org.codehaus.janino.benchmarks.CodeSizeReport
 * </pre>
 *
 * <h2>Comparing with an earlier build</h2>
 * <p>
 *   To measure the effect of a change of the current version, copy the JAR files of the build before the change, and
 *   use them as the baseline:
 * </p>
 * <pre>
 *   (before the change: build as above, then copy "janino-benchmarks/target/janino-current" to <var>dir</var>)
 *   java -Djanino.benchmarks.baseline.dir=<var>dir</var> -jar janino-benchmarks/target/benchmarks.jar
 *   java -Djanino.benchmarks.baseline.dir=<var>dir</var> -cp janino-benchmarks/target/benchmarks.jar \
 *       org.codehaus.janino.benchmarks.CodeSizeReport
 * </pre>
 * <p>
 *   The benchmarks locate the JAR files and JANINO's sources relative to "benchmarks.jar"; if they are moved, set
 *   the system property {@code janino.benchmarks.root} to the root directory of the project.
 * </p>
 */
package org.codehaus.janino.benchmarks;
