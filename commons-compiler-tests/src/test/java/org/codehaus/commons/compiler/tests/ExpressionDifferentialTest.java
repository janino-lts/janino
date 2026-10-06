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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.codehaus.commons.nullanalysis.Nullable;
import org.junit.Test;

/**
 * Generates random expressions over the primitive types, their wrapper types and {@link String} (operators, casts,
 * conditional expressions, assignments, compound assignments, increments and decrements on local variables, fields
 * and array elements), compiles them with JANINO and with the JDK-based compiler, and compares the results. The
 * generator is deterministic (fixed seeds), so that every difference is reproducible.
 * <p>
 *   Each expression is compiled twice: in method {@code p}<var>n</var>, its leaves refer to the method's parameters
 *   (the expression is evaluated at run time), and in method {@code c}<var>n</var>, the leaves that refer to
 *   parameters of primitive type or of type {@link String} are replaced with literals (the compiler folds the
 *   constant subexpressions). Each method returns the value of the expression and its type (or the class of the
 *   exception that the evaluation throws), and the values of all variables that the method uses.
 * </p>
 * <p>
 *   The generator avoids the constructs that JANINO is known to compile incorrectly (see {@link Defect}). Other
 *   differences that are currently known are recorded in {@value #KNOWN_DIFFERENCES}, one per line: "<var>seed</var>
 *   <var>method</var> <var>kind</var>", where <var>kind</var> is one of
 * </p>
 * <dl>
 *   <dt>{@code WRONG}</dt>
 *   <dd>JANINO's code produces a different result</dd>
 *   <dt>{@code INVALID}</dt>
 *   <dd>
 *     JANINO generates a class file that the JVM rejects (e.g. {@code VerifyError}), or fails with an internal
 *     compiler error
 *   </dd>
 *   <dt>{@code REJECTED}</dt>
 *   <dd>JANINO reports a compile error</dd>
 * </dl>
 * <p>
 *   Like {@link ControlFlowDifferentialTest}, the test fails if the actual differences deviate from the recorded ones
 *   in any way, i.e. also when a defect is fixed; then the record must be updated deliberately.
 * </p>
 * <p>
 *   With the system property {@code expression.differential.classes}, the test generates the given number of
 *   classes, prints all differences, and does not compare them with the record. The system property {@code
 *   expression.differential.avoid} lists the defects whose constructs the generator avoids (comma-separated, by
 *   default all), e.g. to verify a fix.
 * </p>
 */
public
class ExpressionDifferentialTest {

    private static final String KNOWN_DIFFERENCES = "src/test/resources/expressionDifferential/known-differences.txt";

    private static final int  CLASSES               = 60;
    private static final int  EXPRESSIONS_PER_CLASS = 25;
    private static final int  ARGUMENT_VECTORS      = 4;
    private static final long SEED                  = 20261005L;

    /**
     * The beginning of the generated class, which declares a static and an instance field of each type, and the
     * method {@code r()}, which describes the value of an expression.
     */
    static final String CLASS_HEADER;
    static {
        StringBuilder sb = new StringBuilder("public class P {\n");
        for (Type t : Type.values()) {
            sb.append("    static ").append(t.name).append(" f").append(t.letter).append(";\n");
            sb.append("    ").append(t.name).append(" x").append(t.letter).append(";\n");
        }
        sb.append("    static String r(Object o) {\n");
        sb.append("        return o == null ? \"null\" : o.getClass().getSimpleName() + \":\" + o;\n");
        sb.append("    }\n");
        CLASS_HEADER = sb.toString();
    }

    @Test public void
    test() throws Exception {

        ICompilerFactory[] compilerFactories = DifferentialTesting.janinoAndJdk();
        ICompilerFactory   janino            = compilerFactories[0];
        ICompilerFactory   jdk               = compilerFactories[1];

        String soak    = System.getProperty("expression.differential.classes");
        int    classes = soak == null ? ExpressionDifferentialTest.CLASSES : Integer.parseInt(soak);

        // By default, the generator avoids the constructs of all known defects.
        Set<Defect> avoided = EnumSet.allOf(Defect.class);
        String      avoid   = System.getProperty("expression.differential.avoid");
        if (avoid != null) {
            avoided.clear();
            for (String name : avoid.split(",")) {
                if (!name.trim().isEmpty()) avoided.add(Defect.valueOf(name.trim()));
            }
        }

        Set<String>         actualDifferences = new TreeSet<>();
        Map<String, String> details           = new HashMap<>();
        for (int c = 0; c < classes; c++) {

            long   seed   = ExpressionDifferentialTest.SEED + c;
            Random random = new Random(seed);

            Object[][] arguments = new Object[ExpressionDifferentialTest.ARGUMENT_VECTORS][];
            for (int v = 0; v < arguments.length; v++) arguments[v] = Type.randomArguments(random);

            Generator    generator = new Generator(random, avoided);
            List<String> names     = new ArrayList<>();
            List<String> methods   = new ArrayList<>();
            for (int e = 0; e < ExpressionDifferentialTest.EXPRESSIONS_PER_CLASS; e++) {
                Template template = generator.template();
                names.add("p" + e);
                methods.add(template.render("p" + e, null));
                names.add("c" + e);
                methods.add(template.render("c" + e, arguments[0]));
            }

            for (Map.Entry<String, String> e : ExpressionDifferentialTest.compare(
                janino,
                jdk,
                names,
                methods,
                arguments
            ).entrySet()) {
                String difference = seed + " " + e.getKey();
                actualDifferences.add(difference);
                String name = e.getKey().substring(0, e.getKey().indexOf(' '));
                details.put(difference, e.getValue() + "\n" + methods.get(names.indexOf(name)));
            }
        }

        if (soak != null) {
            for (String d : actualDifferences) System.out.println(d + "\n" + details.get(d));
            System.out.println(actualDifferences.size() + " differences");
            return;
        }

        DifferentialTesting.assertDifferences(ExpressionDifferentialTest.KNOWN_DIFFERENCES, actualDifferences, details);
    }

    /**
     * Compiles the <var>methods</var> in one class with both compilers, and compares the results.
     *
     * @return The differences ("<var>method</var> <var>kind</var>", mapped to a description)
     */
    private static Map<String, String>
    compare(
        ICompilerFactory janino,
        ICompilerFactory jdk,
        List<String>     names,
        List<String>     methods,
        Object[][]       arguments
    ) throws Exception {

        String source = ExpressionDifferentialTest.classSource(methods, -1);

        // The JDK-based compiler is the reference; the generator must produce code that it accepts.
        ClassLoader expectedCl;
        try {
            expectedCl = DifferentialTesting.compile(jdk, source);
        } catch (CompileException ce) {
            throw new AssertionError("JAVAC rejects the generated code: " + ce + "\n" + source, ce);
        }

        // Compile all methods in one class; only if JANINO fails to compile or load that class, compile each method
        // separately, so that the defects can be attributed to the methods.
        Map<String, String> result = ExpressionDifferentialTest.compareMethods(
            janino,
            expectedCl,
            source,
            names,
            arguments,
            -1
        );
        if (result != null) return result;

        result = new HashMap<>();
        for (int m = 0; m < methods.size(); m++) {
            Map<String, String> methodDifferences = ExpressionDifferentialTest.compareMethods(
                janino,
                expectedCl,
                ExpressionDifferentialTest.classSource(methods, m),
                names,
                arguments,
                m
            );
            assert methodDifferences != null;
            result.putAll(methodDifferences);
        }
        return result;
    }

    /**
     * Compiles the <var>source</var> (which contains all methods, or only the method with index <var>onlyMethod</var>,
     * if not -1) with JANINO, and compares the results of the methods with those of the class that the JDK-based
     * compiler generated.
     *
     * @return The differences ("<var>method</var> <var>kind</var>", mapped to a description), or {@code null} iff
     *         <var>onlyMethod</var> is -1 and JANINO fails to compile or to load the class
     */
    @Nullable private static Map<String, String>
    compareMethods(
        ICompilerFactory janino,
        ClassLoader      expectedCl,
        String           source,
        List<String>     names,
        Object[][]       arguments,
        int              onlyMethod
    ) {

        ClassLoader actualCl;
        try {
            actualCl = DifferentialTesting.compile(janino, source);
        } catch (CompileException ce) {
            if (onlyMethod == -1) return null;
            return Collections.singletonMap(names.get(onlyMethod) + " REJECTED", ce.toString());
        } catch (Exception | AssertionError e) { // E.g. "InternalCompilerException"
            if (onlyMethod == -1) return null;
            Throwable cause = e;
            while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
            return Collections.singletonMap(
                names.get(onlyMethod) + " INVALID",
                cause == e ? e.toString() : e + "\nCaused by: " + cause
            );
        }

        Map<String, String> result = new HashMap<>();
        for (int m = 0; m < names.size(); m++) {
            if (onlyMethod != -1 && m != onlyMethod) continue;

            // The "c" methods contain the arguments of the first argument vector as literals.
            String name    = names.get(m);
            int    vectors = name.startsWith("c") ? 1 : arguments.length;
            for (int v = 0; v < vectors; v++) {
                String expected = ExpressionDifferentialTest.invoke(expectedCl, name, arguments[v]);
                if (expected.startsWith("?")) {
                    throw new AssertionError("The code that JAVAC generated fails: " + name + ": " + expected);
                }

                String actual = ExpressionDifferentialTest.invoke(actualCl, name, arguments[v]);
                if (actual.startsWith("?")) {
                    if (onlyMethod == -1) return null;
                    result.put(name + " INVALID", actual);
                    break;
                }
                if (!expected.equals(actual)) {
                    result.put(
                        name + " WRONG",
                        name + Type.describe(arguments[v]) + ": expected <" + expected + "> but was <" + actual + ">"
                    );
                    break;
                }
            }
        }
        return result;
    }

    private static String
    classSource(List<String> methods, int onlyMethod) {
        StringBuilder sb = new StringBuilder(ExpressionDifferentialTest.CLASS_HEADER);
        for (int m = 0; m < methods.size(); m++) {
            if (onlyMethod == -1 || m == onlyMethod) sb.append(methods.get(m));
        }
        return sb.append("}\n").toString();
    }

    /**
     * @return The result of the method, or "?" and the exception that prevents its invocation (e.g. a {@link
     *         VerifyError})
     */
    private static String
    invoke(ClassLoader cl, String methodName, Object[] arguments) {
        try {
            Method method = cl.loadClass("P").getDeclaredMethod(methodName, Type.PARAMETER_CLASSES);
            return String.valueOf(method.invoke(null, arguments));
        } catch (InvocationTargetException ite) {

            // The generated methods catch all exceptions themselves.
            return "!" + ite.getCause();
        } catch (Throwable t) {
            return "?" + t;
        }
    }

    /**
     * The types of the generated expressions; each generated method has one parameter of each type.
     */
    enum Type {

        BOOLEAN("boolean", 'z', boolean.class),
        BYTE("byte", 'b', byte.class),
        SHORT("short", 's', short.class),
        CHAR("char", 'c', char.class),
        INT("int", 'i', int.class),
        LONG("long", 'j', long.class),
        FLOAT("float", 'f', float.class),
        DOUBLE("double", 'd', double.class),
        BOOLEAN_WRAPPER("Boolean", 'Z', Boolean.class),
        BYTE_WRAPPER("Byte", 'B', Byte.class),
        SHORT_WRAPPER("Short", 'S', Short.class),
        CHAR_WRAPPER("Character", 'C', Character.class),
        INT_WRAPPER("Integer", 'I', Integer.class),
        LONG_WRAPPER("Long", 'J', Long.class),
        FLOAT_WRAPPER("Float", 'F', Float.class),
        DOUBLE_WRAPPER("Double", 'D', Double.class),
        STRING("String", 't', String.class);

        static final Class<?>[] PARAMETER_CLASSES;
        static final String     PARAMETERS;
        static {
            Type[] types = Type.values();
            PARAMETER_CLASSES = new Class<?>[types.length];
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < types.length; i++) {
                PARAMETER_CLASSES[i] = types[i].clazz;
                if (i > 0) sb.append(", ");
                sb.append(types[i].name).append(' ').append(types[i].letter);
            }
            PARAMETERS = sb.toString();
        }

        /**
         * The name of the type in Java source code.
         */
        final String name;

        /**
         * The name of the parameter of this type, and the suffix of the names of the variables of this type.
         */
        final char letter;

        private final Class<?> clazz;

        Type(String name, char letter, Class<?> clazz) {
            this.name   = name;
            this.letter = letter;
            this.clazz  = clazz;
        }

        boolean isPrimitive() { return this.ordinal() < Type.BOOLEAN_WRAPPER.ordinal(); }
        boolean isWrapper()   { return !this.isPrimitive() && this != Type.STRING; }

        /**
         * @return The primitive type of a wrapper type, or the type itself
         */
        Type
        unboxed() { return this.isWrapper() ? Type.values()[this.ordinal() - Type.BOOLEAN_WRAPPER.ordinal()] : this; }

        boolean isBoolean() { return this.unboxed() == Type.BOOLEAN; }

        boolean
        isNumeric() {
            Type u = this.unboxed();
            return u != Type.BOOLEAN && u != Type.STRING;
        }

        boolean isIntegral() { return this.isNumeric() && this.unboxed().ordinal() <= Type.LONG.ordinal(); }

        /**
         * @return The type to which unary numeric promotion (JLS 5.6) converts this numeric type
         */
        Type
        promoted() {
            Type u = this.unboxed();
            return u.ordinal() < Type.INT.ordinal() ? Type.INT : u;
        }

        /**
         * @return Whether this primitive type is identical to <var>to</var>, or can be converted to it by a widening
         *         primitive conversion (JLS 5.1.2)
         */
        boolean
        widensTo(Type to) {
            if (this == to) return true;
            if (!this.isNumeric() || !to.isNumeric()) return false;
            if (to.ordinal() < Type.INT.ordinal()) return this == Type.BYTE && to == Type.SHORT;
            return this.ordinal() < to.ordinal();
        }

        /**
         * The values that the arguments and the literals of this primitive type (or {@link #STRING}) assume.
         */
        Object[]
        pool() {
            switch (this) {
            case BOOLEAN: return new Object[] { true, false };
            case BYTE:
                return new Object[] { (byte) 0, (byte) 1, (byte) -1, (byte) 7, Byte.MIN_VALUE, Byte.MAX_VALUE };
            case SHORT:
                return new Object[] { (short) 0, (short) 1, (short) -1, (short) 300, Short.MIN_VALUE, Short.MAX_VALUE };
            case CHAR:    return new Object[] { (char) 0, 'a', (char) 127, (char) 128, Character.MAX_VALUE };
            case INT:
                return new Object[] { 0, 1, -1, 7, -8, 31, 32, 33, 65, Integer.MIN_VALUE, Integer.MAX_VALUE };
            case LONG:
                return new Object[] { 0L, 1L, -1L, 7L, -8L, 63L, 64L, 4294967296L, Long.MIN_VALUE, Long.MAX_VALUE };
            case FLOAT:
                return new Object[] {
                    0.0F, -0.0F, 1.5F, -2.5F, 1e10F, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY,
                    Float.MAX_VALUE, Float.MIN_VALUE,
                };
            case DOUBLE:
                return new Object[] {
                    0.0, -0.0, 1.5, -2.5, 1e300, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
                    Double.MAX_VALUE, Double.MIN_VALUE,
                };
            case STRING:  return new Object[] { "", "a", "b1" };
            default:      throw new AssertionError(this);
            }
        }

        /**
         * Constant variables of this primitive type, declared in the wrapper classes.
         */
        String[]
        constants() {
            switch (this) {
            case BYTE:   return new String[] { "Byte.MIN_VALUE", "Byte.MAX_VALUE" };
            case SHORT:  return new String[] { "Short.MIN_VALUE", "Short.MAX_VALUE" };
            case CHAR:   return new String[] { "Character.MIN_VALUE", "Character.MAX_VALUE" };
            case INT:    return new String[] { "Integer.MIN_VALUE", "Integer.MAX_VALUE" };
            case LONG:   return new String[] { "Long.MIN_VALUE", "Long.MAX_VALUE" };
            case FLOAT:
                return new String[] { "Float.NaN", "Float.MAX_VALUE", "Float.MIN_VALUE", "Float.NEGATIVE_INFINITY" };
            case DOUBLE:
                return new String[] {
                    "Double.NaN", "Double.MAX_VALUE", "Double.MIN_VALUE", "Double.POSITIVE_INFINITY",
                };
            default:     return new String[0];
            }
        }

        /**
         * @return A literal (or constant expression) of this primitive type (or {@link #STRING}) with the given value
         */
        String
        literal(@Nullable Object value) {

            if (value == null) return "((" + this.name + ") null)";

            switch (this) {
            case BOOLEAN: return value.toString();
            case BYTE:    return "((byte) " + value + ")";
            case SHORT:   return "((short) " + value + ")";
            case CHAR:    return (Character) value == 'a' ? "'a'" : "((char) " + (int) (Character) value + ")";
            case INT:     return ExpressionDifferentialTest.parenthesizeNegative(value.toString());
            case LONG:    return ExpressionDifferentialTest.parenthesizeNegative(value + "L");
            case FLOAT: {
                float f = (Float) value;
                if (Float.isNaN(f))                  return "(0.0F / 0.0F)";
                if (f == Float.POSITIVE_INFINITY)    return "(1.0F / 0.0F)";
                if (f == Float.NEGATIVE_INFINITY)    return "(-1.0F / 0.0F)";
                return ExpressionDifferentialTest.parenthesizeNegative(value + "F");
            }
            case DOUBLE: {
                double d = (Double) value;
                if (Double.isNaN(d))                 return "(0.0 / 0.0)";
                if (d == Double.POSITIVE_INFINITY)   return "(1.0 / 0.0)";
                if (d == Double.NEGATIVE_INFINITY)   return "(-1.0 / 0.0)";
                return ExpressionDifferentialTest.parenthesizeNegative(value.toString());
            }
            case STRING:  return "\"" + value + "\"";
            default:      throw new AssertionError(this);
            }
        }

        /**
         * @return An argument for each parameter of the generated methods; arguments of wrapper type and of type
         *         {@link String} are sometimes {@code null}
         */
        static Object[]
        randomArguments(Random random) {
            Type[]   types  = Type.values();
            Object[] result = new Object[types.length];
            for (int i = 0; i < types.length; i++) {
                Type t = types[i];
                if (!t.isPrimitive() && random.nextInt(16) == 0) continue;
                Object[] values = t.unboxed().pool();
                result[i] = values[random.nextInt(values.length)];
            }
            return result;
        }

        /**
         * @return A readable description of the <var>arguments</var>
         */
        static String
        describe(Object[] arguments) {
            Type[]        types = Type.values();
            StringBuilder sb    = new StringBuilder("(");
            for (int i = 0; i < types.length; i++) {
                if (i > 0) sb.append(", ");
                Object a = arguments[i];
                sb.append(types[i].letter).append('=').append(
                    a instanceof Character ? "(char) " + (int) (Character) a : String.valueOf(a)
                );
            }
            return sb.append(')').toString();
        }
    }

    static String
    parenthesizeNegative(String literal) { return literal.startsWith("-") ? "(" + literal + ")" : literal; }

    /**
     * Known defects of JANINO. The generator avoids the respective constructs, so that it finds other defects; when a
     * defect is fixed, its constant is to be removed.
     */
    enum Defect {

        /**
         * The type of a conditional expression whose operands have different primitive or wrapper types is often
         * wrong (JLS 15.25): JANINO reports a compile error, or the value has the wrong type. Issue #38.
         */
        CONDITIONAL_TYPE,

        /**
         * JANINO does not unbox the left operand of {@code ||} and {@code &&} if the right operand is constant and
         * determines the result ({@code Z || true}, {@code Z && false}), so that a {@code null} operand does not throw
         * a {@link NullPointerException}. Issue #40.
         */
        UNBOXING_OF_LOGICAL_OPERAND,
    }

    /**
     * A generated method: expression statements and the expression whose value the method returns. The leaves that
     * refer to parameters of primitive type or of type {@link String} have the form "$<var>letter</var>$", so that
     * they can be replaced with literals.
     */
    static final
    class Template {

        private static final Pattern PARAMETER = Pattern.compile("\\$(.)\\$");

        private final List<String> statements;
        private final String       expression;
        private final Set<Type>    locals, arrays, instanceFields, staticFields;

        Template(
            List<String> statements,
            String       expression,
            Set<Type>    locals,
            Set<Type>    arrays,
            Set<Type>    instanceFields,
            Set<Type>    staticFields
        ) {
            this.statements     = statements;
            this.expression     = expression;
            this.locals         = locals;
            this.arrays         = arrays;
            this.instanceFields = instanceFields;
            this.staticFields   = staticFields;
        }

        /**
         * @param constants {@code null} for leaves that refer to the parameters, or the arguments that replace these
         *                  leaves (as literals)
         */
        String
        render(String name, @Nullable Object[] constants) {

            StringBuilder sb = new StringBuilder();
            sb.append("    public static String ").append(name).append('(').append(Type.PARAMETERS).append(") {\n");

            StringBuilder state = new StringBuilder();
            for (Type t : this.locals) {
                sb.append("        ").append(t.name).append(" v").append(t.letter).append(" = ").append(t.letter);
                sb.append(";\n");
                state.append(" + \" v").append(t.letter).append("=\" + v").append(t.letter);
            }
            for (Type t : this.arrays) {
                sb.append("        ").append(t.name).append("[] a").append(t.letter).append(" = { ");
                sb.append(t.letter).append(", ").append(t.letter).append(" };\n");
                state.append(" + \" a").append(t.letter).append("=\" + java.util.Arrays.toString(a");
                state.append(t.letter).append(')');
            }
            if (!this.instanceFields.isEmpty()) sb.append("        P o = new P();\n");
            for (Type t : this.instanceFields) {
                sb.append("        o.x").append(t.letter).append(" = ").append(t.letter).append(";\n");
                state.append(" + \" o.x").append(t.letter).append("=\" + o.x").append(t.letter);
            }
            for (Type t : this.staticFields) {
                sb.append("        f").append(t.letter).append(" = ").append(t.letter).append(";\n");
                state.append(" + \" f").append(t.letter).append("=\" + f").append(t.letter);
            }

            sb.append("        String result;\n");
            sb.append("        try {\n");
            for (String s : this.statements) {
                sb.append("            ").append(Template.replaceParameters(s, constants)).append(";\n");
            }
            sb.append("            result = P.r(").append(Template.replaceParameters(this.expression, constants));
            sb.append(");\n");
            sb.append("        } catch (Throwable e) {\n");
            sb.append("            result = \"!\" + e.getClass().getName();\n");
            sb.append("        }\n");
            sb.append("        return result").append(state).append(";\n");
            sb.append("    }\n");
            return sb.toString();
        }

        private static String
        replaceParameters(String code, @Nullable Object[] constants) {

            StringBuffer sb = new StringBuffer();
            Matcher      m  = Template.PARAMETER.matcher(code);
            while (m.find()) {
                char   letter      = m.group(1).charAt(0);
                String replacement = String.valueOf(letter);
                if (constants != null) {
                    for (Type t : Type.values()) {
                        if (t.letter == letter) replacement = t.literal(constants[t.ordinal()]);
                    }
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            return m.appendTail(sb).toString();
        }
    }

    /**
     * Generates expressions of a given type. Every generated expression and every subexpression is either a primary
     * or enclosed in parentheses, so that operator precedence never matters.
     * <p>
     *   The generator determines the type of each expression exactly, with one exception: A conditional expression
     *   whose operands have the types {@code byte}, {@code short} or {@code char} and {@code int} has the narrower
     *   type if the {@code int} operand is a constant that is representable in that type (JLS 15.25), which the
     *   generator does not know; it assumes type {@code int}. Therefore, the generator never uses an expression of
     *   type {@code int} where a narrower type would be invalid, e.g. in an assignment to a variable of type {@link
     *   Integer}.
     * </p>
     */
    static
    class Generator {

        private static final int MAX_DEPTH = 4;

        private final Random       random;
        private final Set<Defect>  avoided;

        // The variables that the current template uses.
        private final Set<Type> locals         = EnumSet.noneOf(Type.class);
        private final Set<Type> arrays         = EnumSet.noneOf(Type.class);
        private final Set<Type> instanceFields = EnumSet.noneOf(Type.class);
        private final Set<Type> staticFields   = EnumSet.noneOf(Type.class);

        /**
         * @param avoided The constructs that the generator does not generate
         */
        Generator(Random random, Set<Defect> avoided) {
            this.random  = random;
            this.avoided = avoided;
        }

        Template
        template() {

            this.locals.clear();
            this.arrays.clear();
            this.instanceFields.clear();
            this.staticFields.clear();

            List<String> statements = new ArrayList<>();
            for (int i = this.random.nextInt(3); i > 0; i--) {
                statements.add(this.assignment(this.randomType(), 1));
            }
            String expression = this.expression(this.randomType(), 0);

            return new Template(
                statements,
                expression,
                this.copy(this.locals),
                this.copy(this.arrays),
                this.copy(this.instanceFields),
                this.copy(this.staticFields)
            );
        }

        private Set<Type>
        copy(Set<Type> types) {
            Set<Type> result = EnumSet.noneOf(Type.class);
            result.addAll(types);
            return result;
        }

        /**
         * @return An expression of type <var>t</var>
         */
        private String
        expression(Type t, int depth) {

            if (depth >= Generator.MAX_DEPTH || this.random.nextInt(5) == 0) return this.leaf(t, depth);

            for (;;) {
                String result;
                switch (this.random.nextInt(10)) {
                case 0:  result = this.cast(t, depth);                           break;
                case 1:  result = this.conditional(t, depth);                    break;
                case 2:  result = "(" + this.assignment(t, depth) + ")";         break;
                case 3:  result = this.unary(t, depth);                          break;
                case 4:
                case 5:
                case 6:
                case 7:  result = this.binary(t, depth);                         break;
                default: result = this.leaf(t, depth);                           break;
                }
                if (result != null) return result;
            }
        }

        /**
         * @return A parameter, a variable or a literal of type <var>t</var>
         */
        private String
        leaf(Type t, int depth) {
            switch (this.random.nextInt(t.isWrapper() ? 2 : 3)) {
            case 0:  return t.isWrapper() ? String.valueOf(t.letter) : "$" + t.letter + "$";
            case 1:  return this.variable(t, depth);
            default: return this.literal(t);
            }
        }

        private String
        literal(Type t) {
            if (t == Type.STRING && this.random.nextInt(4) == 0) return t.literal(null);
            String[] constants = t.constants();
            if (constants.length > 0 && this.random.nextInt(4) == 0) return this.pick(constants);
            return t.literal(this.pick(t.pool()));
        }

        /**
         * @return A local variable, a static field, an instance field or an array element of type <var>t</var>
         */
        private String
        variable(Type t, int depth) {
            switch (this.random.nextInt(5)) {
            case 0:
            case 1:
                this.locals.add(t);
                return "v" + t.letter;
            case 2:
                this.staticFields.add(t);
                return (this.random.nextBoolean() ? "P.f" : "f") + t.letter;
            case 3:
                this.instanceFields.add(t);
                return "o.x" + t.letter;
            default:
                this.arrays.add(t);
                String index = (
                    depth + 1 >= Generator.MAX_DEPTH || this.random.nextBoolean()
                    ? String.valueOf(this.random.nextInt(2))
                    : "((int) " + this.expression(this.castSource(Type.INT), depth + 1) + " & 1)"
                );
                return "a" + t.letter + "[" + index + "]";
            }
        }

        private String
        cast(Type t, int depth) {
            if (t == Type.STRING) return "((String) " + this.expression(Type.STRING, depth + 1) + ")";
            if (t.isWrapper()) {
                Type u = t.unboxed();
                return "((" + t.name + ") (" + u.name + ") " + this.expression(this.castSource(u), depth + 1) + ")";
            }
            return "((" + t.name + ") " + this.expression(this.castSource(t), depth + 1) + ")";
        }

        /**
         * @return A type that can be cast to the primitive type <var>p</var> (JLS 5.5)
         */
        private Type
        castSource(Type p) {
            if (p == Type.BOOLEAN) return this.booleanType();
            List<Type> candidates = new ArrayList<>();
            for (Type t : Type.values()) {
                if (t.isNumeric() && (t.isPrimitive() || t.unboxed().widensTo(p))) candidates.add(t);
            }
            return this.pick(candidates);
        }

        private String
        conditional(Type t, int depth) {
            Type[] operands = this.conditionalOperands(t);
            return (
                "("
                + this.expression(this.booleanType(), depth + 1)
                + " ? "
                + this.expression(operands[0], depth + 1)
                + " : "
                + this.expression(operands[1], depth + 1)
                + ")"
            );
        }

        /**
         * @return The types of the second and the third operand of a conditional expression of type <var>t</var>
         */
        private Type[]
        conditionalOperands(Type t) {

            if (t == Type.BOOLEAN) {
                switch (this.random.nextInt(3)) {
                case 0:  return new Type[] { Type.BOOLEAN, Type.BOOLEAN };
                case 1:  return new Type[] { Type.BOOLEAN, Type.BOOLEAN_WRAPPER };
                default: return new Type[] { Type.BOOLEAN_WRAPPER, Type.BOOLEAN };
                }
            }
            if (t.isPrimitive()) {
                if (!this.avoided.contains(Defect.CONDITIONAL_TYPE)) {
                    for (int i = 0; i < 20; i++) {
                        Type a = this.anyNumericType(), b = this.anyNumericType();
                        if (a != b && Generator.conditionalType(a, b) == t) return new Type[] { a, b };
                    }
                }
                Type w = Type.values()[t.ordinal() + Type.BOOLEAN_WRAPPER.ordinal()];
                switch (this.random.nextInt(3)) {
                case 0:  return new Type[] { t, w };
                case 1:  return new Type[] { w, t };
                default: return new Type[] { t, t };
                }
            }
            return new Type[] { t, t };
        }

        /**
         * @return The type of a conditional expression with numeric operands of types <var>a</var> and <var>b</var>
         *         (JLS 15.25), assuming that operands of type {@code int} are not constant
         */
        private static Type
        conditionalType(Type a, Type b) {
            if (a == b) return a;
            Type ua = a.unboxed(), ub = b.unboxed();
            if (ua == ub) return ua;
            if (
                (ua == Type.BYTE && ub == Type.SHORT)
                || (ua == Type.SHORT && ub == Type.BYTE)
            ) return Type.SHORT;
            return a.promoted().ordinal() > b.promoted().ordinal() ? a.promoted() : b.promoted();
        }

        /**
         * @return An assignment, a compound assignment, or an increment or decrement of a variable of type
         *         <var>t</var>, without enclosing parentheses
         */
        private String
        assignment(Type t, int depth) {

            String variable = this.variable(t, depth);
            String result   = null;
            switch (this.random.nextInt(3)) {
            case 1:
                result = this.compoundAssignment(t, variable, depth);
                break;
            case 2:
                result = this.crement(t, variable);
                break;
            }
            if (result == null) result = variable + " = " + this.expression(this.assignable(t), depth + 1);
            return result;
        }

        /**
         * @return The type of an expression that can be assigned to a variable of type <var>t</var> (JLS 5.2)
         */
        private Type
        assignable(Type t) {

            if (t == Type.STRING) return t;
            if (t == Type.BOOLEAN) return this.booleanType();

            // An expression of type "int" may actually have a narrower type; see the class comment.
            if (t.isWrapper()) return t.unboxed() == Type.INT || this.random.nextBoolean() ? t : t.unboxed();

            List<Type> candidates = new ArrayList<>();
            for (Type c : Type.values()) {
                if (c.isNumeric() && c.unboxed().widensTo(t)) candidates.add(c);
            }
            return this.pick(candidates);
        }

        @Nullable private String
        compoundAssignment(Type t, String variable, int depth) {

            if (t == Type.STRING) return variable + " += " + this.expression(this.randomType(), depth + 1);

            Type u = t.unboxed();
            if (u == Type.BOOLEAN) {
                return (
                    variable
                    + " "
                    + this.pick("&=", "|=", "^=")
                    + " "
                    + this.expression(this.booleanType(), depth + 1)
                );
            }

            // For a variable of wrapper type, the result of the operation must have the primitive type of the variable
            // (JLS 15.26.2), which is impossible for "Byte", "Short" and "Character".
            if (t.isWrapper() && u.ordinal() < Type.INT.ordinal()) return null;
            Type limit = t.isWrapper() ? u : Type.DOUBLE;

            switch (t.isIntegral() ? this.random.nextInt(3) : 0) {
            case 0:
                return (
                    variable
                    + " "
                    + this.pick("+=", "-=", "*=", "/=", "%=")
                    + " "
                    + this.expression(this.numericType(limit, false, false), depth + 1)
                );
            case 1:
                return (
                    variable
                    + " "
                    + this.pick("<<=", ">>=", ">>>=")
                    + " "
                    + this.expression(this.numericType(Type.LONG, true, false), depth + 1)
                );
            default:
                return (
                    variable
                    + " "
                    + this.pick("&=", "|=", "^=")
                    + " "
                    + this.expression(this.numericType(t.isWrapper() ? u : Type.LONG, true, false), depth + 1)
                );
            }
        }

        @Nullable private String
        crement(Type t, String variable) {
            if (!t.isNumeric()) return null;
            switch (this.random.nextInt(4)) {
            case 0:  return variable + "++";
            case 1:  return variable + "--";
            case 2:  return "++" + variable;
            default: return "--" + variable;
            }
        }

        @Nullable private String
        unary(Type t, int depth) {

            if (t == Type.BOOLEAN) return "(!" + this.expression(this.booleanType(), depth + 1) + ")";

            if (!t.isPrimitive() || t.promoted() != t) return null;

            if (t.isIntegral() && this.random.nextInt(3) == 0) {
                return "(~" + this.expression(this.numericType(t, true, true), depth + 1) + ")";
            }
            String operator = this.random.nextBoolean() ? "-" : "+";
            return "(" + operator + this.expression(this.numericType(t, false, true), depth + 1) + ")";
        }

        @Nullable private String
        binary(Type t, int depth) {

            if (t == Type.BOOLEAN) return this.booleanBinary(depth);

            if (t == Type.STRING) {
                String x = this.expression(Type.STRING, depth + 1);
                String y = this.expression(this.randomType(), depth + 1);
                return this.random.nextBoolean() ? "(" + x + " + " + y + ")" : "(" + y + " + " + x + ")";
            }

            if (!t.isPrimitive() || t.promoted() != t) return null;

            switch (t.isIntegral() ? this.random.nextInt(3) : 0) {
            case 0:
                return this.binary(this.operands(t, false), this.pick("*", "/", "%", "+", "-"), depth);
            case 1:
                return this.binary(
                    new Type[] { this.numericType(t, true, true), this.numericType(Type.LONG, true, false) },
                    this.pick("<<", ">>", ">>>"),
                    depth
                );
            default:
                return this.binary(this.operands(t, true), this.pick("&", "|", "^"), depth);
            }
        }

        private String
        booleanBinary(int depth) {
            switch (this.random.nextInt(3)) {
            case 0:
                return this.binary(
                    new Type[] { this.anyNumericType(), this.anyNumericType() },
                    this.pick("<", "<=", ">", ">="),
                    depth
                );
            case 1: {

                // Two operands of wrapper type are compared by reference (JLS 15.21.3), and must have the same type.
                Type a, b;
                switch (this.random.nextInt(3)) {
                case 0:
                    a = this.anyNumericType();
                    b = this.anyNumericType();
                    if (a.isWrapper() && b.isWrapper()) b = b.unboxed();
                    break;
                case 1:
                    a = b = Type.values()[Type.BOOLEAN_WRAPPER.ordinal() + this.random.nextInt(8)];
                    break;
                default:
                    a = this.booleanType();
                    b = a.isWrapper() ? Type.BOOLEAN : this.booleanType();
                    break;
                }
                return this.binary(new Type[] { a, b }, this.pick("==", "!="), depth);
            }
            default: {
                String operator = this.pick("&", "|", "^", "&&", "||");
                Type   a        = this.booleanType();
                if (operator.length() == 2 && this.avoided.contains(Defect.UNBOXING_OF_LOGICAL_OPERAND)) {
                    a = Type.BOOLEAN;
                }
                return this.binary(new Type[] { a, this.booleanType() }, operator, depth);
            }
            }
        }

        private String
        binary(Type[] operands, String operator, int depth) {
            return (
                "("
                + this.expression(operands[0], depth + 1)
                + " "
                + operator
                + " "
                + this.expression(operands[1], depth + 1)
                + ")"
            );
        }

        /**
         * @return The types of two (integral) numeric operands for which binary numeric promotion (JLS 5.6) yields
         *         <var>t</var>
         */
        private Type[]
        operands(Type t, boolean integral) {
            Type a = this.numericType(t, integral, false), b = this.numericType(t, integral, false);
            if (a.promoted() != t && b.promoted() != t) {
                if (this.random.nextBoolean()) {
                    a = this.numericType(t, integral, true);
                } else {
                    b = this.numericType(t, integral, true);
                }
            }
            return new Type[] { a, b };
        }

        /**
         * @param exact Whether the type must promote to <var>promoted</var>, or to <var>promoted</var> or a narrower
         *              type
         * @return      A primitive or wrapper type that promotes to <var>promoted</var> (JLS 5.6)
         */
        private Type
        numericType(Type promoted, boolean integral, boolean exact) {
            List<Type> candidates = new ArrayList<>();
            for (Type t : Type.values()) {
                if (!t.isNumeric() || (integral && !t.isIntegral())) continue;
                int o = t.promoted().ordinal();
                if (exact ? o == promoted.ordinal() : o <= promoted.ordinal()) candidates.add(t);
            }
            return this.pick(candidates);
        }

        private Type
        anyNumericType() { return this.numericType(Type.DOUBLE, false, false); }

        private Type
        booleanType() { return this.random.nextInt(3) == 0 ? Type.BOOLEAN_WRAPPER : Type.BOOLEAN; }

        private Type
        randomType() { return this.pick(Type.values()); }

        @SafeVarargs private final <T> T
        pick(T... values) { return values[this.random.nextInt(values.length)]; }

        private <T> T
        pick(List<T> values) { return values.get(this.random.nextInt(values.size())); }
    }
}
