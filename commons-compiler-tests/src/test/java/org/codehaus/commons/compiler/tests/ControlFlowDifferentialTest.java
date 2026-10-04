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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.CompilerFactoryFactory;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.codehaus.commons.compiler.ISimpleCompiler;
import org.codehaus.commons.compiler.InternalCompilerException;
import org.codehaus.commons.nullanalysis.Nullable;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * Generates methods with nested control flow statements ({@code if}, loops, {@code switch}, {@code try}/{@code
 * catch}/{@code finally}, {@code synchronized}, labeled blocks, and {@code break}, {@code continue}, {@code return}
 * and {@code throw}), compiles them with JANINO and with the JDK-based compiler, and compares the results. The
 * generator is deterministic (fixed seeds), so that every difference is reproducible.
 * <p>
 *   The differences that are currently known are recorded in {@value #KNOWN_DIFFERENCES}, one per line: "<var>seed
 *   </var> <var>method</var> <var>kind</var>", where <var>kind</var> is one of
 * </p>
 * <dl>
 *   <dt>{@code WRONG}</dt>
 *   <dd>JANINO's code produces a different result</dd>
 *   <dt>{@code INVALID}</dt>
 *   <dd>
 *     JANINO generates a class file that the JVM rejects (e.g. {@code VerifyError}), or fails with an internal
 *     compiler error
 *   </dd>
 *   <dt>{@code REJECTED}</dt>
 *   <dd>JANINO reports a compile error</dd>
 * </dl>
 * <p>
 *   Like {@link LanguageSupportTest}, the test fails if the actual differences deviate from the recorded ones in any
 *   way, i.e. also when a defect is fixed; then the record must be updated deliberately.
 * </p>
 * <p>
 *   Try-with-resources statements are not generated yet, because JANINO does not close the resources when the block
 *   completes by {@code return}, {@code break} or {@code continue}.
 * </p>
 */
public
class ControlFlowDifferentialTest {

    private static final String KNOWN_DIFFERENCES = "src/test/resources/controlFlowDifferential/known-differences.txt";

    private static final int   CLASSES           = 30;
    private static final int   METHODS_PER_CLASS = 20;
    private static final long  SEED              = 20261004L;
    private static final int[] ARGUMENTS         = { 0, 1, 2, 5 };

    @Test public void
    test() throws Exception {

        ICompilerFactory janino = null, jdk = null;
        for (ICompilerFactory cf : CompilerFactoryFactory.getAllCompilerFactories(
            ControlFlowDifferentialTest.class.getClassLoader()
        )) {
            if ("org.codehaus.janino".equals(cf.getId()))               janino = cf;
            if ("org.codehaus.commons.compiler.jdk".equals(cf.getId())) jdk    = cf;
        }
        Assume.assumeTrue("Both compilers must be available", janino != null && jdk != null);
        assert janino != null && jdk != null;

        Set<String>         actualDifferences = new TreeSet<>();
        Map<String, String> details           = new HashMap<>();
        for (int c = 0; c < ControlFlowDifferentialTest.CLASSES; c++) {

            long seed = ControlFlowDifferentialTest.SEED + c;

            List<String> methods = new ArrayList<>();
            Random       random  = new Random(seed);
            for (int m = 0; m < ControlFlowDifferentialTest.METHODS_PER_CLASS; m++) {
                methods.add(new Generator(random).method("m" + m));
            }

            // Compile all methods in one class; only if JANINO fails to compile or load that class, compile each
            // method separately, so that the defects can be attributed to the methods.
            Map<String, String> classDifferences = ControlFlowDifferentialTest.compare(janino, jdk, methods, -1);
            if (classDifferences == null) {
                classDifferences = new HashMap<>();
                for (int m = 0; m < methods.size(); m++) {
                    Map<String, String> methodDifferences = ControlFlowDifferentialTest.compare(janino, jdk, methods, m);
                    if (methodDifferences == null) {
                        methodDifferences = Collections.singletonMap("m" + m + " INVALID", "Cannot load the class");
                    }
                    classDifferences.putAll(methodDifferences);
                }
            }

            for (Map.Entry<String, String> e : classDifferences.entrySet()) {
                String difference = seed + " " + e.getKey();
                actualDifferences.add(difference);
                int m = Integer.parseInt(e.getKey().substring(1, e.getKey().indexOf(' ')));
                details.put(difference, e.getValue() + "\n" + methods.get(m));
            }
        }

        Set<String> knownDifferences = ControlFlowDifferentialTest.readKnownDifferences();
        if (actualDifferences.equals(knownDifferences)) return;

        StringBuilder sb = new StringBuilder("The differences between JANINO and JAVAC have changed.");
        for (String d : actualDifferences) {
            if (!knownDifferences.contains(d)) sb.append("\nNew difference: ").append(d).append('\n').append(details.get(d));
        }
        for (String d : knownDifferences) {
            if (!actualDifferences.contains(d)) sb.append("\nNo longer a difference: ").append(d);
        }
        sb.append("\n\nAll current differences:");
        for (String d : actualDifferences) sb.append('\n').append(d);
        Assert.fail(sb.toString());
    }

    /**
     * Compiles the <var>methods</var> (or only the method with index <var>onlyMethod</var>, if not -1) in one class
     * with both compilers, and compares the results.
     *
     * @return The differences ("m<var>index</var> <var>kind</var>", mapped to a description), or {@code null} iff
     *         JANINO fails to compile or to load the class
     */
    @Nullable private static Map<String, String>
    compare(ICompilerFactory janino, ICompilerFactory jdk, List<String> methods, int onlyMethod) throws Exception {

        StringBuilder source = new StringBuilder("public class P {\n    public static StringBuilder t;\n");
        for (int m = 0; m < methods.size(); m++) {
            if (onlyMethod == -1 || m == onlyMethod) source.append(methods.get(m));
        }
        source.append("}\n");

        // The JDK-based compiler is the reference; the generator must produce valid code.
        ClassLoader expectedCl = ControlFlowDifferentialTest.compile(jdk, source.toString());

        ClassLoader actualCl;
        try {
            actualCl = ControlFlowDifferentialTest.compile(janino, source.toString());
        } catch (CompileException ce) {
            if (onlyMethod == -1) return null;
            return Collections.singletonMap("m" + onlyMethod + " REJECTED", ce.toString());
        } catch (InternalCompilerException ice) {
            if (onlyMethod == -1) return null;
            return Collections.singletonMap("m" + onlyMethod + " INVALID", ice.toString());
        }

        Map<String, String> result = new HashMap<>();
        for (int m = 0; m < methods.size(); m++) {
            if (onlyMethod != -1 && m != onlyMethod) continue;
            for (int a : ControlFlowDifferentialTest.ARGUMENTS) {
                String expected = ControlFlowDifferentialTest.invoke(expectedCl, "m" + m, a);
                String actual   = ControlFlowDifferentialTest.invoke(actualCl, "m" + m, a);
                if (actual.startsWith("?")) return null;
                if (!expected.equals(actual)) {
                    result.put("m" + m + " WRONG", "m" + m + "(" + a + "): expected <" + expected + "> but was <" + actual + ">");
                    break;
                }
            }
        }
        return result;
    }

    private static Set<String>
    readKnownDifferences() throws IOException {

        Set<String> result = new TreeSet<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
            new FileInputStream(ControlFlowDifferentialTest.KNOWN_DIFFERENCES),
            StandardCharsets.UTF_8
        ))) {
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) result.add(line);
            }
        }
        return result;
    }

    private static ClassLoader
    compile(ICompilerFactory compilerFactory, String source) throws Exception {
        ISimpleCompiler sc = compilerFactory.newSimpleCompiler();
        sc.cook(source);
        return sc.getClassLoader();
    }

    /**
     * @return The result of the method (or the message of the exception that it throws), and the trace
     */
    private static String
    invoke(ClassLoader cl, String methodName, int argument) {

        String result;
        try {
            Class<?> c      = cl.loadClass("P");
            Method   method = c.getDeclaredMethod(methodName, int.class);
            try {
                result = String.valueOf(method.invoke(null, argument));
            } catch (InvocationTargetException ite) {
                result = "!" + ite.getCause();
            }
            result += "|" + c.getField("t").get(null);
        } catch (Throwable t) { // E.g. a "VerifyError"
            result = "?" + t;
        }
        return result;
    }

    /**
     * Generates a method {@code public static String name(int a)}, which records the statements that it executes in
     * the static field {@code t}. All loops terminate, and no statement is unreachable (JLS 14.22).
     */
    static
    class Generator {

        private static final int MAX_DEPTH = 4;

        private final Random random;

        /**
         * The enclosing statements that {@code break} and {@code continue} statements may refer to.
         */
        private final List<Target> targets = new ArrayList<>();

        private int nextId;

        Generator(Random random) { this.random = random; }

        String
        method(String name) {

            StringBuilder sb = new StringBuilder();
            sb.append("    public static String ").append(name).append("(int a) {\n");
            sb.append("        t = new StringBuilder();\n");
            Fragment body = this.block(1, "        ");
            sb.append(body.code);
            if (body.canCompleteNormally) sb.append("        return \"end\";\n");
            sb.append("    }\n");
            return sb.toString();
        }

        /**
         * A sequence of statements; ends after the first statement that cannot complete normally.
         */
        private Fragment
        block(int depth, String indent) {

            StringBuilder sb = new StringBuilder();
            int           n  = 1 + this.random.nextInt(3);
            for (int i = 0; i < n; i++) {
                Fragment s = this.statement(depth, indent);
                sb.append(s.code);
                if (!s.canCompleteNormally) return new Fragment(sb.toString(), false);
            }
            return new Fragment(sb.toString(), true);
        }

        private Fragment
        statement(int depth, String indent) {

            String id   = String.valueOf(this.nextId++);
            String in   = indent + "    ";
            int    kind = depth >= Generator.MAX_DEPTH ? this.random.nextInt(6) : this.random.nextInt(16);

            switch (kind) {

            case 0:
            case 1:
                return new Fragment(indent + "t.append('" + (char) ('a' + this.random.nextInt(26)) + "');\n", true);

            case 2:
                // Local variables of various sizes, which affect the stack map frames.
                switch (this.random.nextInt(3)) {
                case 0:
                    return new Fragment(indent + "long l" + id + " = a * 3L; t.append(l" + id + ");\n", true);
                case 1:
                    return new Fragment(indent + "double d" + id + " = a / 2.0; t.append(d" + id + ");\n", true);
                default:
                    return new Fragment(indent + "String s" + id + " = \"s\" + a; t.append(s" + id + ");\n", true);
                }

            case 3:
                return this.jump(indent, true);

            case 4:
                return this.jump(indent, false);

            case 5:
                return new Fragment(indent + "if (" + this.condition() + ") t.append('i');\n", true);

            case 6: {
                Fragment thenBlock = this.block(depth + 1, in);
                if (this.random.nextBoolean()) {
                    return new Fragment(indent + "if (" + this.condition() + ") {\n" + thenBlock.code + indent + "}\n", true);
                }
                Fragment elseBlock = this.block(depth + 1, in);
                return new Fragment(
                    indent + "if (" + this.condition() + ") {\n" + thenBlock.code + indent + "} else {\n"
                    + elseBlock.code + indent + "}\n",
                    thenBlock.canCompleteNormally || elseBlock.canCompleteNormally
                );
            }

            case 7:
            case 8: {
                // Loops; "while" and "for" with non-constant conditions can always complete normally.
                String label = "L" + id;
                Target loop  = new Target(label, true);
                this.targets.add(loop);
                try {
                    switch (this.random.nextInt(3)) {
                    case 0: {
                        Fragment body = this.block(depth + 1, in);
                        return new Fragment(
                            indent + label + ": for (int i" + id + " = 0; i" + id + " < 2; i" + id + "++) {\n"
                            + body.code + indent + "}\n",
                            true
                        );
                    }
                    case 1: {
                        Fragment body = this.block(depth + 1, in);
                        return new Fragment(
                            indent + "int c" + id + " = 0;\n"
                            + indent + label + ": while (c" + id + "++ < 2) {\n" + body.code + indent + "}\n",
                            true
                        );
                    }
                    default: {
                        Fragment body = this.block(depth + 1, in);
                        return new Fragment(
                            indent + "int c" + id + " = 0;\n"
                            + indent + label + ": do {\n" + body.code + indent + "} while (c" + id + "++ < 1);\n",
                            body.canCompleteNormally || loop.continues > 0 || loop.breaks > 0
                        );
                    }
                    }
                } finally {
                    this.targets.remove(this.targets.size() - 1);
                }
            }

            case 9:
            case 10: {
                // TRY statement with CATCH and/or FINALLY clauses.
                boolean   hasCatch   = this.random.nextBoolean();
                boolean   hasFinally = !hasCatch || this.random.nextBoolean();
                int[]     beforeTry  = this.saveJumps();
                Fragment  body       = this.block(depth + 1, in);
                StringBuilder sb = new StringBuilder(indent + "try {\n" + body.code + indent + "}");
                boolean ccn = body.canCompleteNormally;
                if (hasCatch) {
                    Fragment handler = this.block(depth + 1, in);
                    sb.append(" catch (RuntimeException e" + id + ") {\n")
                    .append(in).append("t.append('C');\n")
                    .append(handler.code)
                    .append(indent).append("}");
                    ccn |= handler.canCompleteNormally;
                }
                if (hasFinally) {
                    int[]     beforeFinally = this.saveJumps();
                    Fragment  f             = this.block(depth + 1, in);
                    sb.append(" finally {\n").append(in).append("t.append('F');\n").append(f.code).append(indent).append("}");
                    ccn &= f.canCompleteNormally;

                    // BREAK and CONTINUE statements in the TRY block and in the CATCH clause do not exit their
                    // targets if the FINALLY clause cannot complete normally (JLS 14.22).
                    if (!f.canCompleteNormally) this.discardJumps(beforeTry, beforeFinally);
                }
                sb.append('\n');
                return new Fragment(sb.toString(), ccn);
            }

            case 11: {
                Fragment body = this.block(depth + 1, in);
                return new Fragment(
                    indent + "synchronized (P.class) {\n" + body.code + indent + "}\n",
                    body.canCompleteNormally
                );
            }

            case 12:
            case 13: {
                // SWITCH statement; unlabeled BREAK statements in its groups refer to the SWITCH statement.
                Target sw = new Target(null, false);
                this.targets.add(sw);
                try {
                    StringBuilder sb  = new StringBuilder(indent + "switch (a % 3) {\n");
                    boolean       ccn = false;
                    for (String label : new String[] { "case 0:", "case 1:", "default:" }) {
                        Fragment group = this.block(depth + 1, in + "    ");
                        sb.append(in).append(label).append('\n').append(group.code);
                        if (group.canCompleteNormally) {
                            sb.append(in).append("    break;\n");
                            ccn = true;
                        }
                    }
                    sb.append(indent).append("}\n");
                    return new Fragment(sb.toString(), ccn || sw.breaks > 0);
                } finally {
                    this.targets.remove(this.targets.size() - 1);
                }
            }

            default: {
                // Labeled block.
                String label = "B" + id;
                Target block = new Target(label, false);
                this.targets.add(block);
                try {
                    Fragment body = this.block(depth + 1, in);
                    return new Fragment(
                        indent + label + ": {\n" + body.code + indent + "}\n",
                        body.canCompleteNormally || block.breaks > 0
                    );
                } finally {
                    this.targets.remove(this.targets.size() - 1);
                }
            }
            }
        }

        /**
         * A RETURN, THROW, BREAK or CONTINUE statement, either unconditional (cannot complete normally) or guarded by
         * an IF statement (can complete normally).
         */
        private Fragment
        jump(String indent, boolean conditional) {

            String jump;
            int    kind = this.random.nextInt(4);
            if (kind == 2 || kind == 3) {
                jump = this.breakOrContinue(kind == 3);
                if (jump == null) kind = this.random.nextInt(2);
            } else {
                jump = null;
            }
            if (kind == 0) {
                jump = "return t.append('R').toString();";
            } else
            if (kind == 1) {
                jump = "throw new RuntimeException(\"X\" + t.length());";
            }
            assert jump != null;

            if (conditional) return new Fragment(indent + "if (" + this.condition() + ") " + jump + "\n", true);
            return new Fragment(indent + jump + "\n", false);
        }

        /**
         * @return A BREAK or CONTINUE statement that refers to a random enclosing statement, or {@code null}
         */
        private String
        breakOrContinue(boolean continu) {

            List<Target> candidates = new ArrayList<>();
            for (int i = this.targets.size() - 1; i >= 0; i--) {
                Target target = this.targets.get(i);
                if (continu && !target.isLoop) continue;
                candidates.add(target);
            }
            if (candidates.isEmpty()) return null;

            Target target = candidates.get(this.random.nextInt(candidates.size()));

            // An unlabeled BREAK or CONTINUE refers to the innermost loop (or SWITCH, for BREAK).
            boolean innermost = true;
            for (int i = this.targets.size() - 1; i >= 0; i--) {
                Target t = this.targets.get(i);
                if (t == target) break;
                if (t.isLoop || (!continu && t.label == null)) innermost = false;
            }
            if (!innermost && target.label == null) return null;

            if (continu) {
                target.continues++;
            } else {
                target.breaks++;
            }
            String keyword = continu ? "continue" : "break";
            if (target.label == null || (innermost && target.isLoop && this.random.nextBoolean())) {
                return keyword + ";";
            }
            return keyword + " " + target.label + ";";
        }

        /**
         * @return For each enclosing target, the number of BREAK and CONTINUE statements that refer to it
         */
        private int[]
        saveJumps() {
            int[] result = new int[2 * this.targets.size()];
            for (int i = 0; i < this.targets.size(); i++) {
                result[2 * i]     = this.targets.get(i).breaks;
                result[2 * i + 1] = this.targets.get(i).continues;
            }
            return result;
        }

        /**
         * Discards the BREAK and CONTINUE statements that were generated between the two snapshots <var>from</var>
         * and <var>to</var> (see {@link #saveJumps()}), but keeps those that were generated after <var>to</var>.
         */
        private void
        discardJumps(int[] from, int[] to) {
            for (int i = 0; i < this.targets.size(); i++) {
                Target target = this.targets.get(i);
                target.breaks    -= to[2 * i] - from[2 * i];
                target.continues -= to[2 * i + 1] - from[2 * i + 1];
            }
        }

        private String
        condition() {
            switch (this.random.nextInt(3)) {
            case 0:  return "a > " + this.random.nextInt(4);
            case 1:  return "(a & 1) == 0";
            default: return "t.length() < " + (2 + this.random.nextInt(6));
            }
        }
    }

    /**
     * A statement that BREAK and CONTINUE statements may refer to.
     */
    private static final
    class Target {

        @Nullable final String label;
        final boolean          isLoop;
        int                    breaks, continues;

        Target(@Nullable String label, boolean isLoop) {
            this.label  = label;
            this.isLoop = isLoop;
        }
    }

    /**
     * Generated code, and whether it can complete normally (JLS 14.22).
     */
    private static final
    class Fragment {

        final String  code;
        final boolean canCompleteNormally;

        Fragment(String code, boolean canCompleteNormally) {
            this.code                = code;
            this.canCompleteNormally = canCompleteNormally;
        }
    }
}
