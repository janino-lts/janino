
/*
 * Janino - An embedded Java[TM] compiler
 *
 * Copyright (c) 2001-2018 Arno Unkrig. All rights reserved.
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

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.util.resource.DirectoryResourceFinder;
import org.codehaus.janino.ClassBodyEvaluator;
import org.codehaus.janino.ClassLoaderIClassLoader;
import org.codehaus.janino.Compiler;
import org.codehaus.janino.ExpressionEvaluator;
import org.codehaus.janino.JaninoOption;
import org.codehaus.janino.JavaSourceIClassLoader;
import org.codehaus.janino.ScriptEvaluator;
import org.codehaus.janino.SimpleCompiler;
import org.codehaus.janino.UnitCompiler;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

// SUPPRESS CHECKSTYLE JavadocMethod:9999

/**
 * Unit tests for the {@link SimpleCompiler}.
 */
public
class OptionsTest {

    @Before
    public void
    setUp() throws Exception {

        // Optionally print class file disassemblies to the console.
        if (Boolean.getBoolean("disasm")) {
            Logger scl = Logger.getLogger(UnitCompiler.class.getName());
            for (Handler h : scl.getHandlers()) {
                h.setLevel(Level.FINEST);
            }
            scl.setLevel(Level.FINEST);
        }
    }

    /**
     * Tests {@link JaninoOption#EXPRESSIONS_IN_TRY_WITH_RESOURCES_ALLOWED}.
     */
    @Test public void
    testExpressionsInTryWithResourcesAllowed() throws Exception {
        String script = (
            ""
            + "import java.io.Closeable;\n"
            + "import java.io.IOException;\n"
            + "import org.junit.Assert;\n"
            + "\n"
            + "final int[] x = new int[1];\n"
            + "\n"
            + "try (new Closeable() {\n"
            + "    public void close() {\n"
            + "        Assert.assertEquals(2, ++x[0]);\n"
            + "    }\n"
            + "}) {\n"
            + "    Assert.assertEquals(1, ++x[0]);\n"
            + "}\n"
            + "\n"
            + "Assert.assertEquals(3, ++x[0]);\n"
        );

        OptionsTest.assertScriptCompilationError("NewAnonymousClassInstance rvalue not allowed as a resource", script);

        OptionsTest.assertScriptExecutable(script, JaninoOption.EXPRESSIONS_IN_TRY_WITH_RESOURCES_ALLOWED);
    }

    /**
     * Tests the system property {@value JaninoOption#JAVAC_COMPLIANCE_SYSTEM_PROPERTY}: it determines the initial
     * options of a compiler, and an explicit {@code options(...)} call replaces them.
     */
    @Test public void
    testJavacComplianceSystemProperty() throws Exception {

        Assert.assertEquals(EnumSet.noneOf(JaninoOption.class), new SimpleCompiler().options());
        Assert.assertEquals(EnumSet.noneOf(JaninoOption.class), new ScriptEvaluator().options());

        String old = System.setProperty(JaninoOption.JAVAC_COMPLIANCE_SYSTEM_PROPERTY, "true");
        try {
            Assert.assertEquals(EnumSet.of(JaninoOption.JAVAC_COMPLIANCE), JaninoOption.defaultOptions());
            Assert.assertEquals(EnumSet.of(JaninoOption.JAVAC_COMPLIANCE), new SimpleCompiler().options());
            Assert.assertEquals(EnumSet.of(JaninoOption.JAVAC_COMPLIANCE), new ScriptEvaluator().options());
            Assert.assertEquals(EnumSet.of(JaninoOption.JAVAC_COMPLIANCE), new ExpressionEvaluator().options());
            Assert.assertEquals(EnumSet.of(JaninoOption.JAVAC_COMPLIANCE), new ClassBodyEvaluator().options());
            Assert.assertEquals(EnumSet.of(JaninoOption.JAVAC_COMPLIANCE), new Compiler().options());
            Assert.assertEquals(
                EnumSet.of(JaninoOption.JAVAC_COMPLIANCE),
                new JavaSourceIClassLoader(
                    new DirectoryResourceFinder(new File(".")),
                    null,
                    new ClassLoaderIClassLoader(OptionsTest.class.getClassLoader())
                ).options()
            );

            // An explicit "options(...)" call replaces the initial options.
            Assert.assertEquals(
                EnumSet.noneOf(JaninoOption.class),
                new SimpleCompiler().options(EnumSet.noneOf(JaninoOption.class)).options()
            );

            // The property is read when the compiler is created.
            SimpleCompiler sc = new SimpleCompiler();
            System.clearProperty(JaninoOption.JAVAC_COMPLIANCE_SYSTEM_PROPERTY);
            Assert.assertEquals(EnumSet.of(JaninoOption.JAVAC_COMPLIANCE), sc.options());
            Assert.assertEquals(EnumSet.noneOf(JaninoOption.class), new SimpleCompiler().options());
        } finally {
            if (old == null) {
                System.clearProperty(JaninoOption.JAVAC_COMPLIANCE_SYSTEM_PROPERTY);
            } else {
                System.setProperty(JaninoOption.JAVAC_COMPLIANCE_SYSTEM_PROPERTY, old);
            }
        }
    }

    /**
     * Tests {@link JaninoOption#JAVAC_COMPLIANCE}: the deviations S-01 to S-03.
     */
    @Test public void
    testJavacCompliance() throws Exception {

        // S-01: "Z || true" unboxes "Z".
        String s01 = "Boolean Z = null; return Z || true;";
        Assert.assertEquals(true, OptionsTest.evaluateScript(boolean.class, s01));
        try {
            OptionsTest.evaluateScript(boolean.class, s01, JaninoOption.JAVAC_COMPLIANCE);
            Assert.fail("NullPointerException expected");
        } catch (InvocationTargetException ite) {
            Assert.assertTrue(String.valueOf(ite.getCause()), ite.getCause() instanceof NullPointerException);
        }

        // S-02: "f(1)" invokes "f(Object)", not "f(int...)".
        String s02 = (
            ""
            + "class P {\n"
            + "    static String f(Object x) { return \"Object\"; }\n"
            + "    static String f(int... x) { return \"varargs\"; }\n"
            + "}\n"
            + "return P.f(1);\n"
        );
        Assert.assertEquals("varargs", OptionsTest.evaluateScript(String.class, s02));
        Assert.assertEquals("Object", OptionsTest.evaluateScript(String.class, s02, JaninoOption.JAVAC_COMPLIANCE));

        // S-03: "assert" is disabled unless assertions are enabled for the class.
        String s03 = "try { assert false; return \"not thrown\"; } catch (AssertionError e) { return \"thrown\"; }";
        Assert.assertEquals("thrown",     OptionsTest.evaluateScript(String.class, s03));
        Assert.assertEquals("not thrown", OptionsTest.evaluateScript(String.class, s03, JaninoOption.JAVAC_COMPLIANCE));
        {
            ScriptEvaluator se = new ScriptEvaluator();
            se.options(EnumSet.of(JaninoOption.JAVAC_COMPLIANCE));
            se.setReturnType(String.class);
            se.cook(s03);
            se.getMethod().getDeclaringClass().getClassLoader().setDefaultAssertionStatus(true);
            Assert.assertEquals("thrown", se.evaluate());
        }
    }

    private static Object
    evaluateScript(Class<?> returnType, String script, JaninoOption... options) throws Exception {
        ScriptEvaluator se = new ScriptEvaluator();
        EnumSet<JaninoOption> optionSet = EnumSet.noneOf(JaninoOption.class);
        optionSet.addAll(Arrays.asList(options));
        se.options(optionSet);
        se.setReturnType(returnType);
        se.cook(script);
        return se.evaluate();
    }

    private static void
    assertScriptExecutable(String script, JaninoOption... options)
    throws CompileException, InvocationTargetException {
        ScriptEvaluator se = new ScriptEvaluator();
        se.setDebuggingInformation(true, true, true);
        se.options(EnumSet.copyOf(Arrays.asList(options)));
        se.cook(script);
        se.evaluate();
    }

    private static void
    assertScriptCompilationError(String expectedInfix, String script, JaninoOption... options) {
        ScriptEvaluator se = new ScriptEvaluator();
        if (options.length >= 1) se.options(EnumSet.copyOf(Arrays.asList(options)));
        try {
            se.cook(script);
            Assert.fail("CompileException expected");
        } catch (CompileException ce) {
            Assert.assertTrue(
                "Compilation error message\"" + ce.getMessage() + "\" does not contain \"" + expectedInfix + "\"",
                ce.getMessage().contains(expectedInfix)
            );
        }
    }
}
