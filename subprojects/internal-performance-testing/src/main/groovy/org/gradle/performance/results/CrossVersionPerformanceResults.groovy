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
import org.gradle.api.Transformer

@CompileStatic
class CrossVersionPerformanceResults extends PerformanceTestResult {
    String testProject
    List<String> args
    List<String> tasks
    List<String> cleanTasks
    List<String> gradleOpts
    Boolean daemon

    private final Map<String, BaselineVersion> baselineVersions = new LinkedHashMap<>()
    final MeasuredOperationList current = new MeasuredOperationList(name: "Current Gradle")
    private final CurrentVersionResults results = new CurrentVersionResults(current)

    @Override
    String toString() {
        return displayName
    }

    String getDisplayName() {
        def displayName = "Results for test project '$testProject' with tasks ${tasks.join(', ')}"
        if (cleanTasks) {
            displayName += ", cleaned with ${cleanTasks.join(', ')}"
        }
        return displayName
    }

    Collection<BaselineVersion> getBaselineVersions() {
        return baselineVersions.values()
    }

    /**
     * Locates the given baseline version, adding it if not present.
     */
    BaselineVersion baseline(String version) {
        def baselineVersion = baselineVersions[version]
        if (baselineVersion == null) {
            baselineVersion = new BaselineVersion(version)
            baselineVersions[version] = baselineVersion
        }
        return baselineVersion
    }

    /**
     * Locates the given version. Can use either a baseline version or the current branch name.
     */
    VersionResults version(String version) {
        if (version == vcsBranch) {
            return results
        }
        return baseline(version)
    }

    List<MeasuredOperationList> getFailures() {
        def failures = []
        baselineVersions.values().each {
            failures.addAll it.results.findAll { it.exception }
        }
        failures.addAll current.findAll { it.exception }
        return failures
    }

    void assertEveryBuildSucceeds() {
        if (whatToCheck().exceptions()) {
            assert failures.empty: "Some builds have failed: ${failures*.exception}"
        }
    }

    void assertCurrentVersionHasNotRegressed() {
        def slower = checkBaselineVersion({ it.fasterThan(current) }, { it.getSpeedStatsAgainst(displayName, current) })
        assertEveryBuildSucceeds()
        if (slower && whatToCheck().speed()) {
            throw new AssertionError(Object.cast(slower))
        }
    }

    private String checkBaselineVersion(Transformer<Boolean, BaselineVersion> fails, Transformer<String, BaselineVersion> provideMessage) {
        def failed = false
        def failure = new StringBuilder()
        baselineVersions.values().each { it ->
            String message = provideMessage.transform(it)
            if (fails.transform(it)) {
                failed = true
                failure.append message
            }
            println message
        }
        return failed ? failure.toString() : null
    }

    @CompileStatic
    private static class CurrentVersionResults implements VersionResults {
        final MeasuredOperationList results

        CurrentVersionResults(MeasuredOperationList results) {
            this.results = results
        }
    }

}
