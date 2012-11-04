/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.logging.Logging
import org.jscience.physics.amount.Amount

import javax.measure.quantity.DataAmount
import javax.measure.quantity.Duration
import javax.measure.unit.NonSI
import javax.measure.unit.SI

import static PrettyCalculator.prettyBytes
import static PrettyCalculator.prettyTime
import static org.gradle.peformance.fixture.PrettyCalculator.toBytes
import static org.gradle.peformance.fixture.PrettyCalculator.toMillis

public class PerformanceResults {

    String displayName
    Amount<Duration> maxExecutionTimeRegression = Amount.valueOf(0, SI.SECOND)
    Amount<DataAmount> maxMemoryRegression = Amount.valueOf(0, NonSI.BYTE)

    private final static LOGGER = Logging.getLogger(PerformanceTestRunner.class)

    final MeasuredOperationList previous = new MeasuredOperationList(name: "Previous release")
    final MeasuredOperationList current = new MeasuredOperationList(name:  "Current Gradle")
    final Map<String, MeasuredOperationList> others = new TreeMap<>()

    def clear() {
        previous.clear();
        current.clear();
        others.values()*.clear()
    }

    void assertEveryBuildSucceeds() {
        LOGGER.info("Asserting all builds have succeeded...");
        assert previous.size() == current.size()
        def failures = []
        failures.addAll previous.findAll { it.exception }
        others.values().each {
            failures.addAll it.findAll { it.exception }
        }
        failures.addAll current.findAll { it.exception }
        assert failures.collect { it.exception }.empty : "Some builds have failed."
    }

    void assertCurrentVersionHasNotRegressed() {
        def slower = assertCurrentReleaseIsNotSlower()
        def larger = assertMemoryUsed()
        if (slower && larger) {
            throw new AssertionError("$slower\n$larger")
        }
        if (slower) {
            throw new AssertionError(slower)
        }
        if (larger) {
            throw new AssertionError(larger)
        }
        assertEveryBuildSucceeds()
    }

    private String assertMemoryUsed() {
        def failed = (current.avgMemory() - previous.avgMemory()) > maxMemoryRegression

        String message;
        if (current.avgMemory() > previous.avgMemory()) {
            message = "Memory $displayName: current Gradle needs a little more memory on average."
        } else {
            message = "Memory $displayName: AWESOME! current Gradle needs less memory on average :D"
        }
        message += "\n${memoryStats()}"
        println("\n$message")
        return failed ? message : null
    }

    private String assertCurrentReleaseIsNotSlower() {
        def failed = (current.avgTime() - previous.avgTime()) > maxExecutionTimeRegression

        String message;
        if (current.avgTime() > previous.avgTime()) {
            message = "Speed $displayName: current Gradle is a little slower on average."
        } else {
            message = "Speed $displayName: AWESOME! current Gradle is faster on average :D"
        }
        message += "\n${speedStats()}"
        println("\n$message")
        return failed ? message : null
    }

    String memoryStats() {
        def result = new StringBuilder()
        result.append(memoryStats(previous))
        others.values().each {
            result.append(memoryStats(it))
        }
        result.append(memoryStats(current))
        def diff = current.avgMemory() - previous.avgMemory()
        def desc = diff > Amount.ZERO ? "more" : "less"
        result.append("Difference: ${prettyBytes(diff.abs())} $desc (${toBytes(diff.abs())} B), ${PrettyCalculator.percentChange(current.avgMemory(), previous.avgMemory())}%, max regression: ${prettyBytes(maxMemoryRegression)}")
        return result.toString()
    }

    String memoryStats(MeasuredOperationList list) {
        """  ${list.name} avg: ${prettyBytes(list.avgMemory())} ${list.collect { prettyBytes(it.totalMemoryUsed) }}
  ${list.name} min: ${prettyBytes(list.minMemory())}, max: ${prettyBytes(list.maxMemory())}
"""
    }

    String speedStats() {
        def result = new StringBuilder()
        result.append(speedStats(previous))
        others.values().each {
            result.append(speedStats(it))
        }
        result.append(speedStats(current))
        def diff = current.avgTime() - previous.avgTime()
        def desc = diff > Amount.valueOf(0, SI.SECOND) ? "slower" : "faster"
        result.append("Difference: ${prettyTime(diff.abs())} $desc (${toMillis(diff.abs())} ms), ${PrettyCalculator.percentChange(current.avgTime(), previous.avgTime())}%, max regression: ${prettyTime(maxExecutionTimeRegression)}")
        return result.toString()
    }

    String speedStats(MeasuredOperationList list) {
        """  ${list.name} avg: ${prettyTime(list.avgTime())} ${list.collect { prettyTime(it.executionTime) }}
  ${list.name} min: ${prettyTime(list.minTime())}, max: ${prettyTime(list.maxTime())}
"""
    }
}
