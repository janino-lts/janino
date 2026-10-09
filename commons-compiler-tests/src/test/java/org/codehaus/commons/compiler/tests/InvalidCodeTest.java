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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.codehaus.commons.compiler.ISimpleCompiler;
import org.codehaus.commons.compiler.tests.Records.Case;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import util.CommonsCompilerTestSuite;
import util.TestUtil;

/**
 * Characterization tests for <em>invalid</em> code: each case is a compilation unit that violates a rule of the JLS,
 * and must be rejected with a {@link CompileException}. (For valid code, see {@link LanguageSupportTest}.)
 * <p>
 *   See <a href="https://github.com/janino-lts/janino/issues/29">issue #29</a>. The cases are read from the files in
 *   {@value #RESOURCE_DIR}, in the following format (see {@link Records} for the keys that all record files have in
 *   common):
 * </p>
 * <pre>
 * === <var>id</var>
 * janino: <var>the behavior of JANINO with the default error handler, in the compatibility mode, see below</var>
 * compliant: <var>the behavior in the compliance mode (optional, default: the "janino" line)</var>
 * id: <var>the ID of the deviation, or the issue number (required iff "compliant" is present)</var>
 * recovery: <var>the behavior of JANINO with an error handler that does not throw (optional, default "OK")</var>
 * minJava: <var>the minimum JVM version for the JDK-based compiler (optional, default 8)</var>
 * <var>the source code</var>
 * </pre>
 * <p>
 *   The behavior of JANINO with the default error handler is one of:
 * </p>
 * <dl>
 *   <dt>{@code REJECTED <var>message</var>}</dt>
 *   <dd>JANINO reports a compile error whose message contains <var>message</var> (the correct behavior)</dd>
 *   <dt>{@code ACCEPTED}</dt>
 *   <dd>JANINO compiles the code, and the JVM loads the generated classes</dd>
 *   <dt>{@code INVALID <var>class</var>}</dt>
 *   <dd>JANINO fails with another exception or error (e.g. {@code InternalCompilerException})</dd>
 *   <dt>{@code INVALID LinkageError}</dt>
 *   <dd>
 *     JANINO generates a class file that the JVM rejects (e.g. with a {@code VerifyError}; the exact error depends on
 *     the version of the JVM)
 *   </dd>
 * </dl>
 * <p>
 *   With an error handler that does not throw, JANINO must continue compiling after a compile error, and report the
 *   errors through the handler (see {@code UnitCompiler.compileError()}). The behavior is one of:
 * </p>
 * <dl>
 *   <dt>{@code OK}</dt>
 *   <dd>JANINO reports at least one compile error, or throws a {@link CompileException} (the correct behavior)</dd>
 *   <dt>{@code ACCEPTED}</dt>
 *   <dd>JANINO compiles the code without reporting an error</dd>
 *   <dt>{@code INVALID <var>class</var>}</dt>
 *   <dd>JANINO fails with another exception or error</dd>
 * </dl>
 * <p>
 *   The "recovery" line describes the compatibility mode; in the compliance mode, it is checked only for cases
 *   without a "compliant" line.
 * </p>
 * <p>
 *   The JDK-based compiler must reject every case, which verifies that the case is invalid code. JANINO must behave
 *   exactly as recorded, in both modes (see {@link TestUtil#getCompilerFactoriesAndModesForParameters()}); thus every
 *   change of its behavior, intended or not, makes a test fail, and the record must be updated deliberately.
 * </p>
 * <p>
 *   The records describe JANINO with assertions enabled ("-ea", as Maven's Surefire plugin runs the tests): some of
 *   its internal consistency checks are assertions, so a few cases behave differently without them. Without
 *   assertions, the cases for JANINO are skipped.
 * </p>
 */
@RunWith(Parameterized.class) public
class InvalidCodeTest extends CommonsCompilerTestSuite {

    static final String RESOURCE_DIR = "src/test/resources/invalidCode";

    private static final String[] RESOURCE_FILES = {
        "declarations.txt",
        "statements.txt",
        "expressions.txt",
        "names.txt",
        "parser.txt",
    };

    private static final Pattern LOCATION_PREFIX = Pattern.compile("^(?:(?:File '[^']*', )?Line \\d+, Column \\d+: )+");

    private static final String RECOVERY = "recovery";

    private final String mode;
    private final Case   testCase;

    @Parameters(name = "{0}, {1}, {2}") public static List<Object[]>
    parameters() throws Exception {

        List<Case> cases = InvalidCodeTest.readCases();

        List<Object[]> result = new ArrayList<>();
        for (Object[] compilerFactoryAndMode : TestUtil.getCompilerFactoriesAndModesForParameters()) {
            for (Case c : cases) result.add(new Object[] { compilerFactoryAndMode[0], compilerFactoryAndMode[1], c });
        }
        return result;
    }

    public
    InvalidCodeTest(ICompilerFactory compilerFactory, String mode, Case testCase) {
        super(compilerFactory);
        this.mode     = mode;
        this.testCase = testCase;
    }

    @Test public void
    test() throws Exception {

        if (this.isJdk) {
            Assume.assumeTrue(CommonsCompilerTestSuite.JVM_VERSION >= this.testCase.minJava);
            String outcome = InvalidCodeTest.compile(this.compilerFactory, this.mode, this.testCase.source);
            Assert.assertTrue(
                this.testCase + ": The JDK-based compiler does not reject the code: " + outcome,
                outcome.startsWith("REJECTED ")
            );
            return;
        }

        // The records describe JANINO with assertions enabled (as the tests usually run); some of its internal
        // consistency checks are assertions.
        Assume.assumeTrue(
            "The records require that assertions are enabled for JANINO",
            this.compilerFactory.getClass().desiredAssertionStatus()
        );

        String expected = this.testCase.expected(this.mode);
        String outcome  = InvalidCodeTest.compile(this.compilerFactory, this.mode, this.testCase.source);
        if (!(
            expected.startsWith("REJECTED ")
            && outcome.startsWith("REJECTED ")
            && outcome.contains(expected.substring(9))
        )) Assert.assertEquals(this.testCase + " (" + this.mode + ")", expected, outcome);

        if (TestUtil.COMPAT.equals(this.mode) || this.testCase.get(Records.COMPLIANT) == null) {
            Assert.assertEquals(
                this.testCase + " (" + this.mode + ", recovery)",
                this.testCase.get(InvalidCodeTest.RECOVERY, "OK"),
                InvalidCodeTest.compileWithRecovery(this.compilerFactory, this.mode, this.testCase.source)
            );
        }
    }

    /**
     * Compiles the <var>source</var> with the default error handler and, if that succeeds, loads and initializes all
     * generated classes.
     *
     * @return The behavior, in the format of the "janino" lines
     */
    static String
    compile(ICompilerFactory compilerFactory, String mode, String source) throws Exception {

        ISimpleCompiler sc = TestUtil.newSimpleCompiler(compilerFactory, mode);
        try {
            sc.cook(source);
        } catch (CompileException ce) {
            String message = String.valueOf(ce.getMessage());
            return "REJECTED " + InvalidCodeTest.LOCATION_PREFIX.matcher(message).replaceFirst("");
        } catch (Throwable t) {
            return "INVALID " + t.getClass().getSimpleName();
        }

        ClassLoader cl = new UninstrumentedClassLoader(sc.getBytecodes());
        for (String className : sc.getBytecodes().keySet()) {
            try {
                Class.forName(className, true, cl);
            } catch (ExceptionInInitializerError eiie) {
                // The generated code is valid; it only throws an exception.
            } catch (LinkageError le) {

                // The JVM rejects the generated class; the exact error (e.g. "VerifyError" or
                // "IncompatibleClassChangeError") depends on the version of the JVM.
                return "INVALID LinkageError";
            }
        }
        return "ACCEPTED";
    }

    /**
     * Defines classes without a code source location. Coverage agents (e.g. JaCoCo) do not instrument such classes;
     * instrumenting rewrites a class file, and would hide some of its defects (e.g. duplicate entries in the
     * "InnerClasses" attribute).
     */
    private static final
    class UninstrumentedClassLoader extends ClassLoader {

        private final Map<String, byte[]> classes;

        UninstrumentedClassLoader(Map<String, byte[]> classes) {
            super(InvalidCodeTest.class.getClassLoader());
            this.classes = classes;
        }

        @Override protected Class<?>
        findClass(String name) throws ClassNotFoundException {
            byte[] b = this.classes.get(name);
            if (b == null) throw new ClassNotFoundException(name);
            return this.defineClass(name, b, 0, b.length);
        }
    }

    /**
     * Compiles the <var>source</var> with an error handler that counts the errors, but does not throw.
     *
     * @return The behavior, in the format of the "recovery" lines
     */
    static String
    compileWithRecovery(ICompilerFactory compilerFactory, String mode, String source) {
        int[] errorCount = new int[1];
        try {
            ISimpleCompiler sc = TestUtil.newSimpleCompiler(compilerFactory, mode);
            sc.setCompileErrorHandler((message, location) -> errorCount[0]++);
            sc.cook(source);
        } catch (CompileException ce) {
            return "OK";
        } catch (Throwable t) {
            return "INVALID " + t.getClass().getSimpleName();
        }
        return errorCount[0] > 0 ? "OK" : "ACCEPTED";
    }

    /**
     * Reads all cases from the {@link #RESOURCE_FILES}.
     */
    static List<Case>
    readCases() throws IOException {
        return Records.read(
            InvalidCodeTest.RESOURCE_DIR,
            InvalidCodeTest.RESOURCE_FILES,
            new String[0],                            // requiredKeys
            new String[] { InvalidCodeTest.RECOVERY } // optionalKeys
        );
    }
}
