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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import util.TestUtil;

/**
 * Generates sets of overloaded methods and constructors (two to four overloads with one to three parameters of
 * primitive, wrapper, reference and array types, the last one optionally of variable arity) and invocations of
 * them with random arguments (literals, {@code null}, casts, variables of all parameter types, array creations),
 * compiles them with JANINO and with the JDK-based compiler, and compares which overload each invocation selects
 * (JLS 15.12.2: applicability by strict, loose and variable arity invocation, the most specific method), or whether
 * the invocation is rejected. The generator is deterministic (fixed seeds), so that every difference is reproducible.
 * <p>
 *   Unlike the expressions of {@link ExpressionDifferentialTest}, the generated invocations are not always valid:
 *   many have no applicable overload, or are ambiguous. The JDK-based compiler decides: the invocations that it
 *   rejects must be rejected by JANINO as well, and for the others, JANINO must select the same overload. Every
 *   overload returns its tag (e.g. {@code "m#2"}), and a variable arity overload also the length of its last
 *   argument (e.g. {@code "m#2/3"}, or {@code "m#2/null"}).
 * </p>
 * <p>
 *   The differences that are currently known are recorded in {@value #KNOWN_DIFFERENCES}{@code .txt} (the
 *   compatibility mode) and {@value #KNOWN_DIFFERENCES}{@code -compliant.txt} (the compliance mode, see {@link
 *   TestUtil#getCompilerFactoriesAndModesForParameters()}), one per line: "<var>seed</var> <var>method</var>
 *   <var>kind</var>", where <var>kind</var> is one of
 * </p>
 * <dl>
 *   <dt>{@code WRONG}</dt>
 *   <dd>JANINO selects a different overload, or passes the variable arity argument differently</dd>
 *   <dt>{@code REJECTED}</dt>
 *   <dd>JANINO rejects an invocation that the JDK-based compiler accepts</dd>
 *   <dt>{@code ACCEPTED}</dt>
 *   <dd>JANINO accepts an invocation that the JDK-based compiler rejects</dd>
 *   <dt>{@code INVALID}</dt>
 *   <dd>
 *     JANINO generates a class file that the JVM rejects (e.g. {@code VerifyError}), or fails with an internal
 *     compiler error
 *   </dd>
 * </dl>
 * <p>
 *   Like {@link ExpressionDifferentialTest}, the test fails if the actual differences deviate from the recorded ones
 *   in any way, i.e. also when a defect is fixed; then the record must be updated deliberately. The compatibility
 *   mode keeps the rules S-02 and S-13 of {@code JAVAC_DIFFERENCES.md} (variable arity methods before boxing, a
 *   variable arity method is less specific than a fixed arity one, the arguments of a variable arity method are
 *   always expanded), so its record lists the invocations that these rules decide differently than JLS 15.12.2.
 * </p>
 * <p>
 *   With the system property {@code invocation.differential.classes}, the test generates the given number of
 *   classes, prints all differences, and does not compare them with the record.
 * </p>
 */
@RunWith(Parameterized.class) public
class InvocationDifferentialTest {

    private static final String KNOWN_DIFFERENCES = "src/test/resources/invocationDifferential/known-differences";

    private static final int  CLASSES               = 60;
    private static final int  INVOCATIONS_PER_CLASS = 12;
    private static final long SEED                  = 20261011L;

    /**
     * The parameter types of the generated overloads; the last parameter of an overload may be of variable arity.
     */
    private static final String[] PARAMETER_TYPES = {
        "byte", "short", "char", "int", "long", "float", "double", "boolean",
        "Byte", "Short", "Character", "Integer", "Long", "Float", "Double", "Boolean",
        "Object", "Number", "java.io.Serializable", "CharSequence", "String",
        "int[]", "Integer[]", "Object[]", "int[][]", "String[]",
    };

    /**
     * The static fields of the generated class: one variable of each parameter type, with the names that {@link
     * #ARGUMENTS} refers to.
     */
    private static final String[] FIELDS = {
        "static byte b = 1; static short s = 1; static char c = 'c'; static int i = 1; static long j = 1L;",
        "static float f = 1.0f; static double d = 1.0; static boolean z = true;",
        "static Byte B = Byte.valueOf((byte) 1); static Short S = Short.valueOf((short) 1);",
        "static Character C = Character.valueOf('c'); static Integer I = Integer.valueOf(1);",
        "static Long J = Long.valueOf(1L); static Float F = Float.valueOf(1.0f);",
        "static Double D = Double.valueOf(1.0); static Boolean Z = Boolean.TRUE;",
        "static Object o = \"o\"; static Number n = Integer.valueOf(1); static java.io.Serializable r = \"r\";",
        "static CharSequence q = \"q\"; static String t = \"t\";",
        "static int[] ia = { 1 }; static Integer[] Ia = { I }; static Object[] oa = { o };",
        "static int[][] iaa = { ia }; static String[] ta = { t };",
    };

    /**
     * The argument expressions of the generated invocations.
     */
    private static final String[] ARGUMENTS = {
        "1", "1L", "1.0f", "1.0", "'c'", "true", "\"s\"", "null", "(byte) 1", "(short) 1", "(char) 1", "(Object) ia",
        "b", "s", "c", "i", "j", "f", "d", "z", "B", "S", "C", "I", "J", "F", "D", "Z", "o", "n", "r", "q", "t",
        "ia", "Ia", "oa", "iaa", "ta", "new int[] { 1 }", "new Integer[] { I }", "new Object[] { o }",
        "new String[] { t }",
    };

    /**
     * For each parameter type, the argument expressions that convert to it (by identity, widening, boxing or
     * unboxing). The generator prefers these, so that most invocations have an applicable overload; otherwise most
     * invocations would be rejected by both compilers, and the choice of the most specific overload would rarely be
     * exercised.
     */
    private static final Map<String, String[]> COMPATIBLE_ARGUMENTS = new HashMap<>();
    static {
        String[][] table = {
            { "byte",                 "(byte) 1", "b", "B" },
            { "short",                "(short) 1", "s", "S", "b", "(byte) 1" },
            { "char",                 "'c'", "(char) 1", "c", "C" },
            { "int",                  "1", "i", "I", "c", "s", "b", "'c'" },
            { "long",                 "1L", "j", "J", "i", "1", "I" },
            { "float",                "1.0f", "f", "F", "i", "j", "1" },
            { "double",               "1.0", "d", "D", "f", "i", "1.0f" },
            { "boolean",              "true", "z", "Z" },
            { "Byte",                 "B", "null", "b", "(byte) 1" },
            { "Short",                "S", "null", "s", "(short) 1" },
            { "Character",            "C", "null", "c", "'c'" },
            { "Integer",              "I", "null", "i", "1" },
            { "Long",                 "J", "null", "j", "1L" },
            { "Float",                "F", "null", "f", "1.0f" },
            { "Double",               "D", "null", "d", "1.0" },
            { "Boolean",              "Z", "null", "z", "true" },
            { "Object",               "o", "null", "I", "t", "ia", "1", "\"s\"", "(Object) ia", "oa" },
            { "Number",               "n", "null", "I", "J", "D", "1", "1.0", "i" },
            { "java.io.Serializable", "r", "null", "I", "t", "ia", "1", "\"s\"", "Ia" },
            { "CharSequence",         "q", "null", "t", "\"s\"" },
            { "String",               "t", "null", "\"s\"" },
            { "int[]",                "ia", "null", "new int[] { 1 }" },
            { "Integer[]",            "Ia", "null", "new Integer[] { I }" },
            { "Object[]",             "oa", "null", "Ia", "ta", "new Object[] { o }", "new String[] { t }" },
            { "int[][]",              "iaa", "null" },
            { "String[]",             "ta", "null", "new String[] { t }" },
        };
        for (String[] row : table) {
            String[] arguments = new String[row.length - 1];
            System.arraycopy(row, 1, arguments, 0, arguments.length);
            InvocationDifferentialTest.COMPATIBLE_ARGUMENTS.put(row[0], arguments);
        }
    }

    private final String mode;

    @Parameters(name = "{0}") public static List<Object[]>
    parameters() { return DifferentialTesting.modes(); }

    public
    InvocationDifferentialTest(String mode) { this.mode = mode; }

    @Test public void
    test() throws Exception {

        ICompilerFactory[] compilerFactories = DifferentialTesting.janinoAndJdk();
        ICompilerFactory   janino            = compilerFactories[0];
        ICompilerFactory   jdk               = compilerFactories[1];

        String soak    = System.getProperty("invocation.differential.classes");
        int    classes = soak == null ? InvocationDifferentialTest.CLASSES : Integer.parseInt(soak);

        Set<String>         actualDifferences = new TreeSet<>();
        Map<String, String> details           = new HashMap<>();
        int[]               valid             = new int[2]; // valid, invalid
        for (int c = 0; c < classes; c++) {

            long           seed      = InvocationDifferentialTest.SEED + c;
            GeneratedClass generated = new GeneratedClass(new Random(seed));

            for (Map.Entry<String, String> e : InvocationDifferentialTest.compare(
                janino,
                this.mode,
                jdk,
                generated,
                valid
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
                + valid[0]
                + " valid and "
                + valid[1]
                + " invalid invocations"
            );
            return;
        }

        DifferentialTesting.assertDifferences(
            DifferentialTesting.knownDifferencesFile(InvocationDifferentialTest.KNOWN_DIFFERENCES, this.mode),
            actualDifferences,
            details
        );
    }

    /**
     * Compiles the <var>generated</var> class with both compilers, and compares which overload each invocation
     * selects.
     *
     * @param counts The numbers of valid and of invalid invocations, incremented by those of this class
     * @return The differences ("<var>method</var> <var>kind</var>", mapped to a description)
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

        // The JDK-based compiler decides which invocations are valid; one compilation reports all of them.
        Map<String, String> javacErrors = InvocationDifferentialTest.javacErrors(jdk, generated);
        List<String>        valid       = new ArrayList<>();
        for (String name : generated.invocations) {
            if (!javacErrors.containsKey(name)) valid.add(name);
        }
        counts[0] += valid.size();
        counts[1] += javacErrors.size();

        // The expected results of the valid invocations, from the class that the JDK-based compiler generates.
        Map<String, String> expected = new HashMap<>();
        if (!valid.isEmpty()) {
            ClassLoader expectedCl;
            try {
                expectedCl = DifferentialTesting.compile(jdk, TestUtil.JAVAC, generated.source(valid));
            } catch (CompileException ce) {
                throw new AssertionError(
                    "JAVAC rejects the generated code: " + ce + "\n" + generated.source(valid),
                    ce
                );
            }
            for (String name : valid) {
                String value = DifferentialTesting.invokeStatic(expectedCl, "P", name);
                if (value.startsWith("?")) {
                    throw new AssertionError("The code that JAVAC generated fails: " + name + ": " + value);
                }
                expected.put(name, value);
            }
        }

        // JANINO must accept all valid invocations in one class, and select the same overloads. Only if it does not
        // accept the class, compile each invocation separately, so that the defects can be attributed.
        if (!valid.isEmpty()) {
            Map<String, String> differences = InvocationDifferentialTest.compareValid(
                janino,
                mode,
                generated,
                valid,
                expected
            );
            if (differences == null) {
                for (String name : valid) {
                    differences = InvocationDifferentialTest.compareValid(
                        janino,
                        mode,
                        generated,
                        Collections.singletonList(name),
                        expected
                    );
                    assert differences != null;
                    result.putAll(differences);
                }
            } else {
                result.putAll(differences);
            }
        }

        // JANINO must reject the invalid invocations, each compiled separately.
        for (String name : generated.invocations) {
            if (javacErrors.containsKey(name)) {
                String source = generated.source(Collections.singletonList(name));
                try {
                    ClassLoader actualCl = DifferentialTesting.compile(janino, mode, source);
                    String      actual   = DifferentialTesting.invokeStatic(actualCl, "P", name);
                    result.put(name + " ACCEPTED", "JAVAC: " + javacErrors.get(name) + "\nJANINO: " + actual);
                } catch (CompileException ce) {
                    ; // As expected.
                } catch (Exception | AssertionError e) { // E.g. "InternalCompilerException"
                    result.put(
                        name + " INVALID",
                        "JAVAC: " + javacErrors.get(name) + "\nJANINO: " + DifferentialTesting.describe(e)
                    );
                }
            }
        }

        return result;
    }

    /**
     * Compiles the class with the <var>valid</var> invocations with JANINO, and compares the results of the
     * invocations with the <var>expected</var> ones.
     *
     * @return The differences, or {@code null} iff JANINO fails to compile or to load the class and <var>valid</var>
     *         contains more than one invocation
     */
    private static Map<String, String>
    compareValid(
        ICompilerFactory    janino,
        String              mode,
        GeneratedClass      generated,
        List<String>        valid,
        Map<String, String> expected
    ) {

        Map<String, String> result = new TreeMap<>();

        ClassLoader actualCl;
        try {
            actualCl = DifferentialTesting.compile(janino, mode, generated.source(valid));
        } catch (CompileException ce) {
            if (valid.size() > 1) return null;
            result.put(valid.get(0) + " REJECTED", ce.toString());
            return result;
        } catch (Exception | AssertionError e) { // E.g. "InternalCompilerException"
            if (valid.size() > 1) return null;
            result.put(valid.get(0) + " INVALID", DifferentialTesting.describe(e));
            return result;
        }

        for (String name : valid) {
            String actual = DifferentialTesting.invokeStatic(actualCl, "P", name);
            if (actual.startsWith("?")) {
                if (valid.size() > 1) return null;
                result.put(name + " INVALID", actual);
            } else if (!actual.equals(expected.get(name))) {
                result.put(name + " WRONG", "expected <" + expected.get(name) + "> but was <" + actual + ">");
            }
        }
        return result;
    }

    /**
     * Compiles the <var>generated</var> class with all its invocations with the JDK-based compiler.
     *
     * @return The invocations that it rejects, mapped to the error messages
     */
    private static Map<String, String>
    javacErrors(ICompilerFactory jdk, GeneratedClass generated) throws Exception {

        Map<Integer, String> errorsByLine = DifferentialTesting.javacErrorsByLine(
            jdk,
            generated.source(generated.invocations)
        );

        Map<String, String> result = new HashMap<>();
        for (Map.Entry<Integer, String> e : errorsByLine.entrySet()) {
            String name = generated.invocationAtLine(e.getKey());
            if (name == null) {
                throw new AssertionError(
                    "JAVAC rejects the generated declarations: "
                    + e.getValue()
                    + "\n"
                    + generated.source(generated.invocations)
                );
            }
            result.put(name, e.getValue());
        }
        return result;
    }

    /**
     * A generated class: the overloads of the method {@code m} and of the constructor of the nested class {@code
     * Q}, and the invocation methods {@code p}<var>n</var>, each of which invokes one of them and returns the result.
     */
    static final
    class GeneratedClass {

        /** The declarations of the overloads of {@code m}, one per line. */
        final List<String> methods = new ArrayList<>();

        /** The declarations of the constructors of {@code Q}, one per line. */
        final List<String> constructors = new ArrayList<>();

        /** The names of the invocation methods ({@code p0}, {@code p1}, ...), in order. */
        final List<String> invocations = new ArrayList<>();

        /** The body of each invocation method, keyed by its name. */
        final Map<String, String> invocationBodies = new HashMap<>();

        /** The line number of each invocation method in the source of the last call of {@link #source(List)}. */
        private final Map<Integer, String> invocationLines = new HashMap<>();

        GeneratedClass(Random random) {
            List<String[]> methodOverloads      = GeneratedClass.overloads(random);
            List<String[]> constructorOverloads = GeneratedClass.overloads(random);
            for (int k = 0; k < methodOverloads.size(); k++) {
                this.methods.add(GeneratedClass.declaration("static String m", methodOverloads.get(k), "m#" + k));
            }
            for (int k = 0; k < constructorOverloads.size(); k++) {
                this.constructors.add(GeneratedClass.declaration("Q", constructorOverloads.get(k), "Q#" + k));
            }
            for (int n = 0; n < InvocationDifferentialTest.INVOCATIONS_PER_CLASS; n++) {
                String name = "p" + n;
                this.invocations.add(name);
                this.invocationBodies.put(name, (
                    random.nextInt(10) < 7
                    ? "return m(" + GeneratedClass.arguments(random, methodOverloads) + ");"
                    : "return new Q(" + GeneratedClass.arguments(random, constructorOverloads) + ").v;"
                ));
            }
        }

        /**
         * @return Two to four overloads: the parameter types of each, the last one possibly with "..."
         */
        private static List<String[]>
        overloads(Random random) {
            int            n      = 2 + random.nextInt(3);
            Set<String>    keys   = new HashSet<>();
            List<String[]> result = new ArrayList<>();
            for (int tries = 0; result.size() < n && tries < 50; tries++) {
                String[] parameterTypes = new String[1 + random.nextInt(3)];
                for (int k = 0; k < parameterTypes.length; k++) {
                    parameterTypes[k] = InvocationDifferentialTest.PARAMETER_TYPES[
                        random.nextInt(InvocationDifferentialTest.PARAMETER_TYPES.length)
                    ];
                }
                if (random.nextInt(10) < 4) parameterTypes[parameterTypes.length - 1] += "...";

                // Two overloads must not have the same erasure ("int..." and "int[]" are the same).
                if (keys.add(String.join(",", parameterTypes).replace("...", "[]"))) result.add(parameterTypes);
            }
            return result;
        }

        /**
         * @return The declaration of an overload: <var>head</var>, the parameters and a body that returns (or, for a
         *         constructor, stores in the field {@code v}) the <var>tag</var>, and for a variable arity overload
         *         also the length of the last argument
         */
        private static String
        declaration(String head, String[] parameterTypes, String tag) {
            StringBuilder sb = new StringBuilder(head).append('(');
            for (int k = 0; k < parameterTypes.length; k++) {
                if (k > 0) sb.append(", ");
                sb.append(parameterTypes[k]).append(" a").append(k);
            }
            sb.append(") { ").append(head.startsWith("static") ? "return " : "this.v = ");
            sb.append('"').append(tag).append('"');
            int last = parameterTypes.length - 1;
            if (parameterTypes[last].endsWith("...")) {
                sb.append(" + \"/\" + (a").append(last).append(" == null ? \"null\" : a").append(last);
                sb.append(".length)");
            }
            return sb.append("; }").toString();
        }

        /**
         * @return Random arguments for one of the <var>overloads</var>: usually as many as it has parameters (for a
         *         variable arity overload, zero to three for the last parameter), sometimes one more or less; each
         *         argument usually converts to the parameter (see {@link #COMPATIBLE_ARGUMENTS}), sometimes it is
         *         any argument
         */
        private static String
        arguments(Random random, List<String[]> overloads) {
            String[] target = overloads.get(random.nextInt(overloads.size()));
            int      last   = target.length - 1;
            int      count  = target.length;
            if (target[last].endsWith("...")) {
                count += random.nextInt(4) - 1;
            } else if (random.nextInt(8) == 0) {
                count += random.nextBoolean() ? 1 : -1;
            }
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < count; k++) {
                if (k > 0) sb.append(", ");
                String[] arguments = InvocationDifferentialTest.ARGUMENTS;
                if (k <= last && random.nextInt(10) < 7) {
                    String type = target[k];
                    if (type.endsWith("...")) {

                        // For the variable arity parameter: an element, or, sometimes, the array itself.
                        boolean array = k == last && count == target.length && random.nextInt(3) == 0;
                        type = type.substring(0, type.length() - 3) + (array ? "[]" : "");
                    }
                    String[] compatible = InvocationDifferentialTest.COMPATIBLE_ARGUMENTS.get(type);
                    if (compatible != null) arguments = compatible;
                } else if (k > last && random.nextInt(10) < 7) {
                    String type = target[last];
                    if (type.endsWith("...")) {
                        String[] compatible = InvocationDifferentialTest.COMPATIBLE_ARGUMENTS.get(
                            type.substring(0, type.length() - 3)
                        );
                        if (compatible != null) arguments = compatible;
                    }
                }
                sb.append(arguments[random.nextInt(arguments.length)]);
            }
            return sb.toString();
        }

        /**
         * @return The source of the class with the overloads and the given <var>invocations</var> (a subset of
         *         {@link #invocations}), each invocation method on a line of its own
         */
        String
        source(List<String> invocations) {
            this.invocationLines.clear();
            StringBuilder sb   = new StringBuilder("public class P {\n");
            int           line = 2;
            for (String f : InvocationDifferentialTest.FIELDS) {
                sb.append("    ").append(f).append('\n');
                line++;
            }
            for (String m : this.methods) {
                sb.append("    ").append(m).append('\n');
                line++;
            }
            sb.append("    static class Q { final String v;\n");
            line++;
            for (String c : this.constructors) {
                sb.append("        ").append(c).append('\n');
                line++;
            }
            sb.append("    }\n");
            line++;
            for (String name : invocations) {
                this.invocationLines.put(line, name);
                sb.append("    public static String ").append(name).append("() { ");
                sb.append(this.invocationBodies.get(name));
                sb.append(" }\n");
                line++;
            }
            return sb.append("}\n").toString();
        }

        /**
         * @return The name of the invocation method on the given <var>line</var> of the source of the last call of
         *         {@link #source(List)}, or {@code null}
         */
        String
        invocationAtLine(int line) { return this.invocationLines.get(line); }

        /**
         * @return A description of the invocation with the given <var>name</var>: its body and the overloads that it
         *         chooses from
         */
        String
        describe(String name) {
            String        body = this.invocationBodies.get(name);
            StringBuilder sb   = new StringBuilder(name).append(": ").append(body);
            for (String d : body.startsWith("return m(") ? this.methods : this.constructors) {
                sb.append("\n    ").append(d);
            }
            return sb.toString();
        }
    }
}
