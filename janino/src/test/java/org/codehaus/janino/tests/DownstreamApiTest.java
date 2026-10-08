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

package org.codehaus.janino.tests;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.CompilerFactoryFactory;
import org.codehaus.commons.compiler.IClassBodyEvaluator;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.codehaus.commons.compiler.ISimpleCompiler;
import org.codehaus.commons.compiler.InternalCompilerException;
import org.codehaus.commons.compiler.Location;
import org.codehaus.commons.compiler.util.resource.MapResourceFinder;
import org.codehaus.commons.compiler.util.resource.ResourceFinder;
import org.codehaus.janino.ClassBodyEvaluator;
import org.codehaus.janino.JavaSourceClassLoader;
import org.codehaus.janino.Scanner;
import org.codehaus.janino.util.ClassFile;
import org.codehaus.janino.util.ClassFile.AttributeInfo;
import org.codehaus.janino.util.ClassFile.CodeAttribute;
import org.codehaus.janino.util.ClassFile.MethodInfo;
import org.junit.Assert;
import org.junit.Test;

/**
 * Uses the API of JANINO exactly the way the code generators of Apache Spark and Apache Calcite do, with the same
 * classes, methods and fields, statically typed. The members used here are fixed points of the API: they must stay
 * source and binary compatible, because Spark and Calcite (and other projects that compile generated code the same
 * way) depend on them. If one of them changes, this test no longer compiles; see {@code COMPATIBILITY.md}.
 * <p>
 *   The usage of Spark is taken from Spark's {@code CodeCompiler.scala} ({@code JaninoCodeCompiler} and
 *   {@code computeByteCodeStats}, Spark master as of 2026-10-07), {@code CodeGenerator.scala} ({@code doCompile} and
 *   {@code updateAndGetCompilationStats} in Spark 4.2) and {@code QueryExecutionErrors.scala}
 *   ({@code compilerError} and {@code internalCompilerError}):
 * </p>
 * <ul>
 *   <li>{@link ClassBodyEvaluator}: the constructor, {@code setParentClassLoader(ClassLoader)},
 *       {@code setClassName(String)}, {@code setDefaultImports(String...)}, {@code setExtendedClass(Class)},
 *       {@code setDebuggingInformation(boolean, boolean, boolean)}, {@code cook(String, String)},
 *       {@code getBytecodes()} and {@code getClazz()};</li>
 *   <li>{@link ClassFile}: the constructor from an {@code InputStream}, {@code getThisClassName()},
 *       {@code getConstantPoolSize()}, the field {@code methodInfos}, {@code MethodInfo.getName()},
 *       {@code MethodInfo.getAttributes()} and the field {@code CodeAttribute.code};</li>
 *   <li>{@link CompileException}: the constructor {@code (String, Location)} and {@code getLocation()};
 *       {@link InternalCompilerException}: the constructor {@code (String, Throwable)}.</li>
 * </ul>
 * <p>
 *   The usage of Calcite is taken from Calcite 1.42.0: {@code EnumerableInterpretable} and
 *   {@code JaninoRelMetadataProvider} (an {@link ISimpleCompiler}), {@code JaninoRexCompiler} and the benchmark
 *   {@code CodeGenerationBenchmark} (an {@link IClassBodyEvaluator}), {@code RexExecutable} (a
 *   {@link ClassBodyEvaluator} with a {@link Scanner}) and {@code JaninoCompiler} (a subclass of
 *   {@link JavaSourceClassLoader}):
 * </p>
 * <ul>
 *   <li>{@link CompilerFactoryFactory#getDefaultCompilerFactory(ClassLoader)};</li>
 *   <li>{@link ICompilerFactory}: {@code newSimpleCompiler()} and {@code newClassBodyEvaluator()};</li>
 *   <li>{@link ISimpleCompiler}: {@code setParentClassLoader(ClassLoader)},
 *       {@code setDebuggingInformation(boolean, boolean, boolean)}, {@code cook(String)} and
 *       {@code getClassLoader()};</li>
 *   <li>{@link IClassBodyEvaluator}: {@code setClassName(String)}, {@code setExtendedClass(Class)},
 *       {@code setImplementedInterfaces(Class[])}, {@code setParentClassLoader(ClassLoader)},
 *       {@code setDebuggingInformation(boolean, boolean, boolean)} and {@code createInstance(Reader)};</li>
 *   <li>{@link ClassBodyEvaluator}: in addition to the members above, {@code setImplementedInterfaces(Class[])}
 *       and {@code cook(Scanner)}; the {@link Scanner} constructor {@code (String, Reader)};</li>
 *   <li>{@link JavaSourceClassLoader}: the constructor {@code (ClassLoader, ResourceFinder, String)}, the
 *       overridable method {@code generateBytecodes(String)}, {@code setDebuggingInfo(boolean, boolean, boolean)}
 *       and {@code loadClass(String)}; {@link MapResourceFinder}: the constructor {@code (Map)};
 *       {@link ClassFile#getSourceResourceName(String)}.</li>
 * </ul>
 */
public
class DownstreamApiTest {

    /**
     * Stands in for Spark's {@code org.apache.spark.sql.catalyst.expressions.codegen.GeneratedClass}, the superclass
     * of every generated class.
     */
    public abstract static
    class GeneratedClass {

        /** @return An instance of the inner class that the class body declares */
        public abstract Object generate(Object[] references);
    }

    /** The name that Spark gives to the generated class. */
    private static final String CLASS_NAME = "org.apache.spark.sql.catalyst.expressions.GeneratedClass";

    /** Spark passes the names of twenty classes of its own; here, two JDK classes stand in for them. */
    private static final String[] DEFAULT_IMPORTS = { "java.util.Map", "java.util.concurrent.Callable" };

    /** A class body in the shape of the code that Spark generates: a factory method and an inner class. */
    private static final String BODY = (
        ""
        + "public java.lang.Object generate(Object[] references) {\n"
        + "  return new SpecificProjection(references);\n"
        + "}\n"
        + "\n"
        + "class SpecificProjection implements Callable {\n"
        + "\n"
        + "  private Object[] references;\n"
        + "  private Map state;\n"
        + "\n"
        + "  public SpecificProjection(Object[] references) {\n"
        + "    this.references = references;\n"
        + "  }\n"
        + "\n"
        + "  public Object call() {\n"
        + "    StringBuilder sb = new StringBuilder();\n"
        + "    for (int i = 0; i < references.length; i++) {\n"
        + "      sb.append(references[i]);\n"
        + "    }\n"
        + "    return sb.toString();\n"
        + "  }\n"
        + "}\n"
    );

    /** Spark's compile path: configure, cook, inspect the class files, load the class and instantiate it. */
    @Test public void
    testSparkCompile() throws Exception {

        ClassBodyEvaluator evaluator = new ClassBodyEvaluator();
        evaluator.setParentClassLoader(DownstreamApiTest.class.getClassLoader());
        evaluator.setClassName(DownstreamApiTest.CLASS_NAME);
        evaluator.setDefaultImports(DownstreamApiTest.DEFAULT_IMPORTS);
        evaluator.setExtendedClass(GeneratedClass.class);
        evaluator.setDebuggingInformation(true, true, false);

        evaluator.cook("generated.java", DownstreamApiTest.BODY);

        // Spark's bytecode statistics: the size of the constant pool and of the code of every method.
        Map<String, byte[]> bytecodes = evaluator.getBytecodes();
        Assert.assertEquals(bytecodes.keySet().toString(), 2, bytecodes.size());
        int methodsWithCode = 0;
        for (Map.Entry<String, byte[]> entry : bytecodes.entrySet()) {
            ClassFile cf = new ClassFile(new ByteArrayInputStream(entry.getValue()));
            Assert.assertEquals(entry.getKey(), cf.getThisClassName());
            Assert.assertTrue(cf.getConstantPoolSize() > 0);
            for (MethodInfo method : cf.methodInfos) {
                for (AttributeInfo attribute : method.getAttributes()) {
                    if (attribute instanceof CodeAttribute) {
                        int byteCodeSize = ((CodeAttribute) attribute).code.length;
                        Assert.assertTrue(cf.getThisClassName() + "." + method.getName(), byteCodeSize > 0);
                        methodsWithCode++;
                    }
                }
            }
        }
        Assert.assertEquals(4, methodsWithCode); // generate(), the two constructors and call()

        // Spark's instantiation: the no-arg constructor of the generated class, then the factory method.
        Class<?> clazz = evaluator.getClazz();
        Assert.assertEquals(DownstreamApiTest.CLASS_NAME, clazz.getName());
        GeneratedClass generatedClass = (GeneratedClass) clazz.getConstructor().newInstance();
        Object projection = generatedClass.generate(new Object[] { "a", 1, 'c' });
        Assert.assertEquals("a1c", ((Callable<?>) projection).call());
    }

    /** Spark's error path: a {@link CompileException} is rethrown with a new message and the same location. */
    @Test public void
    testSparkCompileError() throws Exception {

        ClassBodyEvaluator evaluator = new ClassBodyEvaluator();
        evaluator.setClassName(DownstreamApiTest.CLASS_NAME);
        evaluator.setExtendedClass(GeneratedClass.class);
        try {
            evaluator.cook("generated.java", "public Object generate(Object[] references) { return undefined; }");
            Assert.fail();
        } catch (CompileException e) {
            CompileException wrapped = new CompileException("Failed to compile: " + e, e.getLocation());
            Location location = wrapped.getLocation();
            Assert.assertNotNull(location);
            Assert.assertEquals("generated.java", location.getFileName());
            Assert.assertEquals(1, location.getLineNumber());
            // The message of a CompileException is prefixed with its location.
            Assert.assertTrue(wrapped.getMessage(), wrapped.getMessage().contains(": Failed to compile: "));
        }
    }

    /** Spark's error path for an {@link InternalCompilerException}: rethrown with a new message and the cause. */
    @Test public void
    testSparkInternalCompilerError() {
        InternalCompilerException cause   = new InternalCompilerException("internal");
        InternalCompilerException wrapped = new InternalCompilerException("Failed to compile: " + cause, cause);
        Assert.assertSame(cause, wrapped.getCause());
        Assert.assertTrue(wrapped.getMessage(), wrapped.getMessage().startsWith("Failed to compile: "));
    }

    // ------------------------------------------------------------------------------------------------------------
    // Apache Calcite

    /** Stands in for Calcite's {@code Bindable}, implemented by the classes of {@code EnumerableInterpretable}. */
    public
    interface Bindable { Object bind(Object dataContext); }

    /** Stands in for Calcite's {@code Scalar.Producer}, the interface that {@code JaninoRexCompiler} implements. */
    public
    interface Producer { Object produce(); }

    /** Stands in for Calcite's {@code Function1}, which the class of {@code RexExecutable} implements. */
    public
    interface Function1 { Object apply(Object a0); }

    /** Stands in for Calcite's {@code Utilities}, the superclass of the classes that {@code RexExecutable} compiles. */
    public static
    class Utilities {
        public static String twice(Object o) { return "" + o + o; }
    }

    /** {@code EnumerableInterpretable}: a complete class, compiled with an {@link ISimpleCompiler}, then loaded. */
    @Test public void
    testCalciteSimpleCompiler() throws Exception {

        ClassLoader      classLoader     = DownstreamApiTest.class.getClassLoader();
        ICompilerFactory compilerFactory = CompilerFactoryFactory.getDefaultCompilerFactory(classLoader);

        ISimpleCompiler compiler = compilerFactory.newSimpleCompiler();
        compiler.setParentClassLoader(classLoader);
        compiler.setDebuggingInformation(true, true, true);

        // Calcite names the interface by "getCanonicalName()", i.e. with dots between the nested names.
        compiler.cook(
            "public final class Baz implements " + Bindable.class.getCanonicalName() + " {\n"
            + "  public Object bind(Object dataContext) { return \"bound \" + dataContext; }\n"
            + "}"
        );
        Bindable bindable = (Bindable) compiler.getClassLoader()
            .loadClass("Baz")
            .getDeclaredConstructors()[0]
            .newInstance();
        Assert.assertEquals("bound 7", bindable.bind(7));
    }

    /** {@code JaninoRelMetadataProvider}: like above, but the constructor of the generated class has arguments. */
    @Test public void
    testCalciteSimpleCompilerWithConstructorArguments() throws Exception {

        ClassLoader      classLoader     = DownstreamApiTest.class.getClassLoader();
        ICompilerFactory compilerFactory = CompilerFactoryFactory.getDefaultCompilerFactory(classLoader);

        ISimpleCompiler compiler = compilerFactory.newSimpleCompiler();
        compiler.setParentClassLoader(classLoader);
        compiler.cook(
            "public final class GeneratedMetadataHandler implements " + Producer.class.getCanonicalName() + " {\n"
            + "  private final String a; private final Object b;\n"
            + "  public GeneratedMetadataHandler(String a, Object b) { this.a = a; this.b = b; }\n"
            + "  public Object produce() { return a + b; }\n"
            + "}"
        );
        Object[] argList  = { "x", 1 };
        Producer producer = (Producer) compiler.getClassLoader()
            .loadClass("GeneratedMetadataHandler")
            .getDeclaredConstructors()[0]
            .newInstance(argList);
        Assert.assertEquals("x1", producer.produce());
    }

    /** {@code JaninoRexCompiler} and {@code CodeGenerationBenchmark}: an {@link IClassBodyEvaluator}. */
    @Test public void
    testCalciteClassBodyEvaluator() throws Exception {

        ClassLoader      classLoader     = DownstreamApiTest.class.getClassLoader();
        ICompilerFactory compilerFactory = CompilerFactoryFactory.getDefaultCompilerFactory(classLoader);

        IClassBodyEvaluator cbe = compilerFactory.newClassBodyEvaluator();
        cbe.setClassName("Buzz");
        cbe.setExtendedClass(Utilities.class);
        cbe.setImplementedInterfaces(new Class[] { Producer.class });
        cbe.setParentClassLoader(classLoader);
        cbe.setDebuggingInformation(true, true, true);
        Producer producer = (Producer) cbe.createInstance(new StringReader(
            "public Object produce() { return twice(\"ab\"); }"
        ));
        Assert.assertEquals("abab", producer.produce());
    }

    /** {@code RexExecutable}: a {@link ClassBodyEvaluator}, cooked from a {@link Scanner}, then instantiated. */
    @Test public void
    testCalciteClassBodyEvaluatorWithScanner() throws Exception {

        ClassBodyEvaluator cbe = new ClassBodyEvaluator();
        cbe.setClassName("Reducer");
        cbe.setExtendedClass(Utilities.class);
        cbe.setImplementedInterfaces(new Class[] { Function1.class, Serializable.class });
        cbe.setParentClassLoader(DownstreamApiTest.class.getClassLoader());
        cbe.cook(new Scanner(null, new StringReader(
            "public Object apply(Object root0) { return new Object[] { twice(root0) }; }"
        )));
        Class<?>  c        = cbe.getClazz();
        Function1 function = (Function1) c.getConstructor().newInstance();
        Assert.assertEquals("cc", ((Object[]) function.apply("c"))[0]);
        Assert.assertTrue(function instanceof Serializable);
    }

    /**
     * Calcite's {@code JaninoCompiler.AccountingClassLoader}: a subclass of {@link JavaSourceClassLoader} that counts
     * the bytes of the class files that it generates.
     */
    static
    class AccountingClassLoader extends JavaSourceClassLoader {

        int nBytes;

        AccountingClassLoader(ClassLoader parentClassLoader, ResourceFinder sourceFinder, String characterEncoding) {
            super(parentClassLoader, sourceFinder, characterEncoding);
        }

        @Override public Map<String, byte[]>
        generateBytecodes(String name) throws ClassNotFoundException {
            Map<String, byte[]> map = super.generateBytecodes(name);
            if (map == null) return null;
            for (byte[] bytes : map.values()) this.nBytes += bytes.length;
            return map;
        }
    }

    /** {@code JaninoCompiler}: a {@link JavaSourceClassLoader} that finds the source in a {@link MapResourceFinder}. */
    @Test public void
    testCalciteJavaSourceClassLoader() throws Exception {

        String              fullClassName = "pkg.Generated";
        Map<String, byte[]> sourceMap     = new HashMap<String, byte[]>();
        sourceMap.put(
            ClassFile.getSourceResourceName(fullClassName),
            (
                "package pkg;\n"
                + "public class Generated implements " + Producer.class.getCanonicalName() + " {\n"
                + "  public Object produce() { return \"generated\"; }\n"
                + "}\n"
            ).getBytes(StandardCharsets.UTF_8)
        );
        MapResourceFinder sourceFinder = new MapResourceFinder(sourceMap);

        AccountingClassLoader classLoader = new AccountingClassLoader(
            DownstreamApiTest.class.getClassLoader(),
            sourceFinder,
            null
        );
        classLoader.setDebuggingInfo(true, true, true);
        Class<?> c = classLoader.loadClass(fullClassName);

        Assert.assertEquals("pkg/Generated.java", ClassFile.getSourceResourceName(fullClassName));
        Assert.assertTrue(classLoader.nBytes > 0);
        Assert.assertEquals("generated", ((Producer) c.getConstructor().newInstance()).produce());
    }
}
