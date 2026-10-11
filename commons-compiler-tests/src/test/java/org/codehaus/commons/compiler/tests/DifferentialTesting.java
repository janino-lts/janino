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
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.CompilerFactoryFactory;
import org.codehaus.commons.compiler.ICompiler;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.codehaus.commons.compiler.ISimpleCompiler;
import org.codehaus.commons.compiler.Location;
import org.codehaus.commons.compiler.util.ResourceFinderClassLoader;
import org.codehaus.commons.compiler.util.resource.MapResourceCreator;
import org.codehaus.commons.compiler.util.resource.MapResourceFinder;
import org.codehaus.commons.compiler.util.resource.Resource;
import org.codehaus.commons.compiler.util.resource.StringResource;
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
     * Compiles the <var>source</var> (one or more classes, in the default package) with the given compiler, in the
     * given mode, to class files.
     *
     * @return The class files, by resource name (e.g. {@code "P.class"})
     * @see    #classLoader(Map)
     */
    static Map<String, byte[]>
    compileToClassFiles(ICompilerFactory compilerFactory, String mode, String source) throws Exception {

        ICompiler compiler = compilerFactory.newCompiler();
        if (TestUtil.COMPLIANT.equals(mode)) TestUtil.setJavacCompliance(compiler);

        Map<String, byte[]> result = new HashMap<>();
        compiler.setClassFileFinder(new MapResourceFinder(result));
        compiler.setClassFileCreator(new MapResourceCreator(result));
        compiler.compile(new Resource[] { new StringResource("P.java", source) });
        return result;
    }

    /**
     * @return A class loader that loads the classes from the given class files (see {@link
     *         #compileToClassFiles(ICompilerFactory, String, String)})
     */
    static ClassLoader
    classLoader(Map<String, byte[]> classFiles) {
        return new ResourceFinderClassLoader(
            new MapResourceFinder(classFiles),
            DifferentialTesting.class.getClassLoader()
        );
    }

    /**
     * Compiles the <var>source</var> with the JDK-based compiler and an error handler, so that one compilation
     * reports all errors.
     *
     * @return The line numbers of the errors, each mapped to the first error message on that line; empty iff the
     *         source compiles
     */
    static Map<Integer, String>
    javacErrorsByLine(ICompilerFactory jdk, String source) throws Exception {

        final Map<Integer, String> result = new TreeMap<>();
        ISimpleCompiler sc = TestUtil.newSimpleCompiler(jdk, TestUtil.JAVAC);
        sc.setCompileErrorHandler((String message, Location location) -> {
            int line = location == null ? -1 : location.getLineNumber();
            if (!result.containsKey(line)) result.put(line, message);
        });
        try {
            sc.cook(source);
        } catch (CompileException ce) {
            if (result.isEmpty()) throw ce;
        }
        return result;
    }

    /**
     * @return The result of the static method without parameters, converted to a string; "!" and the exception
     *         that the method throws; or "?" and the exception that prevents its invocation (e.g. a {@link
     *         VerifyError})
     */
    static String
    invokeStatic(ClassLoader cl, String className, String methodName) {
        try {
            return String.valueOf(cl.loadClass(className).getDeclaredMethod(methodName).invoke(null));
        } catch (InvocationTargetException ite) {
            return "!" + ite.getCause();
        } catch (Throwable t) {
            return "?" + t;
        }
    }

    /**
     * @return The exception, and its root cause if it has one
     */
    static String
    describe(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        return cause == t ? t.toString() : t + "\nCaused by: " + cause;
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
