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
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.codehaus.commons.compiler.ISimpleCompiler;
import org.codehaus.commons.compiler.InternalCompilerException;
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
 * Characterization tests for the support of Java language constructs.
 * <p>
 *   Each case is a compilation unit that declares a class {@code P} with a method {@code public static Object
 *   run()}. The cases are read from the files in {@value #RESOURCE_DIR}, in the following format (see {@link
 *   Records} for the keys that all record files have in common):
 * </p>
 * <pre>
 * === <var>id</var>
 * jls: <var>the correct result: the value of String.valueOf(P.run()), or "throws <var>exception class</var>"</var>
 * janino: <var>the current behavior of JANINO in the compatibility mode, see below</var>
 * compliant: <var>the behavior in the compliance mode (optional, default: the "janino" line)</var>
 * id: <var>the ID of the deviation, or the issue number (required iff "compliant" is present)</var>
 * minJava: <var>the minimum JVM version for the JDK-based compiler (optional, default 8)</var>
 * <var>the source code</var>
 * </pre>
 * <p>
 *   The current behavior of JANINO is one of:
 * </p>
 * <dl>
 *   <dt>{@code OK}</dt>
 *   <dd>JANINO produces the correct result</dd>
 *   <dt>{@code REJECTED <var>message</var>}</dt>
 *   <dd>JANINO reports a compile error whose message contains <var>message</var></dd>
 *   <dt>{@code WRONG <var>result</var>}</dt>
 *   <dd>JANINO compiles the code, but it produces a wrong result</dd>
 *   <dt>{@code INVALID <var>error class</var>}</dt>
 *   <dd>
 *     JANINO generates a class file that the JVM rejects (e.g. {@code VerifyError}), or fails with an internal
 *     compiler error ({@code InternalCompilerException})
 *   </dd>
 * </dl>
 * <p>
 *   The JDK-based compiler must produce the correct result, which verifies the "jls" lines. JANINO must behave
 *   exactly as recorded, in both modes (see {@link TestUtil#getCompilerFactoriesAndModesForParameters()}); thus every
 *   change of its behavior, intended or not, makes a test fail, and the record must be updated deliberately.
 * </p>
 */
@RunWith(Parameterized.class) public
class LanguageSupportTest extends CommonsCompilerTestSuite {

    static final String RESOURCE_DIR = "src/test/resources/languageSupport";

    private static final String[] RESOURCE_FILES = {
        "java1.txt",
        "java5.txt",
        "annotations.txt",
        "java7.txt",
        "java8.txt",
        "java9-11.txt",
        "java14-21.txt",
        "edge-cases.txt",
        "control-flow.txt",
        "finally.txt",
        "access.txt",
        "member-types.txt",
    };

    private static final Pattern LOCATION_PREFIX = Pattern.compile("^(?:(?:File '[^']*', )?Line \\d+, Column \\d+: )+");

    private static final String JLS = "jls";

    private final String mode;
    private final Case   testCase;

    @Parameters(name = "{0}, {1}, {2}") public static List<Object[]>
    parameters() throws Exception {

        List<Case> cases = LanguageSupportTest.readCases();

        List<Object[]> result = new ArrayList<>();
        for (Object[] compilerFactoryAndMode : TestUtil.getCompilerFactoriesAndModesForParameters()) {
            for (Case c : cases) result.add(new Object[] { compilerFactoryAndMode[0], compilerFactoryAndMode[1], c });
        }
        return result;
    }

    public
    LanguageSupportTest(ICompilerFactory compilerFactory, String mode, Case testCase) {
        super(compilerFactory);
        this.mode     = mode;
        this.testCase = testCase;
    }

    @Test public void
    test() throws Exception {

        String jls = this.testCase.get(LanguageSupportTest.JLS);
        assert jls != null;

        Outcome outcome = LanguageSupportTest.execute(this.compilerFactory, this.mode, this.testCase.source);

        if (this.isJdk) {
            Assume.assumeTrue(CommonsCompilerTestSuite.JVM_VERSION >= this.testCase.minJava);
            Assert.assertEquals(this.testCase.toString(), jls, outcome.toString());
            return;
        }

        String expected = this.testCase.expected(this.mode);
        if (expected.startsWith("REJECTED ") && outcome.kind == Outcome.Kind.REJECTED) {
            String expectedMessage = expected.substring(9);
            if (outcome.text.contains(expectedMessage)) return;
        }
        Assert.assertEquals(
            this.testCase + " (" + this.mode + ")",
            expected,
            LanguageSupportTest.record(outcome, jls)
        );
    }

    /**
     * @return The behavior of JANINO, in the format of the "janino" lines
     */
    static String
    record(Outcome outcome, String jls) {
        switch (outcome.kind) {
        case VALUE:
        case THROWS:
            return outcome.toString().equals(jls) ? "OK" : "WRONG " + outcome;
        case REJECTED:
            return "REJECTED " + outcome.text;
        case INVALID:
            return "INVALID " + outcome.text;
        default:
            throw new AssertionError(outcome.kind);
        }
    }

    /**
     * Compiles the <var>source</var>, loads class {@code P} with assertions disabled, and invokes its method {@code
     * run()}.
     */
    static Outcome
    execute(ICompilerFactory compilerFactory, String mode, String source) throws Exception {

        Object result;
        try {
            ISimpleCompiler sc = TestUtil.newSimpleCompiler(compilerFactory, mode);
            sc.cook(source);

            // Make the result independent of "-ea".
            ClassLoader cl = sc.getClassLoader();
            cl.setClassAssertionStatus("P", false);

            result = cl.loadClass("P").getMethod("run").invoke(null);
        } catch (CompileException ce) {
            String message = String.valueOf(ce.getMessage());
            return new Outcome(Outcome.Kind.REJECTED, LanguageSupportTest.LOCATION_PREFIX.matcher(message).replaceFirst(""));
        } catch (InternalCompilerException ice) {
            return new Outcome(Outcome.Kind.INVALID, "InternalCompilerException");
        } catch (VerifyError | ClassFormatError e) {
            return new Outcome(Outcome.Kind.INVALID, e.getClass().getSimpleName());
        } catch (InvocationTargetException ite) {
            Throwable cause = ite.getCause();
            assert cause != null;
            if (cause instanceof VerifyError) return new Outcome(Outcome.Kind.INVALID, "VerifyError");
            return new Outcome(Outcome.Kind.THROWS, cause.getClass().getName());
        } catch (ExceptionInInitializerError eiie) {
            return new Outcome(Outcome.Kind.THROWS, eiie.getClass().getName());
        }

        return new Outcome(Outcome.Kind.VALUE, String.valueOf(result));
    }

    /**
     * Reads all cases from the {@link #RESOURCE_FILES}.
     */
    static List<Case>
    readCases() throws IOException {
        return Records.read(
            LanguageSupportTest.RESOURCE_DIR,
            LanguageSupportTest.RESOURCE_FILES,
            new String[] { LanguageSupportTest.JLS }, // requiredKeys
            new String[0]                             // optionalKeys
        );
    }

    /**
     * The outcome of compiling and executing a case.
     */
    static final
    class Outcome {

        enum Kind { VALUE, THROWS, REJECTED, INVALID }

        final Kind   kind;
        final String text;

        Outcome(Kind kind, String text) {
            this.kind = kind;
            this.text = text;
        }

        /**
         * @return The outcome in the format of the "jls" lines (for {@link Kind#VALUE} and {@link Kind#THROWS})
         */
        @Override public String
        toString() {
            switch (this.kind) {
            case VALUE:    return this.text;
            case THROWS:   return "throws " + this.text;
            case REJECTED: return "compile error: " + this.text;
            case INVALID:  return "invalid: " + this.text;
            default:       throw new AssertionError(this.kind);
            }
        }
    }
}
