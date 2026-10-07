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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The corpus of {@link Workload#SPARK_TPCDS}: the class bodies that Apache Spark generated for the TPC-DS queries
 * (see "tpcds/README.txt" in the resources of this package; "tools/spark-corpus" regenerates them), and the way
 * Spark compiles such a class body: with a new {@code ClassBodyEvaluator}, the classes of Spark on the parent class
 * loader, Spark's default imports, and Spark's {@code GeneratedClass} as the superclass; afterwards, the generated
 * class is loaded and instantiated. The parent class loader must see the complete Spark runtime, because JANINO
 * loads the classes that the generated code references through it and introspects them, which pulls in the
 * classes of their signatures (Hadoop, JSON, logging); the JAR files are loaded from "target/spark-classpath",
 * which the build of this module fills (see the POM).
 */
final
class SparkCodegen {

    private SparkCodegen() {}

    /** The name that Spark gives the generated class. */
    public static final String CLASS_NAME = "org.apache.spark.sql.catalyst.expressions.GeneratedClass";

    /** The superclass of the generated class. */
    public static final String EXTENDED_CLASS = "org.apache.spark.sql.catalyst.expressions.codegen.GeneratedClass";

    /** The file name that Spark passes when it cooks a class body. */
    public static final String FILE_NAME = "generated.java";

    /** The default imports of Spark's {@code CodeGenerator} (Spark 4.2.0). */
    public static final String[] DEFAULT_IMPORTS = {
        "org.apache.spark.unsafe.Platform",
        "org.apache.spark.sql.catalyst.InternalRow",
        "org.apache.spark.sql.catalyst.expressions.UnsafeRow",
        "org.apache.spark.unsafe.types.BinaryView",
        "org.apache.spark.unsafe.types.UTF8String",
        "org.apache.spark.sql.types.Decimal",
        "org.apache.spark.unsafe.types.CalendarInterval",
        "org.apache.spark.unsafe.types.VariantVal",
        "org.apache.spark.sql.catalyst.util.ArrayData",
        "org.apache.spark.sql.catalyst.expressions.UnsafeArrayData",
        "org.apache.spark.sql.catalyst.util.MapData",
        "org.apache.spark.sql.catalyst.expressions.UnsafeMapData",
        "org.apache.spark.sql.catalyst.expressions.Expression",
        "org.apache.spark.TaskContext",
        "org.apache.spark.TaskKilledException",
        "org.apache.spark.executor.InputMetrics",
        "org.apache.spark.sql.catalyst.util.CollationAwareUTF8String",
        "org.apache.spark.sql.catalyst.util.CollationFactory",
        "org.apache.spark.sql.catalyst.util.CollationSupport",
        "org.apache.spark.sql.errors.QueryExecutionErrors",
    };

    /** One class body of the corpus. */
    public static final
    class Body {

        /** The name of the resource, e.g. "q64-broadcast-3-GeneratedIteratorForCodegenStage5.java.txt". */
        public final String fileName;

        /** The class body, as Spark passed it to JANINO. */
        public final String text;

        Body(String fileName, String text) {
            this.fileName = fileName;
            this.text     = text;
        }
    }

    private static List<Body>  bodies;
    private static ClassLoader sparkClassLoader;
    private static Class<?>    extendedClass;

    /** @return The class bodies of the corpus, in the order of "tpcds/index.txt" */
    public static synchronized List<Body>
    bodies() throws IOException {
        if (SparkCodegen.bodies == null) {
            List<Body> result = new ArrayList<>();
            for (String line : SparkCodegen.readLines("tpcds/index.txt")) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String fileName = line.substring(0, line.indexOf('|'));
                result.add(new Body(fileName, String.join("\n", SparkCodegen.readLines("tpcds/" + fileName)) + "\n"));
            }
            if (result.isEmpty()) throw new IOException("The corpus \"tpcds/index.txt\" is empty");
            SparkCodegen.bodies = result;
        }
        return SparkCodegen.bodies;
    }

    /**
     * @return The class loader for the classes of Spark, loaded from the JAR files in "target/spark-classpath"
     */
    public static synchronized ClassLoader
    sparkClassLoader() throws Exception {
        if (SparkCodegen.sparkClassLoader == null) {
            File   dir  = new File(JaninoVersion.targetDirectory(), "spark-classpath");
            File[] jars = dir.listFiles();
            if (jars == null || jars.length == 0) {
                throw new IllegalStateException(
                    "\""
                    + dir
                    + "\" must contain the JAR files of Spark; build with \"mvn -f janino-parent/pom.xml -P benchmarks"
                    + " -DskipTests clean package\" (online, the first time)"
                );
            }
            Arrays.sort(jars);
            URL[] urls = new URL[jars.length];
            for (int i = 0; i < jars.length; i++) urls[i] = jars[i].toURI().toURL();

            // The parent is the platform class loader (Spark needs e.g. "java.sql"), so neither the benchmarks nor
            // JANINO are visible to the generated classes.
            SparkCodegen.sparkClassLoader = new URLClassLoader(urls, ClassLoader.getSystemClassLoader().getParent());
            SparkCodegen.extendedClass    = SparkCodegen.sparkClassLoader.loadClass(SparkCodegen.EXTENDED_CLASS);
        }
        return SparkCodegen.sparkClassLoader;
    }

    /** @return Spark's {@code GeneratedClass}, the superclass of every generated class */
    public static synchronized Class<?>
    extendedClass() throws Exception {
        SparkCodegen.sparkClassLoader();
        return SparkCodegen.extendedClass;
    }

    /**
     * Compiles one class body like Spark does.
     *
     * @return The {@code ClassBodyEvaluator}
     */
    public static Object
    compile(JaninoVersion janino, Body body) throws Exception {
        return janino.compileClassBody(
            SparkCodegen.sparkClassLoader(),
            SparkCodegen.extendedClass(),
            SparkCodegen.DEFAULT_IMPORTS,
            SparkCodegen.CLASS_NAME,
            SparkCodegen.FILE_NAME,
            body.text
        );
    }

    /**
     * Compiles all class bodies of the corpus like Spark does, and loads and instantiates each generated class.
     *
     * @return The instances
     */
    public static List<Object>
    compileAll(JaninoVersion janino) throws Exception {
        List<Object> result = new ArrayList<>();
        for (Body body : SparkCodegen.bodies()) {
            Class<?> clazz = janino.classBodyClass(SparkCodegen.compile(janino, body));
            result.add(clazz.getConstructor().newInstance());
        }
        return result;
    }

    /** @return The class files of all class bodies of the corpus, keyed by "resource name/class name" */
    public static Map<String, byte[]>
    bytecodes(JaninoVersion janino) throws Exception {
        Map<String, byte[]> result = new HashMap<>();
        for (Body body : SparkCodegen.bodies()) {
            Map<String, byte[]> classFiles = janino.classBodyBytecodes(SparkCodegen.compile(janino, body));
            for (Map.Entry<String, byte[]> e : classFiles.entrySet()) {
                result.put(body.fileName + "/" + e.getKey(), e.getValue());
            }
        }
        return result;
    }

    private static List<String>
    readLines(String resourceName) throws IOException {
        try (InputStream is = SparkCodegen.class.getResourceAsStream(resourceName)) {
            if (is == null) throw new IOException("Resource \"" + resourceName + "\" not found");
            List<String> result = new ArrayList<>();
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            for (String line = br.readLine(); line != null; line = br.readLine()) result.add(line);
            return result;
        }
    }
}
