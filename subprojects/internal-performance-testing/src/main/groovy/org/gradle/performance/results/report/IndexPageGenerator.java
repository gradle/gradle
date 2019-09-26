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

import org.gradle.performance.results.ResultsStore;
import org.gradle.performance.results.ResultsStoreHelper;
import org.gradle.performance.results.ScenarioBuildResultData;

import java.io.Writer;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.google.common.collect.ImmutableList.of;
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
        long successCount = executionDataProvider.getScenarioExecutionData().stream().filter(ScenarioBuildResultData::isSuccessful).count();
        long smallRegressions = executionDataProvider.getScenarioExecutionData().stream()
            .filter(ScenarioBuildResultData::isRegressed)
            .filter(this::failsBuild)
            .count();
        long failureCount = executionDataProvider.getScenarioExecutionData().size() - successCount - smallRegressions;

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
                return executionDataProvider.getScenarioExecutionData().stream().filter(ScenarioBuildResultData::isCrossVersion).collect(toList());
            }

            @Override
            protected List<ScenarioBuildResultData> getCrossBuildScenarios() {
                return executionDataProvider.getScenarioExecutionData().stream().filter(ScenarioBuildResultData::isCrossBuild).collect(toList());
            }

            @Override
            protected String determineScenarioBackgroundColorCss(ScenarioBuildResultData scenario) {
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
            protected Set<Tag> determineTags(ScenarioBuildResultData scenario) {
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

            private void markFlakyTestInfo(ScenarioBuildResultData scenario, Set<Tag> result) {
                BigDecimal rate = flakinessDataProvider.getFlakinessRate(scenario.getScenarioName());
                if (rate != null && rate.doubleValue() > PerformanceFlakinessDataProvider.FLAKY_THRESHOLD) {
                    result.add(Tag.FlakinessInfoTag.createFlakinessRateTag(rate));
                    BigDecimal failureThreshold = flakinessDataProvider.getFailureThreshold(scenario.getScenarioName());
                    result.add(Tag.FlakinessInfoTag.createFailureThresholdTag(failureThreshold));
                }
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

    private boolean failsBuild(ScenarioBuildResultData scenario) {
        return scenario.getRawData().stream()
            .filter(ScenarioBuildResultData::isRegressed)
            .map(flakinessDataProvider::getScenarioRegressionResult)
            .allMatch(PerformanceFlakinessDataProvider.ScenarioRegressionResult::isFailsBuild);
    }

}
