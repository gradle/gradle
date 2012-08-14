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

        List previousBytes = previous.collect { it.totalMemoryUsed }
        List currentBytes = current.collect { it.totalMemoryUsed }

        long averagePrevious = previousBytes.sum() / previous.size()
        long averageCurrent  = currentBytes.sum() / current.size()

        def difference = []
        for(int i = 0; i<previous.size(); i++) {
            int percentage = percent(previousBytes[i], currentBytes[i])
            difference << "$percentage%"
        }

        long previousMax = previousBytes.max()
        long currentMin = currentBytes.min()
        long minDifference = percent(previousMax, currentMin)

        println ("\n---------------\nBuild stats. $displayName:\n"
                + " -previous    : $previous\n"
                + " -current     : $current\n"
                + " -diff(%)     : $difference\n"
                + " -min diff(%) : $minDifference%\n"
                + "---------------\n")

        assert (currentMin - (maxRegression * currentMin)) <= previousMax : """
Looks like the current gradle requires more memory than the latest release.
  Previous release stats: ${previous}
  Current gradle stats:   ${current}
  Difference in memory consumption: ${difference}
  Currently configured max regression: $maxRegression
"""
    }

    private double percent(long x, long y) {
        if (y == 0) {
            return 0
        }
        100 * (1d - x / y)
    }

    void assertCurrentReleaseIsNotSlower() {
        assertEveryBuildSucceeds()
        long averagePrevious = previous.collect { it.executionTime }.sum() / previous.size()
        long averageCurrent  = current.collect { it.executionTime }.sum() / current.size()

        println ("\n---------------\nBuild stats. $displayName:\n"
            + " -previous: $previous\n"
            + " -current : $current\n---------------\n")

        if (averageCurrent > averagePrevious) {
            LOGGER.warn("Before applying any statistical tuning, the current release average build time is slower than the previous.")
        }

        assert (averageCurrent - accuracyMs) <= averagePrevious : """Looks like the current gradle is slower than latest release.
  Previous release build times: ${previous}
  Current gradle build times:   ${current}
  Difference between average current and average previous: ${averageCurrent - averagePrevious} millis.
  Currently configured accuracy treshold: $accuracyMs
"""
    }
}
