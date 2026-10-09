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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codehaus.commons.nullanalysis.Nullable;

import util.TestUtil;

/**
 * Reads the record files of {@link InvalidCodeTest} and {@link LanguageSupportTest}.
 * <p>
 *   A record file consists of comment lines, followed by cases. Each case starts with a line "{@code ===
 *   <var>id</var>}", followed by header lines "{@code <var>key</var>: <var>value</var>}" and the source code. The
 *   keys that both tests have in common:
 * </p>
 * <dl>
 *   <dt>{@code janino}</dt>
 *   <dd>The behavior of JANINO in the compatibility mode (the default)</dd>
 *   <dt>{@code compliant}</dt>
 *   <dd>
 *     The behavior of JANINO in the compliance mode ({@code JaninoOption.JAVAC_COMPLIANCE}), in the format of the
 *     {@code janino} line (optional, default: the {@code janino} line)
 *   </dd>
 *   <dt>{@code id}</dt>
 *   <dd>
 *     The ID of the deviation from JAVAC in {@code JAVAC_DIFFERENCES.md} (e.g. "S-01"), or the number of the issue
 *     (e.g. "#97"); required iff {@code compliant} is present
 *   </dd>
 *   <dt>{@code minJava}</dt>
 *   <dd>The minimum JVM version for the JDK-based compiler (optional, default 8)</dd>
 * </dl>
 * <p>
 *   A header line with an unknown key is an error (otherwise, a misspelled key would silently become source code).
 * </p>
 */
final
class Records {

    private Records() {}

    /** The key of the behavior in the compatibility mode. */
    static final String JANINO = "janino";

    /** The key of the behavior in the compliance mode. */
    static final String COMPLIANT = "compliant";

    /** The key of the ID of the deviation. */
    static final String ID = "id";

    private static final String MIN_JAVA = "minJava";

    private static final Pattern HEADER_LINE = Pattern.compile("([a-zA-Z][a-zA-Z0-9]*): ?(.*)");

    /**
     * Reads all cases from the given files.
     *
     * @param requiredKeys The keys that every case must have (in addition to {@value #JANINO})
     * @param optionalKeys The keys that a case may have (in addition to the common keys)
     */
    static List<Case>
    read(String dir, String[] fileNames, String[] requiredKeys, String[] optionalKeys) throws IOException {

        Set<String> required = new HashSet<>(Arrays.asList(requiredKeys));
        required.add(Records.JANINO);

        Set<String> known = new HashSet<>(required);
        known.addAll(Arrays.asList(optionalKeys));
        known.addAll(Arrays.asList(Records.COMPLIANT, Records.ID, Records.MIN_JAVA));

        List<Case> result = new ArrayList<>();
        for (String fileName : fileNames) {
            result.addAll(Records.read(new File(dir, fileName), required, known));
        }
        return result;
    }

    private static List<Case>
    read(File file, Set<String> required, Set<String> known) throws IOException {

        List<Case> result = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(
            new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)
        )) {
            String              id     = null;
            Map<String, String> keys   = new LinkedHashMap<>();
            StringBuilder       source = new StringBuilder();

            for (String line = br.readLine();; line = br.readLine()) {

                if (line == null || line.startsWith("=== ")) {
                    if (id != null) result.add(Records.newCase(file, id, keys, source.toString(), required));
                    if (line == null) break;

                    id     = line.substring(4).trim();
                    keys   = new LinkedHashMap<>();
                    source.setLength(0);
                    continue;
                }

                // Lines before the first case are comments.
                if (id == null) continue;

                Matcher m;
                if (source.length() == 0 && (m = Records.HEADER_LINE.matcher(line)).matches()) {
                    String key = m.group(1);
                    if (!known.contains(key)) {
                        throw new IOException(file + ": Case \"" + id + "\": Unknown key \"" + key + "\"");
                    }
                    if (keys.put(key, m.group(2)) != null) {
                        throw new IOException(file + ": Case \"" + id + "\": Duplicate key \"" + key + "\"");
                    }
                } else
                {
                    source.append(line).append('\n');
                }
            }
        }

        return result;
    }

    private static Case
    newCase(File file, String id, Map<String, String> keys, String source, Set<String> required)
    throws IOException {

        for (String key : required) {
            if (!keys.containsKey(key)) throw new IOException(file + ": Case \"" + id + "\" lacks \"" + key + "\"");
        }
        if (keys.containsKey(Records.COMPLIANT) && !keys.containsKey(Records.ID)) {
            throw new IOException(file + ": Case \"" + id + "\" has \"compliant\", but lacks \"id\"");
        }

        return new Case(file.getName(), id, keys, source);
    }

    /**
     * A test case.
     */
    public static final
    class Case {

        final String              file, id, source;
        final int                 minJava;
        private final Map<String, String> keys;

        Case(String file, String id, Map<String, String> keys, String source) {
            this.file    = file;
            this.id      = id;
            this.keys    = keys;
            this.source  = source;

            String minJava = keys.get(Records.MIN_JAVA);
            this.minJava = minJava == null ? 8 : Integer.parseInt(minJava.trim());
        }

        /**
         * @return The value of the given key, or {@code null} iff the case does not have the key
         */
        @Nullable String
        get(String key) { return this.keys.get(key); }

        /**
         * @return The value of the given key, or <var>defaultValue</var> iff the case does not have the key
         */
        String
        get(String key, String defaultValue) {
            String result = this.keys.get(key);
            return result == null ? defaultValue : result;
        }

        /**
         * @return The expected behavior of JANINO in the given mode ({@link TestUtil#COMPAT} or {@link
         *         TestUtil#COMPLIANT})
         */
        String
        expected(String mode) {
            String janino = this.get(Records.JANINO);
            assert janino != null;
            return TestUtil.COMPLIANT.equals(mode) ? this.get(Records.COMPLIANT, janino) : janino;
        }

        @Override public String
        toString() { return this.file + ": " + this.id; }
    }
}
