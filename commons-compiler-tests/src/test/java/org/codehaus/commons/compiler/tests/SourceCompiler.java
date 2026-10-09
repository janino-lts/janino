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
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.codehaus.commons.compiler.ISimpleCompiler;
import org.codehaus.commons.compiler.InternalCompilerException;
import org.codehaus.commons.nullanalysis.Nullable;

import util.TestUtil;

/**
 * Compiles a compilation unit into class files: with one of the compilers on the class path in a mode (see {@link
 * #of(ICompilerFactory, String)}), or with a previous version of JANINO (see {@link #legacy(File)}).
 */
interface SourceCompiler {

    /**
     * @return The generated class files, by class name
     * @throws CompileException          The compiler rejects the code
     * @throws InternalCompilerException The compiler fails with an internal error
     */
    Map<String, byte[]> compile(String source) throws Exception;

    /**
     * @return A compiler of the given factory in the given mode (see {@link
     *         TestUtil#getCompilerFactoriesAndModesForParameters()})
     */
    static SourceCompiler
    of(ICompilerFactory compilerFactory, String mode) {
        return source -> {
            ISimpleCompiler sc = TestUtil.newSimpleCompiler(compilerFactory, mode);
            sc.cook(source);
            return SourceCompiler.byClassName(sc.getBytecodes());
        };
    }

    /**
     * @return The class files by class name; the JDK-based compiler keys them by resource name ({@code
     *         "pkg/C.class"}), JANINO by class name ({@code "pkg.C"})
     */
    static Map<String, byte[]>
    byClassName(Map<String, byte[]> classFiles) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : classFiles.entrySet()) {
            String name = e.getKey();
            if (name.endsWith(".class")) {
                name = name.substring(0, name.length() - ".class".length()).replace('/', '.');
            }
            result.put(name, e.getValue());
        }
        return result;
    }

    /**
     * Loads a previous version of JANINO (its {@code janino-*.jar} and {@code commons-compiler-*.jar}) from the given
     * directory into a class loader of its own, and returns a compiler that uses its {@code SimpleCompiler} with
     * target version 8. The version's exceptions are translated into the current {@link CompileException} and {@link
     * InternalCompilerException}.
     */
    static SourceCompiler
    legacy(File directory) throws Exception {

        List<URL> urls = new ArrayList<>();
        File[]    files = directory.listFiles();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (n.endsWith(".jar") && (n.startsWith("janino-") || n.startsWith("commons-compiler-"))) {
                    urls.add(f.toURI().toURL());
                }
            }
        }
        if (urls.size() != 2) {
            throw new IllegalStateException(
                "Expected \"janino-*.jar\" and \"commons-compiler-*.jar\" in \"" + directory + "\", found " + urls
            );
        }

        // The parent is the platform class loader (Java 9 and later), or the bootstrap class loader (Java 8), so
        // that the legacy version sees neither the current version nor the test classes.
        ClassLoader parent = null;
        try {
            parent = (ClassLoader) ClassLoader.class.getMethod("getPlatformClassLoader").invoke(null);
        } catch (NoSuchMethodException nsme) {
            ;
        }

        @SuppressWarnings("resource") ClassLoader cl = new URLClassLoader(urls.toArray(new URL[0]), parent);

        Class<?>       simpleCompilerClass = cl.loadClass("org.codehaus.janino.SimpleCompiler");
        Constructor<?> constructor         = simpleCompilerClass.getConstructor();
        Method         setTargetVersion    = simpleCompilerClass.getMethod("setTargetVersion", int.class);
        Method         cook                = simpleCompilerClass.getMethod("cook", String.class);
        Method         getBytecodes        = simpleCompilerClass.getMethod("getBytecodes");

        return source -> {
            Object sc = constructor.newInstance();
            setTargetVersion.invoke(sc, 8);
            try {
                cook.invoke(sc, source);
            } catch (InvocationTargetException ite) {
                throw SourceCompiler.translate(ite.getCause());
            }
            try {
                @SuppressWarnings("unchecked") Map<String, byte[]>
                result = (Map<String, byte[]>) getBytecodes.invoke(sc);
                return result;
            } catch (InvocationTargetException ite) {
                throw SourceCompiler.translate(ite.getCause());
            }
        };
    }

    /**
     * @return The exception of the legacy version, translated into the current {@link CompileException} or {@link
     *         InternalCompilerException} (which it cannot be an instance of, because it was loaded by another class
     *         loader), or unchanged
     * @throws Error The legacy version threw an {@link Error}
     */
    static Exception
    translate(@Nullable Throwable t) {
        if (t == null) return new IllegalStateException("Exception without a cause");
        String className = t.getClass().getName();
        if (className.equals(CompileException.class.getName())) {
            return new CompileException(String.valueOf(t.getMessage()), null, t);
        }
        if (className.equals(InternalCompilerException.class.getName())) {
            return new InternalCompilerException(t.getMessage(), t);
        }
        if (t instanceof Error) throw (Error) t;
        return t instanceof Exception ? (Exception) t : new RuntimeException(t);
    }

    /**
     * Defines the classes in a class loader without a code source location. Coverage agents (e.g. JaCoCo) do not
     * instrument such classes; instrumenting rewrites a class file, and would hide some of its defects (e.g. duplicate
     * entries in the "InnerClasses" attribute).
     */
    static ClassLoader
    loader(Map<String, byte[]> classes) {
        return new ClassLoader(SourceCompiler.class.getClassLoader()) {

            @Override protected Class<?>
            findClass(String name) throws ClassNotFoundException {
                byte[] b = classes.get(name);
                if (b == null) throw new ClassNotFoundException(name);
                return this.defineClass(name, b, 0, b.length);
            }
        };
    }
}
