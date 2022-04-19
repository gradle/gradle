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
import org.apache.commons.lang.StringUtils;
import org.gradle.performance.results.CrossBuildPerformanceTestHistory;
import org.gradle.performance.results.PerformanceReportScenario;
import org.gradle.performance.results.PerformanceReportScenarioHistoryExecution;
import org.gradle.performance.results.PerformanceTestExecutionResult;
import org.gradle.performance.results.PerformanceTestHistory;
import org.gradle.performance.results.ResultsStore;
import org.gradle.performance.results.ResultsStoreHelper;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

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
        List<String> teamcityBuildIds = teamCityExecutionsOfSameScenario
            .stream()
            .map(PerformanceTestExecutionResult::getTeamCityBuildId)
            .filter(StringUtils::isNotBlank)
            .collect(toList());
        PerformanceTestHistory history = resultsStore.getTestResults(teamCityExecutionsOfSameScenario.get(0).getPerformanceExperiment(), DEFAULT_RETRY_COUNT, PERFORMANCE_DATE_RETRIEVE_DAYS, ResultsStoreHelper.determineChannelPatterns(), teamcityBuildIds);

        List<PerformanceReportScenarioHistoryExecution> historyExecutions = removeEmptyExecution(history.getExecutions());
        return new PerformanceReportScenario(
            teamCityExecutionsOfSameScenario,
            historyExecutions,
            history instanceof CrossBuildPerformanceTestHistory,
            historyExecutions.stream().map(PerformanceReportScenarioHistoryExecution::getTeamCityBuildId).noneMatch(performanceTestBuildIds::contains)
        );
    }
}
