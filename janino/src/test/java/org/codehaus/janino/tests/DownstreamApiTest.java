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
import java.util.Map;
import java.util.concurrent.Callable;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.InternalCompilerException;
import org.codehaus.commons.compiler.Location;
import org.codehaus.janino.ClassBodyEvaluator;
import org.codehaus.janino.util.ClassFile;
import org.codehaus.janino.util.ClassFile.AttributeInfo;
import org.codehaus.janino.util.ClassFile.CodeAttribute;
import org.codehaus.janino.util.ClassFile.MethodInfo;
import org.junit.Assert;
import org.junit.Test;

/**
 * Uses the API of JANINO exactly the way the code generator of Apache Spark does, with the same classes, methods and
 * fields, statically typed. The members used here are fixed points of the API: they must stay source and binary
 * compatible, because Spark (and other projects that compile generated code the same way) depend on them. If one of
 * them changes, this test no longer compiles; see {@code COMPATIBILITY.md}.
 * <p>
 *   The usage is taken from Spark's {@code CodeCompiler.scala} ({@code JaninoCodeCompiler} and
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
}
