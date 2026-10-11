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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.codehaus.commons.nullanalysis.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import util.TestUtil;

/**
 * Generates classes in which the same simple names denote different things in different scopes (static, instance,
 * private and constant fields of the outer class, fields of an inner subclass of it, of a static nested class, of
 * a local class and of an anonymous subclass, parameters, local variables), and expressions that read them:
 * simple names, {@code this.x}, {@code P.this.x}, {@code super.x}, invocations of a {@code private} and of an
 * overridden method, also as the condition and the message of an {@code assert} statement, with assertions enabled
 * and disabled. The classes are compiled with JANINO and with the JDK-based compiler, and the results of the
 * expressions are compared (JLS 6.4 and 15.11: scoping and shadowing; 8.2: {@code private} members are not
 * inherited; 15.8.4: the qualified {@code this}; 14.10: {@code assert}). The generator is deterministic (fixed
 * seeds), so that every difference is reproducible.
 * <p>
 *   Each generated class has the entry methods {@code p}<var>n</var>, each of which invokes one probe: a method of
 *   the outer class, of its inner subclass {@code Q} (on an instance whose enclosing instance has other field
 *   values), of a static nested class {@code S}, of a local class, or of an anonymous subclass. Like in {@link
 *   InvocationDifferentialTest}, the JDK-based compiler decides which probes are valid: the probes that it rejects
 *   (e.g. the access to a {@code private} field of the superclass through {@code this}) must be rejected by JANINO
 *   as well. Every valid probe runs twice, with assertions disabled ({@code p}<var>n</var>) and enabled ({@code
 *   p}<var>n</var>{@code +ea}).
 * </p>
 * <p>
 *   The differences that are currently known are recorded in {@value #KNOWN_DIFFERENCES}{@code .txt} (the
 *   compatibility mode) and {@value #KNOWN_DIFFERENCES}{@code -compliant.txt} (the compliance mode, see {@link
 *   TestUtil#getCompilerFactoriesAndModesForParameters()}), one per line: "<var>seed</var> <var>probe</var>
 *   <var>kind</var>", where <var>kind</var> is {@code WRONG} (a different result), {@code REJECTED} (JANINO rejects a
 *   valid probe), {@code ACCEPTED} (JANINO accepts an invalid probe) or {@code INVALID} (a class file that the JVM
 *   rejects, or an internal compiler error). The test fails if the actual differences deviate from the recorded
 *   ones in any way. The compatibility mode keeps the rules S-03 (an {@code assert} statement always throws) and
 *   S-11 ({@code P.this} in a subclass of {@code P} declared in {@code P} denotes {@code this}, and {@code private}
 *   members of the superclass are inherited) of {@code JAVAC_DIFFERENCES.md}, so its record lists the probes that
 *   these rules decide differently than {@code javac}.
 * </p>
 * <p>
 *   With the system property {@code nameResolution.differential.classes}, the test generates the given number of
 *   classes, prints all differences, and does not compare them with the record.
 * </p>
 */
@RunWith(Parameterized.class) public
class NameResolutionDifferentialTest {

    private static final String KNOWN_DIFFERENCES = "src/test/resources/nameResolutionDifferential/known-differences";

    private static final int  CLASSES           = 40;
    private static final int  PROBES_PER_CLASS  = 10;
    private static final long SEED              = 20261012L;

    /** The names that the generated declarations use. */
    private static final String[] NAMES = { "a", "b", "c", "d" };

    private final String mode;

    @Parameters(name = "{0}") public static List<Object[]>
    parameters() { return DifferentialTesting.modes(); }

    public
    NameResolutionDifferentialTest(String mode) { this.mode = mode; }

    @Test public void
    test() throws Exception {

        ICompilerFactory[] compilerFactories = DifferentialTesting.janinoAndJdk();
        ICompilerFactory   janino            = compilerFactories[0];
        ICompilerFactory   jdk               = compilerFactories[1];

        String soak    = System.getProperty("nameResolution.differential.classes");
        int    classes = soak == null ? NameResolutionDifferentialTest.CLASSES : Integer.parseInt(soak);

        Set<String>         actualDifferences = new TreeSet<>();
        Map<String, String> details           = new HashMap<>();
        int[]               counts            = new int[2]; // valid, invalid
        for (int c = 0; c < classes; c++) {

            long           seed      = NameResolutionDifferentialTest.SEED + c;
            GeneratedClass generated = new GeneratedClass(new Random(seed));

            for (Map.Entry<String, String> e : NameResolutionDifferentialTest.compare(
                janino,
                this.mode,
                jdk,
                generated,
                counts
            ).entrySet()) {
                String difference = seed + " " + e.getKey();
                actualDifferences.add(difference);
                String probe = e.getKey().split(" ")[0].replace("+ea", "");
                details.put(difference, e.getValue() + "\n" + generated.describe(probe));
            }
        }

        if (soak != null) {
            for (String d : actualDifferences) System.out.println(d + "\n" + details.get(d));
            System.out.println(
                actualDifferences.size()
                + " differences in "
                + counts[0]
                + " valid and "
                + counts[1]
                + " invalid probes"
            );
            return;
        }

        DifferentialTesting.assertDifferences(
            DifferentialTesting.knownDifferencesFile(NameResolutionDifferentialTest.KNOWN_DIFFERENCES, this.mode),
            actualDifferences,
            details
        );
    }

    /**
     * Compiles the <var>generated</var> class with both compilers, and compares the results of its probes.
     *
     * @param counts The numbers of valid and of invalid probes, incremented by those of this class
     * @return The differences ("<var>probe</var>[{@code +ea}] <var>kind</var>", mapped to a description)
     */
    private static Map<String, String>
    compare(
        ICompilerFactory janino,
        String           mode,
        ICompilerFactory jdk,
        GeneratedClass   generated,
        int[]            counts
    ) throws Exception {

        Map<String, String> result = new TreeMap<>();

        // The JDK-based compiler decides which probes are valid; one compilation reports all of them.
        Map<String, String> javacErrors = new HashMap<>();
        for (Map.Entry<Integer, String> e : DifferentialTesting.javacErrorsByLine(
            jdk,
            generated.source(generated.probes)
        ).entrySet()) {
            String probe = generated.probeAtLine(e.getKey());
            if (probe == null) {
                throw new AssertionError(
                    "JAVAC rejects the generated declarations: "
                    + e.getValue()
                    + "\n"
                    + generated.source(generated.probes)
                );
            }
            if (!javacErrors.containsKey(probe)) javacErrors.put(probe, e.getValue());
        }
        List<String> valid = new ArrayList<>();
        for (String probe : generated.probes) {
            if (!javacErrors.containsKey(probe)) valid.add(probe);
        }
        counts[0] += valid.size();
        counts[1] += javacErrors.size();

        // The expected results of the valid probes, with assertions disabled and enabled.
        if (!valid.isEmpty()) {
            Map<String, String> expected = new HashMap<>();
            String              source   = generated.source(valid);
            for (boolean assertions : new boolean[] { false, true }) {
                ClassLoader expectedCl;
                try {
                    expectedCl = DifferentialTesting.compile(jdk, TestUtil.JAVAC, source);
                } catch (CompileException ce) {
                    throw new AssertionError("JAVAC rejects the generated code: " + ce + "\n" + source, ce);
                }
                expectedCl.setDefaultAssertionStatus(assertions);
                for (String probe : valid) {
                    String value = DifferentialTesting.invokeStatic(expectedCl, "P", probe);
                    if (value.startsWith("?")) {
                        throw new AssertionError("The code that JAVAC generated fails: " + probe + ": " + value);
                    }
                    expected.put(NameResolutionDifferentialTest.key(probe, assertions), value);
                }
            }

            // JANINO must accept all valid probes in one class, and compute the same results. Only if it does not
            // accept the class, compile each probe separately, so that the defects can be attributed.
            Map<String, String> differences = NameResolutionDifferentialTest.compareValid(
                janino,
                mode,
                generated,
                valid,
                expected
            );
            if (differences == null) {
                for (String probe : valid) {
                    differences = NameResolutionDifferentialTest.compareValid(
                        janino,
                        mode,
                        generated,
                        Collections.singletonList(probe),
                        expected
                    );
                    assert differences != null;
                    result.putAll(differences);
                }
            } else {
                result.putAll(differences);
            }
        }

        // JANINO must reject the invalid probes, each compiled separately.
        for (String probe : generated.probes) {
            if (javacErrors.containsKey(probe)) {
                String source = generated.source(Collections.singletonList(probe));
                try {
                    ClassLoader actualCl = DifferentialTesting.compile(janino, mode, source);
                    String      actual   = DifferentialTesting.invokeStatic(actualCl, "P", probe);
                    result.put(probe + " ACCEPTED", "JAVAC: " + javacErrors.get(probe) + "\nJANINO: " + actual);
                } catch (CompileException ce) {
                    ; // As expected.
                } catch (Exception | AssertionError e) { // E.g. "InternalCompilerException"
                    result.put(
                        probe + " INVALID",
                        "JAVAC: " + javacErrors.get(probe) + "\nJANINO: " + DifferentialTesting.describe(e)
                    );
                }
            }
        }

        return result;
    }

    /**
     * Compiles the class with the <var>valid</var> probes with JANINO, with assertions disabled and enabled, and
     * compares the results of the probes with the <var>expected</var> ones.
     *
     * @return The differences, or {@code null} iff JANINO fails to compile or to load the class and <var>valid</var>
     *         contains more than one probe
     */
    @Nullable private static Map<String, String>
    compareValid(
        ICompilerFactory    janino,
        String              mode,
        GeneratedClass      generated,
        List<String>        valid,
        Map<String, String> expected
    ) {

        Map<String, String> result = new TreeMap<>();
        String              source = generated.source(valid);

        for (boolean assertions : new boolean[] { false, true }) {
            ClassLoader actualCl;
            try {
                actualCl = DifferentialTesting.compile(janino, mode, source);
            } catch (CompileException ce) {
                if (valid.size() > 1) return null;
                result.put(valid.get(0) + " REJECTED", ce.toString());
                return result;
            } catch (Exception | AssertionError e) { // E.g. "InternalCompilerException"
                if (valid.size() > 1) return null;
                result.put(valid.get(0) + " INVALID", DifferentialTesting.describe(e));
                return result;
            }
            actualCl.setDefaultAssertionStatus(assertions);

            for (String probe : valid) {
                String key    = NameResolutionDifferentialTest.key(probe, assertions);
                String actual = DifferentialTesting.invokeStatic(actualCl, "P", probe);
                if (actual.startsWith("?")) {
                    if (valid.size() > 1) return null;
                    result.put(key + " INVALID", actual);
                } else if (!actual.equals(expected.get(key))) {
                    result.put(key + " WRONG", "expected <" + expected.get(key) + "> but was <" + actual + ">");
                }
            }
        }
        return result;
    }

    /** @return The key of a probe's result: the probe, and "+ea" if assertions are enabled */
    private static String
    key(String probe, boolean assertions) { return assertions ? probe + "+ea" : probe; }

    /**
     * A generated class {@code P}: its fields and methods, its inner subclass {@code Q}, its static nested class
     * {@code S}, and the probes.
     */
    static final
    class GeneratedClass {

        /** The kinds of a field declaration. */
        private static final String[] FIELD_KINDS = {
            "", "static int", "int", "private int", "private static int", "static final int"
        };

        /** The names of the probes ({@code p0}, {@code p1}, ...), in order. */
        final List<String> probes = new ArrayList<>();

        private final List<String>        fieldsOfP       = new ArrayList<>();  // declarations
        private final Map<String, String> kindOfP         = new HashMap<>();    // name -> kind (see FIELD_KINDS)
        private final List<String>        fieldsOfQ       = new ArrayList<>();
        private final Map<String, String> kindOfQ         = new HashMap<>();
        private final List<String>        fieldsOfS       = new ArrayList<>();
        private final Map<String, String> kindOfS         = new HashMap<>();
        private final boolean             qWho, qWhoP;

        /** The line of each probe in {@code P} (the entry method), in {@code Q}, in {@code S}, or a helper method. */
        private final Map<String, String> entries = new HashMap<>();
        private final Map<String, String> inQ     = new HashMap<>();
        private final Map<String, String> inS     = new HashMap<>();
        private final Map<String, String> helpers = new HashMap<>();

        /** The line number of each probe's lines in the source of the last call of {@link #source(List)}. */
        private final Map<Integer, String> lines = new HashMap<>();

        private int nextValue = 1;

        GeneratedClass(Random random) {

            for (String name : NameResolutionDifferentialTest.NAMES) {
                String kind = GeneratedClass.FIELD_KINDS[random.nextInt(GeneratedClass.FIELD_KINDS.length)];
                if (!kind.isEmpty()) {
                    this.kindOfP.put(name, kind);
                    this.fieldsOfP.add(kind + " " + name + " = " + this.nextValue++ + ";");
                }
            }
            for (String name : NameResolutionDifferentialTest.NAMES) {
                String kind = new String[] { "", "", "int", "private int" }[random.nextInt(4)];
                if (!kind.isEmpty()) {
                    this.kindOfQ.put(name, kind);
                    this.fieldsOfQ.add(kind + " " + name + " = " + this.nextValue++ + ";");
                }
            }
            for (String name : NameResolutionDifferentialTest.NAMES) {
                String kind = new String[] { "", "", "int", "static int" }[random.nextInt(4)];
                if (!kind.isEmpty()) {
                    this.kindOfS.put(name, kind);
                    this.fieldsOfS.add(kind + " " + name + " = " + this.nextValue++ + ";");
                }
            }
            this.qWho  = random.nextBoolean();
            this.qWhoP = random.nextBoolean();

            for (int n = 0; n < NameResolutionDifferentialTest.PROBES_PER_CLASS; n++) {
                String probe = "p" + n;
                this.probes.add(probe);
                this.generateProbe(random, probe);
            }
        }

        /**
         * Generates one probe: its entry method and the method that it invokes.
         */
        private void
        generateProbe(Random random, String probe) {

            String[] names = NameResolutionDifferentialTest.NAMES;
            String   name  = names[random.nextInt(names.length)];
            String   n     = probe.substring(1);

            // The invocation of the probe method, with a parameter and a local variable that may shadow fields.
            String parameter = "int " + name;
            int    argument  = this.nextValue++;
            String local     = "int " + names[random.nextInt(names.length)] + " = " + this.nextValue++ + ";";

            switch (random.nextInt(5)) {

            case 0: // A method of P.
                this.helpers.put(probe, (
                    "String i" + n + "(" + parameter + ") { " + local + " " + this.body(random, Scope.P) + " }"
                ));
                this.entries.put(probe, this.entry(probe, "o.i" + n + "(" + argument + ")", true));
                break;

            case 1: // A method of the inner subclass Q, on an instance whose enclosing instance has other values.
                this.inQ.put(probe, (
                    "String q" + n + "(" + parameter + ") { " + local + " " + this.body(random, Scope.Q) + " }"
                ));
                this.entries.put(probe, this.entry(probe, "o.new Q().q" + n + "(" + argument + ")", true));
                break;

            case 2: // A method of the static nested class S.
                this.inS.put(probe, (
                    "String s" + n + "(" + parameter + ") { " + local + " " + this.body(random, Scope.S) + " }"
                ));
                this.entries.put(probe, this.entry(probe, "new S().s" + n + "(" + argument + ")", false));
                break;

            case 3: // A local class in a static method of P, capturing the parameter and the local variable.
                {
                    String ownField = random.nextBoolean() ? "" : "int " + name + " = " + this.nextValue++ + "; ";
                    this.helpers.put(probe, (
                        "static String t" + n + "(final " + parameter + ") { final " + local
                        + " class L { " + ownField + "String l() { " + this.body(random, Scope.L) + " } }"
                        + " return new L().l(); }"
                    ));
                    this.entries.put(probe, this.entry(probe, "t" + n + "(" + argument + ")", false));
                }
                break;

            default: // An anonymous subclass of P in an instance method of P (the enclosing instance has other values).
                {
                    String ownField = random.nextBoolean() ? "" : "int " + name + " = " + this.nextValue++ + "; ";
                    this.helpers.put(probe, (
                        "String an" + n + "(final " + parameter + ") { final " + local
                        + " return new P() { " + ownField + "String x() { " + this.body(random, Scope.A) + " } }.x(); }"
                    ));
                    this.entries.put(probe, this.entry(probe, "o.an" + n + "(" + argument + ")", true));
                }
                break;
            }
        }

        /**
         * @return The entry method of the <var>probe</var>, which creates an instance {@code o} of {@code P} with
         *         other values in its instance fields (if <var>withInstance</var>) and evaluates the
         *         <var>invocation</var>
         */
        private String
        entry(String probe, String invocation, boolean withInstance) {
            StringBuilder sb = new StringBuilder("public static String ").append(probe).append("() { ");
            if (withInstance) {
                sb.append("P o = new P(); ");
                for (Map.Entry<String, String> e : this.kindOfP.entrySet()) {
                    if (!e.getValue().contains("static")) sb.append("o.").append(e.getKey()).append(" += 100; ");
                }
            }
            return sb.append("return ").append(invocation).append("; }").toString();
        }

        /** Where a probe's expressions are evaluated. */
        enum Scope { P, Q, S, L, A }

        /**
         * @return The body of a probe method: it returns the values of one to three expressions, or evaluates an
         *         {@code assert} statement with two of them and returns their values or the assertion error
         */
        private String
        body(Random random, Scope scope) {

            if (random.nextInt(3) == 0) {
                String e1 = this.intExpression(random, scope);
                String e2 = this.intExpression(random, scope);
                return (
                    "try { assert " + e1 + " == " + e2 + " : \"\" + " + e1 + "; return \"ok:\" + " + e1 + " + \",\" + "
                    + e2 + "; } catch (AssertionError e) { return \"AE:\" + e.getMessage(); }"
                );
            }

            StringBuilder sb = new StringBuilder("return \"\"");
            int           n  = 1 + random.nextInt(3);
            for (int i = 0; i < n; i++) {
                if (i > 0) sb.append(" + \",\"");
                sb.append(" + ").append(
                    random.nextInt(4) == 0 ? this.stringExpression(random, scope) : this.intExpression(random, scope)
                );
            }
            return sb.append(";").toString();
        }

        /**
         * @return An expression of type {@code int} that reads a field, a parameter or a local variable in the
         *         <var>scope</var>: a simple name, or a name qualified with {@code this}, {@code P.this} or {@code
         *         super} where the scope allows it
         */
        private String
        intExpression(Random random, Scope scope) {
            String[] names = NameResolutionDifferentialTest.NAMES;
            String   name  = names[random.nextInt(names.length)];
            switch (random.nextInt(4)) {
            case 0:  return name;
            case 1:  return "this." + name;
            case 2:  return scope == Scope.Q || scope == Scope.A || scope == Scope.P ? "P.this." + name : name;
            default: return scope == Scope.Q || scope == Scope.A ? "super." + name : name;
            }
        }

        /**
         * @return An expression of type {@link String} that invokes the {@code private} method {@code who()} or the
         *         overridable method {@code whoP()} of {@code P}, or of the subclass, in the <var>scope</var>
         */
        private String
        stringExpression(Random random, Scope scope) {
            String method = random.nextBoolean() ? "who()" : "whoP()";
            switch (random.nextInt(4)) {
            case 0:  return method;
            case 1:  return scope == Scope.Q || scope == Scope.A || scope == Scope.P ? "this." + method : method;
            case 2:  return scope == Scope.Q || scope == Scope.A || scope == Scope.P ? "P.this." + method : method;
            default: return scope == Scope.Q || scope == Scope.A ? "super." + method : method;
            }
        }

        /**
         * @return The source of the class with the given <var>probes</var> (a subset of {@link #probes}), each
         *         probe's lines on lines of their own
         */
        String
        source(List<String> probes) {
            this.lines.clear();
            List<String> src = new ArrayList<>();
            src.add("public class P {");
            src.add("    " + String.join(" ", this.fieldsOfP));
            src.add("    private String who() { return \"P.who\"; }");
            src.add("    String whoP() { return \"P.whoP\"; }");
            src.add(
                "    class Q extends P { "
                + String.join(" ", this.fieldsOfQ)
                + (this.qWho ? " private String who() { return \"Q.who\"; }" : "")
                + (this.qWhoP ? " String whoP() { return \"Q.whoP\"; }" : "")
            );
            for (String probe : probes) {
                if (this.inQ.containsKey(probe)) {
                    this.lines.put(src.size() + 1, probe);
                    src.add("        " + this.inQ.get(probe));
                }
            }
            src.add("    }");
            src.add("    static class S { " + String.join(" ", this.fieldsOfS));
            for (String probe : probes) {
                if (this.inS.containsKey(probe)) {
                    this.lines.put(src.size() + 1, probe);
                    src.add("        " + this.inS.get(probe));
                }
            }
            src.add("    }");
            for (String probe : probes) {
                if (this.helpers.containsKey(probe)) {
                    this.lines.put(src.size() + 1, probe);
                    src.add("    " + this.helpers.get(probe));
                }
            }
            for (String probe : probes) {
                this.lines.put(src.size() + 1, probe);
                src.add("    " + this.entries.get(probe));
            }
            src.add("}");
            return String.join("\n", src) + "\n";
        }

        /**
         * @return The probe that owns the given <var>line</var> of the source of the last call of {@link
         *         #source(List)}, or {@code null}
         */
        @Nullable String
        probeAtLine(int line) { return this.lines.get(line); }

        /**
         * @return A description of the <var>probe</var>: its lines, and the declarations of the class
         */
        String
        describe(String probe) {
            StringBuilder sb = new StringBuilder(probe).append(": ").append(this.entries.get(probe));
            for (Map<String, String> m : Arrays.asList(this.inQ, this.inS, this.helpers)) {
                if (m.containsKey(probe)) sb.append("\n    ").append(m.get(probe));
            }
            sb.append("\n    P: ").append(String.join(" ", this.fieldsOfP));
            sb.append("\n    Q: ").append(String.join(" ", this.fieldsOfQ));
            if (this.qWho) sb.append(" private who()");
            if (this.qWhoP) sb.append(" whoP()");
            sb.append("\n    S: ").append(String.join(" ", this.fieldsOfS));
            return sb.toString();
        }
    }
}
