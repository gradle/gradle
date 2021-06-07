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

import com.google.common.collect.ImmutableList;
import org.gradle.performance.results.CrossBuildPerformanceTestHistory;
import org.gradle.performance.results.PerformanceReportScenario;
import org.gradle.performance.results.PerformanceTestExecutionResult;
import org.gradle.performance.results.PerformanceTestExecution;
import org.gradle.performance.results.PerformanceTestHistory;
import org.gradle.performance.results.ResultsStore;
import org.gradle.performance.results.ResultsStoreHelper;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static org.gradle.performance.results.PerformanceTestExecutionResult.FLAKINESS_DETECTION_THRESHOLD;

class FlakinessDetectionPerformanceExecutionDataProvider extends PerformanceExecutionDataProvider {
    public static final int MOST_RECENT_EXECUTIONS = 9;
    private static final Comparator<PerformanceReportScenario> SCENARIO_COMPARATOR =
        comparing(PerformanceReportScenario::isBuildFailed).reversed()
            .thenComparing(PerformanceReportScenario::isSuccessful)
            .thenComparing(comparing(PerformanceReportScenario::isBuildFailed).reversed())
            .thenComparing(comparing(org.gradle.performance.results.report.FlakinessDetectionPerformanceExecutionDataProvider::isFlaky).reversed())
            .thenComparing(comparing(PerformanceReportScenario::getDifferencePercentage).reversed())
            .thenComparing(PerformanceReportScenario::getName);

    public FlakinessDetectionPerformanceExecutionDataProvider(ResultsStore resultsStore, List<File> resultJsons, Set<String> performanceTestBuildIds) {
        super(resultsStore, resultJsons, performanceTestBuildIds);
    }

    @Override
    protected TreeSet<PerformanceReportScenario> queryExecutionData(List<PerformanceTestExecutionResult> scenarioList) {
        Set<PerformanceTestExecutionResult> distinctScenarios = scenarioList
            .stream()
            .collect(treeSetCollector(comparing(PerformanceTestExecutionResult::getPerformanceExperiment)));

        return distinctScenarios.stream()
            .map(this::queryExecutionData)
            .collect(treeSetCollector(SCENARIO_COMPARATOR));
    }

    private PerformanceReportScenario queryExecutionData(PerformanceTestExecutionResult execution) {
        PerformanceTestHistory history = resultsStore.getTestResults(execution.getPerformanceExperiment(), MOST_RECENT_EXECUTIONS, PERFORMANCE_DATE_RETRIEVE_DAYS, ResultsStoreHelper.determineChannel(), ImmutableList.of());
        List<? extends PerformanceTestExecution> executionsOfSameCommit = history.getExecutions().stream().filter(e -> e.getVcsCommits().contains(commitId)).collect(toList());
        List<? extends PerformanceTestExecution> currentExecutions = executionsOfSameCommit.isEmpty()
            ? history.getExecutions().stream().limit(3).collect(toList())
            : executionsOfSameCommit;
        return new PerformanceReportScenario(
            Collections.singletonList(execution),
            removeEmptyExecution(currentExecutions),
            history instanceof CrossBuildPerformanceTestHistory,
            false
        );
    }

    public static boolean isFlaky(PerformanceReportScenario scenario) {
        return scenario.getCurrentExecutions().stream().anyMatch(execution -> execution.getConfidencePercentage() > FLAKINESS_DETECTION_THRESHOLD);
    }
}
