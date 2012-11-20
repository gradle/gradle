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



package org.gradle.peformance.fixture

import static org.gradle.peformance.fixture.PrettyCalculator.*

/**
 * by Szczepan Faber, created at: 11/20/12
 */
class BaselineVersion {

    String version
    Amount<Duration> maxExecutionTimeRegression = Duration.millis(0)
    Amount<DataAmount> maxMemoryRegression = DataAmount.bytes(0)

    MeasuredOperationList results

    void clearResults() {
        results.clear()
    }

    String getSpeedStatsAgainst(MeasuredOperationList current) {
        def sb = new StringBuilder()
        def diff = current.avgTime() - results.avgTime()
        def desc = diff > Duration.millis(0) ? "slower" : "faster"
        sb.append("Difference: ${prettyTime(diff.abs())} $desc (${toMillis(diff.abs())}), ${PrettyCalculator.percentChange(current.avgTime(), results.avgTime())}%, max regression: ${prettyTime(maxExecutionTimeRegression)}\n")
        sb.append(current.speedStats)
        sb.append(results.speedStats)
    }

    String getMemoryStatsAgainst(MeasuredOperationList current) {
        def sb = new StringBuilder()
        def diff = current.avgMemory() - results.avgMemory()
        def desc = diff > DataAmount.bytes(0) ? "more" : "less"
        sb.append("Difference: ${prettyBytes(diff.abs())} $desc (${toBytes(diff.abs())}), ${PrettyCalculator.percentChange(current.avgMemory(), results.avgMemory())}%, max regression: ${prettyBytes(maxMemoryRegression)}\n")
        sb.append(current.memoryStats)
        sb.append(results.memoryStats)
        return sb.toString()
    }
}
