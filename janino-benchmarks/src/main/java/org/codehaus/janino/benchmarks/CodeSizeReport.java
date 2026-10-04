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

import java.util.Locale;
import java.util.Map;

/**
 * Prints the total size of the class files that each {@link Workload} generates with the baseline version and with
 * the current build of JANINO. Unlike the time measurements of {@link Compare}, the sizes are deterministic;
 * differences often explain differences of the compile time.
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
        System.out.printf(Locale.ROOT, "%-14s %12s %12s %8s%n", "Workload", "baseline", "current", "ratio");

        for (Workload workload : Workload.values()) {
            CodeSizeReport.print(
                workload.name(),
                CodeSizeReport.size(workload.bytecodes(baseline)),
                CodeSizeReport.size(workload.bytecodes(current))
            );
        }
        System.out.println();
        System.out.println("Sizes in bytes (sum of all generated class files); ratio = current / baseline.");
    }

    private static void
    print(String name, long baselineSize, long currentSize) {
        System.out.printf(
            Locale.ROOT,
            "%-14s %12d %12d %8.3f%n",
            name,
            baselineSize,
            currentSize,
            (double) currentSize / baselineSize
        );
    }

    private static long
    size(Map<String, byte[]> classFiles) {
        long result = 0;
        for (byte[] classFile : classFiles.values()) result += classFile.length;
        return result;
    }
}
