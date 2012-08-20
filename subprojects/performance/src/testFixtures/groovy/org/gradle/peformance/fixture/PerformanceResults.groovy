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

public class PerformanceResults {

    int accuracyMs
    String displayName

    private final static LOGGER = Logging.getLogger(PerformanceTestRunner.class)

    List<MeasuredOperation> previous = new LinkedList<MeasuredOperation>()
    List<MeasuredOperation> current = new LinkedList<MeasuredOperation>()

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

        List<Long> previousBytes = previous.collect { it.totalMemoryUsed }
        List<Long> currentBytes = current.collect { it.totalMemoryUsed }

        def averagePrevious = (previousBytes.sum() / previous.size()).setScale(2, RoundingMode.HALF_UP)
        def averageCurrent  = (currentBytes.sum() / current.size()).setScale(2, RoundingMode.HALF_UP)

        println ("""---------------
Build stats. $displayName:
 -previous: $previous
 -previous average: ${averagePrevious} b, min: ${previousBytes.min()} b, max: ${previousBytes.max()} b
 -current: $current
 -current average: ${averageCurrent} b, min: ${currentBytes.min()} b, max: ${currentBytes.max()} b
 -change: ${percentChange(averageCurrent, averagePrevious)}%
---------------""")

        assert (averageCurrent - (maxRegression * averagePrevious)) <= averagePrevious : """Looks like the current gradle requires more memory than the latest release.
  Previous release stats: ${previousBytes}
  Current gradle stats:   ${currentBytes}
  Difference in memory consumption: ${averageCurrent - averagePrevious} bytes
  Currently configured max regression: $maxRegression (${averagePrevious * maxRegression})
"""
    }

    private Number percentChange(Number current, Number previous) {
        def result = (100 * (previous-current) / previous).setScale(2, RoundingMode.HALF_UP)
        return result
    }

    void assertCurrentReleaseIsNotSlower() {
        assertEveryBuildSucceeds()
        def previousTimes = previous.collect { it.executionTime }
        def averagePrevious = (previousTimes.sum() / previous.size()).setScale(2, RoundingMode.HALF_UP)
        def currentTimes = current.collect { it.executionTime }
        def averageCurrent  = (currentTimes.sum() / current.size()).setScale(2, RoundingMode.HALF_UP)

        println("""---------------
Build stats. $displayName:
 -previous: $previousTimes
 -previous average: $averagePrevious ms, min: ${previousTimes.min()} ms, max: ${previousTimes.max()} ms
 -current : $currentTimes
 -current average: $averageCurrent ms, min: ${currentTimes.min()} ms, max: ${currentTimes.max()} ms
 -change: ${percentChange(averageCurrent, averagePrevious)}%
---------------""")

        if (averageCurrent > averagePrevious) {
            LOGGER.warn("Before applying any statistical tuning, the current release average build time is slower than the previous.")
        }

        assert (averageCurrent - accuracyMs) <= averagePrevious : """Looks like the current gradle is slower than latest release.
  Previous release build times: ${previousTimes}
  Current gradle build times:   ${currentTimes}
  Difference between average current and average previous: ${averageCurrent - averagePrevious} millis.
  Currently configured accuracy treshold: $accuracyMs
"""
    }
}
