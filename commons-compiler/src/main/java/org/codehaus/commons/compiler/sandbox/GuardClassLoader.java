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

package org.codehaus.commons.compiler.sandbox;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import org.codehaus.commons.compiler.util.Privileged;
import org.codehaus.commons.nullanalysis.Nullable;

/**
 * Makes {@link Guard} and the types of its signatures and exceptions ({@link SandboxLimitExceededError}, {@link
 * SandboxLimits}), including their nested classes, visible to the code that JANINO instruments, even if the parent
 * class loader cannot see them (e.g. because it is the platform class loader), and makes sure that the code uses the
 * same {@link Guard} class as the {@link SandboxExecutor}. All other classes are loaded through the parent class
 * loader.
 */
public final
class GuardClassLoader extends ClassLoader {

    private static final Set<String> TOP_LEVEL_CLASS_NAMES = Collections.unmodifiableSet(new HashSet<String>(
        Arrays.asList(Guard.class.getName(), SandboxLimitExceededError.class.getName(), SandboxLimits.class.getName())
    ));

    private
    GuardClassLoader(@Nullable ClassLoader parent) { super(parent); }

    /**
     * @param parent {@code null} means the bootstrap class loader
     */
    public static ClassLoader
    create(@Nullable final ClassLoader parent) {
        return (ClassLoader) Privileged.run(new Supplier<ClassLoader>() {

            @Override public ClassLoader
            get() { return new GuardClassLoader(parent); }
        });
    }

    /**
     * @return                        The named class if it is one of the classes that this class loader provides
     *                                (see above), otherwise {@code null}
     * @throws ClassNotFoundException The name designates a nested class of these classes that does not exist
     */
    @Nullable public static Class<?>
    getGuardClass(String className) throws ClassNotFoundException {

        int    idx               = className.indexOf('$');
        String topLevelClassName = idx == -1 ? className : className.substring(0, idx);
        if (!GuardClassLoader.TOP_LEVEL_CLASS_NAMES.contains(topLevelClassName)) return null;

        return Class.forName(className, false, Guard.class.getClassLoader());
    }

    @Override protected Class<?>
    loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> result = GuardClassLoader.getGuardClass(name);
        return result != null ? result : super.loadClass(name, resolve);
    }
}
