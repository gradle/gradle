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

class NoResultsStore<T extends PerformanceTestResult> implements WritableResultsStore<T> {

    private static final INSTANCE = new NoResultsStore()

    static <T extends PerformanceTestResult> WritableResultsStore<T> getInstance() {
        return INSTANCE
    }

    @Override
    void report(T results) {
    }

    @Override
    List<PerformanceExperiment> getPerformanceExperiments() {
        []
    }

    @Override
    PerformanceTestHistory getTestResults(PerformanceExperiment experiment, int mostRecentN, int maxDaysOld, List<String> channelPatterns, List<String> teamcityBuildIds) {
        new EmptyPerformanceTestHistory(experiment)
    }

    @Override
    Map<PerformanceExperimentOnOs, Long> getEstimatedExperimentDurationsInMillis() {
        return Collections.emptyMap()
    }

    @Override
    void close() {
    }
}
