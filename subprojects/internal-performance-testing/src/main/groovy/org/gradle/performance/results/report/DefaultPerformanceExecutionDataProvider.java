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
import org.gradle.performance.results.PerformanceReportScenarioTeamCityExecution;
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
import static java.util.stream.Collectors.toSet;
import static org.gradle.performance.results.PerformanceReportScenarioTeamCityExecution.STATUS_SUCCESS;
import static org.gradle.performance.results.PerformanceReportScenarioTeamCityExecution.STATUS_UNKNOWN;

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

    public DefaultPerformanceExecutionDataProvider(ResultsStore resultsStore, List<File> resultJsons, Set<String> teamCityBuildIds) {
        super(resultsStore, resultJsons, teamCityBuildIds);
    }

    @Override
    protected TreeSet<PerformanceReportScenario> queryExecutionData(List<PerformanceReportScenarioTeamCityExecution> scenarioExecutions) {
        // scenarioExecutions contains duplicate scenarios because of rerun
        return scenarioExecutions.stream()
            .collect(groupingBy(PerformanceReportScenarioTeamCityExecution::getPerformanceExperiment))
            .values()
            .stream()
            .map(this::queryAndSortExecutionData)
            .collect(treeSetCollector(SCENARIO_COMPARATOR));
    }

    private PerformanceReportScenario queryAndSortExecutionData(List<PerformanceReportScenarioTeamCityExecution> teamCityExecutionsOfSameScenario) {
        List<String> teamcityBuildIds = teamCityExecutionsOfSameScenario.stream().map(PerformanceReportScenarioTeamCityExecution::getTeamCityBuildId).collect(toList());
        PerformanceTestHistory history = resultsStore.getTestResults(teamCityExecutionsOfSameScenario.get(0).getPerformanceExperiment(), DEFAULT_RETRY_COUNT, PERFORMANCE_DATE_RETRIEVE_DAYS, ResultsStoreHelper.determineChannel(), teamcityBuildIds);

        List<PerformanceReportScenarioHistoryExecution> historyExecutions = removeEmptyExecution(history.getExecutions());
        return new PerformanceReportScenario(
            teamCityExecutionsOfSameScenario,
            historyExecutions,
            history instanceof CrossBuildPerformanceTestHistory,
            historyExecutions.stream().noneMatch(teamcityBuildIds::contains)
        );
    }


//    private void setExecutions(PerformanceTestReportScenarioExecution scenarioBuildResultData, List<String> teamCityBuildIds, List<? extends PerformanceTestExecution> recentExecutions) {
//        List<? extends PerformanceTestExecution> currentBuildExecutions = recentExecutions.stream()
//            .filter(execution -> teamCityBuildIds.contains(execution.getTeamCityBuildId()))
//            .collect(toList());
//        if (currentBuildExecutions.isEmpty()) {
//            scenarioBuildResultData.setRecentExecutions(determineRecentExecutions(removeEmptyExecution(recentExecutions)));
//        } else {
//            scenarioBuildResultData.setCurrentBuildExecutions(removeEmptyExecution(currentBuildExecutions));
//        }
//    }
//
//    private PerformanceScenarioTeamCityExecution mergeScenarioWithSameName(List<PerformanceScenarioTeamCityExecution> scenariosWithSameName) {
//        if (scenariosWithSameName.size() == 1) {
//            PerformanceScenarioTeamCityExecution result = scenariosWithSameName.get(0);
//            result.setRawData(singletonList(result));
//            return result;
//        } else {
//            PerformanceScenarioTeamCityExecution mergedScenario = new PerformanceScenarioTeamCityExecution();
//            mergedScenario.setScenarioName(scenariosWithSameName.get(0).getScenarioName());
//            mergedScenario.setTestProject(scenariosWithSameName.get(0).getTestProject());
//            mergedScenario.setScenarioClass(scenariosWithSameName.get(0).getScenarioClass());
//            mergedScenario.setRawData(scenariosWithSameName);
//            mergedScenario.setStatus(determineMergedScenarioStatus(scenariosWithSameName));
//            return mergedScenario;
//        }
//    }

    private String determineMergedScenarioStatus(List<PerformanceReportScenarioTeamCityExecution> scenariosWithSameName) {
        if (allScenarioHaveSameStatus(scenariosWithSameName)) {
            return scenariosWithSameName.get(0).getStatus();
        } else if (scenariosWithSameName.stream().anyMatch(scenario -> STATUS_SUCCESS.equals(scenario.getStatus()))) {
            // Flaky
            return STATUS_SUCCESS;
        } else {
            return STATUS_UNKNOWN;
        }
    }


    private boolean allScenarioHaveSameStatus(List<PerformanceReportScenarioTeamCityExecution> scenariosWithSameName) {
        return scenariosWithSameName.stream().map(PerformanceReportScenarioTeamCityExecution::getStatus).collect(toSet()).size() == 1;
    }
//
//    private List<PerformanceScenarioTeamCityExecution.ExecutionData> determineRecentExecutions(List<PerformanceScenarioTeamCityExecution.ExecutionData> executions) {
//        List<PerformanceScenarioTeamCityExecution.ExecutionData> executionsOfSameCommit = executions.stream().filter(this::sameCommit).collect(toList());
//        if (executionsOfSameCommit.isEmpty()) {
//            return executions;
//        } else {
//            return executionsOfSameCommit;
//        }
//    }
}
