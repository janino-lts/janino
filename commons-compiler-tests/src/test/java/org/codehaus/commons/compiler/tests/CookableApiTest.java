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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.codehaus.commons.compiler.ICookable;
import org.codehaus.commons.compiler.IExpressionEvaluator;
import org.codehaus.commons.compiler.IMultiCookable;
import org.codehaus.commons.compiler.IScriptEvaluator;
import org.codehaus.commons.compiler.ISimpleCompiler;
import org.codehaus.commons.nullanalysis.Nullable;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import util.CommonsCompilerTestSuite;
import util.TestUtil;

/**
 * Tests all {@code cook...()} methods of {@link ICookable} and {@link IMultiCookable}: each method must compile the
 * documents, report the file name in compile errors if it has one, and decode the documents with the given encoding.
 * <p>
 *   See <a href="https://github.com/janino-lts/janino/issues/19">issue #19</a>: {@code cookFiles(String[])} always
 *   failed, and the other {@code cookFiles(...)} methods did not report the file names.
 * </p>
 */
@RunWith(Parameterized.class) public
class CookableApiTest extends CommonsCompilerTestSuite {

    /** The value that the documents return; it contains non-ASCII characters only where the encoding is known. */
    private static final String NON_ASCII = "\u00e4\u00f6\u00fc";

    /** Is not the default encoding of any platform that runs the tests, so a wrong decoding is noticed. */
    private static final String ENCODING = "ISO-8859-1";

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private int fileCount;

    @Parameters(name = "{0}, {1}") public static Collection<Object[]>
    compilerFactories() throws Exception { return TestUtil.getCompilerFactoriesAndModesForParameters(); }

    public
    CookableApiTest(ICompilerFactory compilerFactory, String mode) { super(compilerFactory, mode); }

    @Test public void
    testCookableResults() throws Exception {
        List<String> failures = new ArrayList<String>();
        for (SingleTarget target : SingleTarget.values()) {
            for (SingleCook cook : SingleCook.values()) {
                String value = cook.decodes ? CookableApiTest.NON_ASCII : "ok";
                try {
                    Document document = this.newDocument(target.source(value), cook.decodes);
                    ICookable cookable = target.newCookable(this.compilerFactory);
                    cook.cook(cookable, document);
                    Assert.assertEquals(value, target.result(cookable));
                } catch (Throwable t) {
                    failures.add(target + "." + cook + ": " + t);
                }
            }
        }
        CookableApiTest.assertNoFailures(failures);
    }

    @Test public void
    testCookableFileNameInCompileErrors() throws Exception {
        List<String> failures = new ArrayList<String>();
        for (SingleTarget target : SingleTarget.values()) {
            for (SingleCook cook : SingleCook.values()) {
                if (!cook.hasFileName) continue;
                try {
                    Document document = this.newDocument(target.source(null), false);
                    CookableApiTest.assertCompileErrorNames(
                        document.file.getName(),
                        () -> cook.cook(target.newCookable(this.compilerFactory), document)
                    );
                } catch (Throwable t) {
                    failures.add(target + "." + cook + ": " + t);
                }
            }
        }
        CookableApiTest.assertNoFailures(failures);
    }

    @Test public void
    testMultiCookableResults() throws Exception {
        List<String> failures = new ArrayList<String>();
        for (MultiTarget target : MultiTarget.values()) {
            for (MultiCook cook : MultiCook.values()) {
                String value = cook.decodes ? CookableApiTest.NON_ASCII : "ok";
                try {
                    Document[] documents = {
                        this.newDocument(target.source("first"), cook.decodes),
                        this.newDocument(target.source(value), cook.decodes),
                    };
                    IMultiCookable cookable = target.newCookable(this.compilerFactory, 2);
                    cook.cook(cookable, documents);
                    Assert.assertEquals("first", target.result(cookable, 0));
                    Assert.assertEquals(value, target.result(cookable, 1));
                } catch (Throwable t) {
                    failures.add(target + "." + cook + ": " + t);
                }
            }
        }
        CookableApiTest.assertNoFailures(failures);
    }

    @Test public void
    testMultiCookableFileNameInCompileErrors() throws Exception {
        List<String> failures = new ArrayList<String>();
        for (MultiTarget target : MultiTarget.values()) {
            for (MultiCook cook : MultiCook.values()) {
                if (!cook.hasFileNames) continue;
                try {

                    // The second document is invalid, so the error must name the second file.
                    Document[] documents = {
                        this.newDocument(target.source("first"), false),
                        this.newDocument(target.source(null), false),
                    };
                    CookableApiTest.assertCompileErrorNames(
                        documents[1].file.getName(),
                        () -> cook.cook(target.newCookable(this.compilerFactory, 2), documents)
                    );
                } catch (Throwable t) {
                    failures.add(target + "." + cook + ": " + t);
                }
            }
        }
        CookableApiTest.assertNoFailures(failures);
    }

    /**
     * A document, both as a string and as a file. The file is encoded in {@link #ENCODING} if the document contains
     * non-ASCII characters, otherwise it consists of ASCII characters only and is valid in any default encoding.
     */
    private static final
    class Document {

        final String text;
        final File   file;

        Document(String text, File file) {
            this.text = text;
            this.file = file;
        }

        Reader
        reader() { return new StringReader(this.text); }

        InputStream
        inputStream() throws Exception { return new FileInputStream(this.file); }
    }

    private Document
    newDocument(String text, boolean nonAscii) throws Exception {
        File file = this.temporaryFolder.newFile("document" + this.fileCount++ + ".txt");
        try (OutputStream os = new FileOutputStream(file)) {
            os.write(text.getBytes(nonAscii ? CookableApiTest.ENCODING : "US-ASCII"));
        }
        return new Document(text, file);
    }

    /** The cookables that are tested with the methods of {@link ICookable}. */
    private enum SingleTarget {

        SCRIPT_EVALUATOR {

            @Override ICookable
            newCookable(ICompilerFactory compilerFactory) {
                IScriptEvaluator se = compilerFactory.newScriptEvaluator();
                se.setReturnType(String.class);
                return se;
            }

            @Override String
            source(@Nullable String value) { return CookableApiTest.script(value); }

            @Override Object
            result(ICookable cookable) throws Exception { return ((IScriptEvaluator) cookable).evaluate(); }
        },

        SIMPLE_COMPILER {

            @Override ICookable
            newCookable(ICompilerFactory compilerFactory) { return compilerFactory.newSimpleCompiler(); }

            @Override String
            source(@Nullable String value) {
                return "public class A { public static String f() { " + CookableApiTest.script(value) + " } }";
            }

            @Override Object
            result(ICookable cookable) throws Exception {
                return ((ISimpleCompiler) cookable).getClassLoader().loadClass("A").getMethod("f").invoke(null);
            }
        };

        abstract ICookable newCookable(ICompilerFactory compilerFactory);

        /** @param value {@code null} means: a document with a compile error */
        abstract String source(@Nullable String value);

        abstract Object result(ICookable cookable) throws Exception;
    }

    /** The cookables that are tested with the methods of {@link IMultiCookable}. */
    private enum MultiTarget {

        SCRIPT_EVALUATOR {

            @Override IMultiCookable
            newCookable(ICompilerFactory compilerFactory, int count) {
                IScriptEvaluator se = compilerFactory.newScriptEvaluator();
                se.setReturnTypes(CookableApiTest.stringTypes(count));
                return se;
            }

            @Override String
            source(@Nullable String value) { return CookableApiTest.script(value); }

            @Override Object
            result(IMultiCookable cookable, int idx) throws Exception {
                return ((IScriptEvaluator) cookable).evaluate(idx, new Object[0]);
            }
        },

        EXPRESSION_EVALUATOR {

            @Override IMultiCookable
            newCookable(ICompilerFactory compilerFactory, int count) {
                IExpressionEvaluator ee = compilerFactory.newExpressionEvaluator();
                ee.setExpressionTypes(CookableApiTest.stringTypes(count));
                return ee;
            }

            @Override String
            source(@Nullable String value) { return value == null ? "undefinedVariable" : '"' + value + '"'; }

            @Override Object
            result(IMultiCookable cookable, int idx) throws Exception {
                return ((IExpressionEvaluator) cookable).evaluate(idx, new Object[0]);
            }
        };

        abstract IMultiCookable newCookable(ICompilerFactory compilerFactory, int count);

        /** @param value {@code null} means: a document with a compile error */
        abstract String source(@Nullable String value);

        abstract Object result(IMultiCookable cookable, int idx) throws Exception;
    }

    /** The methods of {@link ICookable} that cook one document. */
    private enum SingleCook {

        READER(false, true) {
            @Override void
            cook(ICookable c, Document d) throws Exception { c.cook(d.reader()); }
        },
        FILE_NAME_READER(true, true) {
            @Override void
            cook(ICookable c, Document d) throws Exception { c.cook(d.file.getPath(), d.reader()); }
        },
        INPUT_STREAM(false, false) {
            @Override void
            cook(ICookable c, Document d) throws Exception {
                try (InputStream is = d.inputStream()) { c.cook(is); }
            }
        },
        FILE_NAME_INPUT_STREAM(true, false) {
            @Override void
            cook(ICookable c, Document d) throws Exception {
                try (InputStream is = d.inputStream()) { c.cook(d.file.getPath(), is); }
            }
        },
        INPUT_STREAM_ENCODING(false, true) {
            @Override void
            cook(ICookable c, Document d) throws Exception {
                try (InputStream is = d.inputStream()) { c.cook(is, CookableApiTest.ENCODING); }
            }
        },
        FILE_NAME_INPUT_STREAM_ENCODING(true, true) {
            @Override void
            cook(ICookable c, Document d) throws Exception {
                try (InputStream is = d.inputStream()) { c.cook(d.file.getPath(), is, CookableApiTest.ENCODING); }
            }
        },
        STRING(false, true) {
            @Override void
            cook(ICookable c, Document d) throws Exception { c.cook(d.text); }
        },
        FILE_NAME_STRING(true, true) {
            @Override void
            cook(ICookable c, Document d) throws Exception { c.cook(d.file.getPath(), d.text); }
        },
        COOK_FILE_FILE(true, false) {
            @Override void
            cook(ICookable c, Document d) throws Exception { c.cookFile(d.file); }
        },
        COOK_FILE_FILE_ENCODING(true, true) {
            @Override void
            cook(ICookable c, Document d) throws Exception { c.cookFile(d.file, CookableApiTest.ENCODING); }
        },
        COOK_FILE_FILE_NAME(true, false) {
            @Override void
            cook(ICookable c, Document d) throws Exception { c.cookFile(d.file.getPath()); }
        },
        COOK_FILE_FILE_NAME_ENCODING(true, true) {
            @Override void
            cook(ICookable c, Document d) throws Exception { c.cookFile(d.file.getPath(), CookableApiTest.ENCODING); }
        };

        /** Whether the method passes a file name, which must appear in compile errors. */
        final boolean hasFileName;

        /** Whether the method passes the text or its encoding, so non-ASCII characters are decoded correctly. */
        final boolean decodes;

        SingleCook(boolean hasFileName, boolean decodes) {
            this.hasFileName = hasFileName;
            this.decodes     = decodes;
        }

        abstract void cook(ICookable cookable, Document document) throws Exception;
    }

    /** The methods of {@link IMultiCookable} that cook several documents. */
    private enum MultiCook {

        READERS(false, true) {
            @Override void
            cook(IMultiCookable c, Document[] ds) throws Exception { c.cook(CookableApiTest.readers(ds)); }
        },
        FILE_NAMES_READERS(true, true) {
            @Override void
            cook(IMultiCookable c, Document[] ds) throws Exception {
                c.cook(CookableApiTest.fileNames(ds), CookableApiTest.readers(ds));
            }
        },
        STRINGS(false, true) {
            @Override void
            cook(IMultiCookable c, Document[] ds) throws Exception { c.cook(CookableApiTest.texts(ds)); }
        },
        FILE_NAMES_STRINGS(true, true) {
            @Override void
            cook(IMultiCookable c, Document[] ds) throws Exception {
                c.cook(CookableApiTest.fileNames(ds), CookableApiTest.texts(ds));
            }
        },
        INPUT_STREAMS(false, false) {
            @Override void
            cook(IMultiCookable c, Document[] ds) throws Exception {
                InputStream[] iss = CookableApiTest.inputStreams(ds);
                try { c.cook(iss); } finally { CookableApiTest.close(iss); }
            }
        },
        INPUT_STREAMS_ENCODINGS(false, true) {
            @Override void
            cook(IMultiCookable c, Document[] ds) throws Exception {
                InputStream[] iss = CookableApiTest.inputStreams(ds);
                try { c.cook(iss, CookableApiTest.encodings(ds)); } finally { CookableApiTest.close(iss); }
            }
        },
        FILE_NAMES_INPUT_STREAMS(true, false) {
            @Override void
            cook(IMultiCookable c, Document[] ds) throws Exception {
                InputStream[] iss = CookableApiTest.inputStreams(ds);
                try { c.cook(CookableApiTest.fileNames(ds), iss); } finally { CookableApiTest.close(iss); }
            }
        },
        FILE_NAMES_INPUT_STREAMS_ENCODINGS(true, true) {
            @Override void
            cook(IMultiCookable c, Document[] ds) throws Exception {
                InputStream[] iss = CookableApiTest.inputStreams(ds);
                try {
                    c.cook(CookableApiTest.fileNames(ds), iss, CookableApiTest.encodings(ds));
                } finally {
                    CookableApiTest.close(iss);
                }
            }
        },
        COOK_FILES_FILES(true, false) {
            @Override void
            cook(IMultiCookable c, Document[] ds) throws Exception { c.cookFiles(CookableApiTest.files(ds)); }
        },
        COOK_FILES_FILES_ENCODINGS(true, true) {
            @Override void
            cook(IMultiCookable c, Document[] ds) throws Exception {
                c.cookFiles(CookableApiTest.files(ds), CookableApiTest.encodings(ds));
            }
        },
        COOK_FILES_FILE_NAMES(true, false) {
            @Override void
            cook(IMultiCookable c, Document[] ds) throws Exception { c.cookFiles(CookableApiTest.fileNames(ds)); }
        },
        COOK_FILES_FILE_NAMES_ENCODINGS(true, true) {
            @Override void
            cook(IMultiCookable c, Document[] ds) throws Exception {
                c.cookFiles(CookableApiTest.fileNames(ds), CookableApiTest.encodings(ds));
            }
        };

        /** Whether the method passes file names, which must appear in compile errors. */
        final boolean hasFileNames;

        /** Whether the method passes the texts or their encodings, so non-ASCII characters are decoded correctly. */
        final boolean decodes;

        MultiCook(boolean hasFileNames, boolean decodes) {
            this.hasFileNames = hasFileNames;
            this.decodes      = decodes;
        }

        abstract void cook(IMultiCookable cookable, Document[] documents) throws Exception;
    }

    /** An operation that is expected to throw a {@link CompileException}. */
    private interface Cooking {
        void run() throws Exception;
    }

    private static void
    assertCompileErrorNames(String fileName, Cooking cooking) throws Exception {
        try {
            cooking.run();
        } catch (CompileException ce) {
            String message = ce.getMessage();
            Assert.assertTrue(
                "Compile error does not name \"" + fileName + "\": " + message,
                message != null && message.contains(fileName)
            );
            return;
        }
        Assert.fail("CompileException expected");
    }

    private static void
    assertNoFailures(List<String> failures) {
        if (!failures.isEmpty()) Assert.fail(failures.size() + " failure(s):\n" + String.join("\n", failures));
    }

    /** @param value {@code null} means: a script with a compile error */
    private static String
    script(@Nullable String value) {
        return value == null ? "return undefinedVariable;" : "return \"" + value + "\";";
    }

    private static Class<?>[]
    stringTypes(int count) {
        Class<?>[] result = new Class<?>[count];
        for (int i = 0; i < count; i++) result[i] = String.class;
        return result;
    }

    private static Reader[]
    readers(Document[] documents) {
        Reader[] result = new Reader[documents.length];
        for (int i = 0; i < documents.length; i++) result[i] = documents[i].reader();
        return result;
    }

    private static String[]
    texts(Document[] documents) {
        String[] result = new String[documents.length];
        for (int i = 0; i < documents.length; i++) result[i] = documents[i].text;
        return result;
    }

    private static File[]
    files(Document[] documents) {
        File[] result = new File[documents.length];
        for (int i = 0; i < documents.length; i++) result[i] = documents[i].file;
        return result;
    }

    private static String[]
    fileNames(Document[] documents) {
        String[] result = new String[documents.length];
        for (int i = 0; i < documents.length; i++) result[i] = documents[i].file.getPath();
        return result;
    }

    private static String[]
    encodings(Document[] documents) {
        String[] result = new String[documents.length];
        for (int i = 0; i < documents.length; i++) result[i] = CookableApiTest.ENCODING;
        return result;
    }

    /** The caller must {@link #close(InputStream[])} the streams. */
    private static InputStream[]
    inputStreams(Document[] documents) throws Exception {
        InputStream[] result = new InputStream[documents.length];
        for (int i = 0; i < documents.length; i++) result[i] = documents[i].inputStream();
        return result;
    }

    private static void
    close(InputStream[] inputStreams) throws Exception {
        for (InputStream is : inputStreams) is.close();
    }
}
