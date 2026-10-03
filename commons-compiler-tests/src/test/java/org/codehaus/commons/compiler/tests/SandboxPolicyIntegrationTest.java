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

import java.util.List;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.IClassBodyEvaluator;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.codehaus.commons.compiler.ICookable;
import org.codehaus.commons.compiler.IExpressionEvaluator;
import org.codehaus.commons.compiler.IScriptEvaluator;
import org.codehaus.commons.compiler.ISimpleCompiler;
import org.codehaus.commons.compiler.sandbox.SandboxPolicy;
import org.codehaus.commons.compiler.sandbox.SandboxViolationException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import util.TestUtil;

/**
 * Tests for {@link ICookable#setSandboxPolicy(SandboxPolicy)}, for all kinds of cookables.
 */
@RunWith(Parameterized.class) public
class SandboxPolicyIntegrationTest {

    private static final SandboxPolicy POLICY = SandboxPolicy.builder()
        .include(SandboxPolicy.JAVA_LANG_BASIC)
        .include(SandboxPolicy.COLLECTIONS)
        .build();

    private final ICompilerFactory compilerFactory;

    @Parameters(name = "CompilerFactory={0}") public static List<Object[]>
    compilerFactories() throws Exception { return TestUtil.getCompilerFactoriesForParameters(); }

    public
    SandboxPolicyIntegrationTest(ICompilerFactory compilerFactory) { this.compilerFactory = compilerFactory; }

    @Test public void
    testExpressionEvaluator() throws Exception {

        IExpressionEvaluator ee = this.compilerFactory.newExpressionEvaluator();
        ee.setSandboxPolicy(SandboxPolicyIntegrationTest.POLICY);
        ee.setParameters(new String[] { "a", "b" }, new Class<?>[] { int.class, int.class });
        ee.setExpressionType(int.class);
        ee.cook("Math.max(a, b) * 2");
        Assert.assertEquals(8, ee.evaluate(new Object[] { 3, 4 }));

        IExpressionEvaluator ee2 = this.compilerFactory.newExpressionEvaluator();
        ee2.setSandboxPolicy(SandboxPolicyIntegrationTest.POLICY);
        SandboxPolicyIntegrationTest.assertCookFails(
            ee2,
            "System.getProperty(\"foo\")",
            "Access to java.lang.System.getProperty(java.lang.String) is not permitted by the sandbox policy"
        );
    }

    @Test public void
    testScriptEvaluator() throws Exception {

        IScriptEvaluator se = this.compilerFactory.newScriptEvaluator();
        se.setSandboxPolicy(SandboxPolicyIntegrationTest.POLICY);
        se.setReturnType(int.class);
        se.cook("int s = 0; for (int i = 0; i < 4; i++) s += i; return s;");
        Assert.assertEquals(6, se.evaluate());

        IScriptEvaluator se2 = this.compilerFactory.newScriptEvaluator();
        se2.setSandboxPolicy(SandboxPolicyIntegrationTest.POLICY);
        SandboxPolicyIntegrationTest.assertCookFails(se2, "System.exit(0);", "java.lang.System.exit(int)");
    }

    @Test public void
    testClassBodyEvaluator() throws Exception {

        IClassBodyEvaluator cbe = this.compilerFactory.newClassBodyEvaluator();
        cbe.setSandboxPolicy(SandboxPolicyIntegrationTest.POLICY);
        cbe.cook("public static int twice(int x) { return 2 * x; }");
        Assert.assertEquals(14, cbe.getClazz().getMethod("twice", int.class).invoke(null, 7));

        IClassBodyEvaluator cbe2 = this.compilerFactory.newClassBodyEvaluator();
        cbe2.setSandboxPolicy(SandboxPolicyIntegrationTest.POLICY);
        SandboxPolicyIntegrationTest.assertCookFails(
            cbe2,
            "public static boolean delete() { return new java.io.File(\"x\").delete(); }",
            "new java.io.File(java.lang.String)"
        );
    }

    @Test public void
    testSimpleCompiler() throws Exception {

        ISimpleCompiler sc = this.compilerFactory.newSimpleCompiler();
        sc.setSandboxPolicy(SandboxPolicyIntegrationTest.POLICY);
        sc.cook("public class Foo { public static String meth() { return \"x\".toUpperCase(); } }");
        Assert.assertEquals("X", sc.getClassLoader().loadClass("Foo").getMethod("meth").invoke(null));

        ISimpleCompiler sc2 = this.compilerFactory.newSimpleCompiler();
        sc2.setSandboxPolicy(SandboxPolicyIntegrationTest.POLICY);
        SandboxPolicyIntegrationTest.assertCookFails(
            sc2,
            "public class Foo { public static Object meth() { return Runtime.getRuntime(); } }",
            "java.lang.Runtime.getRuntime()"
        );

        // The rejected class must not be loadable.
        try {
            sc2.getClassLoader().loadClass("Foo");
            Assert.fail("ClassNotFoundException expected");
        } catch (ClassNotFoundException cnfe) {
            ;
        }
    }

    /**
     * Verifies that code that the host invokes later (here: a {@link Runnable} returned by the script) is subject
     * to the policy as well. (The legacy, security-manager-based sandbox only confined code that was executed
     * <em>inside</em> {@code Sandbox.confine()}.)
     */
    @Test public void
    testCallback() throws Exception {
        IScriptEvaluator se = this.compilerFactory.newScriptEvaluator();
        se.setSandboxPolicy(SandboxPolicyIntegrationTest.POLICY);
        se.setReturnType(Runnable.class);
        SandboxPolicyIntegrationTest.assertCookFails(
            se,
            "return new Runnable() { public void run() { System.exit(0); } };",
            "java.lang.System.exit(int)"
        );
    }

    /**
     * Verifies that the {@link SandboxViolationException} that is the cause of the {@link CompileException} lists
     * all violations.
     */
    @Test public void
    testAllViolationsAreReported() throws Exception {
        IScriptEvaluator se = this.compilerFactory.newScriptEvaluator();
        se.setSandboxPolicy(SandboxPolicyIntegrationTest.POLICY);
        try {
            se.cook("System.getProperty(\"a\"); Runtime.getRuntime(); Thread.currentThread();");
            Assert.fail("CompileException expected");
        } catch (CompileException ce) {
            Throwable cause = ce.getCause();
            Assert.assertTrue(String.valueOf(cause), cause instanceof SandboxViolationException);
            Assert.assertEquals(
                ((SandboxViolationException) cause).getViolations().toString(),
                3,
                ((SandboxViolationException) cause).getViolations().size()
            );
        }
    }

    /**
     * Verifies that cookables without a sandbox policy (and with a {@code null} policy) are not restricted.
     */
    @Test public void
    testNoPolicy() throws Exception {

        IExpressionEvaluator ee = this.compilerFactory.newExpressionEvaluator();
        ee.setExpressionType(String.class);
        ee.cook("System.getProperty(\"java.version\")");
        Assert.assertEquals(System.getProperty("java.version"), ee.evaluate());

        IExpressionEvaluator ee2 = this.compilerFactory.newExpressionEvaluator();
        ee2.setSandboxPolicy(SandboxPolicyIntegrationTest.POLICY);
        ee2.setSandboxPolicy(null);
        ee2.setExpressionType(String.class);
        ee2.cook("System.getProperty(\"java.version\")");
        Assert.assertEquals(System.getProperty("java.version"), ee2.evaluate());
    }

    private static void
    assertCookFails(ICookable cookable, String document, String expectedMessagePart) throws Exception {
        try {
            cookable.cook(document);
            Assert.fail("CompileException expected");
        } catch (CompileException ce) {
            Assert.assertTrue(ce.getMessage(), ce.getMessage().contains(expectedMessagePart));
            Assert.assertTrue(String.valueOf(ce.getCause()), ce.getCause() instanceof SandboxViolationException);
        }
    }
}
