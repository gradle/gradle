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

    private final static LOGGER = Logging.getLogger(PerformanceTestRunner.class)

    List<BaselineVersion> baselineVersions = [ new BaselineVersion(version:  "1.x", results: new MeasuredOperationList(name: "Gradle 1.x")) ]
    String displayName

    final MeasuredOperationList current = new MeasuredOperationList(name:  "Current G.")

    def clear() {
        baselineVersions.each { it.clearResults() }
        current.clear()
    }

    void assertEveryBuildSucceeds() {
        LOGGER.info("Asserting all builds have succeeded...");
        def failures = []
        baselineVersions.each {
            failures.addAll it.results.findAll { it.exception }
        }
        failures.addAll current.findAll { it.exception }

        assert failures.collect { it.exception }.empty : "Some builds have failed."
    }

    void assertCurrentVersionHasNotRegressed() {
        def slower = assertCurrentReleaseIsNotSlower()
        def larger = assertMemoryUsed()
        assertEveryBuildSucceeds()
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
        def failed = false
        def sb = new StringBuilder()
        baselineVersions.each {
            failed = failed || (current.avgMemory() - it.results.avgMemory()) > it.maxMemoryRegression

            if (current.avgMemory() > it.results.avgMemory()) {
                sb.append "Memory $displayName: we need more memory than $it.version.\n"
            } else {
                sb.append "Memory $displayName: AWESOME! we need less memory than $it.version :D\n"
            }
            sb.append it.getMemoryStatsAgainst(current) + "\n"
        }
        def message = sb.toString()
        println(message)
        return failed ? message : null
    }

    private String assertCurrentReleaseIsNotSlower() {
        def failed = false
        def sb = new StringBuilder()
        baselineVersions.each {
            failed = failed || (current.avgTime() - it.results.avgTime()) > it.maxExecutionTimeRegression

            if (current.avgTime() > it.results.avgTime()) {
                sb.append "Speed $displayName: we're slower than $it.version.\n"
            } else {
                sb.append "Speed $displayName: AWESOME! we're faster than $it.version :D\n"
            }
            sb.append it.getSpeedStatsAgainst(current) + "\n"
        }
        def message = sb.toString()
        println(message)
        return failed ? message : null
    }
}
