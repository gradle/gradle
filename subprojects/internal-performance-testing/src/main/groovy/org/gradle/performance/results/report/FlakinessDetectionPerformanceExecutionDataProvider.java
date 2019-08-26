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

import org.gradle.performance.results.PerformanceTestExecution;
import org.gradle.performance.results.PerformanceTestHistory;
import org.gradle.performance.results.ResultsStore;
import org.gradle.performance.results.ResultsStoreHelper;
import org.gradle.performance.results.ScenarioBuildResultData;

import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static org.gradle.performance.results.ScenarioBuildResultData.FLAKINESS_DETECTION_THRESHOLD;

class FlakinessDetectionPerformanceExecutionDataProvider extends PerformanceExecutionDataProvider {
    public static final int MOST_RECENT_EXECUTIONS = 9;
    private static final Comparator<ScenarioBuildResultData> SCENARIO_COMPARATOR =
        comparing(ScenarioBuildResultData::isBuildFailed).reversed()
            .thenComparing(ScenarioBuildResultData::isSuccessful)
            .thenComparing(comparing(ScenarioBuildResultData::isBuildFailed).reversed())
            .thenComparing(comparing(org.gradle.performance.results.report.FlakinessDetectionPerformanceExecutionDataProvider::isFlaky).reversed())
            .thenComparing(comparing(ScenarioBuildResultData::getDifferencePercentage).reversed())
            .thenComparing(ScenarioBuildResultData::getScenarioName);

    public FlakinessDetectionPerformanceExecutionDataProvider(ResultsStore resultsStore, File resultsJson) {
        super(resultsStore, resultsJson);
    }

    @Override
    protected TreeSet<ScenarioBuildResultData> queryExecutionData(List<ScenarioBuildResultData> scenarioList) {
        Set<ScenarioBuildResultData> distinctScenarios = scenarioList
            .stream()
            .collect(treeSetCollector(comparing(ScenarioBuildResultData::getScenarioName)));

        return distinctScenarios.stream()
            .map(this::queryExecutionData)
            .collect(treeSetCollector(SCENARIO_COMPARATOR));
    }

    private ScenarioBuildResultData queryExecutionData(ScenarioBuildResultData scenario) {
        PerformanceTestHistory history = resultsStore.getTestResults(scenario.getScenarioName(), MOST_RECENT_EXECUTIONS, PERFORMANCE_DATE_RETRIEVE_DAYS, ResultsStoreHelper.determineChannel());
        List<? extends PerformanceTestExecution> currentExecutions = history.getExecutions().stream().filter(execution -> execution.getVcsCommits().contains(commitId)).collect(toList());
        scenario.setCurrentBuildExecutions(removeEmptyExecution(currentExecutions));
        return scenario;
    }

    public static boolean isFlaky(ScenarioBuildResultData scenario) {
        return scenario.getExecutions().stream().anyMatch(execution -> execution.getConfidencePercentage() > FLAKINESS_DETECTION_THRESHOLD);
    }
}
