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

package org.gradle.performance.fixture

import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.performance.measure.DataAmount
import org.gradle.performance.measure.Duration

class GradleVsMavenBuildPerformanceResults extends PerformanceTestResult {
    String testGroup

    private final Map<BuildDisplayInfo, MeasuredOperationList> buildResults = new LinkedHashMap<>()

    @Override
    String toString() {
        testId
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
        buildResults.values().collect() {
            it.exception
        }.flatten().findAll()
    }

    void assertEveryBuildSucceeds() {
        if (failures) {
            throw new DefaultMultiCauseException("Performance test '$testId' failed", failures)
        }
    }

    void assertComparesWithMaven(double maxTimeDifference, double maxMemoryDifference) {
        builds.groupBy { it.displayName - 'Gradle ' - 'Maven ' }.each { scenario, infos ->
            def gradle = buildResults[infos.find { it.displayName.startsWith 'Gradle ' }]
            def maven = buildResults[infos.find { it.displayName.startsWith 'Maven ' }]
            def baselineVersion = new BaselineVersion("Maven")
            baselineVersion.results.addAll(maven)
            baselineVersion.setMaxExecutionTimeRegression(Duration.millis(maxTimeDifference))
            baselineVersion.setMaxMemoryRegression(DataAmount.mbytes(maxMemoryDifference))
            def stats = [baselineVersion.getSpeedStatsAgainst("Gradle", gradle), baselineVersion.getMemoryStatsAgainst("Gradle", gradle)]
            stats.each {
                println it
            }

            def mavenIsFaster = baselineVersion.fasterThan(gradle)
            def mavenUsesLessMemory = baselineVersion.usesLessMemoryThan(gradle)
            if (mavenIsFaster && mavenUsesLessMemory) {
                throw new AssertionError(stats.join('\n'))
            } else if (mavenIsFaster) {
                throw new AssertionError(stats[0])
            } else if (mavenUsesLessMemory) {
                throw new AssertionError(stats[1])
            }
        }
    }
}
