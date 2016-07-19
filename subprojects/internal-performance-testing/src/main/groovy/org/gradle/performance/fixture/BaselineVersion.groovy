/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.performance.fixture

import org.gradle.performance.measure.Amount
import org.gradle.performance.measure.DataAmount
import org.gradle.performance.measure.Duration

import static org.gradle.performance.fixture.PrettyCalculator.toBytes
import static org.gradle.performance.fixture.PrettyCalculator.toMillis

class BaselineVersion implements VersionResults {
    final String version
    final MeasuredOperationList results = new MeasuredOperationList()
    Amount<Duration> maxExecutionTimeRegression = Duration.millis(0)
    Amount<DataAmount> maxMemoryRegression = DataAmount.bytes(0)

    BaselineVersion(String version) {
        this.version = version
        results.name = "Gradle $version"
    }

    String getSpeedStatsAgainst(String displayName, MeasuredOperationList current) {
        def sb = new StringBuilder()
        def thisVersionAverage = results.totalTime.average
        def currentVersionAverage = current.totalTime.average
        if (currentVersionAverage > thisVersionAverage) {
            sb.append "Speed $displayName: we're slower than $version.\n"
        } else {
            sb.append "Speed $displayName: AWESOME! we're faster than $version :D\n"
        }
        def diff = currentVersionAverage - thisVersionAverage
        def desc = diff > Duration.millis(0) ? "slower" : "faster"
        sb.append("Difference: ${diff.abs().format()} $desc (${toMillis(diff.abs())}), ${PrettyCalculator.percentChange(currentVersionAverage, thisVersionAverage)}%, max regression: ${maxExecutionTimeRegression.format()}\n")
        sb.append(current.speedStats)
        sb.append(results.speedStats)
        sb.append("\n")
        sb.toString()
    }

    String getMemoryStatsAgainst(String displayName, MeasuredOperationList current) {
        def sb = new StringBuilder()
        def currentVersionAverage = current.totalMemoryUsed.average
        assert currentVersionAverage != null
        def thisVersionAverage = results.totalMemoryUsed.average
        if (currentVersionAverage > thisVersionAverage) {
            sb.append("Memory $displayName: we need more memory than $version.\n")
        } else {
            sb.append("Memory $displayName: AWESOME! we need less memory than $version :D\n")
        }
        def diff = currentVersionAverage - thisVersionAverage
        def desc = diff > DataAmount.bytes(0) ? "more" : "less"
        sb.append("Difference: ${diff.abs().format()} $desc (${toBytes(diff.abs())}), ${PrettyCalculator.percentChange(currentVersionAverage, thisVersionAverage)}%, max regression: ${maxMemoryRegression.format()}\n")
        sb.append(current.memoryStats)
        sb.append(results.memoryStats)
        sb.append("\n")
        sb.toString()
    }

    boolean usesLessMemoryThan(MeasuredOperationList current) {
        current.totalMemoryUsed.average - results.totalMemoryUsed.average > maxMemoryRegression
    }

    boolean fasterThan(MeasuredOperationList current) {
        current.totalTime.average - results.totalTime.average > maxExecutionTimeRegression
    }
}
