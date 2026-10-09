
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

package util;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.codehaus.commons.compiler.CompilerFactoryFactory;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.codehaus.commons.compiler.ISimpleCompiler;
import org.junit.runners.Parameterized.Parameters;

/**
 * Utility methods for testing.
 */
public final
class TestUtil {

    /**
     * The mode in which JANINO compiles by default, see {@link #getCompilerFactoriesAndModesForParameters()}.
     */
    public static final String COMPAT = "compat";

    /**
     * The mode in which JANINO compiles with the option {@code JaninoOption.JAVAC_COMPLIANCE}, see {@link
     * #getCompilerFactoriesAndModesForParameters()}.
     */
    public static final String COMPLIANT = "compliant";

    /**
     * The "mode" of the JDK-based compiler, which has no modes, see {@link
     * #getCompilerFactoriesAndModesForParameters()}.
     */
    public static final String JAVAC = "javac";

    private static final String JANINO_FACTORY_ID = "org.codehaus.janino";

    private static final String JANINO_OPTION_CLASS_NAME = "org.codehaus.janino.JaninoOption";

    private static final String JAVAC_COMPLIANCE_OPTION_NAME = "JAVAC_COMPLIANCE";

    /**
     * Use this method as follows:
     * <pre>
     *     &#64;Parameters(name = "CompilerFactory={0}")
     *     public static Collection&lt;Object[]>
     *     <em>any-name</em>() throws Exception {
     *         return TestUtil.getCompilerFactoriesForParameters();
     *     }
     * </pre>
     *
     * @return The available compiler factories in a format suitable for JUnit {@link Parameters}
     */
    public static List<Object[]>
    getCompilerFactoriesForParameters() throws Exception {
        ArrayList<Object[]> f = new ArrayList<>();
        for (ICompilerFactory fact : CompilerFactoryFactory.getAllCompilerFactories(TestUtil.class.getClassLoader())) {
            f.add(new Object[] { fact });
        }
        if (f.isEmpty()) {
            throw new RuntimeException("Could not find any Compiler Factories on the classpath");
        }
        return f;
    }

    /**
     * Like {@link #getCompilerFactoriesForParameters()}, but each element is a compiler factory <em>and a mode</em>:
     * JANINO's factory appears twice, with the modes {@link #COMPAT} and {@link #COMPLIANT}; any other factory once,
     * with the mode {@link #JAVAC}. Use {@link #newSimpleCompiler(ICompilerFactory, String)} to create a compiler for
     * the mode.
     */
    public static List<Object[]>
    getCompilerFactoriesAndModesForParameters() throws Exception {
        List<Object[]> result = new ArrayList<>();
        for (Object[] e : TestUtil.getCompilerFactoriesForParameters()) {
            ICompilerFactory cf = (ICompilerFactory) e[0];
            if (TestUtil.isJanino(cf)) {
                result.add(new Object[] { cf, TestUtil.COMPAT });
                result.add(new Object[] { cf, TestUtil.COMPLIANT });
            } else {
                result.add(new Object[] { cf, TestUtil.JAVAC });
            }
        }
        return result;
    }

    /**
     * @return Whether the compiler factory is JANINO's
     */
    public static boolean
    isJanino(ICompilerFactory compilerFactory) { return TestUtil.JANINO_FACTORY_ID.equals(compilerFactory.getId()); }

    /**
     * Creates a simple compiler for the given mode (see {@link #getCompilerFactoriesAndModesForParameters()}).
     */
    public static ISimpleCompiler
    newSimpleCompiler(ICompilerFactory compilerFactory, String mode) throws Exception {
        ISimpleCompiler result = compilerFactory.newSimpleCompiler();
        if (TestUtil.COMPLIANT.equals(mode)) TestUtil.setJavacCompliance(result);
        return result;
    }

    /**
     * Adds the option {@code JaninoOption.JAVAC_COMPLIANCE} to the options of the given JANINO compiler ({@code
     * SimpleCompiler}, an evaluator, {@code Compiler} or {@code JavaSourceIClassLoader}). This module compiles only
     * against the {@code commons-compiler} API, so the option is set through reflection.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" }) public static void
    setJavacCompliance(Object janinoCompiler) throws Exception {

        Class<?> optionClass = Class.forName(
            TestUtil.JANINO_OPTION_CLASS_NAME,
            true,
            janinoCompiler.getClass().getClassLoader()
        );
        Enum option = Enum.valueOf((Class) optionClass, TestUtil.JAVAC_COMPLIANCE_OPTION_NAME);

        Method getter = janinoCompiler.getClass().getMethod("options");
        Method setter = janinoCompiler.getClass().getMethod("options", EnumSet.class);

        EnumSet options = EnumSet.copyOf((EnumSet) getter.invoke(janinoCompiler));
        options.add(option);
        setter.invoke(janinoCompiler, options);
    }

    private TestUtil() {}
}
