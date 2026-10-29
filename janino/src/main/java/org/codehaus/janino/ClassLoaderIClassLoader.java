
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

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.codehaus.commons.nullanalysis.Nullable;

/**
 * An {@link IClassLoader} that loads {@link IClass}es through a reflection {@link ClassLoader}.
 * <p>
 *   The {@link IClass}es of the loaded classes are shared between all compilations, also concurrent ones, so that the
 *   members of a class are read by reflection only once: There is one {@link IClass} per {@link Class}, which lives as
 *   long as the {@link Class}, and does not keep a class loader alive. The classes that a loaded class references
 *   (its superclass, interfaces, and the types of its members) are those that the JVM links, i.e. those that the
 *   defining loader of the class determines. A name in the source code is still resolved through the {@link
 *   ClassLoader} of this object. A name in the package {@code java.lang} that this {@link ClassLoader} does not find
 *   is not looked up again, because these classes are the same for all class loaders (with the only exception of a
 *   class that an agent adds to the boot class path at runtime, on Java 8).
 * </p>
 * <p>
 *   The system property {@code org.codehaus.janino.sharedIClassCache=false} switches that off: Then each {@link
 *   ClassLoaderIClassLoader} reads the members of the classes again, and also resolves the classes that a loaded class
 *   references by name through its {@link ClassLoader}.
 * </p>
 */
public
class ClassLoaderIClassLoader extends IClassLoader {

    private static final Logger LOGGER = Logger.getLogger(ClassLoaderIClassLoader.class.getName());

    /**
     * The names of the classes in the package {@code java.lang} and its subpackages that do not exist.
     */
    private static final Set<String>
    MISSING_JAVA_LANG_CLASSES = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

    /**
     * @param classLoader The delegate that loads the classes
     */
    public
    ClassLoaderIClassLoader(ClassLoader classLoader) {
        super(
            null   // parentIClassLoader
        );
        this.classLoader = classLoader;

        super.postConstruct();
    }

    /**
     * Equivalent to
     * <pre>
     *   ClassLoaderIClassLoader(Thread.currentThread().getContextClassLoader())
     * </pre>
     */
    public
    ClassLoaderIClassLoader() { this(Thread.currentThread().getContextClassLoader()); }

    /**
     * @return The delegate {@link ClassLoader}
     */
    public ClassLoader
    getClassLoader() { return this.classLoader; }

    @Override @Nullable protected IClass
    findIClass(String descriptor) throws ClassNotFoundException {
        ClassLoaderIClassLoader.LOGGER.entering(null, "findIClass", descriptor);

        String  className = Descriptor.toClassName(descriptor);
        boolean javaLang  = ReflectionIClass.SHARED && className.startsWith("java.lang.");
        if (javaLang && ClassLoaderIClassLoader.MISSING_JAVA_LANG_CLASSES.contains(className)) return null;

        Class<?> clazz;
        try {
            clazz = this.classLoader.loadClass(className);
        } catch (NoClassDefFoundError ncdfe) {

            // Handle a very special case here -- see issue #165:
            // Iff a class and a subpackage with the same name (less capitalization) exist in the same
            // package, then class may be loaded accidentially; and "ClassLoader.defaineClass()" throws, e.g.
            //   java.lang.NoClassDefFoundError: com/company/User (wrong name: com/company/user)
            // This case must be handled like "class not found".
            // Tested with
            //    adopt_openjdk-8.0.292.10-hotspot
            //    adopt_openjdk-11.0.11.9-hotspot
            //    jdk-17.0.1+12
            if (ncdfe.getMessage().contains("wrong name")) return null;

            throw ncdfe;
        } catch (ClassNotFoundException e) {

            // Determine whether the class DOES NOT EXIST, or whether there were problems loading it. That's easier
            // said than done... the following seems to work:
            // (See also https://github.com/janino-compiler/janino/issues/104).
            {
                Throwable t = e.getCause();
                while (t instanceof ClassNotFoundException) t = t.getCause();
                if (t == null) {
                    if (javaLang) ClassLoaderIClassLoader.MISSING_JAVA_LANG_CLASSES.add(className);
                    return null;
                }
            }

            throw e;
        }

        ClassLoaderIClassLoader.LOGGER.log(Level.FINE, "clazz={0}", clazz);

        IClass result = ReflectionIClass.SHARED ? ReflectionIClass.of(clazz) : new ReflectionIClass(clazz, this);
        this.defineIClass(result);
        return result;
    }

    private final ClassLoader classLoader;
}
