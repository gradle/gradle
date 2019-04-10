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

package org.gradle.performance.results;

import com.google.common.collect.Sets;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
import static org.gradle.performance.results.ScenarioBuildResultData.FLAKINESS_DETECTION_THRESHOLD;

public class FlakinessIndexPageGenerator extends AbstractTablePageGenerator {
    public static final int MOST_RECENT_EXECUTIONS = 9;
    private static final Comparator<ScenarioBuildResultData> SCENARIO_COMPARATOR =
        comparing(ScenarioBuildResultData::isBuildFailed).reversed()
            .thenComparing(ScenarioBuildResultData::isSuccessful)
            .thenComparing(comparing(ScenarioBuildResultData::isBuildFailed).reversed())
            .thenComparing(comparing(FlakinessIndexPageGenerator::isFlaky).reversed())
            .thenComparing(comparing(ScenarioBuildResultData::getDifferencePercentage).reversed())
            .thenComparing(ScenarioBuildResultData::getScenarioName);

    public FlakinessIndexPageGenerator(ResultsStore resultsStore, File resultJson) {
        super(resultsStore, resultJson);
    }

    @Override
    protected Set<ScenarioBuildResultData> queryExecutionData(List<ScenarioBuildResultData> scenarioList) {
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

    @Override
    public void render(final ResultsStore store, Writer writer) throws IOException {
        new TableHtml(writer) {
            @Override
            protected String getPageTitle() {
                return "Flakiness report for commit " + commitId;
            }

            @Override
            protected String getTableTitle() {
                int total = scenarios.size();
                long flakyCount = scenarios.stream().filter(FlakinessIndexPageGenerator::isFlaky).count();
                return "Scenarios ( total: " + total + ", flaky: " + flakyCount + ")";
            }

            @Override
            protected boolean renderFailureSelectButton() {
                return false;
            }

            @Override
            protected List<ScenarioBuildResultData> getCrossVersionScenarios() {
                return new ArrayList<>(scenarios);
            }

            @Override
            protected List<ScenarioBuildResultData> getCrossBuildScenarios() {
                return Collections.emptyList();
            }

            @Override
            protected String determineScenarioBackgroundColorCss(ScenarioBuildResultData scenario) {
                return isFlaky(scenario) ? "alert-warning" : "alert-info";
            }

            @Override
            protected Set<Tag> determineTags(ScenarioBuildResultData scenario) {
                return isFlaky(scenario) ? Sets.newHashSet(Tag.FLAKY) : Collections.emptySet();
            }

            @Override
            protected void renderScenarioButtons(int index, ScenarioBuildResultData scenario) {
            }
        };
    }

    public void reportToIssueTracker() {
        scenarios.stream().filter(FlakinessIndexPageGenerator::isFlaky).forEach(PerformanceFlakinessAnalyzer.getInstance()::report);
    }

    private static boolean isFlaky(ScenarioBuildResultData scenario) {
        return scenario.getExecutions().stream().anyMatch(execution -> execution.getConfidencePercentage() > FLAKINESS_DETECTION_THRESHOLD);
    }
}
