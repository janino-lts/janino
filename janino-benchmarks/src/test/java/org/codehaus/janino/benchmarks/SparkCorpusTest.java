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

package org.codehaus.janino.benchmarks;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.codehaus.janino.ClassBodyEvaluator;
import org.codehaus.janino.JaninoOption;
import org.codehaus.janino.benchmarks.SparkCodegen.Body;
import org.junit.Assert;
import org.junit.Test;

/**
 * Compiles the corpus of the workload {@link Workload#SPARK_TPCDS} (the class bodies that Apache Spark generated for
 * twelve TPC-DS queries) with the current JANINO, the way Spark compiles it (see {@link SparkCodegen}), in the
 * compatibility mode and in the compliance mode ({@link JaninoOption#JAVAC_COMPLIANCE}), and loads and instantiates
 * every generated class. The test fails if any class body is rejected, crashes the compiler or compiles into a class
 * that the JVM rejects, and lists the class bodies.
 * <p>
 *   The corpus is real generated code of a project that relies on JANINO, with the constructs that a code generator
 *   actually produces, so it can find defects of a mode that the generators and the recorded cases of {@code
 *   commons-compiler-tests} do not cover. The compile time is measured elsewhere
 *   ({@link Compare}), and the class files of the compatibility mode are compared with those of the last release by
 *   {@link CodeSizeReport}; here, the test only writes the names of the class bodies whose class files differ between
 *   the two modes to "target/spark-corpus-mode-differences.txt", for inspection.
 * </p>
 * <p>
 *   Needs the Spark runtime in "target/spark-classpath" (copied by the build before the tests).
 * </p>
 */
public
class SparkCorpusTest {

    @Test public void
    testCompatibilityMode() throws Exception { SparkCorpusTest.compileLoadAndInstantiate(false); }

    @Test public void
    testComplianceMode() throws Exception { SparkCorpusTest.compileLoadAndInstantiate(true); }

    @Test public void
    testClassFilesOfTheModes() throws Exception {

        List<String> differing = new ArrayList<>();
        for (Body body : SparkCodegen.bodies()) {
            Map<String, byte[]> compat    = new TreeMap<>(SparkCorpusTest.compile(body, false).getBytecodes());
            Map<String, byte[]> compliant = new TreeMap<>(SparkCorpusTest.compile(body, true).getBytecodes());
            if (!compat.keySet().equals(compliant.keySet())) {
                differing.add(body.fileName + " (different classes)");
                continue;
            }
            for (Map.Entry<String, byte[]> e : compat.entrySet()) {
                if (!Arrays.equals(e.getValue(), compliant.get(e.getKey()))) {
                    differing.add(body.fileName);
                    break;
                }
            }
        }

        File file = new File(JaninoVersion.targetDirectory(), "spark-corpus-mode-differences.txt");
        Files.write(file.toPath(), differing, StandardCharsets.UTF_8);
        System.out.println(
            differing.size()
            + " of "
            + SparkCodegen.bodies().size()
            + " class bodies compile into different class files in the two modes; see \""
            + file
            + "\""
        );
    }

    /**
     * Compiles every class body of the corpus in the given mode, loads the generated class and instantiates it;
     * fails with the list of the class bodies for which that does not work.
     */
    private static void
    compileLoadAndInstantiate(boolean compliance) throws Exception {

        List<String> failures = new ArrayList<>();
        for (Body body : SparkCodegen.bodies()) {
            try {
                Object instance = SparkCorpusTest.compile(body, compliance).getClazz().getConstructor().newInstance();
                if (!SparkCodegen.extendedClass().isInstance(instance)) {
                    failures.add(body.fileName + ": " + instance.getClass() + " is not a GeneratedClass");
                }
            } catch (Exception | LinkageError e) {
                failures.add(body.fileName + ": " + e);
            }
        }

        if (!failures.isEmpty()) {
            StringBuilder sb = new StringBuilder(
                failures.size()
                + " of "
                + SparkCodegen.bodies().size()
                + " class bodies fail in the "
                + (compliance ? "compliance" : "compatibility")
                + " mode:"
            );
            for (String f : failures) sb.append('\n').append(f);
            Assert.fail(sb.toString());
        }
    }

    /**
     * Compiles one class body like Spark does, with the current JANINO in the given mode.
     */
    private static ClassBodyEvaluator
    compile(Body body, boolean compliance) throws Exception {

        ClassBodyEvaluator cbe = new ClassBodyEvaluator();
        if (compliance) {
            EnumSet<JaninoOption> options = EnumSet.copyOf(cbe.options());
            options.add(JaninoOption.JAVAC_COMPLIANCE);
            cbe.options(options);
        }
        cbe.setParentClassLoader(SparkCodegen.sparkClassLoader());
        cbe.setClassName(SparkCodegen.CLASS_NAME);
        cbe.setDefaultImports(SparkCodegen.DEFAULT_IMPORTS);
        cbe.setExtendedClass(SparkCodegen.extendedClass());
        cbe.cook(SparkCodegen.FILE_NAME, new StringReader(body.text));
        return cbe;
    }
}
