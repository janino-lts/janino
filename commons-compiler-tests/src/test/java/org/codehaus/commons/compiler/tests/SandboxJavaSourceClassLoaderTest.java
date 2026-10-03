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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;

import org.codehaus.commons.compiler.AbstractJavaSourceClassLoader;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.codehaus.commons.compiler.sandbox.SandboxPolicy;
import org.codehaus.commons.compiler.sandbox.SandboxViolationException;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import util.TestUtil;

/**
 * Tests for {@link AbstractJavaSourceClassLoader#setSandboxPolicy(SandboxPolicy)}.
 */
@RunWith(Parameterized.class) public
class SandboxJavaSourceClassLoaderTest {

    private static final SandboxPolicy POLICY = SandboxPolicy.builder()
        .include(SandboxPolicy.JAVA_LANG_BASIC)
        .include(SandboxPolicy.COLLECTIONS)
        .build();

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final ICompilerFactory compilerFactory;

    @Parameters(name = "CompilerFactory={0}") public static List<Object[]>
    compilerFactories() throws Exception { return TestUtil.getCompilerFactoriesForParameters(); }

    public
    SandboxJavaSourceClassLoaderTest(ICompilerFactory compilerFactory) { this.compilerFactory = compilerFactory; }

    /**
     * Verifies that classes that comply with the policy can be loaded, including classes that extend and use other
     * classes loaded by the same class loader.
     */
    @Test public void
    testCompliantClasses() throws Exception {

        File sourceDir = this.createSources();

        AbstractJavaSourceClassLoader jscl = this.newJavaSourceClassLoader(sourceDir);
        Assert.assertEquals("ok-base!", jscl.loadClass("pkg.Good").getMethod("meth").invoke(null));
        Assert.assertEquals(2, jscl.loadClass("pkg.Cycle").getMethod("meth").invoke(null));
    }

    /**
     * Verifies that a class that violates the policy cannot be loaded.
     */
    @Test public void
    testViolatingClass() throws Exception {
        AbstractJavaSourceClassLoader jscl = this.newJavaSourceClassLoader(this.createSources());
        SandboxJavaSourceClassLoaderTest.assertLoadFails(jscl, "pkg.Bad", "java.lang.System.getProperty");
    }

    /**
     * Verifies that a class that complies with the policy, but uses a class that violates it, cannot be loaded.
     */
    @Test public void
    testClassUsingViolatingClass() throws Exception {
        AbstractJavaSourceClassLoader jscl = this.newJavaSourceClassLoader(this.createSources());
        try {
            jscl.loadClass("pkg.UsesBad");
            Assert.fail("ClassNotFoundException expected");
        } catch (ClassNotFoundException cnfe) {
            Assert.assertTrue(String.valueOf(cnfe.getCause()), cnfe.getCause() instanceof SandboxViolationException);
        }
    }

    /**
     * Verifies that classes loaded from the class file cache of a {@code CachingJavaSourceClassLoader} (which are
     * verified one at a time) are verified as well, and that classes that refer to each other can be loaded.
     */
    @Test public void
    testCachingJavaSourceClassLoader() throws Exception {
        Assume.assumeTrue("JANINO only", "org.codehaus.janino".equals(this.compilerFactory.getId()));

        File sourceDir = this.createSources();
        File cacheDir  = this.temporaryFolder.newFolder("cache");

        // Populate the cache.
        {
            AbstractJavaSourceClassLoader jscl = SandboxJavaSourceClassLoaderTest.newCachingJavaSourceClassLoader(
                sourceDir,
                cacheDir
            );
            Assert.assertEquals("ok-base!", jscl.loadClass("pkg.Good").getMethod("meth").invoke(null));
            Assert.assertEquals(2, jscl.loadClass("pkg.Cycle").getMethod("meth").invoke(null));
        }

        // Now load the classes from the cache.
        {
            AbstractJavaSourceClassLoader jscl = SandboxJavaSourceClassLoaderTest.newCachingJavaSourceClassLoader(
                sourceDir,
                cacheDir
            );
            jscl.setSandboxPolicy(SandboxJavaSourceClassLoaderTest.POLICY);
            Assert.assertEquals("ok-base!", jscl.loadClass("pkg.Good").getMethod("meth").invoke(null));
            Assert.assertEquals(2, jscl.loadClass("pkg.Cycle").getMethod("meth").invoke(null));
        }

        // Populate the cache with a violating class (without a policy), then load it from the cache (with the
        // policy).
        {
            AbstractJavaSourceClassLoader jscl = SandboxJavaSourceClassLoaderTest.newCachingJavaSourceClassLoader(
                sourceDir,
                cacheDir
            );
            jscl.loadClass("pkg.Bad");
        }
        {
            AbstractJavaSourceClassLoader jscl = SandboxJavaSourceClassLoaderTest.newCachingJavaSourceClassLoader(
                sourceDir,
                cacheDir
            );
            jscl.setSandboxPolicy(SandboxJavaSourceClassLoaderTest.POLICY);
            SandboxJavaSourceClassLoaderTest.assertLoadFails(jscl, "pkg.Bad", "java.lang.System.getProperty");
        }
    }

    private AbstractJavaSourceClassLoader
    newJavaSourceClassLoader(File sourceDir) {
        AbstractJavaSourceClassLoader jscl = this.compilerFactory.newJavaSourceClassLoader(
            ClassLoader.getSystemClassLoader().getParent()
        );
        jscl.setSourcePath(new File[] { sourceDir });
        jscl.setSandboxPolicy(SandboxJavaSourceClassLoaderTest.POLICY);
        return jscl;
    }

    private static AbstractJavaSourceClassLoader
    newCachingJavaSourceClassLoader(File sourceDir, File cacheDir) throws Exception {

        // The test classpath does not include JANINO at compile time, so use reflection.
        return (AbstractJavaSourceClassLoader) Class.forName(
            "org.codehaus.janino.CachingJavaSourceClassLoader"
        ).getConstructor(
            ClassLoader.class,
            File[].class,
            String.class,
            File.class
        ).newInstance(
            ClassLoader.getSystemClassLoader().getParent(),
            new File[] { sourceDir },
            null,
            cacheDir
        );
    }

    private static void
    assertLoadFails(ClassLoader cl, String className, String expectedMessagePart) {
        try {
            cl.loadClass(className);
            Assert.fail("ClassNotFoundException expected");
        } catch (ClassNotFoundException cnfe) {
            Assert.assertTrue(String.valueOf(cnfe.getCause()), cnfe.getCause() instanceof SandboxViolationException);
            Assert.assertTrue(cnfe.getMessage(), cnfe.getMessage().contains(expectedMessagePart));
        }
    }

    /**
     * @return The source directory
     */
    private File
    createSources() throws IOException {

        File sourceDir = this.temporaryFolder.newFolder("src");

        SandboxJavaSourceClassLoaderTest.write(sourceDir, "pkg/Base.java", (
            ""
            + "package pkg;\n"
            + "public class Base {\n"
            + "    protected String base() { return \"base\"; }\n"
            + "}\n"
        ));
        SandboxJavaSourceClassLoaderTest.write(sourceDir, "pkg/Helper.java", (
            ""
            + "package pkg;\n"
            + "public class Helper extends Base {\n"
            + "    public String text() { return \"ok-\" + this.base(); }\n"
            + "}\n"
        ));
        SandboxJavaSourceClassLoaderTest.write(sourceDir, "pkg/Good.java", (
            ""
            + "package pkg;\n"
            + "public class Good {\n"
            + "    public static String meth() { return new Helper().text() + Inner.suffix(); }\n"
            + "    static class Inner { static String suffix() { return \"!\"; } }\n"
            + "}\n"
        ));
        SandboxJavaSourceClassLoaderTest.write(sourceDir, "pkg/Cycle.java", (
            ""
            + "package pkg;\n"
            + "public class Cycle {\n"
            + "    static int a() { return Inner.b() + 1; }\n"
            + "    static class Inner {\n"
            + "        static int b() { return 1; }\n"
            + "        static int c() { return Cycle.a(); }\n"
            + "    }\n"
            + "    public static int meth() { return Inner.c(); }\n"
            + "}\n"
        ));
        SandboxJavaSourceClassLoaderTest.write(sourceDir, "pkg/Bad.java", (
            ""
            + "package pkg;\n"
            + "public class Bad {\n"
            + "    public static Object meth() { return System.getProperty(\"foo\"); }\n"
            + "}\n"
        ));
        SandboxJavaSourceClassLoaderTest.write(sourceDir, "pkg/UsesBad.java", (
            ""
            + "package pkg;\n"
            + "public class UsesBad {\n"
            + "    public static Object meth() { return Bad.meth(); }\n"
            + "}\n"
        ));

        return sourceDir;
    }

    private static void
    write(File sourceDir, String path, String text) throws IOException {
        File file = new File(sourceDir, path);
        file.getParentFile().mkdirs();
        Writer w = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
        try {
            w.write(text);
        } finally {
            w.close();
        }
    }
}
