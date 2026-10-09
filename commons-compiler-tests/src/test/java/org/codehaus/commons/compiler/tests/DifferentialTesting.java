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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.codehaus.commons.compiler.CompilerFactoryFactory;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.codehaus.commons.compiler.ISimpleCompiler;
import org.junit.Assert;
import org.junit.Assume;

import util.TestUtil;

/**
 * Utility methods for the tests that compile generated code with JANINO and with the JDK-based compiler, and compare
 * the results with the differences that are currently known (see {@link ControlFlowDifferentialTest}).
 */
final
class DifferentialTesting {

    private DifferentialTesting() {}

    /**
     * Skips the calling test (through {@link Assume}) unless both compilers are available.
     *
     * @return JANINO's compiler factory (element 0) and the JDK-based compiler factory (element 1)
     */
    static ICompilerFactory[]
    janinoAndJdk() throws Exception {

        ICompilerFactory janino = null, jdk = null;
        for (ICompilerFactory cf : CompilerFactoryFactory.getAllCompilerFactories(
            DifferentialTesting.class.getClassLoader()
        )) {
            if ("org.codehaus.janino".equals(cf.getId()))               janino = cf;
            if ("org.codehaus.commons.compiler.jdk".equals(cf.getId())) jdk    = cf;
        }
        Assume.assumeTrue("Both compilers must be available", janino != null && jdk != null);
        return new ICompilerFactory[] { janino, jdk };
    }

    /**
     * Compiles the <var>source</var> (one or more classes) with the given compiler, in the given mode (see {@link
     * TestUtil#getCompilerFactoriesAndModesForParameters()}).
     */
    static ClassLoader
    compile(ICompilerFactory compilerFactory, String mode, String source) throws Exception {
        ISimpleCompiler sc = TestUtil.newSimpleCompiler(compilerFactory, mode);
        sc.cook(source);
        return sc.getClassLoader();
    }

    /**
     * @return The parameters for a test that runs JANINO in both modes
     */
    static List<Object[]>
    modes() {
        return Arrays.asList(new Object[] { TestUtil.COMPAT }, new Object[] { TestUtil.COMPLIANT });
    }

    /**
     * @return The name of the file with the known differences for the given mode: <var>baseName</var>{@code .txt}
     *         for the compatibility mode, <var>baseName</var>{@code -compliant.txt} for the compliance mode
     */
    static String
    knownDifferencesFile(String baseName, String mode) {
        return baseName + (TestUtil.COMPLIANT.equals(mode) ? "-compliant.txt" : ".txt");
    }

    /**
     * Fails iff the <var>actualDifferences</var> deviate from those recorded in the file <var>knownDifferences</var>
     * in any way, and reports the new differences with their <var>details</var>.
     */
    static void
    assertDifferences(String knownDifferences, Set<String> actualDifferences, Map<String, String> details)
    throws IOException {

        Set<String> expected = DifferentialTesting.readKnownDifferences(knownDifferences);
        if (actualDifferences.equals(expected)) return;

        StringBuilder sb = new StringBuilder("The differences between JANINO and JAVAC have changed.");
        for (String d : actualDifferences) {
            if (!expected.contains(d)) sb.append("\nNew difference: ").append(d).append('\n').append(details.get(d));
        }
        for (String d : expected) {
            if (!actualDifferences.contains(d)) sb.append("\nNo longer a difference: ").append(d);
        }
        sb.append("\n\nAll current differences:");
        for (String d : actualDifferences) sb.append('\n').append(d);
        Assert.fail(sb.toString());
    }

    /**
     * @return The non-empty lines of the given file, except comment lines (which start with "#")
     */
    private static Set<String>
    readKnownDifferences(String fileName) throws IOException {

        Set<String> result = new TreeSet<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
            new FileInputStream(fileName),
            StandardCharsets.UTF_8
        ))) {
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) result.add(line);
            }
        }
        return result;
    }
}
