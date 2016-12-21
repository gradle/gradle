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
import org.gradle.internal.exceptions.DefaultMultiCauseException

@CompileStatic
class CrossBuildPerformanceResults extends PerformanceTestResult {
    String testGroup

    private final Map<BuildDisplayInfo, MeasuredOperationList> buildResults = new LinkedHashMap<>()

    @Override
    String toString() {
        return testId
    }

    protected Map<BuildDisplayInfo, MeasuredOperationList> getBuildResults() {
        buildResults
    }

    MeasuredOperationList buildResult(BuildDisplayInfo buildInfo) {
        def buildResult = buildResults[buildInfo]
        if (buildResult == null) {
            buildResult = new MeasuredOperationList(name: buildInfo.displayName)
            buildResults[buildInfo] = buildResult
        }
        return buildResult
    }

    public Set<BuildDisplayInfo> getBuilds() {
        buildResults.keySet()
    }

    MeasuredOperationList buildResult(String displayName) {
        def matches = builds.findAll { it.displayName == displayName }
        if (matches.empty) {
            return new MeasuredOperationList(name: displayName)
        }
        assert matches.size() == 1
        buildResult(matches.first())
    }

    List<Exception> getFailures() {
        List.cast(buildResults.values().collect() {
            it.exception
        }.flatten().findAll())
    }

    void assertEveryBuildSucceeds() {
        if (failures && whatToCheck().exceptions()) {
            throw new DefaultMultiCauseException("Performance test '$testId' failed", failures)
        }
    }
}
