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
import org.gradle.performance.measure.DataAmount
import org.gradle.performance.measure.Duration

import static PrettyCalculator.toBytes
import static PrettyCalculator.toMillis

@CompileStatic
class BaselineVersion implements VersionResults {
    // To give us < 0.3% odds of a falsely identified regression.
    // https://en.wikipedia.org/wiki/Standard_deviation#Rules_for_normally_distributed_data
    static final BigDecimal NUM_STANDARD_ERRORS_FROM_MEAN = new BigDecimal("3.0")
    // We want to ignore regressions of less than 2% over the baseline.
    static final BigDecimal MINIMUM_REGRESSION_PERCENTAGE = new BigDecimal("0.02")
    final String version
    final MeasuredOperationList results = new MeasuredOperationList()

    BaselineVersion(String version) {
        this.version = version
        results.name = "Gradle $version"
    }

    String getSpeedStatsAgainst(String displayName, MeasuredOperationList current) {
        def sb = new StringBuilder()
        def thisVersionAverage = results.totalTime.average
        def currentVersionAverage = current.totalTime.average
        if (currentVersionAverage && thisVersionAverage) {
            if (currentVersionAverage > thisVersionAverage) {
                sb.append "Speed $displayName: we're slower than $version.\n"
            } else {
                sb.append "Speed $displayName: AWESOME! we're faster than $version :D\n"
            }

            def diff = currentVersionAverage - thisVersionAverage
            def desc = diff > Duration.millis(0) ? "slower" : "faster"
            sb.append("Difference: ${diff.abs().format()} $desc (${toMillis(diff.abs())}), ${PrettyCalculator.percentChange(currentVersionAverage, thisVersionAverage)}%, max regression: ${getMaxExecutionTimeRegression().format()}\n")
            sb.append(current.speedStats)
            sb.append(results.speedStats)
            sb.append("\n")
            sb.toString()
        } else {
            sb.append("Speed measurement is not available (probably due to a build failure)")
        }
    }

    String getMemoryStatsAgainst(String displayName, MeasuredOperationList current) {
        def sb = new StringBuilder()
        def currentVersionAverage = current.totalMemoryUsed.average
        def thisVersionAverage = results.totalMemoryUsed.average
        if (currentVersionAverage && thisVersionAverage) {
            if (currentVersionAverage > thisVersionAverage) {
                sb.append("Memory $displayName: we need more memory than $version.\n")
            } else {
                sb.append("Memory $displayName: AWESOME! we need less memory than $version :D\n")
            }

            def diff = currentVersionAverage - thisVersionAverage
            def desc = diff > DataAmount.bytes(0) ? "more" : "less"
            sb.append("Difference: ${diff.abs().format()} $desc (${toBytes(diff.abs())}), ${PrettyCalculator.percentChange(currentVersionAverage, thisVersionAverage)}%, max regression: ${getMaxMemoryRegression().format()}\n")
            sb.append(current.memoryStats)
            sb.append(results.memoryStats)
            sb.append("\n")
            sb.toString()
        } else {
            sb.append("Memory measurement is not available (probably due to a build failure)")
        }
    }

    boolean fasterThan(MeasuredOperationList current) {
        results.totalTime && current.totalTime.average - results.totalTime.average > getMaxExecutionTimeRegression()
    }

    boolean usesLessMemoryThan(MeasuredOperationList current) {
        results.totalMemoryUsed && current.totalMemoryUsed.average - results.totalMemoryUsed.average > getMaxMemoryRegression()
    }

    Amount<Duration> getMaxExecutionTimeRegression() {
        def allowedPercentageRegression = results.totalTime.average * MINIMUM_REGRESSION_PERCENTAGE
        def allowedStatisticalRegression = results.totalTime.standardErrorOfMean * NUM_STANDARD_ERRORS_FROM_MEAN
        (allowedStatisticalRegression > allowedPercentageRegression) ? allowedStatisticalRegression : allowedPercentageRegression
    }

    Amount<DataAmount> getMaxMemoryRegression() {
        results.totalMemoryUsed.standardErrorOfMean * NUM_STANDARD_ERRORS_FROM_MEAN
    }
}
