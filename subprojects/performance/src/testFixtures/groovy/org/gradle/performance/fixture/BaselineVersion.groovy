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

import static org.gradle.performance.fixture.PrettyCalculator.*

/**
 * by Szczepan Faber, created at: 11/20/12
 */
class BaselineVersion {

    String version
    Amount<Duration> maxExecutionTimeRegression = Duration.millis(0)
    Amount<DataAmount> maxMemoryRegression = DataAmount.bytes(0)

    MeasuredOperationList results = new MeasuredOperationList()

    void clearResults() {
        results.clear()
    }

    static BaselineVersion baseline(String version) {
        new BaselineVersion(version: version, results: new MeasuredOperationList(name: "Gradle $version"))
    }

    String getSpeedStatsAgainst(String displayName, MeasuredOperationList current) {
        def sb = new StringBuilder()
        if (current.avgTime() > results.avgTime()) {
            sb.append "Speed $displayName: we're slower than $version.\n"
        } else {
            sb.append "Speed $displayName: AWESOME! we're faster than $version :D\n"
        }
        def diff = current.avgTime() - results.avgTime()
        def desc = diff > Duration.millis(0) ? "slower" : "faster"
        sb.append("Difference: ${prettyTime(diff.abs())} $desc (${toMillis(diff.abs())}), ${PrettyCalculator.percentChange(current.avgTime(), results.avgTime())}%, max regression: ${prettyTime(maxExecutionTimeRegression)}\n")
        sb.append(current.speedStats)
        sb.append(results.speedStats)
        sb.append("\n")
        sb.toString()
    }

    String getMemoryStatsAgainst(String displayName, MeasuredOperationList current) {
        def sb = new StringBuilder()
        if (current.avgMemory() > results.avgMemory()) {
            sb.append("Memory $displayName: we need more memory than $version.\n")
        } else {
            sb.append("Memory $displayName: AWESOME! we need less memory than $version :D\n")
        }
        def diff = current.avgMemory() - results.avgMemory()
        def desc = diff > DataAmount.bytes(0) ? "more" : "less"
        sb.append("Difference: ${prettyBytes(diff.abs())} $desc (${toBytes(diff.abs())}), ${PrettyCalculator.percentChange(current.avgMemory(), results.avgMemory())}%, max regression: ${prettyBytes(maxMemoryRegression)}\n")
        sb.append(current.memoryStats)
        sb.append(results.memoryStats)
        sb.append("\n")
        sb.toString()
    }

    boolean usesLessMemoryThan(MeasuredOperationList current) {
        current.avgMemory() - results.avgMemory() > maxMemoryRegression
    }

    boolean fasterThan(MeasuredOperationList current) {
        current.avgTime() - results.avgTime() > maxExecutionTimeRegression
    }
}
