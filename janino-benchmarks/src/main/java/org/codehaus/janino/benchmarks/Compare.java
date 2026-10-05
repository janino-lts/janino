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

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.sun.management.ThreadMXBean;

/**
 * Compares the compile time of the current build of JANINO with the baseline version, by measuring both
 * <em>interleaved</em> in the same JVM.
 * <p>
 *   For each {@link Workload}, both versions are first warmed up, and then measured alternately in rounds of a minimum
 *   duration, in the order A B B A A B B A ... (A = baseline, B = current). Each pair of rounds yields a ratio B / A;
 *   the median and the 10th and 90th percentiles of these ratios are reported. Because the two versions are measured
 *   within milliseconds of each other, and the order alternates, a slow drift of the machine's speed (e.g. caused by
 *   its temperature) does not distort the ratios, unlike measurements of one version after the other.
 * </p>
 * <p>
 *   In addition, the memory that one operation allocates is reported (if the JVM supports that measurement). It
 *   varies much less than the times; with "-Xint", which disables the escape analysis of the JIT compiler, it is
 *   almost deterministic, and shows even small differences reliably.
 * </p>
 * <p>
 *   Usage:
 * </p>
 * <pre>
 *   java [-Xint] -jar janino-benchmarks/target/benchmarks.jar [ <var>option</var> ... ] [ <var>workload</var> ... ]
 * </pre>
 * <p>
 *   Run with "-h" for the options. With "-Xint" (interpreter only), the times are much longer, but they hardly depend
 *   on the decisions of the JIT compiler; the ratios then measure the work that the compiler does.
 * </p>
 */
public final
class Compare {

    private Compare() {}

    /** Keeps the results of the operations alive, so that the JIT compiler cannot eliminate them. */
    private static volatile Object sink;

    /** See the class description. */
    public static void
    main(String[] args) throws Exception {
        int            rounds       = 20;
        int            warmupRounds = 10;
        long           roundMillis  = 500;
        boolean        aa           = false;
        List<Workload> workloads    = new ArrayList<Workload>();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("-rounds".equals(arg)) {
                rounds = Integer.parseInt(args[++i]);
            } else
            if ("-warmup".equals(arg)) {
                warmupRounds = Integer.parseInt(args[++i]);
            } else
            if ("-millis".equals(arg)) {
                roundMillis = Long.parseLong(args[++i]);
            } else
            if ("-aa".equals(arg)) {
                aa = true;
            } else
            if ("-h".equals(arg) || "-help".equals(arg)) {
                Compare.printUsage();
                return;
            } else
            {
                workloads.add(Workload.valueOf(arg));
            }
        }
        if (rounds < 2) throw new IllegalArgumentException("At least 2 rounds are required");
        if (workloads.isEmpty()) workloads.addAll(Arrays.asList(Workload.values()));

        // With "-aa", the current build is compared with itself, loaded through a second class loader. The ratios
        // should then be close to 1.0; their spread shows the precision of the measurement on this machine.
        JaninoVersion a = JaninoVersion.load(aa ? JaninoVersion.CURRENT : JaninoVersion.BASELINE);
        JaninoVersion b = JaninoVersion.load(JaninoVersion.CURRENT);

        System.out.println("A: " + a);
        System.out.println("B: " + b);
        System.out.println(
            "JVM: "
            + System.getProperty("java.vm.name")
            + " "
            + System.getProperty("java.version")
            + (aa ? "; A/A comparison (current with itself)" : "")
        );
        System.out.printf(
            Locale.ROOT,
            "%d warm-up and %d measured rounds of at least %d ms per version and workload%n%n",
            warmupRounds,
            rounds,
            roundMillis
        );
        System.out.printf(
            Locale.ROOT,
            "%-14s %14s %14s %9s %9s %9s %12s %12s %9s%n",
            "Workload",
            "A [us/op]",
            "B [us/op]",
            "B/A",
            "p10",
            "p90",
            "A [KB/op]",
            "B [KB/op]",
            "alloc B/A"
        );

        for (Workload workload : workloads) {
            workload.check(a);
            workload.check(b);

            for (int i = 0; i < warmupRounds; i++) {
                Compare.round(workload, a, roundMillis);
                Compare.round(workload, b, roundMillis);
            }

            double[] ratios = new double[rounds];
            double   sumA   = 0, sumB = 0, allocA = 0, allocB = 0;
            for (int r = 0; r < rounds; r++) {
                Round ra, rb;
                if (r % 2 == 0) {
                    ra = Compare.round(workload, a, roundMillis);
                    rb = Compare.round(workload, b, roundMillis);
                } else {
                    rb = Compare.round(workload, b, roundMillis);
                    ra = Compare.round(workload, a, roundMillis);
                }
                ratios[r] = rb.microsPerOperation / ra.microsPerOperation;
                sumA   += ra.microsPerOperation;
                sumB   += rb.microsPerOperation;
                allocA += ra.bytesPerOperation;
                allocB += rb.bytesPerOperation;
            }
            Arrays.sort(ratios);

            System.out.printf(
                Locale.ROOT,
                "%-14s %14.1f %14.1f %9.3f %9.3f %9.3f %12s %12s %9s%n",
                workload.name(),
                sumA / rounds,
                sumB / rounds,
                Compare.percentile(ratios, 50),
                Compare.percentile(ratios, 10),
                Compare.percentile(ratios, 90),
                Compare.format("%.1f", allocA / rounds / 1024),
                Compare.format("%.1f", allocB / rounds / 1024),
                Compare.format("%.3f", allocB / allocA)
            );
        }

        System.out.println();
        System.out.println(
            "B/A is the median of the ratios of the rounds (less than 1.0 means that B is faster); p10 and p90 are the "
            + "10th and 90th percentiles."
        );
        System.out.println(
            "KB/op is the memory that one operation allocates (on average), and alloc B/A the ratio of these values; "
            + "with -Xint, they are almost deterministic."
        );
    }

    /** The result of a {@link Compare#round(Workload, JaninoVersion, long)}. */
    private static final
    class Round {

        /** The average duration of one operation, in microseconds. */
        final double microsPerOperation;

        /** The average number of bytes that one operation allocates, or {@link Double#NaN} if it is not available. */
        final double bytesPerOperation;

        Round(double microsPerOperation, double bytesPerOperation) {
            this.microsPerOperation = microsPerOperation;
            this.bytesPerOperation  = bytesPerOperation;
        }
    }

    /** Executes the <var>workload</var> repeatedly, for at least <var>millis</var> milliseconds, but at least once. */
    private static Round
    round(Workload workload, JaninoVersion janino, long millis) throws Exception {
        long allocatedBefore = Compare.allocatedBytes();
        long start           = System.nanoTime();
        long end             = start + millis * 1000000L;
        int  n               = 0;
        long now;
        do {
            Compare.sink = workload.run(janino);
            n++;
            now = System.nanoTime();
        } while (now < end);
        long allocatedAfter = Compare.allocatedBytes();

        return new Round(
            (now - start) / 1e3 / n,
            allocatedBefore == -1 || allocatedAfter == -1 ? Double.NaN : (double) (allocatedAfter - allocatedBefore) / n
        );
    }

    /**
     * @return The number of bytes that the current thread has allocated so far, or -1 if the JVM does not support
     *         that measurement
     */
    private static long
    allocatedBytes() {
        ThreadMXBean threadMxBean = Compare.THREAD_MX_BEAN;
        return threadMxBean == null ? -1 : threadMxBean.getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    /** The HotSpot extension of the {@code ThreadMXBean}, or {@code null} if it is not available. */
    private static final ThreadMXBean THREAD_MX_BEAN;
    static {
        ThreadMXBean threadMxBean = null;
        try {
            java.lang.management.ThreadMXBean tmb = ManagementFactory.getThreadMXBean();
            if (tmb instanceof ThreadMXBean && ((ThreadMXBean) tmb).isThreadAllocatedMemorySupported()) {
                threadMxBean = (ThreadMXBean) tmb;
                threadMxBean.setThreadAllocatedMemoryEnabled(true);
            }
        } catch (LinkageError le) {
            // E.g. "com.sun.management" is not available on this JVM; the allocations are then reported as "n/a".
        }
        THREAD_MX_BEAN = threadMxBean;
    }

    /** @return The formatted <var>value</var>, or "n/a" if it is {@link Double#NaN} */
    private static String
    format(String format, double value) {
        return Double.isNaN(value) ? "n/a" : String.format(Locale.ROOT, format, value);
    }

    /** @param sortedValues Must be sorted in ascending order */
    private static double
    percentile(double[] sortedValues, int percent) {
        return sortedValues[(int) Math.round((sortedValues.length - 1) * percent / 100.0)];
    }

    private static void
    printUsage() {
        System.out.println("Usage:");
        System.out.println("  java [-Xint] -jar benchmarks.jar [ option ... ] [ workload ... ]");
        System.out.println();
        System.out.println("Compares the compile time of the current build of JANINO (B) with the baseline version");
        System.out.println("(A), measuring both interleaved in the same JVM.");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  -rounds n   Measured rounds per version and workload (default: 20)");
        System.out.println("  -warmup n   Warm-up rounds per version and workload (default: 10)");
        System.out.println("  -millis n   Minimum duration of a round in milliseconds (default: 500)");
        System.out.println("  -aa         Compare the current build with itself (A = B), to see how precise the");
        System.out.println("              measurement is on this machine");
        System.out.println("  -h          Print this text");
        System.out.println();
        System.out.println("Workloads (default: all):");
        for (Workload workload : Workload.values()) System.out.println("  " + workload.name());
        System.out.println();
        System.out.println("-Xint (interpreter only) makes the times hardly depend on the JIT compiler; the ratios");
        System.out.println("then measure the work that the compiler does.");
        System.out.println();
        System.out.println("-Djanino.benchmarks.baseline.dir=dir loads A from the JAR files in dir instead, e.g. from");
        System.out.println("a copy of \"janino-benchmarks/target/janino-current\" that was made before a change.");
    }
}
