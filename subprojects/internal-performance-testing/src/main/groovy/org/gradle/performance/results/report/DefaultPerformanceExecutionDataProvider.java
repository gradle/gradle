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
import org.gradle.performance.results.PerformanceTestExecution;
import org.gradle.performance.results.PerformanceTestHistory;
import org.gradle.performance.results.ResultsStore;
import org.gradle.performance.results.ResultsStoreHelper;
import org.gradle.performance.results.ScenarioBuildResultData;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import static java.util.Collections.singletonList;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.gradle.performance.results.ScenarioBuildResultData.STATUS_SUCCESS;
import static org.gradle.performance.results.ScenarioBuildResultData.STATUS_UNKNOWN;

public class DefaultPerformanceExecutionDataProvider extends PerformanceExecutionDataProvider {
    private static final int DEFAULT_RETRY_COUNT = 3;
    @VisibleForTesting
    static final Comparator<ScenarioBuildResultData> SCENARIO_COMPARATOR = comparing(ScenarioBuildResultData::isBuildFailed).reversed()
        .thenComparing(comparing(ScenarioBuildResultData::isFlaky).reversed())
        .thenComparing(ScenarioBuildResultData::isSuccessful)
        .thenComparing(comparing(ScenarioBuildResultData::isBuildFailed).reversed())
        .thenComparing(comparing(ScenarioBuildResultData::isAboutToRegress).reversed())
        .thenComparing(comparing(ScenarioBuildResultData::getDifferenceSortKey).reversed())
        .thenComparing(comparing(ScenarioBuildResultData::getDifferencePercentage).reversed())
        .thenComparing(ScenarioBuildResultData::getScenarioName);

    public DefaultPerformanceExecutionDataProvider(ResultsStore resultsStore, File resultsJson) {
        super(resultsStore, resultsJson);
    }

    @Override
    protected TreeSet<ScenarioBuildResultData> queryExecutionData(List<ScenarioBuildResultData> scenarioList) {
        // scenarioList contains duplicate scenarios because of rerun
        return scenarioList.stream()
            .collect(groupingBy(ScenarioBuildResultData::getScenarioName))
            .values()
            .stream()
            .map(this::queryAndSortExecutionData).collect(treeSetCollector(SCENARIO_COMPARATOR));
    }

    private ScenarioBuildResultData queryAndSortExecutionData(List<ScenarioBuildResultData> scenarios) {
        ScenarioBuildResultData mergedScenario = mergeScenarioWithSameName(scenarios);

        PerformanceTestHistory history = resultsStore.getTestResults(scenarios.get(0).getScenarioName(), DEFAULT_RETRY_COUNT, PERFORMANCE_DATE_RETRIEVE_DAYS, ResultsStoreHelper.determineChannel());
        List<? extends PerformanceTestExecution> recentExecutions = history.getExecutions();

        scenarios.forEach(scenario -> setExecutions(scenario, singletonList(scenario.getTeamCityBuildId()), recentExecutions));
        setExecutions(mergedScenario,  scenarios.stream().map(ScenarioBuildResultData::getTeamCityBuildId).collect(toList()), recentExecutions);

        mergedScenario.setCrossBuild(history instanceof CrossBuildPerformanceTestHistory);

        return mergedScenario;
    }

    private void setExecutions(ScenarioBuildResultData scenarioBuildResultData, List<String> teamCityBuildIds, List<? extends PerformanceTestExecution> recentExecutions) {
        List<? extends PerformanceTestExecution> currentBuildExecutions = recentExecutions.stream()
            .filter(execution -> teamCityBuildIds.contains(execution.getTeamCityBuildId()))
            .collect(toList());
        if (currentBuildExecutions.isEmpty()) {
            scenarioBuildResultData.setRecentExecutions(determineRecentExecutions(removeEmptyExecution(recentExecutions)));
        } else {
            scenarioBuildResultData.setCurrentBuildExecutions(removeEmptyExecution(currentBuildExecutions));
        }
    }

    private ScenarioBuildResultData mergeScenarioWithSameName(List<ScenarioBuildResultData> scenariosWithSameName) {
        if (scenariosWithSameName.size() == 1) {
            ScenarioBuildResultData result = scenariosWithSameName.get(0);
            result.setRawData(singletonList(result));
            return result;
        } else {
            ScenarioBuildResultData mergedScenario = new ScenarioBuildResultData();
            mergedScenario.setScenarioName(scenariosWithSameName.get(0).getScenarioName());
            mergedScenario.setRawData(scenariosWithSameName);
            mergedScenario.setStatus(determineMergedScenarioStatus(scenariosWithSameName));
            return mergedScenario;
        }
    }

    private String determineMergedScenarioStatus(List<ScenarioBuildResultData> scenariosWithSameName) {
        if (allScenarioHaveSameStatus(scenariosWithSameName)) {
            return scenariosWithSameName.get(0).getStatus();
        } else if (scenariosWithSameName.stream().anyMatch(scenario -> STATUS_SUCCESS.equals(scenario.getStatus()))) {
            // Flaky
            return STATUS_SUCCESS;
        } else {
            return STATUS_UNKNOWN;
        }
    }


    private boolean allScenarioHaveSameStatus(List<ScenarioBuildResultData> scenariosWithSameName) {
        return scenariosWithSameName.stream().map(ScenarioBuildResultData::getStatus).collect(toSet()).size() == 1;
    }

    private List<ScenarioBuildResultData.ExecutionData> determineRecentExecutions(List<ScenarioBuildResultData.ExecutionData> executions) {
        List<ScenarioBuildResultData.ExecutionData> executionsOfSameCommit = executions.stream().filter(this::sameCommit).collect(toList());
        if (executionsOfSameCommit.isEmpty()) {
            return executions;
        } else {
            return executionsOfSameCommit;
        }
    }
}
