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

import org.gradle.performance.results.PerformanceExperiment;
import org.gradle.performance.results.PerformanceReportScenario;
import org.gradle.performance.results.PerformanceTestExecutionResult;
import org.gradle.performance.results.ResultsStore;
import org.gradle.performance.results.ResultsStoreHelper;

import java.io.Writer;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static org.gradle.performance.results.report.Tag.FixedTag.FAILED;
import static org.gradle.performance.results.report.Tag.FixedTag.FROM_CACHE;
import static org.gradle.performance.results.report.Tag.FixedTag.IMPROVED;
import static org.gradle.performance.results.report.Tag.FixedTag.NEARLY_FAILED;
import static org.gradle.performance.results.report.Tag.FixedTag.REGRESSED;
import static org.gradle.performance.results.report.Tag.FixedTag.UNKNOWN;
import static org.gradle.performance.results.report.Tag.FixedTag.UNTAGGED;

public class IndexPageGenerator extends AbstractTablePageGenerator {
    public IndexPageGenerator(PerformanceFlakinessDataProvider flakinessDataProvider, PerformanceExecutionDataProvider executionDataProvider) {
        super(flakinessDataProvider, executionDataProvider);
    }

    @Override
    public void render(final ResultsStore store, Writer writer) {
        long successCount = executionDataProvider.getReportScenarios().stream().filter(PerformanceReportScenario::isSuccessful).count();
        long smallRegressions = executionDataProvider.getReportScenarios().stream()
            .filter(PerformanceReportScenario::isRegressed)
            .filter(scenario -> !failsBuild(scenario))
            .count();
        long failureCount = executionDataProvider.getReportScenarios().size() - successCount - smallRegressions;

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
            protected List<PerformanceReportScenario> getCrossVersionScenarios() {
                return executionDataProvider.getReportScenarios().stream().filter(PerformanceReportScenario::isCrossVersion).collect(toList());
            }

            @Override
            protected List<PerformanceReportScenario> getCrossBuildScenarios() {
                return executionDataProvider.getReportScenarios().stream().filter(PerformanceReportScenario::isCrossBuild).collect(toList());
            }

            @Override
            protected String determineScenarioBackgroundColorCss(PerformanceReportScenario scenario) {
                if (scenario.isUnknown()) {
                    return "alert-dark";
                } else if (!scenario.isSuccessful()) {
                    return failsBuild(scenario) ? "alert-danger" : "alert-warning";
                } else if (scenario.isAboutToRegress()) {
                    return "alert-warning";
                } else if (scenario.isImproved()) {
                    return "alert-success";
                } else {
                    return "alert-info";
                }
            }

            @Override
            protected Set<Tag> determineTags(PerformanceReportScenario scenario) {
                Set<Tag> result = new HashSet<>();
                if (scenario.isFromCache()) {
                    result.add(FROM_CACHE);
                }

                markFlakyTestInfo(scenario, result);

                if (scenario.isUnknown()) {
                    result.add(UNKNOWN);
                } else if (scenario.isBuildFailed()) {
                    result.add(FAILED);
                } else if (scenario.isRegressed()) {
                    result.add(failsBuild(scenario) ? REGRESSED : NEARLY_FAILED);
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

            private void markFlakyTestInfo(PerformanceReportScenario scenario, Set<Tag> result) {
                PerformanceExperiment experiment = scenario.getPerformanceExperiment();
                BigDecimal rate = flakinessDataProvider.getFlakinessRate(experiment);
                if (rate != null && rate.doubleValue() > PerformanceFlakinessDataProvider.FLAKY_THRESHOLD) {
                    result.add(Tag.FlakinessInfoTag.createFlakinessRateTag(rate));
                    BigDecimal failureThreshold = flakinessDataProvider.getFailureThreshold(experiment);
                    result.add(Tag.FlakinessInfoTag.createFailureThresholdTag(failureThreshold));
                }
            }

            @Override
            protected void renderScenarioButtons(int index, PerformanceReportScenario scenario) {
                List<String> webUrls = scenario.getTeamCityExecutions().stream().map(PerformanceTestExecutionResult::getWebUrl).collect(toList());
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

    private boolean failsBuild(PerformanceReportScenario scenario) {
        return scenario.getCurrentExecutions().stream()
            .map(execution -> flakinessDataProvider.getScenarioRegressionResult(scenario.getPerformanceExperiment(), execution))
            .allMatch(PerformanceFlakinessDataProvider.ScenarioRegressionResult::isFailsBuild);
    }
}
