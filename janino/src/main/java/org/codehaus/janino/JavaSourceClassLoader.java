
/*
 * Janino - An embedded Java[TM] compiler
 *
 * Copyright (c) 2001-2010 Arno Unkrig. All rights reserved.
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

package org.codehaus.janino;

import java.io.File;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.commons.compiler.AbstractJavaSourceClassLoader;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.ErrorHandler;
import org.codehaus.commons.compiler.InternalCompilerException;
import org.codehaus.commons.compiler.WarningHandler;
import org.codehaus.commons.compiler.lang.ClassLoaders;
import org.codehaus.commons.compiler.sandbox.BytecodeVerifier;
import org.codehaus.commons.compiler.sandbox.GuardClassLoader;
import org.codehaus.commons.compiler.sandbox.SandboxPolicy;
import org.codehaus.commons.compiler.sandbox.SandboxViolation;
import org.codehaus.commons.compiler.sandbox.SandboxViolationException;
import org.codehaus.commons.compiler.util.Disassembler;
import org.codehaus.commons.compiler.util.resource.DirectoryResourceFinder;
import org.codehaus.commons.compiler.util.resource.PathResourceFinder;
import org.codehaus.commons.compiler.util.resource.ResourceFinder;
import org.codehaus.commons.nullanalysis.Nullable;
import org.codehaus.janino.UnitCompiler.ClassFileConsumer;
import org.codehaus.janino.util.ClassFile;

/**
 * A {@link ClassLoader} that, unlike usual {@link ClassLoader}s, does not load byte code, but reads Java source code
 * and then scans, parses, compiles and loads it into the virtual machine.
 * <p>
 *   As with any {@link ClassLoader}, it is not possible to "update" classes after they've been loaded. The way to
 *   achieve this is to give up on the {@link JavaSourceClassLoader} and create a new one.
 * </p>
 * <p>
 *   Notice that this class loader does not support resoures in the sense of {@link ClassLoader#getResource(String)},
 *   {@link ClassLoader#getResourceAsStream(String)} nd {@link ClassLoader#getResources(String)}.
 * </p>
 *
 * @see ClassLoaders
 */
public
class JavaSourceClassLoader extends AbstractJavaSourceClassLoader {

    public
    JavaSourceClassLoader() { this(ClassLoader.getSystemClassLoader()); }

    public
    JavaSourceClassLoader(ClassLoader parentClassLoader) {
        this(
            parentClassLoader,
            (File[]) null,     // sourcePath
            null               // characterEncoding
        );
    }

    /**
     * Sets up a {@link JavaSourceClassLoader} that finds Java source code in a file that resides in either of
     * the directories specified by the given source path.
     *
     * @param parentClassLoader         See {@link ClassLoader}
     * @param sourcePath        A collection of directories that are searched for Java source files in
     *                                  the given order
     * @param characterEncoding The encoding of the Java source files ({@code null} for platform
     *                                  default encoding)
     */
    public
    JavaSourceClassLoader(
        ClassLoader      parentClassLoader,
        @Nullable File[] sourcePath,
        @Nullable String characterEncoding
    ) {
        this(
            parentClassLoader, // parentClassLoader
            (                  // sourceFinder
                sourcePath == null
                ? new DirectoryResourceFinder(new File("."))
                : new PathResourceFinder(sourcePath)
            ),
            characterEncoding  // characterEncoding
        );
    }

    /**
     * Constructs a {@link JavaSourceClassLoader} that finds Java source code through a given {@link
     * ResourceFinder}.
     * <p>
     *   You can specify to include certain debugging information in the generated class files, which is useful if you
     *   want to debug through the generated classes (see {@link Scanner#Scanner(String, Reader)}).
     * </p>
     *
     * @param parentClassLoader         See {@link ClassLoader}
     * @param sourceFinder              Used to locate additional source files
     * @param characterEncoding The encoding of the Java source files ({@code null} for platform
     *                                  default encoding)
     */
    public
    JavaSourceClassLoader(
        ClassLoader      parentClassLoader,
        ResourceFinder   sourceFinder,
        @Nullable String characterEncoding
    ) {
        this(parentClassLoader, new JavaSourceIClassLoader(
            sourceFinder,                                                          // sourceFinder
            characterEncoding,                                                     // characterEncoding
            new ClassLoaderIClassLoader(GuardClassLoader.create(parentClassLoader)) // parentIClassLoader
        ));
    }

    /**
     * Constructs a {@link JavaSourceClassLoader} that finds classes through an {@link JavaSourceIClassLoader}.
     */
    public
    JavaSourceClassLoader(ClassLoader parentClassLoader, JavaSourceIClassLoader iClassLoader) {
        super(parentClassLoader);
        this.iClassLoader = iClassLoader;
    }

    @Override public void
    setSourcePath(File[] sourcePath) { this.setSourceFinder(new PathResourceFinder(sourcePath)); }

    @Override public void
    setSourceFinder(ResourceFinder sourceFinder) { this.iClassLoader.setSourceFinder(sourceFinder); }

    @Override public void
    setSourceCharset(Charset charset) { this.iClassLoader.setSourceCharset(charset); }

    @Override public void
    setDebuggingInfo(boolean debugSource, boolean debugLines, boolean debugVars) {
        this.debugSource = debugSource;
        this.debugLines  = debugLines;
        this.debugVars   = debugVars;
    }

    @Override public void
    setTargetVersion(int version) { this.iClassLoader.setTargetVersion(version); }

    /**
     * @see UnitCompiler#setCompileErrorHandler
     */
    public void
    setCompileErrorHandler(@Nullable ErrorHandler compileErrorHandler) {
        this.iClassLoader.setCompileErrorHandler(compileErrorHandler);
    }

    /**
     * @see Parser#setWarningHandler(WarningHandler)
     * @see UnitCompiler#setCompileErrorHandler
     */
    public void
    setWarningHandler(@Nullable WarningHandler warningHandler) {
        this.iClassLoader.setWarningHandler(warningHandler);
    }

    /**
     * Code that JANINO compiles with a sandbox policy calls the {@link
     * org.codehaus.commons.compiler.sandbox.Guard Guard}, which must be visible to it even if the parent class loader
     * cannot see it; see {@link GuardClassLoader}.
     */
    @Override protected Class<?>
    loadClass(@Nullable String name, boolean resolve) throws ClassNotFoundException {
        assert name != null;
        Class<?> result = GuardClassLoader.getGuardClass(name);
        return result != null ? result : super.loadClass(name, resolve);
    }

    /**
     * Implementation of {@link ClassLoader#findClass(String)}.
     *
     * @throws ClassNotFoundException
     */
    @Override protected /*synchronized <- No need to synchronize, because 'loadClass()' is synchronized */ Class<?>
    findClass(@Nullable String name) throws ClassNotFoundException {
        assert name != null;

        // Check if the bytecode for that class was generated already.
        byte[] bytecode = (byte[]) this.precompiledClasses.remove(name);
        if (bytecode == null) {

            // Read, scan, parse and compile the right compilation unit.
            {
                Map<String /*name*/, byte[] /*bytecode*/> bytecodes = this.generateBytecodes(name);
                if (bytecodes == null) throw new ClassNotFoundException(name);

                SandboxPolicy sandboxPolicy = this.sandboxPolicy;
                if (sandboxPolicy != null) this.verify(bytecodes, sandboxPolicy);

                this.precompiledClasses.putAll(bytecodes);
            }

            // Now the bytecode for our class should be available.
            bytecode = (byte[]) this.precompiledClasses.remove(name);
            if (bytecode == null) {
                throw new InternalCompilerException(
                    "SNO: Scanning, parsing and compiling class \""
                    + name
                    + "\" did not create a class file!?"
                );
            }
        }

        if (Boolean.getBoolean("disasm")) Disassembler.disassembleToStdout(bytecode);

        return this.defineBytecode(name, bytecode);
    }

    /**
     * Verifies the <var>bytecodes</var> against the <var>sandboxPolicy</var>.
     *
     * @throws ClassNotFoundException The bytecodes violate the policy; the cause is a {@link
     *                                SandboxViolationException}
     */
    private void
    verify(Map<String /*name*/, byte[] /*bytecode*/> bytecodes, SandboxPolicy sandboxPolicy)
    throws ClassNotFoundException {

        // While the bytecodes are being verified, other classes may be loaded (and verified) that refer to them,
        // so make the bytecodes available as "pending classes".
        this.classesBeingVerified.putAll(bytecodes);
        try {
            Map<String /*name*/, byte[] /*bytecode*/> pendingClasses = new HashMap<>(this.precompiledClasses);
            pendingClasses.putAll(this.classesBeingVerified);

            List<SandboxViolation> violations = new BytecodeVerifier(sandboxPolicy).verify(
                bytecodes,
                this,
                pendingClasses
            );
            if (!violations.isEmpty()) {
                SandboxViolationException sve = new SandboxViolationException(violations);
                throw new ClassNotFoundException(sve.getMessage(), sve);
            }
        } finally {
            this.classesBeingVerified.keySet().removeAll(bytecodes.keySet());
        }
    }

    /**
     * The classes that are currently being verified.
     */
    private final Map<String /*name*/, byte[] /*bytecode*/> classesBeingVerified = new HashMap<>();

    private final Set<UnitCompiler> compiledUnitCompilers = new HashSet<>();

    /**
     * This {@link Map} keeps those classes which were already compiled, but not yet defined i.e. which were not yet
     * passed to {@link ClassLoader#defineClass(java.lang.String, byte[], int, int)}.
     */
    private final Map<String /*name*/, byte[] /*bytecode*/> precompiledClasses = new HashMap<>();

    /**
     * Finds, scans, parses the right compilation unit. Compile the parsed compilation unit to bytecode. This may cause
     * more compilation units being scanned and parsed. Continue until all compilation units are compiled.
     *
     * @return String name =&gt; byte[] bytecode, or {@code null} if no source code could be found
     * @throws ClassNotFoundException on compilation problems
     */
    @Nullable protected Map<String /*name*/, byte[] /*bytecode*/>
    generateBytecodes(String name) throws ClassNotFoundException {

        // Code that is compiled with a sandbox policy checks the resource limits of a "SandboxExecutor".
        this.iClassLoader.setSandboxInstrumentation(this.sandboxPolicy != null);

        if (this.iClassLoader.loadIClass(Descriptor.fromClassName(name)) == null) return null;

        final Map<String /*className*/, byte[] /*bytecode*/> bytecodes = new HashMap<>();
        COMPILE_UNITS:
        for (;;) {
            for (UnitCompiler uc : this.iClassLoader.getUnitCompilers()) {
                if (!this.compiledUnitCompilers.contains(uc)) {
                    try {

                        // Violations of a sandbox policy are reported with source locations, which requires debugging
                        // information.
                        uc.compileUnit(
                            this.debugSource || this.sandboxPolicy != null,
                            this.debugLines || this.sandboxPolicy != null,
                            this.debugVars,
                            new ClassFileConsumer() {

                                @Override public void
                                consume(ClassFile classFile) {
                                    bytecodes.put(classFile.getThisClassName(), classFile.toByteArray());
                                }
                            }
                        );
                    } catch (CompileException ex) {
                        throw new ClassNotFoundException(ex.getMessage(), ex);
                    }
                    this.compiledUnitCompilers.add(uc);
                    continue COMPILE_UNITS;
                }
            }
            return bytecodes;
        }
    }

    /**
     * @throws ClassFormatError
     * @see #setProtectionDomainFactory
     */
    private Class<?>
    defineBytecode(String className, byte[] ba) {

        return this.defineClass(className, ba, 0, ba.length, (
            this.protectionDomainFactory != null
            ? this.protectionDomainFactory.getProtectionDomain(ClassFile.getSourceResourceName(className))
            : null
        ));
    }

    private final JavaSourceIClassLoader iClassLoader;

    private boolean debugSource = Boolean.getBoolean(Scanner.SYSTEM_PROPERTY_SOURCE_DEBUGGING_ENABLE);
    private boolean debugLines  = this.debugSource;
    private boolean debugVars   = this.debugSource;
}
