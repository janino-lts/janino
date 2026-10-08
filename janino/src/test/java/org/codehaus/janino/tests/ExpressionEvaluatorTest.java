
/*
 * Janino - An embedded Java[TM] compiler
 *
 * Copyright (c) 2001-2010 Arno Unkrig. All rights reserved.
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

package org.codehaus.janino.tests;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.codehaus.janino.ExpressionEvaluator;
import org.codehaus.janino.Java;
import org.codehaus.janino.Parser;
import org.codehaus.janino.Scanner;
import org.codehaus.janino.ScriptEvaluator;
import org.codehaus.janino.SimpleCompiler;
import org.codehaus.janino.util.AbstractTraverser;
import org.junit.Assert;
import org.junit.Test;

// SUPPRESS CHECKSTYLE JavadocMethod:9999

/**
 * Unit tests fot the {@link ExpressionEvaluator}.
 */
public
class ExpressionEvaluatorTest {

    @Test public void
    testGuessParameterNames() throws Exception {
        Set<String> parameterNames = new HashSet<>(
            Arrays.asList(ExpressionEvaluator.guessParameterNames(new Scanner(null, new StringReader(
                ""
                + "import o.p;\n"
                + "a + b.c + d.e() + f() + g.h.I.j() + k.l.M"
            ))))
        );
        Assert.assertEquals(new HashSet<>(Arrays.asList("a", "b", "d")), parameterNames);

        parameterNames = new HashSet<>(
            Arrays.asList(ScriptEvaluator.guessParameterNames(new Scanner(null, new StringReader(
                ""
                + "import o.p;\n"
                + "int a;\n"
                + "return a + b.c + d.e() + f() + g.h.I.j() + k.l.M;"
            ))))
        );
        Assert.assertEquals(new HashSet<>(Arrays.asList("b", "d")), parameterNames);
    }

    /**
     * The names in the initializers of local variables and fields, and in array initializers (issue #76): before
     * 3.1.18, {@code guessParameterNames()} did not find them, because {@link AbstractTraverser} did not descend into
     * such initializers.
     */
    @Test public void
    testGuessParameterNamesInInitializers() throws Exception {
        Assert.assertEquals(ExpressionEvaluatorTest.set("a", "b"), ExpressionEvaluatorTest.guessExpression(
            "new int[] { a, b }"
        ));
        Assert.assertEquals(ExpressionEvaluatorTest.set("c", "d"), ExpressionEvaluatorTest.guessExpression(
            "new Object() { int f = c; }.hashCode() + d"
        ));
        Assert.assertEquals(ExpressionEvaluatorTest.set("x"), ExpressionEvaluatorTest.guessScript(
            "int y = x; return y;"
        ));
        Assert.assertEquals(ExpressionEvaluatorTest.set("x"), ExpressionEvaluatorTest.guessScript(
            "int y; y = x; return y;"
        ));
        Assert.assertEquals(ExpressionEvaluatorTest.set("p", "q"), ExpressionEvaluatorTest.guessScript(
            "int[] a = { p, q.length() }; return a;"
        ));
        Assert.assertEquals(ExpressionEvaluatorTest.set("v"), ExpressionEvaluatorTest.guessScript(
            "Runnable r = new Runnable() { public void run() { int u = v; } }; return r;"
        ));

        // A name that is declared as a local variable is not a parameter, also in an initializer.
        Assert.assertEquals(ExpressionEvaluatorTest.set(), ExpressionEvaluatorTest.guessScript(
            "int y = 1; int z = y + Math.abs(y); return z;"
        ));
    }

    /**
     * {@link AbstractTraverser} descends into the initializers of variables and fields, and into array initializers
     * (issue #76). The 3.1.x line keeps the old behavior, which did not; there, only the traversers of {@code
     * guessParameterNames()} descend into initializers.
     */
    @Test public void
    testAbstractTraverserInitializers() throws Exception {
        Assert.assertEquals(ExpressionEvaluatorTest.set("x"), ExpressionEvaluatorTest.ambiguousNames(
            "class P { void f() { int y = x + 1; } }"
        ));
        Assert.assertEquals(ExpressionEvaluatorTest.set("x"), ExpressionEvaluatorTest.ambiguousNames(
            "class P { int y = f(x); }"
        ));
        Assert.assertEquals(ExpressionEvaluatorTest.set("a", "b"), ExpressionEvaluatorTest.ambiguousNames(
            "class P { int[][] y = { { a + 1 }, { -b } }; }"
        ));
    }

    /**
     * {@link AbstractTraverser} visits the constants of an enum: their arguments, class bodies and annotations, each
     * node once (issue #88).
     */
    @Test public void
    testAbstractTraverserEnumConstants() throws Exception {
        String enumBody = (
            "{\n"
            + "    @Deprecated A(f(x)) { int g = h; void m() { int y = z; } },\n"
            + "    B;\n"
            + "    E() { }\n"
            + "    E(int i) { }\n"
            + "    static int f(int i) { return i; }\n"
            + "}\n"
        );
        String[] expected = {
            "annotation @Deprecated", "constant A", "constant B", "method f", "method m", "name h", "name i",
            "name x", "name z",
        };
        Assert.assertEquals(
            Arrays.asList(expected),
            ExpressionEvaluatorTest.enumNodes("enum E " + enumBody)
        );
        Assert.assertEquals(
            Arrays.asList(expected),
            ExpressionEvaluatorTest.enumNodes("class P { enum E " + enumBody + "}")
        );
    }

    /**
     * {@code guessParameterNames()} does not look into enum constants, so that its results are the same as before
     * issue #88: a name in an enum constant cannot denote a parameter.
     */
    @Test public void
    testGuessParameterNamesEnumConstants() throws Exception {
        Assert.assertEquals(ExpressionEvaluatorTest.set("d"), ExpressionEvaluatorTest.guessExpression(
            "new Object() { enum E { A(q) { int g = r; }; E(int i) { } } }.hashCode() + d"
        ));
        Assert.assertEquals(ExpressionEvaluatorTest.set("d"), ExpressionEvaluatorTest.guessScript(
            "Object o = new Object() { enum E { A(q) { int g = r; }; E(int i) { } } }; return o.hashCode() + d;"
        ));
    }

    /**
     * The effectively final analysis (issue #24) does not look into enum constants, so that it accepts the same code
     * as before issue #88: the assignment to the field {@code x} in the class body of the constant {@code A} does not
     * count against the local variable {@code x}.
     */
    @Test public void
    testEffectivelyFinalEnumConstants() throws Exception {
        new SimpleCompiler().cook(
            ""
            + "public class P {\n"
            + "    public static Object f() {\n"
            + "        int x = 1;\n"
            + "        Object o = new Object() { enum E { A { int x; void m() { x = 2; } } } };\n"
            + "        return new Object() { public String toString() { return \"\" + x; } };\n"
            + "    }\n"
            + "}\n"
        );
    }

    /**
     * @return The first identifiers of the {@link Java.AmbiguousName}s that {@link AbstractTraverser} visits in the
     *         <var>compilationUnit</var>
     */
    private static Set<String>
    ambiguousNames(String compilationUnit) throws Exception {
        Java.AbstractCompilationUnit cu = new Parser(new Scanner(null, new StringReader(compilationUnit)))
            .parseAbstractCompilationUnit();

        final Set<String> names = new HashSet<>();
        new AbstractTraverser<RuntimeException>() {
            @Override public void traverseAmbiguousName(Java.AmbiguousName an) { names.add(an.identifiers[0]); }
        }.visitAbstractCompilationUnit(cu);
        return names;
    }

    /**
     * @return What {@link AbstractTraverser} visits in the <var>compilationUnit</var>: enum constants, annotations,
     *         methods, and the first identifiers of ambiguous names, sorted
     */
    private static List<String>
    enumNodes(String compilationUnit) throws Exception {
        Java.AbstractCompilationUnit cu = new Parser(new Scanner(null, new StringReader(compilationUnit)))
            .parseAbstractCompilationUnit();

        final List<String> result = new ArrayList<>();
        new AbstractTraverser<RuntimeException>() {
            @Override public void traverseEnumConstant(Java.EnumConstant ec) {
                result.add("constant " + ec.name);
                super.traverseEnumConstant(ec);
            }
            @Override public void traverseAnnotation(Java.Annotation a) {
                result.add("annotation " + a);
                super.traverseAnnotation(a);
            }
            @Override public void traverseMethodDeclarator(Java.MethodDeclarator md) {
                result.add("method " + md.name);
                super.traverseMethodDeclarator(md);
            }
            @Override public void traverseAmbiguousName(Java.AmbiguousName an) {
                result.add("name " + an.identifiers[0]);
            }
        }.visitAbstractCompilationUnit(cu);
        Collections.sort(result);
        return result;
    }

    private static Set<String>
    guessExpression(String expression) throws Exception {
        return ExpressionEvaluatorTest.set(
            ExpressionEvaluator.guessParameterNames(new Scanner(null, new StringReader(expression)))
        );
    }

    private static Set<String>
    guessScript(String script) throws Exception {
        return ExpressionEvaluatorTest.set(
            ScriptEvaluator.guessParameterNames(new Scanner(null, new StringReader(script)))
        );
    }

    private static Set<String>
    set(String... elements) { return new HashSet<>(Arrays.asList(elements)); }
}
