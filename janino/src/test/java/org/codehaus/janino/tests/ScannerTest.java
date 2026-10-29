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

package org.codehaus.janino.tests;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.commons.nullanalysis.Nullable;
import org.codehaus.janino.Scanner;
import org.codehaus.janino.Token;
import org.codehaus.janino.TokenType;
import org.codehaus.janino.UnicodeUnescapeException;
import org.junit.Assert;
import org.junit.Test;

/**
 * Tests for the {@link Scanner}: token values and types, line and column numbers, unicode escapes, and the error
 * messages and their locations.
 */
public
class ScannerTest {

    private static final String BS = "\\";

    @Test public void
    testLineAndColumnNumbers() throws Exception {

        // A TAB advances the column to the next multiple of 8; CR, LF and CR LF each end a line. The END_OF_INPUT
        // token has the location of the preceding token.
        ScannerTest.assertTokens(
            "a\tb\r\n\tc\rd\ne  f",
            "IDENTIFIER a 1:1",
            "IDENTIFIER b 1:9",
            "IDENTIFIER c 2:9",
            "IDENTIFIER d 3:1",
            "IDENTIFIER e 4:1",
            "IDENTIFIER f 4:4",
            "END_OF_INPUT end-of-input 4:4"
        );
    }

    @Test public void
    testUnicodeEscapes() throws Exception {

        // In an identifier, with redundant "u"s.
        ScannerTest.assertTokens(
            BS + "u0041" + BS + "uu0042",
            "IDENTIFIER AB 1:1",
            "END_OF_INPUT end-of-input 1:1"
        );

        // A backslash that is preceded by an odd number of backslashes does not start a unicode escape.
        ScannerTest.assertTokens(
            "\"" + BS + BS + "u0041\"",
            "STRING_LITERAL \"" + BS + BS + "u0041\" 1:1",
            "END_OF_INPUT end-of-input 1:1"
        );
        ScannerTest.assertTokens(
            "\"" + BS + BS + BS + "u0041\"",
            "STRING_LITERAL \"" + BS + BS + "A\" 1:1",
            "END_OF_INPUT end-of-input 1:1"
        );

        // In a string literal; also the closing quote. The columns count the decoded characters.
        ScannerTest.assertTokens(
            "\"" + BS + "u0041\" \"a" + BS + "u0022",
            "STRING_LITERAL \"A\" 1:1",
            "STRING_LITERAL \"a\" 1:5",
            "END_OF_INPUT end-of-input 1:5"
        );

        // Unicode escapes that yield backslashes, which form an escape sequence of a character literal.
        ScannerTest.assertTokens(
            "'" + BS + "u005c" + BS + "u005c'",
            "CHARACTER_LITERAL '" + BS + BS + "' 1:1",
            "END_OF_INPUT end-of-input 1:1"
        );

        // A unicode escape that yields a backslash does not start another unicode escape; a backslash followed by "u"
        // is no valid escape sequence in a string literal.
        ScannerTest.assertTokens(
            "\"" + BS + "u005cu0041\"",
            "File 'f.java', Line 1, Column 1: Invalid escape sequence"
        );
    }

    @Test public void
    testInvalidUnicodeEscapes() throws Exception {

        // The location is that of the preceding token.
        ScannerTest.assertTokens(
            "x " + BS + "u004",
            "IDENTIFIER x 1:1",
            "File 'f.java', Line 1, Column 1: Incomplete escape sequence"
        );
        ScannerTest.assertTokens(
            "x " + BS + "uuu",
            "IDENTIFIER x 1:1",
            "File 'f.java', Line 1, Column 1: Incomplete escape sequence"
        );
        ScannerTest.assertTokens(
            "x " + BS + "u00zz",
            "IDENTIFIER x 1:1",
            "File 'f.java', Line 1, Column 1: Invalid escape sequence \"" + BS + "u00zz\""
        );

        // An invalid unicode escape right after an operator is reported with the location of the operator, and
        // before the operator token is produced.
        ScannerTest.assertTokens(
            "x(" + BS + "u00zz",
            "IDENTIFIER x 1:1",
            "File 'f.java', Line 1, Column 2: Invalid escape sequence \"" + BS + "u00zz\""
        );

        try {
            ScannerTest.scan(new StringReader(BS + "u00zz"));
            Assert.fail();
        } catch (CompileException ce) {
            Assert.assertTrue(ce.getCause() instanceof UnicodeUnescapeException);
        }
    }

    @Test public void
    testTextBlock() throws Exception {
        ScannerTest.assertTokens(
            "\"\"\"\n  abc\"\"\" x",
            "TEXT_BLOCK \"\"\"\n  abc\"\"\" 1:1",
            "IDENTIFIER x 2:10",
            "END_OF_INPUT end-of-input 2:10"
        );
    }

    @Test public void
    testOperators() throws Exception {

        // The longest operator is taken; "..." is not an operator.
        ScannerTest.assertTokens(
            ">>>= -> :: ... >>>>= <<= ++ += /=/",
            "OPERATOR >>>= 1:1",
            "OPERATOR -> 1:6",
            "OPERATOR :: 1:9",
            "OPERATOR . 1:12",
            "OPERATOR . 1:13",
            "OPERATOR . 1:14",
            "OPERATOR >>> 1:16",
            "OPERATOR >= 1:19",
            "OPERATOR <<= 1:22",
            "OPERATOR ++ 1:26",
            "OPERATOR += 1:29",
            "OPERATOR /= 1:32",
            "OPERATOR / 1:34",
            "END_OF_INPUT end-of-input 1:34"
        );

        ScannerTest.assertTokens(
            "a#",
            "IDENTIFIER a 1:1",
            "File 'f.java', Line 1, Column 2: Invalid character input \"#\" (character code 35)"
        );
    }

    @Test public void
    testReferenceEquality() throws Exception {

        // The parser and the compiler compare these token values with "==".
        List<Token> tokens = ScannerTest.scan(new StringReader("class true false null + >>>= ( x"));
        Assert.assertSame("class", tokens.get(0).value);
        Assert.assertSame("true",  tokens.get(1).value);
        Assert.assertSame("false", tokens.get(2).value);
        Assert.assertSame("null",  tokens.get(3).value);
        Assert.assertSame("+",     tokens.get(4).value);
        Assert.assertSame(">>>=",  tokens.get(5).value);
        Assert.assertSame("(",     tokens.get(6).value);
        Assert.assertEquals(TokenType.KEYWORD,         tokens.get(0).type);
        Assert.assertEquals(TokenType.BOOLEAN_LITERAL, tokens.get(1).type);
        Assert.assertEquals(TokenType.NULL_LITERAL,    tokens.get(3).type);
        Assert.assertEquals(TokenType.IDENTIFIER,      tokens.get(7).type);
    }

    @Test public void
    testNumericLiterals() throws Exception {

        // "09" is scanned as the two tokens "0" and "9".
        ScannerTest.assertTokens(
            "09 09L 0x1F 0b10 1_000 .5e1f 0189",
            "INTEGER_LITERAL 0 1:1",
            "INTEGER_LITERAL 9 1:2",
            "INTEGER_LITERAL 0 1:4",
            "INTEGER_LITERAL 9L 1:5",
            "INTEGER_LITERAL 0x1F 1:8",
            "INTEGER_LITERAL 0b10 1:13",
            "INTEGER_LITERAL 1_000 1:18",
            "FLOATING_POINT_LITERAL .5e1f 1:24",
            "File 'f.java', Line 1, Column 30: Digit '8' not allowed in octal literal"
        );
    }

    @Test public void
    testEndOfInput() throws Exception {
        ScannerTest.assertTokens("/* x", "File 'f.java', Line 1, Column 1: Unexpected end-of-input in C-style comment");
        ScannerTest.assertTokens("\"abc", "File 'f.java', Line 1, Column 1: EOF in literal");
        ScannerTest.assertTokens("'a", "File 'f.java', Line 1, Column 1: Closing single quote missing");
        ScannerTest.assertTokens(
            "// c",
            "C_PLUS_PLUS_STYLE_COMMENT // c 1:1",
            "END_OF_INPUT end-of-input 1:1"
        );
    }

    @Test public void
    testReaderReturnsSingleCharacters() throws Exception {

        // A reader that returns at most one character per "read(char[], int, int)" yields the same tokens.
        String source = (
            ""
            + "class A {\r\n"
            + "\tString s = \"" + BS + "u0041" + BS + BS + "u0042\";\n"
            + "\tint i = 0x1F >>> 2; // " + BS + "u0043\r"
            + "\t/** doc */ char c = '" + BS + "u005c" + BS + "u005c';\n"
            + "}\n"
        );
        List<Token> expected = ScannerTest.scan(new StringReader(source));
        List<Token> actual   = ScannerTest.scan(new Reader() {

            private int pos;

            @Override public int
            read(@Nullable char[] cbuf, int off, int len) {
                assert cbuf != null;
                if (len == 0) return 0;
                if (this.pos == source.length()) return -1;
                cbuf[off] = source.charAt(this.pos++);
                return 1;
            }

            @Override public void
            close() {}
        });
        Assert.assertEquals(ScannerTest.toStrings(expected), ScannerTest.toStrings(actual));
    }

    /**
     * Asserts that the scanner produces the <var>expected</var> tokens from the <var>input</var>, and optionally,
     * after the tokens, a {@link CompileException} with the given message.
     */
    private static void
    assertTokens(String input, String... expected) throws IOException {
        List<Token> tokens    = new ArrayList<>();
        String      exception = null;
        try {
            ScannerTest.scan(new StringReader(input), tokens);
        } catch (CompileException ce) {
            exception = ce.getMessage();
        }
        List<String> actual = ScannerTest.toStrings(tokens);
        if (exception != null) actual.add(exception);
        Assert.assertEquals(String.join("\n", expected), String.join("\n", actual));
    }

    private static List<Token>
    scan(Reader reader) throws CompileException, IOException {
        List<Token> result = new ArrayList<>();
        ScannerTest.scan(reader, result);
        return result;
    }

    private static void
    scan(Reader reader, List<Token> result) throws CompileException, IOException {
        Scanner scanner = new Scanner("f.java", reader);
        scanner.setIgnoreWhiteSpace(true);
        for (;;) {
            Token t = scanner.produce();
            result.add(t);
            if (t.type == TokenType.END_OF_INPUT) return;
        }
    }

    private static List<String>
    toStrings(List<Token> tokens) {
        List<String> result = new ArrayList<>();
        for (Token t : tokens) {
            result.add(
                t.type
                + " "
                + t.value
                + " "
                + t.getLocation().getLineNumber()
                + ":"
                + t.getLocation().getColumnNumber()
            );
        }
        return result;
    }
}
