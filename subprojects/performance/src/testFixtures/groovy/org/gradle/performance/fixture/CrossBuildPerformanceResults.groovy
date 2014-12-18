/*
 * Copyright 2014 the original author or authors.
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

class CrossBuildPerformanceResults {
    String testId
    String jvm
    String versionUnderTest
    String operatingSystem
    long testTime
    String vcsBranch
    String vcsCommit

    private final Map<BuildSpecification, MeasuredOperationList> buildResults = new LinkedHashMap<>()

    def clear() {
        buildResults.clear()
    }

    @Override
    String toString() {
        return testId
    }

    MeasuredOperationList buildResult(BuildSpecification buildSpecification) {
        def buildResult = buildResults[buildSpecification]
        if (buildResult == null) {
            buildResult = new MeasuredOperationList(name: buildSpecification.displayName)
            buildResults[buildSpecification] = buildResult
        }
        return buildResult
    }

    public Set<BuildSpecification> getBuildSpecifications() {
        return buildResults.keySet();
    }

    List<Exception> getFailures() {
        buildResults.values().collect() {
            it.exception
        }.flatten().findAll()
    }

    void assertEveryBuildSucceeds() {
        assert failures.empty
    }
}
