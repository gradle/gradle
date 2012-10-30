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
import java.math.RoundingMode

import static org.gradle.peformance.fixture.MeasuredOperation.prettyBytes
import static org.gradle.util.Clock.prettyTime

public class PerformanceResults {

    int accuracyMs
    String displayName

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
        def currentExceptions  = previous.findAll { it.exception }.collect() { it.exception }
        assert previousExceptions.isEmpty() & currentExceptions.isEmpty()
    }

    void assertMemoryUsed(double maxRegression) {
        assertEveryBuildSucceeds()
        assert (current.avgMemory() - (maxRegression * previous.avgMemory())) <= previous.avgMemory(): "Looks like the current gradle requires more memory than the latest release.\n${memoryStats(maxRegression)}"

        String message;
        if (current.avgMemory() > previous.avgMemory()) {
            message = "Memory $displayName: current Gradle needs a little more memory on average."
        } else {
            message = "Memory $displayName: AWESOME! current Gradle needs less memory on average :D"
        }
        println("\n$message\n${memoryStats(maxRegression)}")
    }

    void assertCurrentReleaseIsNotSlower() {
        assertEveryBuildSucceeds()

        assert (current.avgTime() - accuracyMs) <= previous.avgTime() : "Looks like the current gradle is slower than latest release.\n${speedStats()}"

        String message;
        if (current.avgTime() > previous.avgTime()) {
            message = "Speed $displayName: current Gradle is a little slower on average."
        } else {
            message = "Speed $displayName: AWESOME! current Gradle is faster on average :D"
        }
        println("\n$message\n${speedStats()}")
    }

    String memoryStats(double maxRegression) {
        """  Previous release avg: ${prettyBytes(previous.avgMemory())} ${previous*.prettyBytes}
  Current gradle avg: ${prettyBytes(current.avgMemory())} ${current*.prettyBytes}%
  Difference: ${prettyBytes(current.avgMemory() - previous.avgMemory())} (${(current.avgMemory() - previous.avgMemory()).round(2)} B), ${percentChange(current.avgMemory().round(), previous.avgMemory().round())}%, max regression: $maxRegression (${prettyBytes((long) (previous.avgMemory() * maxRegression))})"""
    }

    String speedStats() {
        """  Previous release avg: ${prettyTime(previous.avgTime().round())} ${previous*.prettyTime}
  Current gradle avg: ${prettyTime(current.avgTime().round())} ${current*.prettyTime}
  Difference: ${prettyTime((current.avgTime() - previous.avgTime()).round())} (${(current.avgTime() - previous.avgTime()).round(2)} ms), ${percentChange(current.avgTime().round(), previous.avgTime().round())}%, max regression: $accuracyMs ms"""
    }

    private Number percentChange(Number current, Number previous) {
        def result = (-1) * (100 * (previous-current) / previous).setScale(2, RoundingMode.HALF_UP)
        return result
    }
}