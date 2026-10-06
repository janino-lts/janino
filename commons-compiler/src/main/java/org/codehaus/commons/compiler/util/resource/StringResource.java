
/*
 * Janino - An embedded Java[TM] compiler
 *
 * Copyright (c) 2001-2018 Arno Unkrig. All rights reserved.
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

package org.codehaus.commons.compiler.util.resource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * A resource who's content is a {@link String}.
 */
public
class StringResource implements Resource {

    private final String fileName;

    /**
     * Encodes the <var>text</var> with the platform default charset.
     *
     * @see #StringResource(String, String, Charset)
     */
    public
    StringResource(String fileName, String text) { this(fileName, text, Charset.defaultCharset()); }

    /**
     * Encodes the <var>text</var> with the given <var>charset</var>, which should be the source charset of the compiler
     * that reads the resource; see {@link org.codehaus.commons.compiler.ICompiler#setSourceCharset(Charset)}.
     */
    public
    StringResource(String fileName, String text, Charset charset) {
        this.fileName = fileName;
        this.data     = text.getBytes(charset);
    }

    // Implement "Resource".
    @Override public final String      getFileName()  { return this.fileName;                       }
    @Override public final InputStream open()         { return new ByteArrayInputStream(this.data); }
    @Override public final long        lastModified() { return 0L;                                  }

    @Override public final String toString() { return this.getFileName(); }

    private final byte[] data;
}
