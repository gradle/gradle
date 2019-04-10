/*
 * Copyright 2013 the original author or authors.
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

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.google.common.collect.ImmutableList.of;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.gradle.performance.results.AbstractTablePageGenerator.Tag.FAILED;
import static org.gradle.performance.results.AbstractTablePageGenerator.Tag.FLAKY;
import static org.gradle.performance.results.AbstractTablePageGenerator.Tag.FROM_CACHE;
import static org.gradle.performance.results.AbstractTablePageGenerator.Tag.IMPROVED;
import static org.gradle.performance.results.AbstractTablePageGenerator.Tag.KNOWN_FLAKY;
import static org.gradle.performance.results.AbstractTablePageGenerator.Tag.NEARLY_FAILED;
import static org.gradle.performance.results.AbstractTablePageGenerator.Tag.REGRESSED;
import static org.gradle.performance.results.AbstractTablePageGenerator.Tag.UNKNOWN;
import static org.gradle.performance.results.AbstractTablePageGenerator.Tag.UNTAGGED;
import static org.gradle.performance.results.ScenarioBuildResultData.ExecutionData;
import static org.gradle.performance.results.ScenarioBuildResultData.STATUS_SUCCESS;
import static org.gradle.performance.results.ScenarioBuildResultData.STATUS_UNKNOWN;

public class IndexPageGenerator extends AbstractTablePageGenerator {
    private static final int DEFAULT_RETRY_COUNT = 3;

    @VisibleForTesting
    static final Comparator<ScenarioBuildResultData> SCENARIO_COMPARATOR = comparing(ScenarioBuildResultData::isBuildFailed).reversed()
        .thenComparing(ScenarioBuildResultData::isSuccessful)
        .thenComparing(comparing(ScenarioBuildResultData::isBuildFailed).reversed())
        .thenComparing(comparing(IndexPageGenerator::isFlaky).reversed())
        .thenComparing(comparing(ScenarioBuildResultData::isAboutToRegress).reversed())
        .thenComparing(comparing(ScenarioBuildResultData::getDifferenceSortKey).reversed())
        .thenComparing(comparing(ScenarioBuildResultData::getDifferencePercentage).reversed())
        .thenComparing(ScenarioBuildResultData::getScenarioName);

    public IndexPageGenerator(ResultsStore resultsStore, File resultJson) {
        super(resultsStore, resultJson);
    }

    @Override
    protected Set<ScenarioBuildResultData> queryExecutionData(List<ScenarioBuildResultData> scenarioList) {
        // scenarioList contains duplicate scenarios because of rerun
        return scenarioList.stream()
            .collect(groupingBy(ScenarioBuildResultData::getScenarioName))
            .values()
            .stream()
            .map(this::queryAndSortExecutionData).collect(treeSetCollector(SCENARIO_COMPARATOR));
    }

    private ScenarioBuildResultData queryAndSortExecutionData(List<ScenarioBuildResultData> scenarios) {
        ScenarioBuildResultData mergedScenario = mergeScenarioWithSameName(scenarios);

        List<String> teamCityBuildIds = scenarios.stream().map(ScenarioBuildResultData::getTeamCityBuildId).collect(toList());

        PerformanceTestHistory history = resultsStore.getTestResults(scenarios.get(0).getScenarioName(), DEFAULT_RETRY_COUNT, PERFORMANCE_DATE_RETRIEVE_DAYS, ResultsStoreHelper.determineChannel());
        List<? extends PerformanceTestExecution> recentExecutions = history.getExecutions();
        List<? extends PerformanceTestExecution> currentBuildExecutions = recentExecutions.stream()
            .filter(execution -> teamCityBuildIds.contains(execution.getTeamCityBuildId()))
            .collect(toList());

        if (currentBuildExecutions.isEmpty()) {
            mergedScenario.setRecentExecutions(determineRecentExecutions(removeEmptyExecution(recentExecutions)));
        } else {
            mergedScenario.setCurrentBuildExecutions(removeEmptyExecution(currentBuildExecutions));
        }

        mergedScenario.setCrossBuild(history instanceof CrossBuildPerformanceTestHistory);

        return mergedScenario;
    }

    private ScenarioBuildResultData mergeScenarioWithSameName(List<ScenarioBuildResultData> scenariosWithSameName) {
        if (scenariosWithSameName.size() == 1) {
            return scenariosWithSameName.get(0);
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

    private List<ExecutionData> determineRecentExecutions(List<ExecutionData> executions) {
        List<ExecutionData> executionsOfSameCommit = executions.stream().filter(this::sameCommit).collect(toList());
        if (executionsOfSameCommit.isEmpty()) {
            return executions;
        } else {
            return executionsOfSameCommit;
        }
    }

    @Override
    public void render(final ResultsStore store, Writer writer) throws IOException {
        long successCount = scenarios.stream().filter(ScenarioBuildResultData::isSuccessful).count();
        long failureCount = scenarios.size() - successCount;

        new TableHtml(writer) {
            @Override
            protected String getPageTitle() {
                return "Profile report for channel " + ResultsStoreHelper.determineChannel();
            }

            @Override
            protected String getTableTitle() {
                StringBuilder sb = new StringBuilder("Scenarios (").append(successCount).append(" successful");
                if (failureCount > 0) {
                    sb.append(", ").append(failureCount).append(" failed");
                }
                sb.append(")");
                return sb.toString();
            }

            @Override
            protected boolean renderFailureSelectButton() {
                return failureCount > 0;
            }

            @Override
            protected List<ScenarioBuildResultData> getCrossVersionScenarios() {
                return scenarios.stream().filter(ScenarioBuildResultData::isCrossVersion).collect(toList());
            }

            @Override
            protected List<ScenarioBuildResultData> getCrossBuildScenarios() {
                return scenarios.stream().filter(ScenarioBuildResultData::isCrossBuild).collect(toList());
            }

            @Override
            protected String determineScenarioBackgroundColorCss(ScenarioBuildResultData scenario) {
                if (scenario.isUnknown()) {
                    return "alert-dark";
                } else if (!scenario.isSuccessful()) {
                    return "alert-danger";
                } else if (scenario.isAboutToRegress()) {
                    return "alert-warning";
                } else if (scenario.isImproved()) {
                    return "alert-success";
                } else {
                    return "alert-info";
                }
            }

            @Override
            protected Set<Tag> determineTags(ScenarioBuildResultData scenario) {
                Set<Tag> result = new HashSet<>();
                if (scenario.isFromCache()) {
                    result.add(FROM_CACHE);
                }

                if (isFlaky(scenario)) {
                    result.add(FLAKY);
                } else if (PerformanceFlakinessAnalyzer.getInstance().findKnownFlakyTest(scenario) != null) {
                    result.add(KNOWN_FLAKY);
                }

                if (scenario.isUnknown()) {
                    result.add(UNKNOWN);
                } else if (scenario.isBuildFailed()) {
                    result.add(FAILED);
                } else if (scenario.isRegressed()) {
                    result.add(REGRESSED);
                } else if (scenario.isAboutToRegress()) {
                    result.add(NEARLY_FAILED);
                } else if (scenario.isImproved()) {
                    result.add(IMPROVED);
                }

                if (result.isEmpty()) {
                    result.add(UNTAGGED);
                }
                return result;
            }

            @Override
            protected void renderScenarioButtons(int index, ScenarioBuildResultData scenario) {
                List<String> webUrls =
                    scenario.getRawData().isEmpty()
                        ? of(scenario.getWebUrl())
                        : scenario.getRawData().stream().map(ScenarioBuildResultData::getWebUrl).collect(toList());
                if (webUrls.size() == 1) {
                    a().target("_blank").classAttr("btn btn-primary btn-sm").href(webUrls.get(0)).text("Build").end();
                } else {
                    // @formatter:off
                    div().classAttr("dropdown").style("display: inline-block");
                        button().classAttr("btn btn-primary btn-sm dropdown-toggle").attr("data-toggle", "dropdown").text("Build").end();
                        div().classAttr("dropdown-menu");
                            for (int i = 0; i < webUrls.size(); ++i) {
                                a().target("_blank").classAttr("dropdown-item").href(webUrls.get(i)).text("Build " + (i + 1)).end();
                            }
                        end();
                    end();
                    // @formatter:on
                }
            }
        };
    }

    private static boolean isFlaky(ScenarioBuildResultData scenario) {
        if (scenario.getRawData().size() < 1) {
            return false;
        }

        Set<String> statuses = scenario.getRawData().stream().map(ScenarioBuildResultData::getStatus).collect(toSet());
        return statuses.size() > 1 && statuses.contains(STATUS_SUCCESS);
    }
}
