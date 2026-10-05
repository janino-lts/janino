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

import java.io.File;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import org.codehaus.commons.compiler.lang.ClassLoaders;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for {@link ClassLoaders#getSubresourcesOf(java.net.URL, String, boolean, boolean)} with "file:" URLs.
 */
public
class ClassLoadersTest {

    /** Below "target", because {@link ClassLoaders} does not decode the paths of "file:" URLs (e.g. "%20"). */
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder(new File("target"));

    @Test public void
    testGetSubresourcesOfDirectory() throws Exception {
        File root = this.newTree();

        Assert.assertEquals(
            new TreeSet<String>(Arrays.asList("", "a.txt", "sub/", "sub/b.txt")),
            ClassLoadersTest.getSubresourceNames(root)
        );
    }

    /**
     * A directory that cannot be listed must be treated like an empty directory; see <a
     * href="https://github.com/janino-lts/janino/issues/20">issue #20</a>. Skipped where the directory cannot be
     * made unreadable (e.g. on Windows, or when running as "root").
     */
    @Test public void
    testGetSubresourcesOfDirectoryWithUnreadableSubdirectory() throws Exception {
        File root = this.newTree();
        File sub  = new File(root, "sub");
        try {
            Assume.assumeTrue("Cannot make a directory unreadable", sub.setReadable(false) && sub.listFiles() == null);

            Assert.assertEquals(
                new TreeSet<String>(Arrays.asList("", "a.txt", "sub/")),
                ClassLoadersTest.getSubresourceNames(root)
            );
        } finally {
            sub.setReadable(true);
        }
    }

    /** @return A directory with the file "a.txt" and the subdirectory "sub", which contains the file "b.txt" */
    private File
    newTree() throws Exception {
        File root = this.temporaryFolder.newFolder("root");
        File sub  = new File(root, "sub");
        Assert.assertTrue(sub.mkdir());
        Assert.assertTrue(new File(root, "a.txt").createNewFile());
        Assert.assertTrue(new File(sub, "b.txt").createNewFile());
        return root;
    }

    private static Set<String>
    getSubresourceNames(File directory) throws Exception {
        return new TreeSet<String>(ClassLoaders.getSubresourcesOf(directory.toURI().toURL(), "", true, true).keySet());
    }
}
