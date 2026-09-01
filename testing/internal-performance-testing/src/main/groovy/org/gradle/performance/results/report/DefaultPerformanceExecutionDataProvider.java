/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.performance.results.report;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.performance.results.CrossBuildPerformanceTestHistory;
import org.gradle.performance.results.PerformanceReportScenario;
import org.gradle.performance.results.PerformanceReportScenarioHistoryExecution;
import org.gradle.performance.results.PerformanceTestExecutionResult;
import org.gradle.performance.results.PerformanceTestHistory;
import org.gradle.performance.results.ResultsStore;
import org.gradle.performance.results.ResultsStoreHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;

public class DefaultPerformanceExecutionDataProvider extends PerformanceExecutionDataProvider {
    private static final int DEFAULT_RETRY_COUNT = 3;
    @VisibleForTesting
    static final Comparator<PerformanceReportScenario> SCENARIO_COMPARATOR = comparing(PerformanceReportScenario::isBuildFailed).reversed()
        .thenComparing(comparing(PerformanceReportScenario::isFlaky).reversed())
        .thenComparing(PerformanceReportScenario::isSuccessful)
        .thenComparing(comparing(PerformanceReportScenario::isBuildFailed).reversed())
        .thenComparing(comparing(PerformanceReportScenario::isAboutToRegress).reversed())
        .thenComparing(comparing(PerformanceReportScenario::getDifferenceSortKey).reversed())
        .thenComparing(comparing(PerformanceReportScenario::getDifferencePercentage).reversed())
        .thenComparing(PerformanceReportScenario::getName);

    public DefaultPerformanceExecutionDataProvider(ResultsStore resultsStore, List<File> resultJsons, Set<String> performanceTestBuildIds) {
        super(resultsStore, resultJsons, performanceTestBuildIds);
    }

    @Override
    protected TreeSet<PerformanceReportScenario> queryExecutionData(List<PerformanceTestExecutionResult> scenarioExecutions) {
        // scenarioExecutions contains duplicate scenarios because of rerun
        return scenarioExecutions.stream()
            .collect(groupingBy(PerformanceTestExecutionResult::getPerformanceExperiment))
            .values()
            .stream()
            .map(this::queryAndSortExecutionData)
            .collect(treeSetCollector(SCENARIO_COMPARATOR));
    }

    private PerformanceReportScenario queryAndSortExecutionData(List<PerformanceTestExecutionResult> teamCityExecutionsOfSameScenario) {
        // Fetch this pipeline's own runs from the DB using the authoritative bucket build IDs when known. On a
        // build-cache hit no DB row was written, so nothing spurious is pulled in. When the set is unknown (local runs)
        // we fetch recent history for the experiment and PerformanceReportScenario identifies current executions by the
        // commit under test instead.
        List<String> buildIdsToFetch = new ArrayList<>(performanceTestBuildIds);
        PerformanceTestHistory history = resultsStore.getTestResults(teamCityExecutionsOfSameScenario.get(0).getPerformanceExperiment(), DEFAULT_RETRY_COUNT, PERFORMANCE_DATE_RETRIEVE_DAYS, ResultsStoreHelper.determineChannelPatterns(), buildIdsToFetch);

        List<PerformanceReportScenarioHistoryExecution> historyExecutions = removeEmptyExecution(history.getExecutions());
        return new PerformanceReportScenario(
            teamCityExecutionsOfSameScenario,
            historyExecutions,
            history instanceof CrossBuildPerformanceTestHistory,
            performanceTestBuildIds,
            commitId
        );
    }
}
