/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.performance.results

import groovy.transform.CompileStatic
import org.gradle.performance.measure.MeasuredOperation

/**
 * Assertions on the speedup ratio {@code median(baseline) / median(current)}.
 *
 * <p>Two checks, each statistically robust to per-sample noise:
 * <ul>
 *   <li>{@link #assertSpeedupAtLeast} — the regression guard.
 *   <li>{@link #assertSpeedupAtMost}  — the lock-in guard, fired when an
 *       improvement pushes the speedup past the expected ceiling so we ratchet
 *       the bound in the same PR rather than letting the win silently erode.
 * </ul>
 *
 * <p>Both checks share one primitive: scale {@code current} by the threshold
 * {@code factor} and ask {@link BaselineVersion#significantlyFasterByMedianThan}
 * which side of the threshold the data falls on. Median is equivariant under
 * positive scalar multiplication ({@code median(k·X) = k·median(X)}), so
 * {@code median(baseline) ⋛ factor x median(current)} reduces to a comparison
 * between {@code baseline} and the scaled series, no new noise model required.
 *
 * <p>Assumes per-sample noise is approximately multiplicative (variance scales
 * with the mean). This holds for build-time work after warm-ups absorb fixed
 * costs; if a scenario violates it, the assertion may flake near the boundary.
 */
@CompileStatic
class SpeedupAssertions {

    /**
     * Relative-median tolerance passed to {@link BaselineVersion#significantlyFasterByMedianThan}.
     * Matches {@code MINIMUM_RELATIVE_MEDIAN_DIFFERENCE} in {@link BaselineVersion}: 5% is
     * the smallest relative difference the existing infrastructure considers measurable.
     * Below this, observed differences are treated as noise.
     */
    private static final double NOISE_FLOOR = 0.05

    /**
     * Smallest speedup factor the check can meaningfully resolve. A threshold below this
     * sits inside (or at the edge of) the noise band around 1.0x — meaning "no speedup at
     * all" and "the threshold" become statistically indistinguishable, and the assertion
     * degenerates into a coin flip on noisy runs. Set to {@code 1 + 2 * NOISE_FLOOR} so
     * there is clear daylight between the threshold and "no speedup".
     */
    private static final double MIN_RESOLVABLE_FACTOR = 1.0 + 2 * NOISE_FLOOR

    /**
     * Asserts that {@code current} is at least {@code minSpeedup}x as fast as {@code baseline}
     * (i.e. the observed speedup is not significantly below {@code minSpeedup}). Treats an
     * inconclusive result as a failure: the expected speedups checked here are large enough
     * that landing inside the noise band around the floor is itself a signal of drift.
     */
    static void assertSpeedupAtLeast(
        MeasuredOperationList baseline,
        MeasuredOperationList current,
        double minSpeedup,
        String location
    ) {
        requireResolvableFactor(minSpeedup, "minSpeedup", location)
        def speedup = requireSpeedup(baseline, current, location)
        if (compareSpeedupToFactor(baseline, current, minSpeedup) <= 0) {
            throw new AssertionError("""[FAIL] '${current.name}' is not ${formatThreshold(minSpeedup)}x faster than '${baseline.name}' (with at least ${(NOISE_FLOOR * 100) as int}% margin)
  observed:  ${formatSpeedup(speedup)}x faster ${formatMedians(baseline, current)}
  location:  ${location}
  → Either fix the regression in '${current.name}', or — only if the slowdown is intentional and accepted — lower the floor at this location.""" as Object)
        }
    }

    /**
     * Asserts that {@code current} is no more than {@code maxSpeedup}x as fast as {@code baseline}
     * (i.e. the observed speedup is not significantly above {@code maxSpeedup}).
     *
     * <p>Used to lock in expected speedups: an unexpected improvement past this ceiling fires
     * so the bound gets ratcheted alongside the improvement, instead of silently eroding later.
     */
    static void assertSpeedupAtMost(
        MeasuredOperationList baseline,
        MeasuredOperationList current,
        double maxSpeedup,
        String location
    ) {
        requireResolvableFactor(maxSpeedup, "maxSpeedup", location)
        def speedup = requireSpeedup(baseline, current, location)
        if (compareSpeedupToFactor(baseline, current, maxSpeedup) > 0) {
            throw new AssertionError("""[FAIL] '${current.name}' is more than ${formatThreshold(maxSpeedup)}x faster than '${baseline.name}' (lock-in ceiling exceeded)
  observed:  ${formatSpeedup(speedup)}x faster ${formatMedians(baseline, current)}
  location:  ${location}
  → Looks like an improvement landed. Confirm it is real (re-run, inspect a build scan), then raise both the floor and ceiling at this location so the new range reflects the new normal.""" as Object)
        }
    }

    /**
     * Compares the observed speedup ({@code median(baseline) / median(current)}) against
     * a threshold {@code factor}, with statistical significance.
     *
     * <p>Returns {@code +1} when the speedup is significantly greater than {@code factor}
     * (by at least {@link #NOISE_FLOOR} relative-median margin), {@code -1} when it is
     * significantly less, and {@code 0} when the difference falls within the noise floor.
     */
    private static int compareSpeedupToFactor(
        MeasuredOperationList baseline,
        MeasuredOperationList current,
        double factor
    ) {
        def scaled = scale(current, factor)
        def baselineBV = asBaselineVersion(baseline)
        def scaledBV = asBaselineVersion(scaled)
        // BaselineVersion.significantlyFasterByMedianThan(other) is true iff
        // this.median is significantly LESS than other.median (faster = smaller time).
        if (scaledBV.significantlyFasterByMedianThan(baseline, NOISE_FLOOR)) {
            // factor · current.median < baseline.median ⇒ speedup > factor
            return +1
        }
        if (baselineBV.significantlyFasterByMedianThan(scaled, NOISE_FLOOR)) {
            // baseline.median < factor · current.median ⇒ speedup < factor
            return -1
        }
        return 0
    }

    private static void requireResolvableFactor(double factor, String paramName, String location) {
        if (factor < MIN_RESOLVABLE_FACTOR) {
            throw new IllegalArgumentException(
                "Speedup factor ${paramName}=${factor} at ${location} is below the minimum " +
                "resolvable bound of ${MIN_RESOLVABLE_FACTOR} (= 1 + 2 * NOISE_FLOOR). " +
                "Below this, the threshold is statistically indistinguishable from 'no speedup'. " +
                "Either pick a larger factor, or tighten NOISE_FLOOR (currently ${NOISE_FLOOR}) " +
                "if the scenario is empirically less noisy.")
        }
    }

    private static BigDecimal requireSpeedup(
        MeasuredOperationList baseline,
        MeasuredOperationList current,
        String location
    ) {
        def baselineMedian = baseline.totalTime.median
        def currentMedian = current.totalTime.median
        if (baselineMedian == null || currentMedian == null) {
            throw new AssertionError(
                "Cannot assert speedup at ${location}: missing measurements " +
                "(likely a build failure). baseline=${baseline.name}, current=${current.name}." as Object)
        }
        return baselineMedian.div(currentMedian)
    }

    private static MeasuredOperationList scale(MeasuredOperationList src, double factor) {
        def k = BigDecimal.valueOf(factor)
        def scaled = new MeasuredOperationList(name: "${src.name} x ${factor}")
        for (MeasuredOperation op : src) {
            scaled.add(new MeasuredOperation(totalTime: op.totalTime.multiply(k)))
        }
        return scaled
    }

    private static BaselineVersion asBaselineVersion(MeasuredOperationList list) {
        def bv = new BaselineVersion(list.name ?: "anonymous")
        bv.results.name = list.name
        bv.results.addAll(list)
        return bv
    }

    private static String formatSpeedup(BigDecimal speedup) {
        String.format('%.2f', speedup)
    }

    private static String formatThreshold(double factor) {
        String.format('%.2f', factor)
    }

    private static String formatMedians(MeasuredOperationList baseline, MeasuredOperationList current) {
        return "(${baseline.totalTime.median.format()} → ${current.totalTime.median.format()} median, n=${current.size()})"
    }
}
