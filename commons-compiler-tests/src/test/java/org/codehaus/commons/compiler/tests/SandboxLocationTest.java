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

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.ErrorHandler;
import org.codehaus.commons.compiler.IClassBodyEvaluator;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.codehaus.commons.compiler.ICookable;
import org.codehaus.commons.compiler.IExpressionEvaluator;
import org.codehaus.commons.compiler.IScriptEvaluator;
import org.codehaus.commons.compiler.ISimpleCompiler;
import org.codehaus.commons.compiler.Location;
import org.codehaus.commons.compiler.sandbox.ClassFileReader;
import org.codehaus.commons.compiler.sandbox.SandboxPolicy;
import org.codehaus.commons.compiler.sandbox.SandboxViolation;
import org.codehaus.commons.compiler.sandbox.SandboxViolationException;
import org.codehaus.commons.nullanalysis.Nullable;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import util.TestUtil;

/**
 * Tests for the source locations of sandbox violations, see {@link SandboxViolation#getLocation()}.
 */
@RunWith(Parameterized.class) public
class SandboxLocationTest {

    private static final String GET_PROPERTY = (
        "Access to java.lang.System.getProperty(java.lang.String) is not permitted by the sandbox policy"
    );

    private final ICompilerFactory compilerFactory;

    @Parameters(name = "CompilerFactory={0}") public static List<Object[]>
    compilerFactories() throws Exception { return TestUtil.getCompilerFactoriesForParameters(); }

    public
    SandboxLocationTest(ICompilerFactory compilerFactory) { this.compilerFactory = compilerFactory; }

    /**
     * Verifies the locations of the violations in a script, including a violation in an anonymous class, which is
     * compiled into a separate class file.
     */
    @Test public void
    testScript() throws Exception {

        IScriptEvaluator se = this.compilerFactory.newScriptEvaluator();
        se.setSandboxPolicy(SandboxPolicy.JAVA_LANG_BASIC);

        CompileException ce = SandboxLocationTest.assertCookFails(se, "script.txt", (
            ""
            + "int x = 1;\n"
            + "String a = System.getProperty(\"a\");\n"
            + "Object o = System.out;\n"
            + "Runnable r = new Runnable() { public void run() { Runtime.getRuntime(); } };\n"
            + "String b = System.getProperty(\"b\");\n"
        ));

        SandboxLocationTest.assertLocation("script.txt", 2, ce.getLocation());

        List<SandboxViolation> violations = SandboxLocationTest.getViolations(ce);
        Assert.assertEquals(violations.toString(), 4, violations.size());
        SandboxLocationTest.assertViolation("script.txt", 2, SandboxLocationTest.GET_PROPERTY, violations.get(0));
        SandboxLocationTest.assertViolation(
            "script.txt",
            3,
            "Access to java.lang.System.out is not permitted by the sandbox policy",
            violations.get(1)
        );
        SandboxLocationTest.assertViolation(
            "script.txt",
            4,
            "Access to java.lang.Runtime.getRuntime() is not permitted by the sandbox policy",
            violations.get(2)
        );
        SandboxLocationTest.assertViolation("script.txt", 5, SandboxLocationTest.GET_PROPERTY, violations.get(3));
    }

    /**
     * Verifies that the message of a single violation is that of the violation, and that a document without a name
     * yields locations without a file name.
     */
    @Test public void
    testExpression() throws Exception {

        IExpressionEvaluator ee = this.compilerFactory.newExpressionEvaluator();
        ee.setSandboxPolicy(SandboxPolicy.JAVA_LANG_BASIC);

        CompileException ce = SandboxLocationTest.assertCookFails(ee, null, "System.getProperty(\"a\")");

        Assert.assertEquals("Line 1: " + SandboxLocationTest.GET_PROPERTY, ce.getMessage());
        SandboxLocationTest.assertLocation(null, 1, ce.getLocation());
    }

    @Test public void
    testClassBody() throws Exception {

        IClassBodyEvaluator cbe = this.compilerFactory.newClassBodyEvaluator();
        cbe.setSandboxPolicy(SandboxPolicy.JAVA_LANG_BASIC);

        CompileException ce = SandboxLocationTest.assertCookFails(cbe, "body.txt", (
            ""
            + "public static void meth() {\n"
            + "    int x = 0;\n"
            + "    System.exit(x);\n"
            + "}\n"
        ));

        SandboxLocationTest.assertLocation("body.txt", 3, ce.getLocation());
    }

    /**
     * Verifies the location of a violating method reference, which is a bootstrap argument of an INVOKEDYNAMIC
     * instruction. (JANINO does not yet support method references.)
     */
    @Test public void
    testMethodReference() throws Exception {
        Assume.assumeTrue("JDK only", "org.codehaus.commons.compiler.jdk".equals(this.compilerFactory.getId()));

        ISimpleCompiler sc = this.compilerFactory.newSimpleCompiler();
        sc.setSandboxPolicy(SandboxPolicy.JAVA_LANG_BASIC);

        CompileException ce = SandboxLocationTest.assertCookFails(sc, "Foo.java", (
            ""
            + "public class Foo {\n"
            + "    public static Object meth() {\n"
            + "        java.util.function.Supplier<Runtime> s = Runtime::getRuntime;\n"
            + "        return s;\n"
            + "    }\n"
            + "}\n"
        ));

        Assert.assertEquals(
            "File 'Foo.java', Line 3: Access to java.lang.Runtime.getRuntime() is not permitted by the sandbox policy",
            ce.getMessage()
        );
    }

    /**
     * Verifies that all violations are reported through the {@link ErrorHandler}, and that cooking fails
     * nevertheless.
     */
    @Test public void
    testErrorHandler() throws Exception {

        ISimpleCompiler sc = this.compilerFactory.newSimpleCompiler();
        sc.setSandboxPolicy(SandboxPolicy.JAVA_LANG_BASIC);

        final List<String> errors = new ArrayList<String>();
        sc.setCompileErrorHandler(new ErrorHandler() {

            @Override public void
            handleError(String message, @Nullable Location location) { errors.add(location + ": " + message); }
        });

        SandboxLocationTest.assertCookFails(sc, "Foo.java", (
            ""
            + "public class Foo {\n"
            + "    public static void meth() {\n"
            + "        System.getProperty(\"a\");\n"
            + "        System.exit(0);\n"
            + "    }\n"
            + "}\n"
        ));

        Assert.assertEquals(2, errors.size());
        Assert.assertEquals("File 'Foo.java', Line 3: " + SandboxLocationTest.GET_PROPERTY, errors.get(0));
        Assert.assertEquals(
            "File 'Foo.java', Line 4: Access to java.lang.System.exit(int) is not permitted by the sandbox policy",
            errors.get(1)
        );
    }

    /**
     * Verifies that a {@code finalize()} declaration is reported with the location of the method.
     */
    @Test public void
    testFinalize() throws Exception {

        ISimpleCompiler sc = this.compilerFactory.newSimpleCompiler();
        sc.setSandboxPolicy(SandboxPolicy.JAVA_LANG_BASIC);

        CompileException ce = SandboxLocationTest.assertCookFails(sc, "Foo.java", (
            ""
            + "public class Foo {\n"
            + "    protected void finalize() {}\n"
            + "}\n"
        ));

        Assert.assertEquals(
            (
                "File 'Foo.java', Line 2: "
                + "Declaring finalize() is not permitted, because the JVM calls it outside of the sandbox"
            ),
            ce.getMessage()
        );
    }

    /**
     * Verifies that a violation that concerns the class as a whole (here: a forbidden interface) has no location.
     */
    @Test public void
    testViolationWithoutLocation() throws Exception {

        ISimpleCompiler sc = this.compilerFactory.newSimpleCompiler();
        sc.setSandboxPolicy(SandboxPolicy.JAVA_LANG_BASIC);

        CompileException ce = SandboxLocationTest.assertCookFails(
            sc,
            "Foo.java",
            "public class Foo implements java.util.RandomAccess {}"
        );

        List<SandboxViolation> violations = SandboxLocationTest.getViolations(ce);
        Assert.assertEquals(violations.toString(), 1, violations.size());
        Assert.assertNull(violations.get(0).getLocation());
        Assert.assertEquals(
            "Foo: Implementing java.util.RandomAccess is not permitted by the sandbox policy",
            violations.get(0).toString()
        );
    }

    /**
     * Verifies that the debugging information that the source locations require is generated if and only if a
     * sandbox policy is set (unless debugging information is enabled explicitly).
     */
    @Test public void
    testDebuggingInformation() throws Exception {

        String cu = "public class Foo { public static String meth() { return String.valueOf(7); } }";

        ISimpleCompiler sc = this.compilerFactory.newSimpleCompiler();
        sc.cook("Foo.java", new StringReader(cu));
        ClassFileReader cfr = new ClassFileReader(SandboxLocationTest.getClassFile(sc));
        Assert.assertNull(cfr.getSourceFileName());
        Assert.assertTrue(cfr.getLineNumbers(cfr.getMemberReferences().get(0)).isEmpty());

        ISimpleCompiler sc2 = this.compilerFactory.newSimpleCompiler();
        sc2.setSandboxPolicy(SandboxPolicy.JAVA_LANG_BASIC);
        sc2.cook("Foo.java", new StringReader(cu));
        ClassFileReader cfr2 = new ClassFileReader(SandboxLocationTest.getClassFile(sc2));
        Assert.assertEquals("Foo.java", cfr2.getSourceFileName());
        boolean found = false;
        for (ClassFileReader.MemberReference mr : cfr2.getMemberReferences()) {
            if ("valueOf".equals(mr.getName())) {
                Assert.assertEquals("[1]", cfr2.getLineNumbers(mr).toString());
                found = true;
            }
        }
        Assert.assertTrue(found);
    }

    // ====================================== END OF TEST CASES ======================================

    /**
     * Cooks the <var>document</var> and asserts that cooking fails because of a sandbox violation.
     *
     * @return The {@link CompileException}
     */
    private static CompileException
    assertCookFails(ICookable cookable, @Nullable String fileName, String document) throws Exception {
        try {
            cookable.cook(fileName, new StringReader(document));
        } catch (CompileException ce) {
            Assert.assertTrue(String.valueOf(ce.getCause()), ce.getCause() instanceof SandboxViolationException);
            return ce;
        }
        Assert.fail("CompileException expected");
        throw new AssertionError();
    }

    /**
     * @return The one and only class file that the <var>simpleCompiler</var> generated
     */
    private static byte[]
    getClassFile(ISimpleCompiler simpleCompiler) {
        Collection<byte[]> classFiles = simpleCompiler.getBytecodes().values();
        Assert.assertEquals(1, classFiles.size());
        return classFiles.iterator().next();
    }

    private static List<SandboxViolation>
    getViolations(CompileException ce) {
        return ((SandboxViolationException) ce.getCause()).getViolations();
    }

    private static void
    assertLocation(@Nullable String expectedFileName, int expectedLineNumber, @Nullable Location actual) {
        Assert.assertNotNull(actual);
        assert actual != null;
        Assert.assertEquals(expectedFileName, actual.getFileName());
        Assert.assertEquals(expectedLineNumber, actual.getLineNumber());
        Assert.assertEquals(0, actual.getColumnNumber());
    }

    private static void
    assertViolation(
        @Nullable String expectedFileName,
        int              expectedLineNumber,
        String           expectedMessage,
        SandboxViolation actual
    ) {
        Assert.assertEquals(expectedFileName, actual.getFileName());
        Assert.assertEquals(expectedLineNumber, actual.getLineNumber());
        Assert.assertEquals(expectedMessage, actual.getMessage());
    }
}
