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

package org.codehaus.janino.benchmarks;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One version of JANINO, loaded through its own class loader from the JAR files in {@code
 * janino-benchmarks/target/janino-}<var>name</var>, and invoked through reflection. Thus, two versions can be compared
 * in the same JVM, and the benchmarks need not be compiled against either of them.
 * <p>
 *   Only APIs that exist in all versions since 3.1.12 are used. The reflection objects are looked up once, so their
 *   invocation costs (nanoseconds) do not distort the measured operations (micro- to milliseconds).
 * </p>
 */
public final
class JaninoVersion {

    /**
     * The name of the version that is compared with the current build; see "janino.baseline.version" in the POM, and
     * {@link #load(String)}.
     */
    public static final String BASELINE = "baseline";

    /** The name of the current build. */
    public static final String CURRENT = "current";

    private final String      name;
    private final String      jarFiles;
    private final ClassLoader classLoader;

    // org.codehaus.janino.SimpleCompiler
    private final Constructor<?> simpleCompilerConstructor;
    private final Method         simpleCompilerCook;
    private final Method         simpleCompilerGetClassLoader;
    private final Method         simpleCompilerGetBytecodes;

    // org.codehaus.janino.ExpressionEvaluator
    private final Constructor<?> expressionEvaluatorConstructor;
    private final Method         expressionEvaluatorSetParameters;
    private final Method         expressionEvaluatorSetExpressionType;
    private final Method         expressionEvaluatorCook;
    private final Method         expressionEvaluatorEvaluate;
    private final Method         expressionEvaluatorGetBytecodes;

    // org.codehaus.janino.Compiler and the resource classes of commons-compiler
    private final Constructor<?> compilerConstructor;
    private final Method         compilerSetSourceFinder;
    private final Method         compilerSetClassPath;
    private final Method         compilerSetTargetVersion;
    private final Method         compilerSetClassFileCreator;
    private final Method         compilerSetClassFileFinder;
    private final Method         compilerCompile;
    private final Class<?>       resourceFinderClass;
    private final Object         emptyResourceFinder;
    private final Constructor<?> directoryResourceFinderConstructor;
    private final Constructor<?> multiResourceFinderConstructor;
    private final Constructor<?> mapResourceCreatorConstructor;

    private
    JaninoVersion(String name, File[] jars) throws Exception {
        this.name     = name;
        this.jarFiles = Arrays.toString(jars);

        URL[] urls = new URL[jars.length];
        for (int i = 0; i < jars.length; i++) urls[i] = jars[i].toURI().toURL();

        // The parent is the bootstrap class loader, so the benchmarks are not visible to JANINO.
        this.classLoader = new URLClassLoader(urls, null);

        Class<?> simpleCompilerClass = this.loadClass("org.codehaus.janino.SimpleCompiler");
        this.simpleCompilerConstructor    = simpleCompilerClass.getConstructor();
        this.simpleCompilerCook           = simpleCompilerClass.getMethod("cook", String.class);
        this.simpleCompilerGetClassLoader = simpleCompilerClass.getMethod("getClassLoader");
        this.simpleCompilerGetBytecodes   = simpleCompilerClass.getMethod("getBytecodes");

        Class<?> expressionEvaluatorClass = this.loadClass("org.codehaus.janino.ExpressionEvaluator");
        this.expressionEvaluatorConstructor       = expressionEvaluatorClass.getConstructor();
        this.expressionEvaluatorSetParameters     = expressionEvaluatorClass.getMethod(
            "setParameters",
            String[].class,
            Class[].class
        );
        this.expressionEvaluatorSetExpressionType = expressionEvaluatorClass.getMethod("setExpressionType", Class.class);
        this.expressionEvaluatorCook              = expressionEvaluatorClass.getMethod("cook", String.class);
        this.expressionEvaluatorEvaluate          = expressionEvaluatorClass.getMethod("evaluate", Object[].class);
        this.expressionEvaluatorGetBytecodes      = expressionEvaluatorClass.getMethod("getBytecodes");

        this.resourceFinderClass = this.loadClass("org.codehaus.commons.compiler.util.resource.ResourceFinder");
        Class<?> resourceCreatorClass = this.loadClass("org.codehaus.commons.compiler.util.resource.ResourceCreator");
        this.emptyResourceFinder = this.resourceFinderClass.getField("EMPTY_RESOURCE_FINDER").get(null);

        this.directoryResourceFinderConstructor = this.loadClass(
            "org.codehaus.commons.compiler.util.resource.DirectoryResourceFinder"
        ).getConstructor(File.class);
        this.multiResourceFinderConstructor = this.loadClass(
            "org.codehaus.commons.compiler.util.resource.MultiResourceFinder"
        ).getConstructor(Array.newInstance(this.resourceFinderClass, 0).getClass());
        this.mapResourceCreatorConstructor = this.loadClass(
            "org.codehaus.commons.compiler.util.resource.MapResourceCreator"
        ).getConstructor(Map.class);

        Class<?> compilerClass = this.loadClass("org.codehaus.janino.Compiler");
        this.compilerConstructor         = compilerClass.getConstructor();
        this.compilerSetSourceFinder     = compilerClass.getMethod("setSourceFinder", this.resourceFinderClass);
        this.compilerSetClassPath        = compilerClass.getMethod("setClassPath", File[].class);
        this.compilerSetTargetVersion    = compilerClass.getMethod("setTargetVersion", int.class);
        this.compilerSetClassFileCreator = compilerClass.getMethod("setClassFileCreator", resourceCreatorClass);
        this.compilerSetClassFileFinder  = compilerClass.getMethod("setClassFileFinder", this.resourceFinderClass);
        this.compilerCompile             = compilerClass.getMethod("compile", File[].class);
    }

    /**
     * Loads the version with the given <var>name</var> ({@link #BASELINE} or {@link #CURRENT}) from the JAR files in
     * {@code janino-benchmarks/target/janino-}<var>name</var>.
     * <p>
     *   The system property {@code janino.benchmarks.baseline.dir} replaces the directory of the {@link #BASELINE};
     *   e.g. a directory with the JAR files of an earlier build of the same version.
     * </p>
     */
    public static JaninoVersion
    load(String name) throws Exception {

        String baselineDir = (
            JaninoVersion.BASELINE.equals(name)
            ? System.getProperty("janino.benchmarks.baseline.dir")
            : null
        );

        File dir = (
            baselineDir != null
            ? new File(baselineDir)
            : new File(JaninoVersion.targetDirectory(), "janino-" + name)
        );

        // Exactly one "janino" and one "commons-compiler" JAR file; a build with another baseline version, but without
        // "clean", would leave the JAR files of the previous baseline version in the directory.
        File[] jars = dir.listFiles();
        if (
            jars == null
            || jars.length != 2
            || JaninoVersion.countFiles(jars, "janino-") != 1
            || JaninoVersion.countFiles(jars, "commons-compiler-") != 1
        ) {
            throw new IllegalStateException(
                "\""
                + dir
                + "\" must contain exactly one \"janino\" and one \"commons-compiler\" JAR file"
                + (
                    baselineDir != null
                    ? ""
                    : "; build with \"mvn -f janino-parent/pom.xml -P benchmarks -DskipTests clean package\""
                )
            );
        }
        Arrays.sort(jars);

        return new JaninoVersion(name, jars);
    }

    /**
     * @return The root directory of the project (which contains "janino" and "commons-compiler"); can be set with the
     *         system property {@code janino.benchmarks.root}
     */
    public static File
    projectRoot() throws URISyntaxException {
        String root = System.getProperty("janino.benchmarks.root");
        if (root != null) return new File(root);
        return JaninoVersion.targetDirectory().getParentFile().getParentFile();
    }

    /**
     * @return {@code janino-benchmarks/target}, i.e. the directory that contains "benchmarks.jar" (or "classes",
     *         when run from an IDE)
     */
    private static File
    targetDirectory() throws URISyntaxException {
        URL location = JaninoVersion.class.getProtectionDomain().getCodeSource().getLocation();
        return new File(location.toURI()).getAbsoluteFile().getParentFile();
    }

    /** @return {@link #BASELINE} or {@link #CURRENT} */
    public String
    getName() { return this.name; }

    @Override public String
    toString() { return this.name + " " + this.jarFiles; }

    /**
     * Compiles a compilation unit with a new {@code SimpleCompiler}, loads the class <var>className</var>, and
     * creates an instance of it; this links and verifies the class.
     *
     * @return The new instance
     */
    public Object
    compileAndLoad(String source, String className) throws Exception {
        Object simpleCompiler = this.simpleCompilerConstructor.newInstance();
        JaninoVersion.invoke(this.simpleCompilerCook, simpleCompiler, source);
        ClassLoader cl = (ClassLoader) JaninoVersion.invoke(this.simpleCompilerGetClassLoader, simpleCompiler);
        return cl.loadClass(className).getConstructor().newInstance();
    }

    /**
     * Compiles a compilation unit with a new {@code SimpleCompiler}.
     *
     * @return The generated class files
     */
    public Map<String, byte[]>
    compileToBytecodes(String source) throws Exception {
        Object simpleCompiler = this.simpleCompilerConstructor.newInstance();
        JaninoVersion.invoke(this.simpleCompilerCook, simpleCompiler, source);
        @SuppressWarnings("unchecked") Map<String, byte[]>
        result = (Map<String, byte[]>) JaninoVersion.invoke(this.simpleCompilerGetBytecodes, simpleCompiler);
        return result;
    }

    /**
     * Compiles an expression with a new {@code ExpressionEvaluator}.
     *
     * @return The {@code ExpressionEvaluator}
     */
    public Object
    compileExpression(String expression, String[] parameterNames, Class<?>[] parameterTypes, Class<?> expressionType)
    throws Exception {
        Object ee = this.expressionEvaluatorConstructor.newInstance();
        JaninoVersion.invoke(this.expressionEvaluatorSetParameters, ee, parameterNames, parameterTypes);
        JaninoVersion.invoke(this.expressionEvaluatorSetExpressionType, ee, expressionType);
        JaninoVersion.invoke(this.expressionEvaluatorCook, ee, expression);
        return ee;
    }

    /** Evaluates an expression that was compiled with {@link #compileExpression(String, String[], Class[], Class)}. */
    public Object
    evaluate(Object expressionEvaluator, Object... arguments) throws Exception {
        return JaninoVersion.invoke(this.expressionEvaluatorEvaluate, expressionEvaluator, (Object) arguments);
    }

    /** @return The class files generated by an {@code ExpressionEvaluator} */
    public Map<String, byte[]>
    getBytecodes(Object expressionEvaluator) throws Exception {
        @SuppressWarnings("unchecked") Map<String, byte[]>
        result = (Map<String, byte[]>) JaninoVersion.invoke(this.expressionEvaluatorGetBytecodes, expressionEvaluator);
        return result;
    }

    /**
     * Compiles JANINO's {@code Compiler.java} and {@code ExpressionDemo.java}, and everything they reference, from the
     * sources of the project (in "janino/src/main/java" and "commons-compiler/src/main/java"), like {@code
     * CompilerTest.testSelfCompile()}.
     *
     * @return The generated class files
     */
    public Map<String, byte[]>
    selfCompile() throws Exception {
        File root = JaninoVersion.projectRoot();
        File janinoSources          = new File(root, "janino/src/main/java");
        File commonsCompilerSources = new File(root, "commons-compiler/src/main/java");

        List<Object> sourceFinders = new ArrayList<Object>();
        sourceFinders.add(this.directoryResourceFinderConstructor.newInstance(janinoSources));
        sourceFinders.add(this.directoryResourceFinderConstructor.newInstance(commonsCompilerSources));
        Object sourceFinderArray = sourceFinders.toArray(
            (Object[]) Array.newInstance(this.resourceFinderClass, sourceFinders.size())
        );

        Map<String, byte[]> result = new HashMap<String, byte[]>();

        Object compiler = this.compilerConstructor.newInstance();
        JaninoVersion.invoke(
            this.compilerSetSourceFinder,
            compiler,
            this.multiResourceFinderConstructor.newInstance(sourceFinderArray)
        );
        JaninoVersion.invoke(this.compilerSetClassPath, compiler, (Object) new File[0]);

        // JANINO's sources use Java 8 language features (e.g. default methods).
        JaninoVersion.invoke(this.compilerSetTargetVersion, compiler, 8);

        JaninoVersion.invoke(
            this.compilerSetClassFileCreator,
            compiler,
            this.mapResourceCreatorConstructor.newInstance(result)
        );
        JaninoVersion.invoke(this.compilerSetClassFileFinder, compiler, this.emptyResourceFinder);
        JaninoVersion.invoke(this.compilerCompile, compiler, (Object) new File[] {
            new File(janinoSources,          "org/codehaus/janino/Compiler.java"),
            new File(commonsCompilerSources, "org/codehaus/commons/compiler/samples/ExpressionDemo.java"),
        });

        return result;
    }

    private static int
    countFiles(File[] files, String namePrefix) {
        int result = 0;
        for (File file : files) {
            if (file.getName().startsWith(namePrefix) && file.getName().endsWith(".jar")) result++;
        }
        return result;
    }

    private Class<?>
    loadClass(String className) throws ClassNotFoundException {
        return Class.forName(className, false, this.classLoader);
    }

    /** Invokes the <var>method</var> and unwraps an {@link InvocationTargetException}. */
    private static Object
    invoke(Method method, Object target, Object... arguments) throws Exception {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException ite) {
            Throwable t = ite.getTargetException();
            if (t instanceof Exception) throw (Exception) t;
            if (t instanceof Error)     throw (Error) t;
            throw ite;
        }
    }
}
