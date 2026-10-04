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

package org.codehaus.janino.benchmarks;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * Prints the total size of the class files that each {@link Workload} generates with the baseline version and with
 * the current build of JANINO, and whether the class files are identical. Unlike the time measurements of {@link
 * Compare}, the sizes are deterministic; differences often explain differences of the compile time. A change that
 * should only make the compiler faster must not change the class files.
 * <p>
 *   Usage:
 * </p>
 * <pre>
 *   java -cp janino-benchmarks/target/benchmarks.jar org.codehaus.janino.benchmarks.CodeSizeReport
 * </pre>
 */
public final
class CodeSizeReport {

    private CodeSizeReport() {}

    /** See the class description. */
    public static void
    main(String[] args) throws Exception {
        JaninoVersion baseline = JaninoVersion.load(JaninoVersion.BASELINE);
        JaninoVersion current  = JaninoVersion.load(JaninoVersion.CURRENT);

        System.out.println("Baseline: " + baseline);
        System.out.println("Current:  " + current);
        System.out.println();
        System.out.printf(
            Locale.ROOT,
            "%-14s %12s %12s %8s  %s%n",
            "Workload",
            "baseline",
            "current",
            "ratio",
            "identical"
        );

        for (Workload workload : Workload.values()) {
            Map<String, byte[]> baselineClassFiles = workload.bytecodes(baseline);
            Map<String, byte[]> currentClassFiles  = workload.bytecodes(current);

            // Compiling the same code twice must yield the same class files; otherwise, a comparison is meaningless.
            String identical = (
                !CodeSizeReport.identical(currentClassFiles, workload.bytecodes(current))
                ? "(not deterministic)"
                : CodeSizeReport.identical(baselineClassFiles, currentClassFiles)
                ? "yes"
                : "no"
            );

            long baselineSize = CodeSizeReport.size(baselineClassFiles);
            long currentSize  = CodeSizeReport.size(currentClassFiles);
            System.out.printf(
                Locale.ROOT,
                "%-14s %12d %12d %8.3f  %s%n",
                workload.name(),
                baselineSize,
                currentSize,
                (double) currentSize / baselineSize,
                identical
            );
        }
        System.out.println();
        System.out.println("Sizes in bytes (sum of all generated class files); ratio = current / baseline.");
        System.out.println("identical: Whether both versions generate the same class files, byte for byte.");
    }

    /** @return Whether the two maps contain the same class names and the same bytes for each class name */
    private static boolean
    identical(Map<String, byte[]> classFiles1, Map<String, byte[]> classFiles2) {
        if (!classFiles1.keySet().equals(classFiles2.keySet())) return false;
        for (Map.Entry<String, byte[]> e : classFiles1.entrySet()) {
            if (!Arrays.equals(e.getValue(), classFiles2.get(e.getKey()))) return false;
        }
        return true;
    }

    private static long
    size(Map<String, byte[]> classFiles) {
        long result = 0;
        for (byte[] classFile : classFiles.values()) result += classFile.length;
        return result;
    }
}
