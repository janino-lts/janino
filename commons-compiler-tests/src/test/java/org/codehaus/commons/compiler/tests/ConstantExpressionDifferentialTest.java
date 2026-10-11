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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
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

import de.unkrig.jdisasm.ClassFile;
import util.TestUtil;

/**
 * Generates random expressions over literals, constant variables ({@code static final} fields and {@code final}
 * local variables with constant initializers), variables (also of the wrapper types {@link Byte}, {@link Short},
 * {@link Character} and {@link Integer}), casts, conditional expressions with constant and with non-constant
 * conditions, string concatenations and the other operators of JLS 15.28, and uses each expression where the
 * language decides by whether it is a constant expression: as the initializer of a {@code static final} field, as a
 * {@code case} label, as the condition of a {@code while} statement, in a reference comparison of two evaluations of
 * the expression ({@code ==}, the interning of constant strings), and as the value of an {@link Object} (the type of
 * the expression, e.g. of a conditional expression, JLS 15.25). The classes are compiled with JANINO and with the
 * JDK-based compiler, and the results are compared (JLS 15.28: constant expressions; 5.2: the narrowing of constants
 * in assignment contexts; 13.1 and JVMS 4.7.2: the {@code ConstantValue} attribute of a constant variable; 14.11 and
 * 14.21: {@code case} labels and reachability). The generator is deterministic (fixed seeds), so that every
 * difference is reproducible.
 * <p>
 *   For each expression <var>n</var>, the generated class {@code P} has some of the following probes, each on a line
 *   of its own: fields {@code f}<var>n</var><var>type</var> (e.g. {@code f3byte}: {@code static final byte f3byte =
 *   ...}), the method {@code o}<var>n</var> (returns the expression as an {@link Object}, described with its
 *   class), {@code sw}<var>n</var> (the expression as a {@code case} label), {@code wh}<var>n</var> (as the condition
 *   of a {@code while} statement) and {@code id}<var>n</var> (the reference comparison). Like in {@link
 *   InvocationDifferentialTest}, the JDK-based compiler decides which probes are valid: the probes that it rejects
 *   (e.g. a {@code byte} field whose initializer is not a constant expression) must be rejected by JANINO as well.
 *   For a valid field probe, the {@code ConstantValue} attribute in the class file (present or not, and its value)
 *   and the value of the field are compared; for a method probe, its result.
 * </p>
 * <p>
 *   The generator avoids the constructs that JANINO is known not to fold (see {@link Defect}). Other differences
 *   that are currently known are recorded in {@value #KNOWN_DIFFERENCES}{@code .txt} (the compatibility mode) and
 *   {@value #KNOWN_DIFFERENCES}{@code -compliant.txt} (the compliance mode, see {@link
 *   TestUtil#getCompilerFactoriesAndModesForParameters()}), one per line: "<var>seed</var> <var>probe</var>
 *   <var>kind</var>", where <var>kind</var> is {@code WRONG} (a different value or result), {@code FOLDING} (the
 *   same value, but the {@code ConstantValue} attribute differs: JANINO does not treat the field as a constant
 *   variable, or treats it as one where {@code javac} does not), {@code REJECTED} (JANINO rejects a valid probe),
 *   {@code ACCEPTED} (JANINO accepts an invalid probe) or {@code INVALID} (a class file that the JVM rejects, or an
 *   internal compiler error). The test fails if the actual differences deviate from the recorded ones in any way.
 * </p>
 * <p>
 *   With the system property {@code constantExpression.differential.classes}, the test generates the given number
 *   of classes, prints all differences, and does not compare them with the record. The system property {@code
 *   constantExpression.differential.avoid} lists the defects whose constructs the generator avoids (comma-separated,
 *   by default all), e.g. to verify a fix.
 * </p>
 */
@RunWith(Parameterized.class) public
class ConstantExpressionDifferentialTest {

    private static final String
    KNOWN_DIFFERENCES = "src/test/resources/constantExpressionDifferential/known-differences";

    private static final int  CLASSES               = 50;
    private static final int  EXPRESSIONS_PER_CLASS = 12;
    private static final long SEED                  = 20261013L;

    /** The defects of JANINO whose constructs the generator avoids by default. */
    enum Defect {

        /**
         * JANINO does not fold shifts, relational operators, {@code ~}, operations with {@code char} operands, casts
         * to and from {@code char}, {@code &}, {@code |} and {@code ^} with {@code boolean} operands, and casts to
         * {@link String} (S-05 of {@code JAVAC_DIFFERENCES.md}, both modes). The generator avoids the constant
         * operands of such operations.
         */
        S05,

        /**
         * JANINO does not treat a {@code final} local variable with a constant initializer as a constant variable
         * (JLS 4.12.4), so e.g. {@code case k:} with {@code final int k = 3;} is rejected (both modes). The generator
         * avoids such variables.
         */
        LOCAL_CONSTANTS,
    }

    private final String mode;

    @Parameters(name = "{0}") public static List<Object[]>
    parameters() { return DifferentialTesting.modes(); }

    public
    ConstantExpressionDifferentialTest(String mode) { this.mode = mode; }

    @Test public void
    test() throws Exception {

        ICompilerFactory[] compilerFactories = DifferentialTesting.janinoAndJdk();
        ICompilerFactory   janino            = compilerFactories[0];
        ICompilerFactory   jdk               = compilerFactories[1];

        String soak    = System.getProperty("constantExpression.differential.classes");
        int    classes = soak == null ? ConstantExpressionDifferentialTest.CLASSES : Integer.parseInt(soak);

        Set<Defect> avoided = EnumSet.allOf(Defect.class);
        String      avoid   = System.getProperty("constantExpression.differential.avoid");
        if (avoid != null) {
            avoided.clear();
            for (String name : avoid.split(",")) {
                if (!name.trim().isEmpty()) avoided.add(Defect.valueOf(name.trim()));
            }
        }

        Set<String>         actualDifferences = new TreeSet<>();
        Map<String, String> details           = new HashMap<>();
        int[]               counts            = new int[2]; // valid, invalid
        for (int c = 0; c < classes; c++) {

            long           seed      = ConstantExpressionDifferentialTest.SEED + c;
            GeneratedClass generated = new GeneratedClass(new Random(seed), avoided);

            for (Map.Entry<String, String> e : ConstantExpressionDifferentialTest.compare(
                janino,
                this.mode,
                jdk,
                generated,
                counts
            ).entrySet()) {
                String difference = seed + " " + e.getKey();
                actualDifferences.add(difference);
                details.put(difference, e.getValue() + "\n" + generated.describe(e.getKey().split(" ")[0]));
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
            DifferentialTesting.knownDifferencesFile(ConstantExpressionDifferentialTest.KNOWN_DIFFERENCES, this.mode),
            actualDifferences,
            details
        );
    }

    /**
     * Compiles the <var>generated</var> class with both compilers, and compares the results of its probes.
     *
     * @param counts The numbers of valid and of invalid probes, incremented by those of this class
     * @return The differences ("<var>probe</var> <var>kind</var>", mapped to a description)
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

        // The JDK-based compiler decides which probes are valid; one compilation reports all errors of one phase,
        // but e.g. "missing return statement" (the flow analysis) is only reported if the attribution of the whole
        // class succeeded, so the probes that remain are compiled again until no error is reported.
        Map<String, String> javacErrors = new HashMap<>();
        List<String>        valid       = new ArrayList<>(generated.probes);
        for (;;) {
            Map<Integer, String> errors = DifferentialTesting.javacErrorsByLine(jdk, generated.source(valid));
            if (errors.isEmpty()) break;
            for (Map.Entry<Integer, String> e : errors.entrySet()) {
                String probe = generated.probeAtLine(e.getKey());
                if (probe == null) {
                    throw new AssertionError(
                        "JAVAC rejects the generated declarations: " + e.getValue() + "\n" + generated.source(valid)
                    );
                }
                if (!javacErrors.containsKey(probe)) javacErrors.put(probe, e.getValue());
                valid.remove(probe);
            }
        }
        counts[0] += valid.size();
        counts[1] += javacErrors.size();

        // The expected results of the valid probes.
        if (!valid.isEmpty()) {
            String              source = generated.source(valid);
            Map<String, byte[]> classFiles;
            try {
                classFiles = DifferentialTesting.compileToClassFiles(jdk, TestUtil.JAVAC, source);
            } catch (CompileException ce) {
                throw new AssertionError("JAVAC rejects the generated code: " + ce + "\n" + source, ce);
            }
            Map<String, String> expected = ConstantExpressionDifferentialTest.results(classFiles, valid);
            for (String probe : valid) {
                if (expected.get(probe).startsWith("?")) {
                    throw new AssertionError(
                        "The code that JAVAC generated fails: " + probe + ": " + expected.get(probe)
                    );
                }
            }

            // JANINO must accept all valid probes in one class, and compute the same results. Only if it does not
            // accept the class, compile each probe separately, so that the defects can be attributed.
            Map<String, String> differences = ConstantExpressionDifferentialTest.compareValid(
                janino,
                mode,
                generated,
                valid,
                expected
            );
            if (differences == null) {
                for (String probe : valid) {
                    differences = ConstantExpressionDifferentialTest.compareValid(
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
                    Map<String, byte[]> classFiles = DifferentialTesting.compileToClassFiles(janino, mode, source);
                    String              actual     = ConstantExpressionDifferentialTest.results(
                        classFiles,
                        Collections.singletonList(probe)
                    ).get(probe);
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
     * Compiles the class with the <var>valid</var> probes with JANINO, and compares the results of the probes with
     * the <var>expected</var> ones.
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

        Map<String, byte[]> classFiles;
        try {
            classFiles = DifferentialTesting.compileToClassFiles(janino, mode, source);
        } catch (CompileException ce) {
            if (valid.size() > 1) return null;
            result.put(valid.get(0) + " REJECTED", ce.toString());
            return result;
        } catch (Exception | AssertionError e) { // E.g. "InternalCompilerException"
            if (valid.size() > 1) return null;
            result.put(valid.get(0) + " INVALID", DifferentialTesting.describe(e));
            return result;
        }

        Map<String, String> actual = ConstantExpressionDifferentialTest.results(classFiles, valid);
        for (String probe : valid) {
            String a = actual.get(probe);
            String e = expected.get(probe);
            if (a.startsWith("?")) {
                if (valid.size() > 1) return null;
                result.put(probe + " INVALID", a);
            } else if (!a.equals(e)) {
                String kind = (
                    ConstantExpressionDifferentialTest.valueOf(a).equals(ConstantExpressionDifferentialTest.valueOf(e))
                    ? " FOLDING"
                    : " WRONG"
                );
                result.put(probe + kind, "expected <" + e + "> but was <" + a + ">");
            }
        }
        return result;
    }

    /**
     * @return The results of the <var>probes</var> of the class {@code P} in the given class files: for a field
     *         probe, its {@code ConstantValue} attribute (or "none") and its value; for a method probe, the result
     *         of the method (see {@link DifferentialTesting#invokeStatic(ClassLoader, String, String)})
     */
    private static Map<String, String>
    results(Map<String, byte[]> classFiles, List<String> probes) {

        Map<String, String> result = new HashMap<>();
        ClassLoader         cl     = DifferentialTesting.classLoader(classFiles);
        for (String probe : probes) {
            if (probe.startsWith("f")) {
                String value;
                try {
                    Field f = cl.loadClass("P").getDeclaredField(probe);
                    f.setAccessible(true);
                    value = ConstantExpressionDifferentialTest.describeValue(f.get(null));
                } catch (Throwable t) {
                    result.put(probe, "?" + t);
                    continue;
                }
                result.put(
                    probe,
                    (
                        "ConstantValue "
                        + ConstantExpressionDifferentialTest.constantValue(classFiles.get("P.class"), probe)
                        + "; value "
                        + value
                    )
                );
            } else {
                result.put(probe, DifferentialTesting.invokeStatic(cl, "P", probe));
            }
        }
        return result;
    }

    /**
     * @return The value of the {@code ConstantValue} attribute of the given field of the given class file, or "none"
     */
    private static String
    constantValue(byte[] classFile, String fieldName) {
        try {
            ClassFile cf = new ClassFile(new DataInputStream(new ByteArrayInputStream(classFile)));
            for (ClassFile.Field f : cf.fields) {
                if (f.name.equals(fieldName)) {
                    return f.constantValueAttribute == null ? "none" : f.constantValueAttribute.constantValue;
                }
            }
            return "no such field";
        } catch (Exception e) {
            return "?" + e;
        }
    }

    /** @return The "value" part of the result of a field probe, or the whole result of a method probe */
    private static String
    valueOf(String result) {
        int idx = result.indexOf("; value ");
        return idx == -1 ? result : result.substring(idx);
    }

    /** @return The class and the string value of the object, e.g. "Integer:7"; "null" for {@code null} */
    static String
    describeValue(@Nullable Object o) { return o == null ? "null" : o.getClass().getSimpleName() + ":" + o; }

    /** The kinds of the generated expressions. */
    enum Kind { NUM, BOOL, STR }

    /** A generated expression. */
    static final
    class Expr {

        final String  text;
        final Kind    kind;

        /** Whether the expression is a constant expression according to JLS 15.28. */
        final boolean constant;

        /** Whether the expression refers to a local variable of the probe methods. */
        final boolean usesLocals;

        Expr(String text, Kind kind, boolean constant, boolean usesLocals) {
            this.text       = text;
            this.kind       = kind;
            this.constant   = constant;
            this.usesLocals = usesLocals;
        }
    }

    /**
     * A generated class {@code P}: its constant and non-constant fields, the local variables of the probe methods,
     * the expressions and the probes.
     */
    static final
    class GeneratedClass {

        private static final int[]    VALUES  = {
            0, 1, 2, 5, 7, 100, 127, 128, 200, 255, 256, 1000, 65535, 65536, -1, -128, -129
        };
        private static final String[] CHARS   = { "a", "b", "c", "z", "0", " " };
        private static final String[] STRINGS = { "a", "b", "ab", "x", "" };

        /** The field types that a numeric expression may be assigned to. */
        private static final String[] NUMERIC_FIELD_TYPES = {
            "int", "long", "short", "byte", "char", "Integer", "Short", "Byte", "Character"
        };

        /** The names of the probes, in order. */
        final List<String> probes = new ArrayList<>();

        private final Random       random;
        private final Set<Defect>  avoided;
        private final List<String> declarations = new ArrayList<>(); // The constant and non-constant fields.
        private final String       locals;                           // The local variables of the probe methods.

        private final List<Expr>          expressions = new ArrayList<>();
        private final Map<String, String> fieldProbes = new HashMap<>();  // name -> declaration
        private final Map<String, String> methodProbes = new HashMap<>(); // name -> declaration
        private final Map<String, Integer> expressionOfProbe = new HashMap<>();

        /** The line number of each probe's line in the source of the last call of {@link #source(List)}. */
        private final Map<Integer, String> lines = new HashMap<>();

        GeneratedClass(Random random, Set<Defect> avoided) {
            this.random  = random;
            this.avoided = avoided;

            this.declarations.add(
                "static final int I = " + this.intLiteral() + "; static final long L = " + this.longLiteral() + ";"
                + " static final short S = (short) " + this.intLiteral() + "; static final byte B = (byte) "
                + this.intLiteral() + ";"
            );
            this.declarations.add(
                "static final char C = " + this.charLiteral() + "; static final String T = " + this.stringLiteral()
                + "; static final boolean Z = " + random.nextBoolean() + ";"
            );
            this.declarations.add(
                "static int i = " + this.intLiteral() + "; static long l = " + this.longLiteral() + ";"
                + " static short s = (short) " + this.intLiteral() + "; static byte b = (byte) " + this.intLiteral()
                + "; static char c = " + this.charLiteral() + ";"
            );
            this.declarations.add(
                "static String t = " + this.stringLiteral() + "; static boolean z = " + random.nextBoolean() + ";"
            );
            this.declarations.add(
                "static Integer II = " + this.intLiteral() + "; static Short SS = (short) " + this.intLiteral() + ";"
                + " static Byte BB = (byte) " + this.intLiteral() + "; static Character CC = " + this.charLiteral()
                + ";"
            );
            this.declarations.add(
                "static String r(Object o) { return o == null ? \"null\" : o.getClass().getSimpleName() + \":\" + o; }"
            );
            this.locals = (
                "final int k = " + this.intLiteral() + "; final char q = " + this.charLiteral()
                + "; final boolean y = " + random.nextBoolean() + "; final String u = " + this.stringLiteral()
                + "; int v = " + this.intLiteral() + "; String w = " + this.stringLiteral() + ";"
            );

            for (int n = 0; n < ConstantExpressionDifferentialTest.EXPRESSIONS_PER_CLASS; n++) {
                Expr e;
                switch (random.nextInt(5)) {
                case 0:  e = this.bool(3); break;
                case 1:  e = this.str(3);  break;
                default: e = this.num(3);  break;
                }
                this.expressions.add(e);
                this.generateProbes(n, e);
            }
        }

        /**
         * Generates the probes of the expression <var>e</var>: the {@code static final} fields (if the expression
         * does not use local variables), the method that returns it as an {@link Object}, and the method that uses
         * it as a {@code case} label, as the condition of a {@code while} statement, or in a reference comparison.
         */
        private void
        generateProbes(int n, Expr e) {

            if (!e.usesLocals) {
                List<String> types = new ArrayList<>();
                switch (e.kind) {
                case NUM:
                    List<String> all = new ArrayList<>(Arrays.asList(GeneratedClass.NUMERIC_FIELD_TYPES));
                    Collections.shuffle(all, this.random);
                    types.addAll(all.subList(0, 4));
                    break;
                case BOOL:
                    types.add("boolean");
                    if (this.random.nextBoolean()) types.add("Boolean");
                    break;
                default:
                    types.add("String");
                    break;
                }
                for (String type : types) {
                    this.addProbe(n, "f" + n + type, "static final " + type + " f" + n + type + " = " + e.text + ";");
                }
            }

            this.addProbe(n, "o" + n, (
                "public static String o" + n + "() { " + this.locals + " return r(" + e.text + "); }"
            ));

            switch (e.kind) {

            case NUM:
                {
                    String selector = new String[] { "i", "i", "i", "s", "b", "c" }[this.random.nextInt(6)];
                    this.addProbe(n, "sw" + n, (
                        "public static int sw" + n + "() { " + this.locals + " switch (" + selector + ") { case "
                        + e.text + ": return 1; default: return 0; } }"
                    ));
                }
                break;

            case BOOL:
                this.addProbe(n, "wh" + n, (
                    "public static int wh" + n + "() { " + this.locals + " while (" + e.text + ") { return 1; } }"
                ));
                break;

            default:
                this.addProbe(n, "sw" + n, (
                    "public static int sw" + n + "() { " + this.locals + " switch (t) { case " + e.text
                    + ": return 1; default: return 0; } }"
                ));
                this.addProbe(n, "id" + n, (
                    "public static boolean id" + n + "() { " + this.locals + " return (" + e.text + ") == (" + e.text
                    + "); }"
                ));
                break;
            }
        }

        private void
        addProbe(int n, String name, String declaration) {
            this.probes.add(name);
            this.expressionOfProbe.put(name, n);
            if (name.startsWith("f")) {
                this.fieldProbes.put(name, declaration);
            } else {
                this.methodProbes.put(name, declaration);
            }
        }

        private String
        intLiteral() {
            int v = GeneratedClass.VALUES[this.random.nextInt(GeneratedClass.VALUES.length)];
            return v < 0 ? "(" + v + ")" : String.valueOf(v);
        }

        private String
        longLiteral() {
            int v = GeneratedClass.VALUES[this.random.nextInt(GeneratedClass.VALUES.length)];
            return v < 0 ? "(" + v + "L)" : v + "L";
        }

        private String
        charLiteral() { return "'" + GeneratedClass.CHARS[this.random.nextInt(GeneratedClass.CHARS.length)] + "'"; }

        private String
        stringLiteral() {
            return "\"" + GeneratedClass.STRINGS[this.random.nextInt(GeneratedClass.STRINGS.length)] + "\"";
        }

        private boolean
        avoids(Defect defect) { return this.avoided.contains(defect); }

        /** @return A constant expression of type {@code char}: a literal, the field {@code C} or the local {@code q} */
        private Expr
        charConstant() {
            switch (this.random.nextInt(3)) {
            case 0:  return new Expr(this.charLiteral(), Kind.NUM, true, false);
            case 1:  return new Expr("C", Kind.NUM, true, false);
            default: return this.avoids(Defect.LOCAL_CONSTANTS) ? new Expr("C", Kind.NUM, true, false) : new Expr(
                "q", Kind.NUM, true, true
            );
            }
        }

        /** @return A non-constant local variable of the given <var>kind</var>, or a literal for {@link Kind#BOOL} */
        private Expr
        localVariable(Kind kind) {
            switch (kind) {
            case NUM:  return new Expr("v", Kind.NUM, false, true);
            case STR:  return new Expr("w", Kind.STR, false, true);
            default:   return new Expr(String.valueOf(this.random.nextBoolean()), Kind.BOOL, true, false);
            }
        }

        /**
         * @return A random numeric expression (of type {@code int}, {@code long}, {@code short}, {@code byte} or
         *         {@code char}, or of a wrapper type)
         */
        private Expr
        num(int depth) {

            if (depth == 0 || this.random.nextInt(10) < 3) {
                switch (this.random.nextInt(13)) {
                case 0:  return new Expr(this.intLiteral(), Kind.NUM, true, false);
                case 1:  return new Expr(this.longLiteral(), Kind.NUM, true, false);
                case 2:  return new Expr("(short) " + this.intLiteral(), Kind.NUM, true, false);
                case 3:  return new Expr("(byte) " + this.intLiteral(), Kind.NUM, true, false);
                case 4:  return new Expr("I", Kind.NUM, true, false);
                case 5:  return new Expr("S", Kind.NUM, true, false);
                case 6:  return new Expr("B", Kind.NUM, true, false);
                case 7:  return this.avoids(Defect.LOCAL_CONSTANTS) ? this.localVariable(Kind.NUM) : new Expr(
                    "k", Kind.NUM, true, true
                );
                case 8:  return new Expr(
                    new String[] { "i", "l", "s", "b", "c" }[this.random.nextInt(5)], Kind.NUM, false, false
                );
                case 9:  return new Expr(
                    new String[] { "II", "SS", "BB", "CC" }[this.random.nextInt(4)], Kind.NUM, false, false
                );
                case 10: return new Expr("v", Kind.NUM, false, true);
                case 11: return new Expr("L", Kind.NUM, true, false);
                default: return this.avoids(Defect.S05) ? new Expr("0", Kind.NUM, true, false) : this.charConstant();
                }
            }

            switch (this.random.nextInt(this.avoids(Defect.S05) ? 6 : 8)) {

            case 0:
            case 1:
            case 2:
                {
                    Expr   lhs = this.num(depth - 1);
                    Expr   rhs = this.num(depth - 1);
                    String op  = new String[] { "+", "-", "*", "&", "|", "^" }[this.random.nextInt(6)];
                    return new Expr(
                        "(" + lhs.text + " " + op + " " + rhs.text + ")",
                        Kind.NUM,
                        lhs.constant && rhs.constant,
                        lhs.usesLocals || rhs.usesLocals
                    );
                }

            case 3:
                {
                    Expr   operand = this.num(depth - 1);
                    String type    = new String[] { "int", "long", "short", "byte", "char" }[this.random.nextInt(
                        this.avoids(Defect.S05) && operand.constant ? 4 : 5
                    )];
                    return new Expr("(" + type + ") " + operand.text, Kind.NUM, operand.constant, operand.usesLocals);
                }

            case 4:
                {
                    Expr operand = this.num(depth - 1);
                    return new Expr("(-" + operand.text + ")", Kind.NUM, operand.constant, operand.usesLocals);
                }

            case 5:
                return this.conditional(depth, Kind.NUM);

            case 6:
                {
                    Expr   lhs = this.num(depth - 1);
                    Expr   rhs = this.num(depth - 1);
                    String op  = new String[] { "<<", ">>", ">>>" }[this.random.nextInt(3)];
                    return new Expr(
                        "(" + lhs.text + " " + op + " " + rhs.text + ")",
                        Kind.NUM,
                        lhs.constant && rhs.constant,
                        lhs.usesLocals || rhs.usesLocals
                    );
                }

            default:
                {
                    Expr operand = this.num(depth - 1);
                    return new Expr("(~" + operand.text + ")", Kind.NUM, operand.constant, operand.usesLocals);
                }
            }
        }

        /**
         * @return A random expression of type {@code boolean} or {@link Boolean}
         */
        private Expr
        bool(int depth) {

            if (depth == 0 || this.random.nextInt(10) < 4) {
                switch (this.random.nextInt(5)) {
                case 0:  return new Expr("true", Kind.BOOL, true, false);
                case 1:  return new Expr("false", Kind.BOOL, true, false);
                case 2:  return new Expr("Z", Kind.BOOL, true, false);
                case 3:  return this.avoids(Defect.LOCAL_CONSTANTS) ? this.localVariable(Kind.BOOL) : new Expr(
                    "y", Kind.BOOL, true, true
                );
                default: return new Expr("z", Kind.BOOL, false, false);
                }
            }

            switch (this.random.nextInt(4)) {

            case 0:
                {
                    Expr operand = this.bool(depth - 1);
                    return new Expr("(!" + operand.text + ")", Kind.BOOL, operand.constant, operand.usesLocals);
                }

            case 1:
                {
                    Expr   lhs = this.bool(depth - 1);
                    Expr   rhs = this.bool(depth - 1);
                    String op  = new String[] { "&&", "||", "&", "|", "^" }[this.random.nextInt(
                        this.avoids(Defect.S05) && (lhs.constant || rhs.constant) ? 2 : 5
                    )];
                    return new Expr(
                        "(" + lhs.text + " " + op + " " + rhs.text + ")",
                        Kind.BOOL,
                        lhs.constant && rhs.constant,
                        lhs.usesLocals || rhs.usesLocals
                    );
                }

            case 2:
                return this.conditional(depth, Kind.BOOL);

            default:
                {
                    Expr   lhs = this.num(depth - 1);
                    Expr   rhs = this.num(depth - 1);
                    String op  = new String[] { "==", "!=", "<", "<=" }[this.random.nextInt(
                        this.avoids(Defect.S05) && (lhs.constant || rhs.constant) ? 2 : 4
                    )];
                    return new Expr(
                        "(" + lhs.text + " " + op + " " + rhs.text + ")",
                        Kind.BOOL,
                        lhs.constant && rhs.constant,
                        lhs.usesLocals || rhs.usesLocals
                    );
                }
            }
        }

        /**
         * @return A random expression of type {@link String}
         */
        private Expr
        str(int depth) {

            if (depth == 0 || this.random.nextInt(10) < 3) {
                switch (this.random.nextInt(7)) {
                case 0:
                case 1:  return new Expr(this.stringLiteral(), Kind.STR, true, false);
                case 2:  return new Expr("T", Kind.STR, true, false);
                case 3:  return this.avoids(Defect.LOCAL_CONSTANTS) ? this.localVariable(Kind.STR) : new Expr(
                    "u", Kind.STR, true, true
                );
                case 4:  return new Expr("t", Kind.STR, false, false);
                case 5:  return new Expr("w", Kind.STR, false, true);
                default: return new Expr("(String) null", Kind.STR, false, false);
                }
            }

            switch (this.random.nextInt(5)) {

            case 0:
            case 1:
                {
                    Expr lhs = this.str(depth - 1);
                    Expr rhs = this.str(depth - 1);
                    return new Expr(
                        "(" + lhs.text + " + " + rhs.text + ")",
                        Kind.STR,
                        lhs.constant && rhs.constant,
                        lhs.usesLocals || rhs.usesLocals
                    );
                }

            case 2:
                {
                    Expr    s     = this.str(depth - 1);
                    Expr    other = this.random.nextInt(4) == 0 ? this.bool(depth - 1) : this.num(depth - 1);
                    boolean first = this.random.nextBoolean();
                    return new Expr(
                        "(" + (first ? s.text + " + " + other.text : other.text + " + " + s.text) + ")",
                        Kind.STR,
                        s.constant && other.constant,
                        s.usesLocals || other.usesLocals
                    );
                }

            case 3:
                {
                    Expr operand = this.str(depth - 1);
                    if (this.avoids(Defect.S05) && operand.constant) return operand;
                    return new Expr("(String) " + operand.text, Kind.STR, operand.constant, operand.usesLocals);
                }

            default:
                return this.conditional(depth, Kind.STR);
            }
        }

        /**
         * @return A conditional expression with operands of the given <var>kind</var>; its condition is constant with
         *         a high probability
         */
        private Expr
        conditional(int depth, Kind kind) {

            Expr condition;
            switch (this.random.nextInt(8)) {
            case 0:  condition = new Expr("true", Kind.BOOL, true, false);  break;
            case 1:  condition = new Expr("false", Kind.BOOL, true, false); break;
            case 2:  condition = new Expr("Z", Kind.BOOL, true, false);     break;
            case 3:  condition = new Expr("(!Z)", Kind.BOOL, true, false);  break;
            case 4:  condition = this.avoids(Defect.LOCAL_CONSTANTS) ? this.localVariable(Kind.BOOL) : new Expr(
                "y", Kind.BOOL, true, true
            );
            break;
            case 5:  condition = new Expr("z", Kind.BOOL, false, false);    break;
            default: condition = this.bool(depth - 1);                      break;
            }

            Expr lhs, rhs;
            switch (kind) {
            case NUM:  lhs = this.num(depth - 1);  rhs = this.num(depth - 1);  break;
            case BOOL: lhs = this.bool(depth - 1); rhs = this.bool(depth - 1); break;
            default:   lhs = this.str(depth - 1);  rhs = this.str(depth - 1);  break;
            }

            return new Expr(
                "(" + condition.text + " ? " + lhs.text + " : " + rhs.text + ")",
                kind,
                condition.constant && lhs.constant && rhs.constant,
                condition.usesLocals || lhs.usesLocals || rhs.usesLocals
            );
        }

        /**
         * @return The source of the class with the given <var>probes</var> (a subset of {@link #probes}), each probe
         *         on a line of its own
         */
        String
        source(List<String> probes) {
            this.lines.clear();
            List<String> src = new ArrayList<>();
            src.add("public class P {");
            for (String d : this.declarations) src.add("    " + d);
            for (String probe : probes) {
                if (this.fieldProbes.containsKey(probe)) {
                    this.lines.put(src.size() + 1, probe);
                    src.add("    " + this.fieldProbes.get(probe));
                }
            }
            for (String probe : probes) {
                if (this.methodProbes.containsKey(probe)) {
                    this.lines.put(src.size() + 1, probe);
                    src.add("    " + this.methodProbes.get(probe));
                }
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
         * @return A description of the <var>probe</var>: its line, whether its expression is a constant expression,
         *         and the declarations of the class
         */
        String
        describe(String probe) {
            Expr          e  = this.expressions.get(this.expressionOfProbe.get(probe));
            StringBuilder sb = new StringBuilder(probe).append(": ").append(
                this.fieldProbes.containsKey(probe) ? this.fieldProbes.get(probe) : this.methodProbes.get(probe)
            );
            sb.append("\n    ").append(e.constant ? "a constant expression" : "not a constant expression");
            for (String d : this.declarations.subList(0, 5)) sb.append("\n    ").append(d);
            if (e.usesLocals) sb.append("\n    ").append(this.locals);
            return sb.toString();
        }
    }
}
