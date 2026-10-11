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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.codehaus.commons.compiler.ICompiler;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.codehaus.commons.compiler.IExpressionEvaluator;
import org.codehaus.commons.compiler.ISimpleCompiler;
import org.codehaus.commons.compiler.util.ResourceFinderClassLoader;
import org.codehaus.commons.compiler.util.resource.MapResourceCreator;
import org.codehaus.commons.compiler.util.resource.MapResourceFinder;
import org.codehaus.commons.compiler.util.resource.Resource;
import org.codehaus.commons.compiler.util.resource.ResourceFinder;
import org.codehaus.commons.compiler.util.resource.StringResource;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import util.CommonsCompilerTestSuite;
import util.TestUtil;

/**
 * Tests that source code keeps its non-ASCII characters on the way from a {@link String} to the compiler.
 * <p>
 *   See <a href="https://github.com/janino-lts/janino/issues/6">issue #6</a>: {@link StringResource} and {@link
 *   MapResourceFinder#addResource(String, String)} encode with the platform default charset, regardless of the source
 *   charset of the compiler.
 * </p>
 * <p>
 *   The build runs this test twice: with the platform default charset, and with {@code -Dfile.encoding=ISO-8859-1},
 *   which can not represent all characters of {@link #TEXT}.
 * </p>
 */
@RunWith(Parameterized.class) public
class SourceCharsetTest extends CommonsCompilerTestSuite {

    /** Contains characters that ISO-8859-1 and windows-1252 can not represent. */
    private static final String TEXT = "äöü中";

    /** Can be represented by ISO-8859-1, windows-1252 and UTF-8. */
    private static final String LATIN_1_TEXT = "äöü";

    @Parameters(name = "{0}, {1}") public static Collection<Object[]>
    compilerFactories() throws Exception { return TestUtil.getCompilerFactoriesAndModesForParameters(); }

    public
    SourceCharsetTest(ICompilerFactory compilerFactory, String mode) { super(compilerFactory, mode); }

    @Test public void
    testStringResourceWithCharset() throws Exception {
        for (Charset charset : new Charset[] { StandardCharsets.UTF_8, StandardCharsets.UTF_16BE }) {
            ICompiler           compiler = this.compilerFactory.newCompiler();
            Map<String, byte[]> classes  = new HashMap<>();
            compiler.setSourceCharset(charset);
            compiler.setClassFileCreator(new MapResourceCreator(classes));

            compiler.compile(new Resource[] {
                new StringResource("pkg/A.java", SourceCharsetTest.source("A", SourceCharsetTest.TEXT), charset),
            });

            Assert.assertEquals(charset.name(), SourceCharsetTest.TEXT, SourceCharsetTest.callS(classes, "pkg.A"));
        }
    }

    @Test public void
    testMapResourceFinderWithCharset() throws Exception {
        MapResourceFinder sourceFinder = new MapResourceFinder();
        sourceFinder.addResource(
            "pkg/B.java",
            SourceCharsetTest.source("B", SourceCharsetTest.TEXT),
            StandardCharsets.UTF_8
        );

        ICompiler           compiler = this.compilerFactory.newCompiler();
        Map<String, byte[]> classes  = new HashMap<>();
        compiler.setSourceCharset(StandardCharsets.UTF_8);
        compiler.setSourceFinder(sourceFinder);
        compiler.setClassFileFinder(ResourceFinder.EMPTY_RESOURCE_FINDER);
        compiler.setClassFileCreator(new MapResourceCreator(classes));

        compiler.compile(new Resource[] {
            new StringResource(
                "pkg/A.java",
                "package pkg; public class A { public static String s() { return B.s(); } }",
                StandardCharsets.UTF_8
            ),
        });

        Assert.assertEquals(SourceCharsetTest.TEXT, SourceCharsetTest.callS(classes, "pkg.A"));
    }

    /**
     * The methods without a charset still encode with the platform default charset, which is also the default source
     * charset.
     */
    @Test public void
    testDefaultCharsetUnchanged() throws Exception {
        Assume.assumeTrue(Charset.defaultCharset().newEncoder().canEncode(SourceCharsetTest.LATIN_1_TEXT));

        MapResourceFinder sourceFinder = new MapResourceFinder();
        sourceFinder.addResource("pkg/B.java", SourceCharsetTest.source("B", SourceCharsetTest.LATIN_1_TEXT));

        ICompiler           compiler = this.compilerFactory.newCompiler();
        Map<String, byte[]> classes  = new HashMap<>();
        compiler.setSourceFinder(sourceFinder);
        compiler.setClassFileFinder(ResourceFinder.EMPTY_RESOURCE_FINDER);
        compiler.setClassFileCreator(new MapResourceCreator(classes));

        compiler.compile(new Resource[] {
            new StringResource("pkg/A.java", SourceCharsetTest.source("A", SourceCharsetTest.LATIN_1_TEXT)),
            new StringResource(
                "pkg/C.java",
                "package pkg; public class C { public static String s() { return B.s(); } }"
            ),
        });

        Assert.assertEquals(SourceCharsetTest.LATIN_1_TEXT, SourceCharsetTest.callS(classes, "pkg.A"));
        Assert.assertEquals(SourceCharsetTest.LATIN_1_TEXT, SourceCharsetTest.callS(classes, "pkg.C"));
    }

    /**
     * The simple compiler gets the source code as characters, so it must keep all characters, whatever the platform
     * default charset is.
     */
    @Test public void
    testSimpleCompilerKeepsAllCharacters() throws Exception {
        ISimpleCompiler sc = this.compilerFactory.newSimpleCompiler();
        sc.cook(SourceCharsetTest.source("A", SourceCharsetTest.TEXT));

        Assert.assertEquals(
            SourceCharsetTest.TEXT,
            sc.getClassLoader().loadClass("pkg.A").getDeclaredMethod("s").invoke(null)
        );
    }

    @Test public void
    testExpressionEvaluatorKeepsAllCharacters() throws Exception {
        IExpressionEvaluator ee = this.compilerFactory.newExpressionEvaluator();
        ee.setExpressionType(String.class);
        ee.cook("\"" + SourceCharsetTest.TEXT + "\"");

        Assert.assertEquals(SourceCharsetTest.TEXT, ee.evaluate(new Object[0]));
    }

    /**
     * @return The source code of class {@code pkg.}<var>className</var> with a static method {@code s()} that returns
     *         <var>text</var>
     */
    private static String
    source(String className, String text) {
        return "package pkg; public class " + className + " { public static String s() { return \"" + text + "\"; } }";
    }

    /**
     * Loads the class <var>className</var> from <var>classes</var> and invokes its static method {@code s()}.
     */
    private static Object
    callS(Map<String, byte[]> classes, String className) throws Exception {
        ClassLoader cl = new ResourceFinderClassLoader(
            new MapResourceFinder(classes),    // resourceFinder
            ClassLoader.getSystemClassLoader() // parent
        );
        return cl.loadClass(className).getDeclaredMethod("s").invoke(null);
    }
}
