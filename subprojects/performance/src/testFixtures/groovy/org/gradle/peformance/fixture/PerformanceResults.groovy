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

import static PrettyCalculator.prettyBytes
import static org.gradle.util.Clock.prettyTime

public class PerformanceResults {

    int accuracyMs
    String displayName
    double maxMemoryRegression

    private final static LOGGER = Logging.getLogger(PerformanceTestRunner.class)

    MeasuredOperationList previous = new MeasuredOperationList()
    MeasuredOperationList current = new MeasuredOperationList()

    def clear() {
        previous.clear();
        current.clear();
    }

    void addResult(MeasuredOperation previous, MeasuredOperation current) {
        this.previous.add(previous)
        this.current.add(current)
    }

    void assertEveryBuildSucceeds() {
        LOGGER.info("Asserting all builds have succeeded...");
        assert previous.size() == current.size()
        def previousExceptions = previous.findAll { it.exception }.collect() { it.exception }
        def currentExceptions = previous.findAll { it.exception }.collect() { it.exception }
        assert previousExceptions.isEmpty() & currentExceptions.isEmpty()
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
    }

    private String assertMemoryUsed() {
        double maxRegression = maxMemoryRegression
        assertEveryBuildSucceeds()
        def failed = (current.avgMemory() - (maxRegression * previous.avgMemory())) > previous.avgMemory()

        String message;
        if (current.avgMemory() > previous.avgMemory()) {
            message = "Memory $displayName: current Gradle needs a little more memory on average."
        } else {
            message = "Memory $displayName: AWESOME! current Gradle needs less memory on average :D"
        }
        message += "\n${memoryStats(maxRegression)}"
        println("\n$message")
        return failed ? message : null
    }

    private String assertCurrentReleaseIsNotSlower() {
        assertEveryBuildSucceeds()

        def failed = (current.avgTime() - accuracyMs) > previous.avgTime()

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

    String memoryStats(double maxRegression) {
        """  Previous release avg: ${prettyBytes(previous.avgMemory())} ${previous*.prettyBytes}
  Previous release min: ${prettyBytes(previous.minMemory())}, max: ${prettyBytes(previous.maxMemory())}
  Current gradle avg: ${prettyBytes(current.avgMemory())} ${current*.prettyBytes}%
  Current gradle min: ${prettyBytes(current.minMemory())}, max: ${prettyBytes(current.maxMemory())}
  Difference: ${prettyBytes(current.avgMemory() - previous.avgMemory())} (${(current.avgMemory() - previous.avgMemory()).round(2)} B), ${PrettyCalculator.percentChange(current.avgMemory(), previous.avgMemory())}%, max regression: $maxRegression (${prettyBytes((long) (previous.avgMemory() * maxRegression))})"""
    }

    String speedStats() {
        """  Previous release avg: ${prettyTime(previous.avgTime().round())} ${previous*.prettyTime}
  Previous release min: ${prettyTime(previous.minTime())}, max: ${prettyTime(previous.maxTime())}
  Current gradle avg: ${prettyTime(current.avgTime().round())} ${current*.prettyTime}
  Current gradle min: ${prettyTime(current.minTime())}, max: ${prettyTime(current.maxTime())}
  Difference: ${prettyTime((current.avgTime() - previous.avgTime()).round())} (${(current.avgTime() - previous.avgTime()).round(2)} ms), ${PrettyCalculator.percentChange(current.avgTime(), previous.avgTime())}%, max regression: $accuracyMs ms"""
    }

}
