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

import java.util.List;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.compiler.ICompilerFactory;
import org.codehaus.commons.compiler.IScriptEvaluator;
import org.codehaus.commons.compiler.sandbox.SandboxPolicy;
import org.codehaus.commons.compiler.sandbox.SandboxViolationException;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import util.TestUtil;

/**
 * Verifies that typical code compiles and runs with the presets of {@link SandboxPolicy}, i.e. that the presets
 * cover the declaring classes of the members that such code uses.
 */
@RunWith(Parameterized.class) public
class SandboxPresetsTest {

    private final ICompilerFactory compilerFactory;

    @Parameters(name = "CompilerFactory={0}") public static List<Object[]>
    compilerFactories() throws Exception { return TestUtil.getCompilerFactoriesForParameters(); }

    public
    SandboxPresetsTest(ICompilerFactory compilerFactory) { this.compilerFactory = compilerFactory; }

    @Test public void
    testFunctionalAndStreams() throws Exception {

        // JANINO does not support lambda expressions, so the functions are anonymous classes.
        Assert.assertEquals("BB,CC|10|4", this.evaluate(
            SandboxPolicy.STREAMS,
            ""
            + "java.util.function.Function f = new java.util.function.Function() {\n"
            + "    public Object apply(Object o) { return ((String) o).toUpperCase(); }\n"
            + "};\n"
            + "java.util.function.Predicate p = new java.util.function.Predicate() {\n"
            + "    public boolean test(Object o) { return ((String) o).length() > 1; }\n"
            + "};\n"
            + "String s = (String) java.util.Arrays.asList(new Object[] { \"a\", \"bb\", \"cc\" }).stream()\n"
            + "    .filter(p)\n"
            + "    .map(f)\n"
            + "    .collect(java.util.stream.Collectors.joining(\",\"));\n"
            + "java.util.IntSummaryStatistics st = java.util.stream.IntStream.rangeClosed(1, 4).summaryStatistics();\n"
            + "return s + \"|\" + st.getSum() + \"|\" + st.getMax();\n"
        ));

        this.assertCookFails(
            SandboxPolicy.STREAMS,
            "return String.valueOf(java.util.stream.IntStream.range(0, 9).parallel().sum());",
            "java.util.stream.IntStream.parallel()"
        );
    }

    @Test public void
    testLambdas() throws Exception {
        Assume.assumeTrue("JDK only", "org.codehaus.commons.compiler.jdk".equals(this.compilerFactory.getId()));

        Assert.assertEquals("6|2", this.evaluate(
            SandboxPolicy.STREAMS,
            ""
            + "java.util.List<String> l = java.util.Arrays.asList(\"a\", \"bb\", \"ccc\");\n"
            + "int sum = l.stream().mapToInt(String::length).sum();\n"
            + "long n = l.stream().filter(s -> s.length() > 1).count();\n"
            + "return sum + \"|\" + n;\n"
        ));
    }

    @Test public void
    testMath() throws Exception {
        Assert.assertEquals("246913578024691357802469135780|3.14", this.evaluate(
            SandboxPolicy.MATH,
            ""
            + "java.math.BigInteger i = new java.math.BigInteger(\"123456789012345678901234567890\");\n"
            + "java.math.BigDecimal d = new java.math.BigDecimal(\"3.14159\");\n"
            + "return i.multiply(java.math.BigInteger.valueOf(2)) + \"|\" + d.setScale(2, java.math.RoundingMode.HALF_UP);\n"
        ));
    }

    @Test public void
    testRegex() throws Exception {
        Assert.assertEquals("34-12|a,b,c", this.evaluate(
            SandboxPolicy.REGEX,
            ""
            + "java.util.regex.Matcher m = java.util.regex.Pattern.compile(\"(\\\\d+)-(\\\\d+)\").matcher(\"12-34\");\n"
            + "if (!m.matches()) return \"no match\";\n"
            + "String[] parts = java.util.regex.Pattern.compile(\"\\\\s*;\\\\s*\").split(\"a ; b;c\");\n"
            + "return m.group(2) + \"-\" + m.group(1) + \"|\" + parts[0] + \",\" + parts[1] + \",\" + parts[2];\n"
        ));
    }

    @Test public void
    testJavaTime() throws Exception {
        Assert.assertEquals("2026-10-05|MONDAY|1791158400|120|31|P1M", this.evaluate(
            SandboxPolicy.JAVA_TIME,
            ""
            + "java.time.LocalDate d = java.time.LocalDate.of(2026, 10, 4).plusDays(1);\n"
            + "java.time.ZonedDateTime z = d.atStartOfDay(java.time.ZoneId.of(\"UTC\"));\n"
            + "java.time.LocalDate last = d.with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());\n"
            + "return (\n"
            + "    d.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)\n"
            + "    + \"|\" + d.getDayOfWeek().name()\n"
            + "    + \"|\" + z.toInstant().getEpochSecond()\n"
            + "    + \"|\" + java.time.Duration.ofHours(2).toMinutes()\n"
            + "    + \"|\" + last.getDayOfMonth()\n"
            + "    + \"|\" + java.time.Period.between(d, d.plusMonths(1))\n"
            + ");\n"
        ));
    }

    @Test public void
    testText() throws Exception {
        SandboxPolicy policy = SandboxPolicy.builder()
            .include(SandboxPolicy.TEXT)
            .include(SandboxPolicy.UTILITIES)
            .build();
        Assert.assertEquals("1,234.50|x has 3 items|1970-01-01", this.evaluate(
            policy,
            ""
            + "java.text.DecimalFormat df = new java.text.DecimalFormat(\n"
            + "    \"#,##0.00\",\n"
            + "    new java.text.DecimalFormatSymbols(java.util.Locale.US)\n"
            + ");\n"
            + "java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(\"yyyy-MM-dd\", java.util.Locale.US);\n"
            + "sdf.setTimeZone(java.util.TimeZone.getTimeZone(\"UTC\"));\n"
            + "return (\n"
            + "    df.format(1234.5)\n"
            + "    + \"|\" + java.text.MessageFormat.format(\"{0} has {1} items\", new Object[] { \"x\", \"3\" })\n"
            + "    + \"|\" + sdf.format(new java.util.Date(0L))\n"
            + ");\n"
        ));
    }

    @Test public void
    testUtilities() throws Exception {
        Assert.assertEquals("r|1|aGk=|2|007", this.evaluate(
            SandboxPolicy.UTILITIES,
            ""
            + "java.util.StringJoiner j = new java.util.StringJoiner(\"|\");\n"
            + "j.add(new java.util.Random(42).nextInt(100) >= 0 ? \"r\" : \"x\");\n"
            + "j.add(String.valueOf(\n"
            + "    java.util.UUID.fromString(\"00000000-0000-0000-0000-000000000001\").getLeastSignificantBits()\n"
            + "));\n"
            + "j.add(java.util.Base64.getEncoder().encodeToString(new byte[] { 'h', 'i' }));\n"
            + "java.util.BitSet b = new java.util.BitSet();\n"
            + "b.set(3);\n"
            + "b.set(5);\n"
            + "j.add(String.valueOf(b.cardinality()));\n"
            + "j.add(new java.util.Formatter().format(\"%03d\", new Object[] { Integer.valueOf(7) }).toString());\n"
            + "return j.toString();\n"
        ));

        this.assertCookFails(
            SandboxPolicy.UTILITIES,
            "try { new java.util.Formatter(\"out.txt\"); } catch (java.io.IOException e) {} return null;",
            "new java.util.Formatter(java.lang.String)"
        );
    }

    /**
     * Cooks and evaluates the <var>script</var> with the <var>preset</var> and {@link SandboxPolicy#JAVA_LANG_BASIC}
     * and {@link SandboxPolicy#COLLECTIONS}.
     */
    private Object
    evaluate(SandboxPolicy preset, String script) throws Exception {
        IScriptEvaluator se = this.newScriptEvaluator(preset);
        se.cook(script);
        return se.evaluate();
    }

    private void
    assertCookFails(SandboxPolicy preset, String script, String expectedMessagePart) throws Exception {
        try {
            this.newScriptEvaluator(preset).cook(script);
            Assert.fail("CompileException expected");
        } catch (CompileException ce) {
            Assert.assertTrue(ce.getMessage(), ce.getMessage().contains(expectedMessagePart));
            Assert.assertTrue(String.valueOf(ce.getCause()), ce.getCause() instanceof SandboxViolationException);
        }
    }

    private IScriptEvaluator
    newScriptEvaluator(SandboxPolicy preset) throws Exception {
        IScriptEvaluator se = this.compilerFactory.newScriptEvaluator();
        se.setSandboxPolicy(
            SandboxPolicy.builder()
                .include(SandboxPolicy.JAVA_LANG_BASIC)
                .include(SandboxPolicy.COLLECTIONS)
                .include(preset)
                .build()
        );
        se.setReturnType(String.class);
        return se;
    }
}
