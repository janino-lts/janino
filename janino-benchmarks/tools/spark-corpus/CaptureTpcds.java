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

package org.codehaus.janino.benchmarks.tools;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.spark.sql.SparkSession;

/**
 * Runs the TPC-DS queries on Spark (local mode, tiny generated tables) and writes the class bodies that Spark's
 * code generator compiled to the output directory, one file per distinct class body, plus an index. See
 * "capture.sh", which configures the logging that this program reads: Spark logs every class body through the
 * logger of its {@code CodeGenerator} (formatted with line numbers, which are removed here), and this program
 * logs a marker before every query, so that the index tells which query produced which class.
 */
public final
class CaptureTpcds {

    private static final String CODEGEN_LOGGER = "org.apache.spark.sql.catalyst.expressions.codegen.CodeGenerator";
    private static final String EVENT_MARKER   = "@@EVENT@@";
    private static final String QUERY_MARKER   = "@@QUERY ";

    /** The line number prefix that Spark's {@code CodeFormatter} adds to every line. */
    private static final Pattern LINE_PREFIX = Pattern.compile("^/\\* \\d+ \\*/ ?");

    /** The first class declaration of a class body names the generated class. */
    private static final Pattern CLASS_NAME = Pattern.compile("\\bclass (\\w+)\\b");

    private static final Pattern QUERY_FILE = Pattern.compile("tpcds/(q\\d+[ab]?)\\.sql");

    private CaptureTpcds() {}

    public static void
    main(String[] args) throws Exception {
        File out  = new File(args.length > 0 ? args[0] : "out");
        File log  = new File(System.getProperty("spark.corpus.log", "work/codegen.log"));
        int  rows = Integer.getInteger("spark.corpus.rows", 20);

        List<String> queries = CaptureTpcds.queryNames();
        System.out.println(queries.size() + " TPC-DS queries, " + rows + " rows per table");

        Logger logger = LogManager.getLogger(CaptureTpcds.CODEGEN_LOGGER);

        SparkSession spark = SparkSession.builder()
            .master("local[2]")
            .appName("spark-corpus")
            .config("spark.ui.enabled", "false")
            .config("spark.sql.adaptive.enabled", "false")
            .config("spark.sql.shuffle.partitions", "4")
            .config("spark.sql.codegen.logLevel", "DEBUG")
            .config("spark.sql.codegen.logging.maxLines", "-1")
            .config("spark.sql.codegen.comments", "false")
            .config("spark.sql.warehouse.dir", Files.createTempDirectory("spark-corpus").toUri().toString())
            .getOrCreate();
        try {
            CaptureTpcds.createTables(spark, rows);

            // Pass 1 with the default broadcast threshold (broadcast hash joins), pass 2 without broadcast joins
            // (sort merge joins).
            String[][] passes = { { "broadcast", "10485760" }, { "sortmerge", "-1" } };
            for (String[] pass : passes) {
                spark.conf().set("spark.sql.autoBroadcastJoinThreshold", pass[1]);
                int failed = 0;
                long start = System.nanoTime();
                for (String q : queries) {
                    logger.debug(CaptureTpcds.QUERY_MARKER + q + " " + pass[0]);
                    try {
                        spark.sql(CaptureTpcds.resource("tpcds/" + q + ".sql")).collect();
                    } catch (Exception e) {
                        failed++;
                        String message = String.valueOf(e.getMessage());
                        int nl = message.indexOf('\n');
                        String first = nl < 0 ? message : message.substring(0, nl);
                        System.out.println(q + " " + pass[0] + " FAILED: " + first);
                    }
                }
                System.out.printf(
                    Locale.ROOT,
                    "pass %s: %d queries, %d failed, %d s%n",
                    pass[0],
                    queries.size(),
                    failed,
                    (System.nanoTime() - start) / 1000000000L
                );
            }
        } finally {
            spark.stop();
        }

        CaptureTpcds.extract(log, out);
    }

    /** @return The names of the TPC-DS queries ("q1", "q14a", ...) in the "tests" JAR of spark-sql */
    private static List<String>
    queryNames() throws Exception {
        URL url = CaptureTpcds.class.getClassLoader().getResource("tpcds/q1.sql");
        if (url == null) throw new IOException("\"tpcds/q1.sql\" not on the class path; is the tests JAR missing?");
        String spec = url.toString();
        if (!spec.startsWith("jar:file:") || !spec.contains("!/")) throw new IOException("Unexpected URL " + spec);
        File jar = new File(new URI(spec.substring(4, spec.indexOf("!/"))));

        List<String> result = new ArrayList<>();
        try (JarFile jf = new JarFile(jar)) {
            for (Enumeration<JarEntry> e = jf.entries(); e.hasMoreElements();) {
                Matcher m = CaptureTpcds.QUERY_FILE.matcher(e.nextElement().getName());
                if (m.matches()) result.add(m.group(1));
            }
        } catch (Exception e) {
            throw new IOException("Reading " + jar, e);
        }
        Collections.sort(result, (a, b) -> {
            int na = Integer.parseInt(a.replaceAll("\\D", "")), nb = Integer.parseInt(b.replaceAll("\\D", ""));
            return na != nb ? na - nb : a.compareTo(b);
        });
        return result;
    }

    private static String
    resource(String name) throws IOException {
        try (InputStream is = CaptureTpcds.class.getClassLoader().getResourceAsStream(name)) {
            if (is == null) throw new IOException("Resource \"" + name + "\" not found");
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Creates a temporary view for each table declared in "tpcds-tables.sql", with the given number of generated
     * rows. The values are deterministic functions of the row number; they need not make sense, because the
     * generated code depends on the query plan, not on the data (adaptive query execution is disabled).
     */
    private static void
    createTables(SparkSession spark, int rows) throws IOException {
        String name = null;
        List<String> exprs = new ArrayList<>();
        int tables = 0;
        for (String line : Files.readAllLines(new File("tpcds-tables.sql").toPath(), StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("--")) continue;
            if (line.startsWith("CREATE TABLE ")) {
                name = line.substring("CREATE TABLE ".length(), line.indexOf(' ', "CREATE TABLE ".length()));
                exprs.clear();
            } else
            if (line.equals(");")) {
                spark.range(rows).selectExpr(exprs.toArray(new String[0])).createOrReplaceTempView(name);
                tables++;
            } else
            {
                if (line.endsWith(",")) line = line.substring(0, line.length() - 1);
                int    sp     = line.indexOf(' ');
                String column = line.substring(0, sp);
                exprs.add(CaptureTpcds.valueExpression(line.substring(sp + 1), exprs.size()) + " AS `" + column + "`");
            }
        }
        System.out.println(tables + " tables created");
    }

    /** @return A Spark SQL expression over the column "id" of "spark.range()" that yields a value of the type */
    private static String
    valueExpression(String type, int column) {
        String t = type.toUpperCase(Locale.ROOT);
        String k = "(id + " + column + ")";
        if (t.startsWith("INT"))     return "CAST(" + k + " % 23 + 1 AS INT)";
        if (t.startsWith("DECIMAL")) return "CAST((" + k + " % 97 + 1) / 4 AS " + type + ")";
        if (t.startsWith("DATE"))    return "DATE_ADD(DATE '2000-01-01', CAST(" + k + " % 31 AS INT))";
        if (t.startsWith("CHAR") || t.startsWith("VARCHAR") || t.startsWith("STRING")) {
            return "CAST(CONCAT('v', " + k + " % 7) AS STRING)";
        }
        throw new IllegalArgumentException("Column type " + type);
    }

    /**
     * Splits the log into events, keeps the class bodies, removes the line number prefixes, drops duplicates and
     * writes the bodies and the index.
     */
    private static void
    extract(File log, File out) throws Exception {
        if (!out.isDirectory() && !out.mkdirs()) throw new IOException("Cannot create " + out);
        List<String> lines = Files.readAllLines(log.toPath(), StandardCharsets.UTF_8);

        List<String>              index     = new ArrayList<>();
        Set<String>               hashes    = new HashSet<>();
        TreeMap<String, Integer>  kinds     = new TreeMap<>();
        String                    query     = "setup", pass = "setup";
        int                       seq       = 0, events = 0, duplicates = 0, truncated = 0;
        long                      bytes     = 0;
        MessageDigest             digest    = MessageDigest.getInstance("SHA-256");

        List<String> event = null;
        lines.add(CaptureTpcds.EVENT_MARKER); // Flushes the last event.
        for (String line : lines) {
            if (line.equals(CaptureTpcds.EVENT_MARKER)) {
                if (event == null) { event = new ArrayList<>(); continue; }

                // Process the previous event.
                String text = String.join("\n", event).trim();
                if (text.startsWith(CaptureTpcds.QUERY_MARKER)) {
                    String[] parts = text.substring(CaptureTpcds.QUERY_MARKER.length()).trim().split(" ");
                    query = parts[0];
                    pass  = parts.length > 1 ? parts[1] : "";
                    seq   = 0;
                } else
                if (text.startsWith("/* ")) {
                    events++;
                    if (text.contains("[truncated to ")) {
                        truncated++;
                    } else {
                        StringBuilder body = new StringBuilder();
                        for (String l : event) {
                            Matcher m = CaptureTpcds.LINE_PREFIX.matcher(l);
                            if (m.find()) l = l.substring(m.end());
                            body.append(l).append('\n');
                        }
                        String b = body.toString().trim() + "\n";
                        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
                        String hash = CaptureTpcds.hex(digest.digest(bb));
                        if (!hashes.add(hash)) {
                            duplicates++;
                        } else {
                            Matcher m = CaptureTpcds.CLASS_NAME.matcher(b);
                            String className = m.find() ? m.group(1) : "Unknown";
                            String kind = className.replaceAll("\\d+$", "");
                            seq++;
                            String fileName = query + "-" + pass + "-" + seq + "-" + className + ".java.txt";
                            Files.write(new File(out, fileName).toPath(), bb);
                            int n = b.split("\n").length;
                            index.add(
                                fileName + "|" + query + "|" + pass + "|" + className + "|" + n + "|" + bb.length
                            );
                            kinds.merge(kind, 1, Integer::sum);
                            bytes += bb.length;
                        }
                    }
                }
                event = new ArrayList<>();
            } else {
                if (event != null) event.add(line);
            }
        }

        List<String> indexLines = new ArrayList<>();
        indexLines.add("# Class bodies that Apache Spark compiled for the TPC-DS queries; written by CaptureTpcds.");
        indexLines.add("# file|query|pass|class|lines|bytes");
        indexLines.addAll(index);
        Files.write(new File(out, "index.txt").toPath(), indexLines, StandardCharsets.UTF_8);

        System.out.printf(
            Locale.ROOT,
            "%d class bodies logged, %d distinct written to %s (%d bytes), %d duplicates, %d truncated%n",
            events,
            index.size(),
            out,
            bytes,
            duplicates,
            truncated
        );
        for (java.util.Map.Entry<String, Integer> e : kinds.entrySet()) {
            System.out.println("  " + e.getValue() + " " + e.getKey());
        }
    }

    private static String
    hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
