/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.performance.measure.Amount
import org.gradle.performance.measure.DataSeries
import org.gradle.performance.measure.Duration

import java.util.function.BiPredicate

import static PrettyCalculator.toMillis

/**
 * Allows comparing one Gradle version's results against another, using the Mann–Whitney U test with a minimum confidence of 999%.
 *
 * We prefer the Mann-Whitney U test over Student's T test, because performance data often has a non-normal distribution.
 * Many scenarios have 2 maxima, one for a typical build and another for builds with a major GC pause.
 *
 * https://en.wikipedia.org/wiki/Mann%E2%80%93Whitney_U_test
 */
@CompileStatic
class BaselineVersion implements VersionResults {
    private static final double MINIMUM_CONFIDENCE = 0.999
    // 5 percent difference is something we can measure reliably
    private static final double MINIMUM_RELATIVE_MEDIAN_DIFFERENCE = 0.05
    // 20 percent difference is something where we should always fail
    private static final double HIGH_RELATIVE_MEDIAN_DIFFERENCE = 0.2
    private static final Amount<Duration> MINIMUM_DIFFERENCE_WE_CAN_MEASURE = Duration.millis(10)

    final String version
    final MeasuredOperationList results = new MeasuredOperationList()

    BaselineVersion(String version) {
        this.version = version
        results.name = "Gradle $version"
    }

    String getSpeedStatsAgainst(String displayName, MeasuredOperationList current) {
        def sb = new StringBuilder()
        def thisVersionMean = results.totalTime.median
        def currentVersionMean = current.totalTime.median
        if (currentVersionMean && thisVersionMean) {
            if (significantlyFasterThan(current)) {
                sb.append "Speed $displayName: we're slower than $version"
            } else if (significantlySlowerThan(current)) {
                sb.append "Speed $displayName: AWESOME! we're faster than $version"
            } else {
                sb.append "Speed $displayName: Results were inconclusive"
            }
            String confidencePercent = DataSeries.confidenceInDifference(results.totalTime, current.totalTime) * 100 as int
            sb.append(" with " + confidencePercent + "% confidence.\n")

            def diff = currentVersionMean - thisVersionMean
            def desc = diff > Duration.millis(0) ? "slower" : "faster"
            sb.append("Difference: ${diff.abs().format()} $desc (${toMillis(diff.abs())}), ${PrettyCalculator.percentChange(currentVersionMean, thisVersionMean)}%\n")
            sb.append(current.speedStats)
            sb.append(results.speedStats)
            sb.append("\n")
            sb.toString()
        } else {
            sb.append("Speed measurement is not available (probably due to a build failure)")
        }
    }

    boolean significantlyFasterThan(MeasuredOperationList other, double minConfidence = MINIMUM_CONFIDENCE) {
        return significantlyFasterThanBy(other) { myTime, otherTime ->
            differenceIsSignificant(myTime, otherTime, minConfidence)
        }
    }

    boolean significantlyFasterByMedianThan(MeasuredOperationList other, double minRelativeMedianDifference = MINIMUM_RELATIVE_MEDIAN_DIFFERENCE) {
        return significantlyFasterThanBy(other) { myTime, otherTime ->
            differenceInMedianIsSignificant(myTime, otherTime, minRelativeMedianDifference)
        }
    }

    private boolean significantlyFasterThanBy(
        MeasuredOperationList other,
        BiPredicate<DataSeries<Duration>, DataSeries<Duration>> differenceSignificantCheck
    ) {
        def myTime = results.totalTime
        def otherTime = other.totalTime
        return myTime && myTime.median < otherTime.median && differenceSignificantCheck.test(myTime, otherTime)
    }

    boolean significantlySlowerThan(MeasuredOperationList other, double minConfidence = MINIMUM_CONFIDENCE) {
        def myTime = results.totalTime
        def otherTime = other.totalTime
        myTime && myTime.median > otherTime.median && differenceIsSignificant(myTime, otherTime, minConfidence)
    }

    private static boolean differenceIsSignificant(DataSeries<Duration> myTime, DataSeries<Duration> otherTime, double minConfidence) {
        return (myTime.median - otherTime.median).abs() > MINIMUM_DIFFERENCE_WE_CAN_MEASURE &&
            (relativeDifferenceInMedianIsVeryHigh(myTime, otherTime) || DataSeries.confidenceInDifference(myTime, otherTime) > minConfidence)
    }

    private static boolean differenceInMedianIsSignificant(DataSeries<Duration> myTime, DataSeries<Duration> otherTime, double minRelativeMedianDifference) {
        def minimumMedian = [myTime.median, otherTime.median].min()
        return ((myTime.median - otherTime.median) / minimumMedian).abs() > minRelativeMedianDifference
    }

    private static boolean relativeDifferenceInMedianIsVeryHigh(DataSeries<Duration> myTime, DataSeries<Duration> otherTime) {
        return differenceInMedianIsSignificant(myTime, otherTime, HIGH_RELATIVE_MEDIAN_DIFFERENCE)
    }
}
