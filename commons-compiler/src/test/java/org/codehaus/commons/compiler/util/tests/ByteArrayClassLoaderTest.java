
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

package org.codehaus.commons.compiler.util.tests;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Permissions;
import java.security.ProtectionDomain;
import java.util.Collections;
import java.util.Map;

import org.codehaus.commons.compiler.util.reflect.ByteArrayClassLoader;
import org.junit.Assert;
import org.junit.Test;

// SUPPRESS CHECKSTYLE Javadoc:9999

public
class ByteArrayClassLoaderTest {

    /**
     * The class that the tests load through a {@link ByteArrayClassLoader}.
     */
    public static
    class Empty {}

    @SuppressWarnings("static-method") @Test public void
    testDefaultProtectionDomain() throws Exception {

        Class<?> c = new ByteArrayClassLoader(ByteArrayClassLoaderTest.emptyClass(), null).loadClass(
            Empty.class.getName()
        );

        Assert.assertNotSame(Empty.class, c);
        Assert.assertSame(ByteArrayClassLoader.class.getProtectionDomain(), c.getProtectionDomain());
    }

    @SuppressWarnings("static-method") @Test public void
    testExplicitProtectionDomain() throws Exception {

        ProtectionDomain protectionDomain = new ProtectionDomain(null, new Permissions());

        Class<?> c = new ByteArrayClassLoader(
            ByteArrayClassLoaderTest.emptyClass(),
            null,
            protectionDomain
        ).loadClass(Empty.class.getName());

        Assert.assertNotSame(Empty.class, c);
        Assert.assertSame(protectionDomain, c.getProtectionDomain());
    }

    /**
     * @return The class file of {@link Empty}, keyed by its class name
     */
    private static Map<String, byte[]>
    emptyClass() throws IOException {

        String name = Empty.class.getName();

        InputStream is = Empty.class.getResourceAsStream(name.substring(name.lastIndexOf('.') + 1) + ".class");
        Assert.assertNotNull(is);
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[]                buffer = new byte[4096];
            for (int n = is.read(buffer); n != -1; n = is.read(buffer)) baos.write(buffer, 0, n);
            return Collections.singletonMap(name, baos.toByteArray());
        } finally {
            is.close();
        }
    }
}
