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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;

/**
 * Extracts test cases from the regression tests of the Eclipse compiler (ECJ), for "JdtCorpusTest" in the module
 * "commons-compiler-tests". The tests are not part of this repository: they are read from a checkout of
 * "eclipse-jdt/eclipse.jdt.core" (directory
 * "org.eclipse.jdt.core.tests.compiler/src/org/eclipse/jdt/core/tests/compiler/regression"), and the cases are written
 * to a directory that only the test reads.
 * <p>
 *   Usage (on JDK 11 or later, which can run a source file):
 * </p>
 * <pre>
 *   java JdtCorpusExtractor.java <regression-dir> <out-dir> <test-class> ...
 * </pre>
 * <p>
 *   The test sources are parsed with the parser of JAVAC, so that escape sequences and text blocks are decoded like
 *   by the compiler. Each invocation of "runConformTest()", "runWarningTest()" and "runNegativeTest()" in a method
 *   "test...()", and each "Runner" object, becomes a case, if its test files, its expected output and its VM
 *   arguments are constant (string literals, concatenations of them, "null" and array initializers). Other arguments
 *   (compiler options, "JavacTestOptions", flags) are ignored: the cases are only programs, and the test decides with
 *   JAVAC whether a case counts. Cases with class libraries, dependant files, VM arguments other than those for
 *   assertions, or a test file that calls "System.exit()" are skipped, with the reason in "skipped.txt".
 * </p>
 * <p>
 *   For each case, the directory "<out-dir>/<test-class>.<method>[#<n>]" contains:
 * </p>
 * <dl>
 *   <dt>{@code kind.txt}</dt>
 *   <dd>"conform" (compile and run), "compile" (compile only) or "negative" (the compilation fails)</dd>
 *   <dt>{@code files.txt}</dt>
 *   <dd>The names of the test files, one per line; the class of the first file is the main class</dd>
 *   <dt>{@code src/...}</dt>
 *   <dd>The test files</dd>
 *   <dt>{@code expected.txt}</dt>
 *   <dd>The expected output of the main class (only for "conform")</dd>
 *   <dt>{@code vmargs.txt}</dt>
 *   <dd>The VM arguments, one per line (e.g. "-ea")</dd>
 * </dl>
 * <p>
 *   "<out-dir>/index.txt" lists the cases, "<out-dir>/skipped.txt" the skipped cases with the reasons.
 * </p>
 */
public
class JdtCorpusExtractor {

    /** The value of the literal "null". */
    private static final Object NULL = new Object();

    private static final List<String> ASSERTION_VM_ARGUMENTS = Arrays.asList(
        "-ea", "-da", "-esa", "-dsa", "-enableassertions", "-disableassertions"
    );

    private final Path                 outDir;
    private final List<String>         index   = new ArrayList<>();
    private final List<String>         skipped = new ArrayList<>();
    private final Map<String, Integer> reasons = new TreeMap<>();

    JdtCorpusExtractor(Path outDir) { this.outDir = outDir; }

    public static void
    main(String[] args) throws IOException {

        if (args.length < 3) {
            System.err.println("Usage: java JdtCorpusExtractor.java <regression-dir> <out-dir> <test-class> ...");
            System.exit(1);
        }
        Path regressionDir = Path.of(args[0]);
        Path outDir        = Path.of(args[1]);
        Files.createDirectories(outDir);

        JdtCorpusExtractor extractor = new JdtCorpusExtractor(outDir);
        for (String className : Arrays.asList(args).subList(2, args.length)) {
            extractor.extract(regressionDir.resolve(className + ".java"), className);
        }

        Files.write(outDir.resolve("index.txt"), extractor.index, StandardCharsets.UTF_8);
        Files.write(outDir.resolve("skipped.txt"), extractor.skipped, StandardCharsets.UTF_8);
        System.out.println(extractor.index.size() + " cases, " + extractor.skipped.size() + " skipped");
        for (Map.Entry<String, Integer> e : extractor.reasons.entrySet()) {
            System.out.println("  " + e.getValue() + " " + e.getKey());
        }
    }

    private void
    extract(Path file, String className) throws IOException {

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(
                null,
                fm,
                d -> {},
                Arrays.asList("-proc:none"),
                null,
                fm.getJavaFileObjects(file.toFile())
            );
            for (CompilationUnitTree cu : task.parse()) {
                for (Tree t : cu.getTypeDecls()) {
                    if (!(t instanceof ClassTree)) continue;
                    for (Tree m : ((ClassTree) t).getMembers()) {
                        if (!(m instanceof MethodTree)) continue;
                        MethodTree mt = (MethodTree) m;
                        String     mn = mt.getName().toString();
                        if (!mn.startsWith("test") || !mt.getParameters().isEmpty() || mt.getBody() == null) continue;
                        this.extract(mt, className + "." + mn);
                    }
                }
            }
        }
    }

    /**
     * Extracts the cases of one test method, in the order of the source.
     */
    private void
    extract(MethodTree method, final String methodId) {

        final List<Case>                            cases   = new ArrayList<>();
        final Map<String, Map<String, ExpressionTree>> runners = new HashMap<>();

        new TreeScanner<Void, Void>() {

            @Override public Void
            visitVariable(VariableTree node, Void p) {
                if (JdtCorpusExtractor.isNewRunner(node.getInitializer())) {
                    runners.put(node.getName().toString(), new LinkedHashMap<String, ExpressionTree>());
                }
                return super.visitVariable(node, p);
            }

            @Override public Void
            visitAssignment(AssignmentTree node, Void p) {
                ExpressionTree lhs = node.getVariable();
                if (lhs instanceof IdentifierTree && JdtCorpusExtractor.isNewRunner(node.getExpression())) {
                    runners.put(((IdentifierTree) lhs).getName().toString(), new LinkedHashMap<>());
                } else
                if (lhs instanceof MemberSelectTree) {
                    MemberSelectTree ms = (MemberSelectTree) lhs;
                    if (ms.getExpression() instanceof IdentifierTree) {
                        Map<String, ExpressionTree> fields = runners.get(
                            ((IdentifierTree) ms.getExpression()).getName().toString()
                        );
                        if (fields != null) fields.put(ms.getIdentifier().toString(), node.getExpression());
                    }
                }
                return super.visitAssignment(node, p);
            }

            @Override public Void
            visitMethodInvocation(MethodInvocationTree node, Void p) {
                ExpressionTree ms = node.getMethodSelect();
                String         name;
                ExpressionTree receiver;
                if (ms instanceof IdentifierTree) {
                    name     = ((IdentifierTree) ms).getName().toString();
                    receiver = null;
                } else
                if (ms instanceof MemberSelectTree) {
                    name     = ((MemberSelectTree) ms).getIdentifier().toString();
                    receiver = ((MemberSelectTree) ms).getExpression();
                } else
                {
                    return super.visitMethodInvocation(node, p);
                }

                if (receiver instanceof IdentifierTree && !"this".equals(receiver.toString())) {
                    Map<String, ExpressionTree> fields = runners.get(((IdentifierTree) receiver).getName().toString());
                    if (fields != null && node.getArguments().isEmpty()) cases.add(Case.fromRunner(name, fields));
                } else
                if (receiver == null || "this".equals(receiver.toString())) {
                    Case c = Case.fromInvocation(name, node.getArguments());
                    if (c != null) cases.add(c);
                }
                return super.visitMethodInvocation(node, p);
            }
        }.scan(method.getBody(), null);

        for (int i = 0; i < cases.size(); i++) {
            String id = cases.size() == 1 ? methodId : methodId + "#" + (i + 1);
            Case   c  = cases.get(i);
            String reason = c.skipReason();
            if (reason != null) {
                this.skipped.add(id + " " + reason);
                this.reasons.merge(reason, 1, Integer::sum);
                continue;
            }
            try {
                c.write(this.outDir.resolve(id));
            } catch (IOException ioe) {
                throw new RuntimeException(id, ioe);
            }
            this.index.add(id);
        }
    }

    static boolean
    isNewRunner(ExpressionTree e) {
        return e instanceof NewClassTree && "Runner".equals(((NewClassTree) e).getIdentifier().toString());
    }

    /**
     * One invocation of a "run...Test()" method.
     */
    static final
    class Case {

        String       kind;
        Object       testFiles;
        Object       expectedOutput = NULL;
        Object       vmArguments    = NULL;
        Object       classLibraries = NULL;
        Object       dependantFiles = NULL;
        Object       expectedError  = NULL;
        String       unsupported;

        /**
         * @return {@code null} iff the method is not a "run...Test()" method
         */
        static Case
        fromInvocation(String methodName, List<? extends ExpressionTree> args) {

            Case c = new Case();
            switch (methodName) {
            case "runConformTest": c.kind = "conform";  break;
            case "runWarningTest": c.kind = "compile";  break;
            case "runNegativeTest": c.kind = "negative"; break;
            default: return null;
            }
            if (args.isEmpty()) return null;

            // "runConformTest(boolean skipJavac, JavacTestOptions, String[] testFiles, ...)".
            int i = 0;
            if (Case.value(args.get(0)) instanceof Boolean) i = 2;
            if (i >= args.size()) {
                c.unsupported = "unknown overload";
                return c;
            }
            c.testFiles = Case.value(args.get(i));
            if (!c.kind.equals("conform")) return c;

            if (i + 1 >= args.size()) {
                c.kind = "compile";
                return c;
            }
            Object second = Case.value(args.get(i + 1));
            if (second instanceof String[]) {
                c.dependantFiles = second;
                return c;
            }
            if (second == NULL) {
                c.kind = "compile";
            } else
            if (second instanceof String) {
                c.expectedOutput = second;
            } else
            if (args.size() - i == 2) {

                // "runConformTest(String[], Map)" or "runConformTest(String[], ASTVisitor)".
                c.kind = "compile";
                return c;
            } else
            {
                c.unsupported = "non-constant expected output";
                return c;
            }

            // "(files, expected, String[] classLibraries, boolean flush, String[] vmArguments, ...)", or
            // "(files, expected, Map options, String[] vmArguments, ...)".
            if (args.size() - i >= 5 && Case.value(args.get(i + 3)) instanceof Boolean) {
                c.classLibraries = Case.value(args.get(i + 2));
                c.vmArguments    = Case.value(args.get(i + 4));
            } else
            if (args.size() - i >= 4) {
                c.vmArguments = Case.value(args.get(i + 3));
            }
            return c;
        }

        static Case
        fromRunner(String methodName, Map<String, ExpressionTree> fields) {

            Case c = new Case();
            switch (methodName) {
            case "runConformTest":  c.kind = "conform";  break;
            case "runWarningTest":  c.kind = "compile";  break;
            case "runNegativeTest": c.kind = "negative"; break;
            default: c.unsupported = "Runner." + methodName + "()"; return c;
            }
            c.testFiles = Case.field(fields, "testFiles");
            if (c.kind.equals("conform")) {
                c.expectedOutput = Case.field(fields, "expectedOutputString");
                if (c.expectedOutput == NULL) c.kind = "compile";
                c.vmArguments    = Case.field(fields, "vmArguments");
                c.expectedError  = Case.field(fields, "expectedErrorString");
            }
            c.classLibraries = Case.field(fields, "classLibraries");
            c.dependantFiles = Case.field(fields, "dependantFiles");
            return c;
        }

        private static Object
        field(Map<String, ExpressionTree> fields, String name) {
            ExpressionTree e = fields.get(name);
            return e == null ? NULL : Case.value(e);
        }

        /**
         * @return The reason why this case is skipped, or {@code null}
         */
        String
        skipReason() {
            if (this.unsupported != null)   return this.unsupported;
            if (!(this.testFiles instanceof String[])) return "non-constant test files";
            String[] tf = (String[]) this.testFiles;
            if (tf.length == 0 || tf.length % 2 != 0) return "odd number of test file elements";
            for (int i = 0; i < tf.length; i += 2) {
                if (tf[i] == null || tf[i + 1] == null)  return "null test file";
                if (!tf[i].endsWith(".java"))           return "test file other than .java";
                if (tf[i + 1].contains("System.exit"))  return "System.exit()";
            }
            if (this.dependantFiles != NULL)                                  return "dependant files";
            if (this.classLibraries != NULL)                                  return "class libraries";
            if (this.expectedError != NULL && !"".equals(this.expectedError)) return "expected error output";
            if (this.kind.equals("conform") && !(this.expectedOutput instanceof String)) {
                return "non-constant expected output";
            }
            if (this.vmArguments != NULL) {
                if (!(this.vmArguments instanceof String[])) return "non-constant VM arguments";
                for (String a : (String[]) this.vmArguments) {
                    if (!ASSERTION_VM_ARGUMENTS.contains(a)) return "VM arguments";
                }
            }
            return null;
        }

        void
        write(Path dir) throws IOException {

            String[]     tf    = (String[]) this.testFiles;
            List<String> names = new ArrayList<>();
            for (int i = 0; i < tf.length; i += 2) {
                Path p = dir.resolve("src").resolve(tf[i]);
                Files.createDirectories(p.getParent());
                Files.write(p, tf[i + 1].getBytes(StandardCharsets.UTF_8));
                names.add(tf[i]);
            }
            Files.write(dir.resolve("files.txt"), names, StandardCharsets.UTF_8);
            Files.write(dir.resolve("kind.txt"), Arrays.asList(this.kind), StandardCharsets.UTF_8);
            if (this.kind.equals("conform")) {
                Files.write(
                    dir.resolve("expected.txt"),
                    ((String) this.expectedOutput).getBytes(StandardCharsets.UTF_8)
                );
            }
            Files.write(
                dir.resolve("vmargs.txt"),
                this.vmArguments instanceof String[] ? Arrays.asList((String[]) this.vmArguments) : List.of(),
                StandardCharsets.UTF_8
            );
        }

        /**
         * @return The value of a constant expression: a {@link String}, {@link Boolean}, {@link Number}, {@link
         *         Character}, {@code String[]} or {@link #NULL}; or {@code null} iff the expression is not constant
         */
        static Object
        value(ExpressionTree e) {

            if (e instanceof ParenthesizedTree) return Case.value(((ParenthesizedTree) e).getExpression());

            if (e instanceof LiteralTree) {
                Object v = ((LiteralTree) e).getValue();
                return v == null ? NULL : v;
            }

            if (e instanceof BinaryTree && e.getKind() == Tree.Kind.PLUS) {
                Object l = Case.value(((BinaryTree) e).getLeftOperand());
                Object r = Case.value(((BinaryTree) e).getRightOperand());
                if (l == null || r == null || l == NULL || r == NULL) return null;
                if (l instanceof String || r instanceof String) return String.valueOf(l) + String.valueOf(r);
                return null;
            }

            if (e instanceof NewArrayTree) {
                NewArrayTree na = (NewArrayTree) e;
                if (na.getInitializers() == null) return null;
                List<String> elements = new ArrayList<>();
                for (ExpressionTree i : na.getInitializers()) {
                    Object v = Case.value(i);
                    if (v == NULL) {
                        elements.add(null);
                    } else
                    if (v instanceof String) {
                        elements.add((String) v);
                    } else
                    {
                        return null;
                    }
                }
                return elements.toArray(new String[elements.size()]);
            }

            return null;
        }
    }
}
