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

import java.util.List;
import java.util.Map;

/**
 * The workloads of {@link Compare} and {@link CodeSizeReport}. The sources are generated deterministically (or are
 * JANINO's own sources), so every operation compiles exactly the same code.
 */
public
enum Workload {

    /**
     * A tiny expression ({@code a * 3 + b}), compiled with a new {@code ExpressionEvaluator} and evaluated once.
     * Measures the fixed costs of a compiler instance.
     */
    TINY {

        @Override public Object
        run(JaninoVersion janino) throws Exception {
            Object ee = this.compile(janino);
            janino.evaluate(ee, 7, 2);
            return ee;
        }

        @Override public void
        check(JaninoVersion janino) throws Exception {
            Object result = janino.evaluate(this.compile(janino), 7, 2);
            if (!Integer.valueOf(23).equals(result)) {
                throw new IllegalStateException(janino + ": \"a * 3 + b\" returned " + result + " instead of 23");
            }
        }

        @Override public Map<String, byte[]>
        bytecodes(JaninoVersion janino) throws Exception { return janino.getBytecodes(this.compile(janino)); }

        private Object
        compile(JaninoVersion janino) throws Exception {
            return janino.compileExpression(
                "a * 3 + b",
                new String[] { "a", "b" },
                new Class<?>[] { int.class, int.class },
                int.class
            );
        }
    },

    /**
     * A class with 15 larger methods: loops, cascades of {@code if} and {@code instanceof}, many invocations of
     * methods of the JDK (collections, {@code String}, {@code StringBuilder}, {@code Math}), like the code that SQL
     * engines generate. Compiled with a new {@code SimpleCompiler}, loaded and instantiated.
     */
    GENERATED {

        @Override String
        source() {
            StringBuilder sb = new StringBuilder();
            sb.append("package bench;\n");
            sb.append("import java.util.*;\n");
            sb.append("public class Generated {\n");
            sb.append("    private final Map<String, Object> m = new HashMap<String, Object>();\n");
            sb.append("    private int[] a = new int[100];\n");
            for (int k = 0; k < 15; k++) {
                sb.append("    public long f").append(k).append("(Object[] row, int n) {\n");
                sb.append("        long sum = 0; String s = null; StringBuilder b = new StringBuilder();\n");
                sb.append("        for (int j = 0; j < n; j++) {\n");
                sb.append("            Object o = row[j % row.length];\n");
                sb.append("            if (o == null) { sum += ").append(k).append("; continue; }\n");
                sb.append("            if (o instanceof Integer) {\n");
                sb.append("                sum += ((Integer) o).intValue() * 31L;\n");
                sb.append("            } else if (o instanceof String) {\n");
                sb.append("                s = (String) o; sum += s.length() + s.hashCode(); b.append(s.trim());\n");
                sb.append("            } else {\n");
                sb.append("                sum ^= o.hashCode(); m.put(String.valueOf(j), o);\n");
                sb.append("            }\n");
                sb.append("            a[j % a.length] = (int) Math.max(sum, (long) Integer.MIN_VALUE);\n");
                sb.append("        }\n");
                sb.append("        List<Object> l = new ArrayList<Object>(m.values());\n");
                sb.append("        Collections.reverse(l);\n");
                sb.append("        return sum + b.length() + l.size() + Arrays.hashCode(a);\n");
                sb.append("    }\n");
            }
            sb.append("}\n");
            return sb.toString();
        }

        @Override String
        className() { return "bench.Generated"; }
    },

    /**
     * A class with 15 methods that consist of nested try-with-resources, {@code synchronized} and {@code
     * try}/{@code catch}/{@code finally} statements, which are left through labeled {@code break} and {@code
     * continue} statements and {@code return} statements. Compiled with a new {@code SimpleCompiler}, loaded and
     * instantiated.
     */
    CONTROL_FLOW {

        @Override String
        source() {
            StringBuilder sb = new StringBuilder();
            sb.append("package bench;\n");
            sb.append("import java.io.*;\n");
            sb.append("public class ControlFlow {\n");
            sb.append("    static class R implements Closeable { int c; public void close() { c++; } }\n");
            sb.append("    private final Object lock = new Object();\n");
            for (int k = 0; k < 15; k++) {
                sb.append("    public long f").append(k).append("(Object[] row, int n) {\n");
                sb.append("        long sum = 0;\n");
                sb.append("        OUTER: for (int j = 0; j < n; j++) {\n");
                sb.append("            try (R r = new R()) {\n");
                sb.append("                synchronized (lock) {\n");
                sb.append("                    try {\n");
                sb.append("                        Object o = row[j % row.length];\n");
                sb.append("                        if (o == null) continue OUTER;\n");
                sb.append("                        if (o instanceof Double) break OUTER;\n");
                sb.append("                        sum += o.hashCode();\n");
                sb.append("                        if (sum == ").append(k).append(") return sum;\n");
                sb.append("                    } catch (IllegalStateException e) {\n");
                sb.append("                        sum--;\n");
                sb.append("                    } catch (RuntimeException e) {\n");
                sb.append("                        sum++;\n");
                sb.append("                    } finally {\n");
                sb.append("                        sum += 2;\n");
                sb.append("                    }\n");
                sb.append("                }\n");
                sb.append("            }\n");
                sb.append("        }\n");
                sb.append("        return sum;\n");
                sb.append("    }\n");
            }
            sb.append("}\n");
            return sb.toString();
        }

        @Override String
        className() { return "bench.ControlFlow"; }
    },

    /**
     * Compiles a large part of JANINO's own sources with a new {@code Compiler}; see {@link
     * JaninoVersion#selfCompile()}. Both versions compile the same (current) sources.
     */
    SELF_COMPILE {

        @Override public Object
        run(JaninoVersion janino) throws Exception { return janino.selfCompile(); }

        @Override public void
        check(JaninoVersion janino) throws Exception {
            int n = janino.selfCompile().size();
            if (n < 70) throw new IllegalStateException(janino + ": only " + n + " class files generated");
        }

        @Override public Map<String, byte[]>
        bytecodes(JaninoVersion janino) throws Exception { return janino.selfCompile(); }
    },

    /**
     * The class bodies that Apache Spark 4.2.0 generated for the TPC-DS queries, compiled the way Spark compiles
     * them: each with a new {@code ClassBodyEvaluator}, Spark's default imports and Spark's {@code GeneratedClass}
     * as the superclass, then loaded and instantiated (see {@link SparkCodegen}, and "tpcds/README.txt" in the
     * resources for the corpus). One operation compiles the whole corpus, so measure it with longer rounds, e.g.
     * {@code -millis 5000}.
     */
    SPARK_TPCDS {

        @Override public Object
        run(JaninoVersion janino) throws Exception { return SparkCodegen.compileAll(janino); }

        @Override public void
        check(JaninoVersion janino) throws Exception {
            List<Object> instances = SparkCodegen.compileAll(janino);
            int          n         = SparkCodegen.bodies().size();
            if (instances.size() != n) {
                throw new IllegalStateException(janino + ": " + instances.size() + " instead of " + n + " classes");
            }
            for (Object instance : instances) {
                if (!SparkCodegen.extendedClass().isInstance(instance)) {
                    throw new IllegalStateException(janino + ": " + instance.getClass() + " is not a GeneratedClass");
                }
            }
        }

        @Override public Map<String, byte[]>
        bytecodes(JaninoVersion janino) throws Exception { return SparkCodegen.bytecodes(janino); }
    };

    /**
     * Executes one operation of this workload: compiles the source with a new compiler instance and loads the
     * result.
     *
     * @return An object that depends on the result, so that the JIT compiler cannot eliminate the operation
     */
    public Object
    run(JaninoVersion janino) throws Exception { return janino.compileAndLoad(this.source(), this.className()); }

    /**
     * Executes the workload once and invokes a method of the result, so that a failure is reported before the
     * measurement starts. The result is not compared with an expected value: some versions compute different results
     * (e.g. 3.1.12 for {@link #CONTROL_FLOW}, because of the defects fixed in 3.1.14).
     */
    public void
    check(JaninoVersion janino) throws Exception {
        Object instance = this.run(janino);
        instance.getClass().getMethod("f0", Object[].class, int.class).invoke(
            instance,
            new Object[] { new Object[] { 1, "x", null, 2.0 }, 3 }
        );
    }

    /** @return The class files that this workload generates */
    public Map<String, byte[]>
    bytecodes(JaninoVersion janino) throws Exception { return janino.compileToBytecodes(this.source()); }

    /** @return The compilation unit that this workload compiles (not applicable for {@link #TINY}) */
    String
    source() { throw new UnsupportedOperationException(this.name()); }

    /** @return The name of the class that {@link #source()} declares */
    String
    className() { throw new UnsupportedOperationException(this.name()); }
}
