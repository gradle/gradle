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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import org.gradle.performance.util.Git;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingDouble;

public class IndexPageGenerator extends HtmlPageGenerator<ResultsStore> {
    private final Set<ScenarioBuildResultData> scenarios;
    private final String gitCommitId;

    public IndexPageGenerator(File resultJson) {
        this.scenarios = readBuildResultData(resultJson);
        this.gitCommitId = Git.current().getCommitId();
    }

    @VisibleForTesting
    Set<ScenarioBuildResultData> readBuildResultData(File resultJson) {
        try {
            Comparator<ScenarioBuildResultData> comparator = comparing(ScenarioBuildResultData::isSuccessful)
                .thenComparing(ScenarioBuildResultData::isRegressed)
                .thenComparing(comparingDouble(ScenarioBuildResultData::getRegressionPercentage).reversed())
                .thenComparing(ScenarioBuildResultData::getScenarioName);

            List<ScenarioBuildResultData> list = new ObjectMapper().readValue(resultJson, new TypeReference<List<ScenarioBuildResultData>>() {
            });
            return list.stream().collect(() -> new TreeSet<>(comparator), TreeSet::add, TreeSet::addAll);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void render(final ResultsStore store, Writer writer) throws IOException {
        new MetricsHtml(writer) {
            // @formatter:off
            {
                html();
                    head();
                        headSection(this);
                        link().rel("stylesheet").type("text/css").href("https://maxcdn.bootstrapcdn.com/bootstrap/3.3.7/css/bootstrap.min.css").end();
                        title().text("Profile report for channel " + ResultsStoreHelper.determineChannel()).end();
                    end();
                    body();
                        div().id("content");
                        renderSummary();
                        renderTable();
                    end();
                footer(this);
                endAll();
            }

            private void renderTable() {
                table().classAttr("table table-condensed table-striped table-bordered");
                    renderHeaders();
                    scenarios.forEach(this::renderScenario);
                end();
            }

            private void renderHeaders() {
                tr();
                    th().text("Scenario").end();
                    th().colspan("2").text("Control Group").end();
                    th().colspan("2").text("Experiment Group").end();
                    th().colspan("2").text("Regression").end();
                end();
            }

            private String determineScenarioCss(ScenarioBuildResultData scenario) {
                if(scenario.isSuccessful()) {
                    return "success";
                } else if(scenario.isRegressed()) {
                    return "warning";
                } else {
                    return "danger";
                }
            }

            private void renderScenario(ScenarioBuildResultData scenario) {
                if (scenario.getExperimentData().isEmpty()) {
                    renderScenario(scenario, 0);
                } else {
                    for (int i = 0; i < scenario.getExperimentData().size(); i++) {
                        renderScenario(scenario, i);
                    }
                }
            }

            private void renderScenario(ScenarioBuildResultData scenario, int experimentIndex) {
                tr().classAttr(determineScenarioCss(scenario));

                if (experimentIndex == 0) {
                    td().rowspan(scenario.getExperimentData().isEmpty() ? "1" : "" + scenario.getExperimentData().size());
                        a().href(scenario.getWebUrl()).classAttr("label label-" + determineScenarioCss(scenario));
                            big().u().text(scenario.getScenarioName()).end().end();
                        end();
                        a().href("tests/" + urlEncode(scenario.getScenarioName().replaceAll("\\s+", "-")) + ".html").classAttr("label label-info");
                            big().u().text("details...").end().end();
                        end();
                    if (!gitCommitId.equals(scenario.getGitCommitId(experimentIndex)) && !"N/A".equals(scenario.getBuildId(experimentIndex))) {
                        a().href("https://builds.gradle.org/viewLog.html?buildId=" + scenario.getBuildId(experimentIndex)).classAttr("label label-info");
                            big().u().text("original build").end().end();
                        end();
                    }
                    end();
                }

                    td();
                        strong().text(scenario.getControlGroupName(experimentIndex)).end();
                    end();

                    td();
                        span().classAttr(scenario.getRegressionPercentage() > 0 ? "text-success" : "text-danger").text(scenario.getControlGroupMedian(experimentIndex));
                            small().classAttr("text-muted").text("se: " + scenario.getControlGroupStandardError(experimentIndex)).end();
                        end();
                    end();

                    td();
                        strong().text(scenario.getExperimentGroupName(experimentIndex)).end();
                    end();

                    td();
                        span().classAttr(scenario.getRegressionPercentage() <= 0 ? "text-success" : "text-danger").text(scenario.getExperimentGroupMedian(experimentIndex));
                            small().classAttr("text-muted").text("se: " + scenario.getExperimentGroupStandardError(experimentIndex)).end();
                        end();
                    end();

                    td();
                        span().classAttr(scenario.getRegressionPercentage() <= 0 ? "text-success": "text-danger").text(scenario.getFormattedRegression(experimentIndex));
                            small().classAttr("text-muted").text("conf: " + scenario.getConfidence(experimentIndex)).end();
                        end();
                    end();
                end();
            }
            // @formatter:on

            private void renderSummary() {
                long successCount = scenarios.stream().filter(ScenarioBuildResultData::isSuccessful).count();
                long regressedCount = scenarios.stream().filter(ScenarioBuildResultData::isRegressed).count();
                long failureCount = scenarios.size() - successCount - regressedCount;
                h3().text("" + successCount + " successful scenarios");
                if (regressedCount > 0) {
                    text(", " + regressedCount + " regressed scenarios");
                }
                if (failureCount > 0) {
                    text(", " + failureCount + " failed scenarios");
                }
                end();
            }
        };
    }
}
