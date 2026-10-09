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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.codehaus.commons.compiler.tests.Records.Case;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Compiles the cases of {@link InvalidCodeTest} and {@link LanguageSupportTest} with JANINO 3.1.12, the reference of
 * the compatibility mode (see {@code JAVAC_COMPLIANCE.md}), and compares its behavior with the recorded behavior of
 * the compatibility mode (the "janino" lines).
 * <p>
 *   The two must be of the same kind (see {@link Records#kind(String)}; the message of a compile error is not
 *   behavior), unless the record has a "legacy" line, which states the behavior of 3.1.12 (see {@link
 *   Records#matches(String, String)}) and, with its "id" line, which correction (an issue, or an ID of {@code
 *   JAVAC_DIFFERENCES.md}) explains the difference. Thus every difference between the compatibility mode and 3.1.12
 *   is recorded and justified, and an unrecorded difference, e.g. a regression, makes a test fail.
 * </p>
 * <p>
 *   The JAR files of 3.1.12 are copied to {@code target/legacy} by the build; the system property {@code
 *   janino.legacy.dir} names another directory. With the system property {@code legacy.differential.report}, the
 *   test prints the differences instead of failing (to write the "legacy" lines).
 * </p>
 */
@RunWith(Parameterized.class) public
class LegacyDifferentialTest {

    private static final String DEFAULT_LEGACY_DIR = "target/legacy";

    /**
     * {@code true} for the cases of {@link InvalidCodeTest}, {@code false} for those of {@link LanguageSupportTest}.
     */
    private final boolean invalidCode;
    private final Case    testCase;

    @Parameters(name = "{1}") public static List<Object[]>
    parameters() throws Exception {
        List<Object[]> result = new ArrayList<>();
        for (Case c : InvalidCodeTest.readCases())     result.add(new Object[] { true,  c });
        for (Case c : LanguageSupportTest.readCases()) result.add(new Object[] { false, c });
        return result;
    }

    public
    LegacyDifferentialTest(boolean invalidCode, Case testCase) {
        this.invalidCode = invalidCode;
        this.testCase    = testCase;
    }

    @Test public void
    test() throws Exception {

        SourceCompiler legacy = LegacyDifferentialTest.legacy();

        String janino = this.testCase.get(Records.JANINO);
        assert janino != null;

        String actual;
        if (this.invalidCode) {
            actual = InvalidCodeTest.compile(legacy, this.testCase.source);
        } else {
            String jls = this.testCase.get("jls");
            assert jls != null;
            actual = LanguageSupportTest.record(LanguageSupportTest.execute(legacy, this.testCase.source), jls);
        }

        String  recorded = this.testCase.get(Records.LEGACY);
        String  expected = recorded != null ? recorded : Records.kind(janino);
        String  mismatch = (
            Records.matches(expected, actual)
            ? null
            : (
                this.testCase
                + ": janino: "
                + janino
                + (recorded != null ? " | legacy (recorded): " + recorded : "")
                + " | legacy (actual): "
                + actual
            )
        );

        if (Boolean.getBoolean("legacy.differential.report")) {
            if (mismatch != null) System.out.println("LEGACY " + mismatch);
            return;
        }

        if (mismatch != null) {
            Assert.fail((
                recorded != null
                ? "The recorded behavior of 3.1.12 is wrong; "
                : "3.1.12 behaves differently, which the record does not state (add \"legacy:\" and \"id:\"); "
            ) + mismatch);
        }
    }

    private static SourceCompiler legacyCompiler;

    private static synchronized SourceCompiler
    legacy() throws Exception {
        if (LegacyDifferentialTest.legacyCompiler == null) {
            File dir = new File(System.getProperty("janino.legacy.dir", LegacyDifferentialTest.DEFAULT_LEGACY_DIR));
            Assume.assumeTrue("The legacy JANINO is not available in \"" + dir + "\"", dir.isDirectory());
            LegacyDifferentialTest.legacyCompiler = SourceCompiler.legacy(dir);
        }
        return LegacyDifferentialTest.legacyCompiler;
    }
}
