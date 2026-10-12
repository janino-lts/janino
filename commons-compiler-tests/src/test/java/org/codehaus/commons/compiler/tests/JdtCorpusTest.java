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

package org.codehaus.commons.compiler.tests;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.ICompiler;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.codehaus.commons.compiler.util.resource.MapResourceCreator;
import org.codehaus.commons.compiler.util.resource.MapResourceFinder;
import org.codehaus.commons.compiler.util.resource.Resource;
import org.codehaus.commons.compiler.util.resource.StringResource;
import org.codehaus.commons.nullanalysis.Nullable;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import util.TestUtil;

/**
 * Compiles and runs the programs of the regression tests of the Eclipse compiler (ECJ) with JANINO and with the
 * JDK-based compiler, and compares the results. The programs are an independent source of test cases for the areas
 * in which JANINO's rules differ from those of {@code javac}: variable arity methods, boxing, the conditional
 * operator, constant expressions, inner classes, {@code assert} and name resolution.
 * <p>
 *   The programs are not part of this repository (they are under the Eclipse Public License 2.0): the script {@code
 *   .github/jdt/JdtCorpusExtractor.java} extracts them from a checkout of {@code eclipse-jdt/eclipse.jdt.core} at the
 *   tag {@value #JDT_TAG}, and the test reads them from the directory that the system property {@code jdt.corpus}
 *   names. Without that property, the test is skipped. Locally:
 * </p>
 * <pre>
 *   git clone --depth 1 --branch R4_41 --filter=blob:none --sparse https://github.com/eclipse-jdt/eclipse.jdt.core.git
 *   cd eclipse.jdt.core
 *   git sparse-checkout set org.eclipse.jdt.core.tests.compiler/src/org/eclipse/jdt/core/tests/compiler/regression
 *   cd ..
 *   java .github/jdt/JdtCorpusExtractor.java \
 *       eclipse.jdt.core/org.eclipse.jdt.core.tests.compiler/src/org/eclipse/jdt/core/tests/compiler/regression \
 *       jdt-corpus VarargsTest AssertionTest ...   (the classes that ".github/workflows/ci.yml" names)
 *   mvn -f janino-parent/pom.xml install -DskipTests
 *   mvn -f janino-parent/pom.xml test -pl :commons-compiler-tests -Dtest=JdtCorpusTest \
 *       -Dsurefire.failIfNoSpecifiedTests=false -Djdt.corpus=$PWD/jdt-corpus
 * </pre>
 * <p>
 *   The oracle is {@code javac} (with {@code -source 8}), not ECJ: a program counts only if the JDK-based compiler
 *   compiles it, and if it prints exactly the output that the ECJ test expects, twice. Programs that ECJ rejects
 *   ("negative" tests) count only if {@code javac} rejects them too, and then only an internal compiler error of
 *   JANINO is a difference; that JANINO accepts invalid code is not (see section 3 of {@code JAVAC_DIFFERENCES.md}).
 * </p>
 * <p>
 *   The differences that are currently known are recorded in {@value #KNOWN_DIFFERENCES}{@code .txt} (the
 *   compatibility mode) and {@value #KNOWN_DIFFERENCES}{@code -compliant.txt} (the compliance mode), one per line:
 *   "<var>test-class</var>{@code .}<var>method</var>[{@code #}<var>n</var>] <var>kind</var>", where <var>kind</var>
 *   is {@code REJECTED} (JANINO rejects a valid program), {@code CRASH} (JANINO throws an exception other than a
 *   {@link CompileException}), {@code WRONG} (the program behaves differently, or its class files are rejected by
 *   the JVM) or {@code TIMEOUT}. The test fails if the actual differences deviate from the recorded ones in any
 *   way.
 * </p>
 * <p>
 *   With the system property {@code jdt.corpus.report}, the test prints all differences with their details, and
 *   does not compare them with the record.
 * </p>
 */
@RunWith(Parameterized.class) public
class JdtCorpusTest {

    /** The tag of {@code eclipse-jdt/eclipse.jdt.core} from which the programs are extracted. */
    public static final String JDT_TAG = "R4_41";

    private static final String KNOWN_DIFFERENCES = "src/test/resources/jdtCorpus/known-differences";

    /** The time that a program may run, in milliseconds. */
    private static final long TIMEOUT = 10000;

    /** The results of the JDK-based compiler, by case; {@code null} for the cases that do not count. */
    @Nullable private static Map<String, String> javacResults;

    private final String mode;

    @Parameters(name = "{0}") public static List<Object[]>
    parameters() { return DifferentialTesting.modes(); }

    public
    JdtCorpusTest(String mode) { this.mode = mode; }

    @Test public void
    test() throws Exception {

        String corpus = System.getProperty("jdt.corpus");
        Assume.assumeTrue("The JDT corpus is not available (system property \"jdt.corpus\")", corpus != null);
        File corpusDir = new File(corpus);

        ICompilerFactory[] compilerFactories = DifferentialTesting.janinoAndJdk();
        ICompilerFactory   janino            = compilerFactories[0];
        ICompilerFactory   jdk               = compilerFactories[1];

        List<Case>          cases        = JdtCorpusTest.readCases(corpusDir);
        Map<String, String> javacResults = JdtCorpusTest.javacResults(jdk, cases);

        Set<String>         actualDifferences = new TreeSet<>();
        Map<String, String> details           = new HashMap<>();
        for (Case c : cases) {
            String expected = javacResults.get(c.id);
            if (expected == null) continue;

            String actual;
            String kind;
            try {
                Map<String, byte[]> classFiles = JdtCorpusTest.compile(janino, this.mode, c);
                if (c.kind.equals("negative")) continue;
                if (!c.kind.equals("conform")) continue;
                actual = JdtCorpusTest.run(classFiles, c);
                if (actual.equals(expected)) continue;
                kind = actual.equals("TIMEOUT") ? "TIMEOUT" : "WRONG";
            } catch (CompileException ce) {
                if (c.kind.equals("negative")) continue;
                actual = ce.toString();
                kind   = "REJECTED";
            } catch (Exception | Error e) {
                actual = DifferentialTesting.describe(e);
                kind   = "CRASH";
            }
            String difference = c.id + " " + kind;
            actualDifferences.add(difference);
            details.put(difference, "  javac:  " + expected + "\n  janino: " + actual);
        }

        int valid = 0, invalid = 0;
        for (Case c : cases) {
            if (javacResults.get(c.id) == null) continue;
            if (c.kind.equals("negative")) {
                invalid++;
            } else {
                valid++;
            }
        }
        String summary = (
            cases.size()
            + " cases, confirmed by javac: "
            + valid
            + " valid and "
            + invalid
            + " invalid programs; "
            + actualDifferences.size()
            + " differences ("
            + this.mode
            + ")"
        );

        if (System.getProperty("jdt.corpus.report") != null) {
            for (String d : actualDifferences) System.out.println(d + "\n" + details.get(d));
            System.out.println(summary);
            return;
        }
        System.out.println(summary);

        DifferentialTesting.assertDifferences(
            DifferentialTesting.knownDifferencesFile(JdtCorpusTest.KNOWN_DIFFERENCES, this.mode),
            actualDifferences,
            details
        );
    }

    /**
     * Compiles and runs each case with the JDK-based compiler (once for both modes).
     *
     * @return The result of each case that counts (see {@link #run(Map, Case)}; {@code ""} for the cases that are
     *         only compiled), or {@code null}
     */
    private static synchronized Map<String, String>
    javacResults(ICompilerFactory jdk, List<Case> cases) throws Exception {

        Map<String, String> result = JdtCorpusTest.javacResults;
        if (result != null) return result;

        result = new HashMap<>();
        for (Case c : cases) {
            Map<String, byte[]> classFiles;
            try {
                classFiles = JdtCorpusTest.compile(jdk, TestUtil.JAVAC, c);
            } catch (CompileException ce) {
                result.put(c.id, c.kind.equals("negative") ? "rejected" : null);
                continue;
            }
            if (c.kind.equals("negative")) {
                result.put(c.id, null);
            } else
            if (c.kind.equals("compile")) {
                result.put(c.id, "");
            } else
            {
                String r1 = JdtCorpusTest.run(classFiles, c);
                String r2 = JdtCorpusTest.run(classFiles, c);
                result.put(c.id, r1.equals(r2) && r1.equals("out:" + c.expectedOutput) ? r1 : null);
            }
        }
        return (JdtCorpusTest.javacResults = result);
    }

    /**
     * Compiles the test files of the case with the given compiler, in the given mode, to class files.
     *
     * @return The class files, by resource name (e.g. {@code "p/X.class"})
     */
    private static Map<String, byte[]>
    compile(ICompilerFactory compilerFactory, String mode, Case c) throws CompileException, IOException {

        Map<String, byte[]> sources = new HashMap<>();
        Resource[]          resources = new Resource[c.files.size()];
        for (int i = 0; i < resources.length; i++) {
            String name = c.files.get(i);
            String text = c.sources.get(name);
            sources.put(name, text.getBytes(StandardCharsets.UTF_8));
            resources[i] = new StringResource(name, text, StandardCharsets.UTF_8);
        }

        ICompiler compiler = compilerFactory.newCompiler();
        if (TestUtil.COMPLIANT.equals(mode)) {
            try {
                TestUtil.setJavacCompliance(compiler);
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
        if (!TestUtil.isJanino(compilerFactory)) {
            compiler.setSourceVersion(8);
            compiler.setTargetVersion(8);
        }
        compiler.setSourceCharset(StandardCharsets.UTF_8);
        compiler.setSourceFinder(new MapResourceFinder(sources));
        MapResourceCreator classFiles = new MapResourceCreator();
        compiler.setClassFileCreator(classFiles);
        compiler.compile(resources);
        return classFiles.getMap();
    }

    /**
     * Runs the main method of the case.
     *
     * @return "{@code out:}" and the output (trimmed, with line feeds as line separators); then, if the program
     *         printed to {@code System.err}, "{@code \nerr:}" and that output; then, if the program threw an
     *         exception, "{@code \nexception:}" and its class; or "{@code TIMEOUT}"
     */
    private static String
    run(Map<String, byte[]> classFiles, final Case c) {

        final ClassLoader cl = DifferentialTesting.classLoader(classFiles);
        for (String a : c.vmArguments) {
            if (a.equals("-ea") || a.equals("-enableassertions")) cl.setDefaultAssertionStatus(true);
        }

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        final Throwable[]           exception = new Throwable[1];

        PrintStream savedOut = System.out, savedErr = System.err;
        System.setOut(new PrintStream(out, true));
        System.setErr(new PrintStream(err, true));
        Thread t = new Thread(() -> {
            try {
                Method main = cl.loadClass(c.mainClass).getMethod("main", String[].class);
                main.setAccessible(true);
                main.invoke(null, (Object) new String[0]);
            } catch (InvocationTargetException ite) {
                exception[0] = ite.getCause();
            } catch (Throwable th) { // SUPPRESS CHECKSTYLE IllegalCatch
                exception[0] = th;
            }
        });
        t.setDaemon(true);
        try {
            t.start();
            t.join(JdtCorpusTest.TIMEOUT);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            System.setOut(savedOut);
            System.setErr(savedErr);
        }
        if (t.isAlive()) return "TIMEOUT";

        StringBuilder sb = new StringBuilder("out:").append(JdtCorpusTest.normalize(out));
        String        e  = JdtCorpusTest.normalize(err);
        if (!e.isEmpty()) sb.append("\nerr:").append(e);
        if (exception[0] != null) sb.append("\nexception:").append(exception[0].getClass().getName());
        return sb.toString();
    }

    private static String
    normalize(ByteArrayOutputStream baos) {
        return JdtCorpusTest.normalize(new String(baos.toByteArray(), StandardCharsets.UTF_8));
    }

    private static String
    normalize(String s) { return s.replace("\r\n", "\n").replace('\r', '\n').trim(); }

    private static List<Case>
    readCases(File corpusDir) throws IOException {

        List<Case> result = new ArrayList<>();
        for (String id : JdtCorpusTest.readLines(new File(corpusDir, "index.txt"))) {
            if (!id.isEmpty()) result.add(new Case(id, new File(corpusDir, id)));
        }
        return result;
    }

    static List<String>
    readLines(File file) throws IOException {
        List<String> result = new ArrayList<>();
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            line = line.trim();
            if (!line.isEmpty()) result.add(line);
        }
        return result;
    }

    /**
     * One program, as written by {@code .github/jdt/JdtCorpusExtractor.java}.
     */
    private static final
    class Case {

        final String              id;
        final String              kind;          // "conform", "compile" or "negative"
        final List<String>        files;         // e.g. "p/X.java"
        final Map<String, String> sources = new TreeMap<>();
        final String              mainClass;     // e.g. "p.X"
        @Nullable final String    expectedOutput;
        final List<String>        vmArguments;

        Case(String id, File dir) throws IOException {
            this.id          = id;
            this.kind        = JdtCorpusTest.readLines(new File(dir, "kind.txt")).get(0);
            this.files       = JdtCorpusTest.readLines(new File(dir, "files.txt"));
            this.vmArguments = JdtCorpusTest.readLines(new File(dir, "vmargs.txt"));
            for (String f : this.files) {
                this.sources.put(f, new String(
                    Files.readAllBytes(new File(new File(dir, "src"), f).toPath()),
                    StandardCharsets.UTF_8
                ));
            }
            String first = this.files.get(0);
            this.mainClass = first.substring(0, first.length() - 5).replace('/', '.');
            File expected = new File(dir, "expected.txt");
            this.expectedOutput = expected.exists() ? JdtCorpusTest.normalize(new String(
                Files.readAllBytes(expected.toPath()),
                StandardCharsets.UTF_8
            )) : null;
        }
    }
}
