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
import org.gradle.performance.measure.DataSeries;
import org.gradle.performance.measure.Duration;
import org.gradle.performance.util.Git;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;

public class IndexPageGenerator extends HtmlPageGenerator<ResultsStore> {
    public static final int ENOUGH_REGRESSION_CONFIDENCE_THRESHOLD = 90;
    private static final int DEFAULT_RETRY_COUNT = 3;
    private static final int PERFORMANCE_DATE_RETRIEVE_DAYS = 2;
    private final Set<ScenarioBuildResultData> scenarios;
    private final ResultsStore resultsStore;
    private final String commitId = Git.current().getCommitId();

    public IndexPageGenerator(ResultsStore resultsStore, File resultJson) {
        this.resultsStore = resultsStore;
        this.scenarios = readBuildResultData(resultJson);
    }

    private Set<ScenarioBuildResultData> readBuildResultData(File resultJson) {
        try {
            List<ScenarioBuildResultData> list = new ObjectMapper().readValue(resultJson, new TypeReference<List<ScenarioBuildResultData>>() { });
            return sortBuildResultData(list.stream().map(this::queryExecutionData));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @VisibleForTesting
    Set<ScenarioBuildResultData> sortBuildResultData(Stream<ScenarioBuildResultData> data) {
        Comparator<ScenarioBuildResultData> comparator = comparing(ScenarioBuildResultData::isBuildFailed).reversed()
            .thenComparing(ScenarioBuildResultData::isSuccessful)
            .thenComparing(comparing(ScenarioBuildResultData::isAboutToRegress).reversed())
            .thenComparing(comparing(ScenarioBuildResultData::getDifferenceSortKey).reversed())
            .thenComparing(comparing(ScenarioBuildResultData::getDifferencePercentage).reversed())
            .thenComparing(ScenarioBuildResultData::getScenarioName);
        return data.collect(() -> new TreeSet<>(comparator), TreeSet::add, TreeSet::addAll);
    }

    private ScenarioBuildResultData queryExecutionData(ScenarioBuildResultData scenario) {
        PerformanceTestHistory history = resultsStore.getTestResults(scenario.getScenarioName(), DEFAULT_RETRY_COUNT, PERFORMANCE_DATE_RETRIEVE_DAYS, ResultsStoreHelper.determineChannel());
        List<? extends PerformanceTestExecution> recentExecutions = history.getExecutions();
        List<? extends PerformanceTestExecution> currentCommitExecutions = recentExecutions.stream().filter(execution -> execution.getVcsCommits().contains(commitId)).collect(Collectors.toList());
        if (currentCommitExecutions.isEmpty()) {
            scenario.setRecentExecutions(recentExecutions.stream().map(this::extractExecutionData).filter(Objects::nonNull).collect(Collectors.toList()));
        } else {
            scenario.setCurrentCommitExecutions(currentCommitExecutions.stream().map(this::extractExecutionData).filter(Objects::nonNull).collect(Collectors.toList()));
        }

        return scenario;
    }

    private ScenarioBuildResultData.ExecutionData extractExecutionData(PerformanceTestExecution performanceTestExecution) {
        List<MeasuredOperationList> nonEmptyExecutions = performanceTestExecution
            .getScenarios()
            .stream()
            .filter(testExecution -> !testExecution.getTotalTime().isEmpty())
            .collect(Collectors.toList());
        if (nonEmptyExecutions.size() > 1) {
            int size = nonEmptyExecutions.size();
            return new ScenarioBuildResultData.ExecutionData(performanceTestExecution.getStartTime(), getCommit(performanceTestExecution), nonEmptyExecutions.get(size - 2), nonEmptyExecutions.get(size - 1));
        } else {
            return null;
        }
    }

    private String getCommit(PerformanceTestExecution execution) {
        if (execution.getVcsCommits().isEmpty()) {
            return "";
        } else {
            String commit = execution.getVcsCommits().get(0);
            return commit.substring(0, Math.min(7, commit.length()));
        }
    }

    @Override
    public void render(final ResultsStore store, Writer writer) throws IOException {
        new MetricsHtml(writer) {
            // @formatter:off
            {
                html();
                    head();
                        metaTag(this);
                        link().rel("stylesheet").type("text/css").href("https://stackpath.bootstrapcdn.com/bootstrap/4.1.3/css/bootstrap.min.css").end();
                        link().rel("stylesheet").type("text/css").href("https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css").end();
                        script().src("https://code.jquery.com/jquery-3.3.1.min.js").end();
                        script().src("https://stackpath.bootstrapcdn.com/bootstrap/4.1.3/js/bootstrap.bundle.min.js").end();
                        script().src("js/anchorControl.js").end();
                        title().text("Profile report for channel " + ResultsStoreHelper.determineChannel()).end();
                    end();
                    body();
                        div().id("acoordion").classAttr("mx-auto");
                        renderHeader();
                        renderTable();
                    end();
                footer(this);
                endAll();
            }

            private void renderHeader() {
                long successCount = scenarios.stream().filter(ScenarioBuildResultData::isSuccessful).count();
                long failureCount = scenarios.size() - successCount;
                div().classAttr("row alert alert-primary m-0");
                    div().classAttr("col p-0");
                        a().classAttr("btn btn-sm btn-outline-primary").attr("data-toggle", "tooltip").title("Go back to Perfomrance Coordinator Build")
                            .href("https://builds.gradle.org/viewLog.html?buildId=" + System.getenv("BUILD_ID")).target("_blank").text("<-").end();
                    end();
                    div().classAttr("col-9 p-0");
                        text("Scenarios (" + successCount + " successful");
                        if (failureCount > 0) {
                            text(", " + failureCount + " failed");
                        }
                        text(")");
                        a().target("_blank").href("https://github.com/gradle/gradle/commits/"+ commitId).small().classAttr("text-muted").text(commitId).end().end();
                    end();
                    div().classAttr("col p-0")
                        .attr("data-toggle", "tooltip")
                        .title("The difference between two series of execution data (usually baseline vs current Gradle), positive numbers indicate current Gradle is slower, and vice versa.")
                        .text("Difference");
                            i().classAttr("fa fa-info-circle").text(" ").end()
                    .end();
                    div().classAttr("col p-0")
                        .attr("data-toggle", "tooltip")
                        .title("The confidence with which these two data series are different. E.g. 90% means they're different with 90% confidence. Currently we fail the test if the confidence > 99%.")
                        .text("Confidence");
                            i().classAttr("fa fa-info-circle").text(" ").end()
                    .end();
                end();
            }

            private void renderTable() {
                AtomicInteger index = new AtomicInteger(0);
                scenarios.forEach(scenario -> renderScenario(index.incrementAndGet(), scenario));
            }

            private String determineScenarioBackgroundColorCss(ScenarioBuildResultData scenario) {
                if (!scenario.isSuccessful()) {
                    return "alert-danger";
                } else if (scenario.isAboutToRegress()) {
                    return "alert-warning";
                } else if (scenario.isImproved()) {
                    return "alert-success";
                } else {
                    return "alert-info";
                }
            }

            private String getTextColorCss(ScenarioBuildResultData.ExecutionData executionData) {
                if (executionData.confidentToSayBetter()) {
                    return "text-success";
                } else if (executionData.confidentToSayWorse()) {
                    return "text-danger";
                } else {
                    return "text-dark";
                }
            }

            private void renderScenario(int index, ScenarioBuildResultData scenario) {
                div().classAttr("card m-0 p-0 alert " + determineScenarioBackgroundColorCss(scenario)).id("scenario" + index);
                    div().id("heading" + index).classAttr("card-header");
                        div().classAttr("row align-items-center data-row").attr("scenario", String.valueOf(index));
                            div().classAttr("col").text(String.valueOf(index)).
                                a().attr("data-toggle", "tooltip").classAttr("section-sign").title("Click to copy url of this scenario to clipboard").href("#scenario" + index).style("display:none")
                                    .id("section-sign-" + index).text("§");
                                end();
                            end();
                            div().classAttr("col-7");
                                big().text(scenario.getScenarioName()).end();
                                if(scenario.isFromCache()) {
                                    span().classAttr("badge badge-info").attr("data-toggle", "tooltip").title("The test is not really executed - its results are fetched from build cache.").text("FROM-CACHE").end();
                                }
                                if(scenario.isBuildFailed()) {
                                    span().classAttr("badge badge-danger").attr("data-toggle", "tooltip").title("The build failed and doesn't generate any execution data.").text("FAILED").end();
                                } else if(!scenario.isSuccessful()) {
                                    span().classAttr("badge badge-danger").attr("data-toggle", "tooltip").title("Regression confidence > 99% despite retries.").text("REGRESSED").end();
                                } else if(scenario.isAboutToRegress()) {
                                    span().classAttr("badge badge-warning").attr("data-toggle", "tooltip").title("Regression confidence > 90%, we're going to fail soon.").text("NEARLY-FAILED").end();
                                } else if(scenario.isImproved()) {
                                    span().classAttr("badge badge-success").attr("data-toggle", "tooltip").title("Improvement confidence > 90%, rebaseline it to keep this improvement! :-)").text("IMPROVED").end();
                                }
                            end();
                            div().classAttr("col-2");
                                a().target("_blank").classAttr("btn btn-primary btn-sm").href(scenario.getWebUrl()).text("Build").end();
                                a().target("_blank").classAttr("btn btn-primary btn-sm").href("tests/" + urlEncode(scenario.getScenarioName().replaceAll("\\s+", "-") + ".html")).text("Graph").end();
                                a().classAttr("btn btn-primary btn-sm collapsed").href("#").attr("data-toggle", "collapse", "data-target", "#collapse" + index).text("Detail ▼").end();
                            end();
                            div().classAttr("col-2 p-0");
                                if(scenario.isBuildFailed()) {
                                    text("N/A");
                                } else {
                                    scenario.getExecutionsToDisplayInRow().forEach(execution -> {
                                        div().classAttr("row p-0");
                                            div().classAttr("p-0 col " + getTextColorCss(execution)).text(execution.getDifferenceDisplay()).end();
                                            div().classAttr("p-0 col " + getTextColorCss(execution)).text(execution.getFormattedConfidence()).end();
                                        end();
                                    });
                                }
                            end();
                        end();
                    end();

                    div().id("collapse" + index).classAttr("collapse");
                        div().classAttr("card-body");
                            if(scenario.isBuildFailed()) {
                                pre().text(scenario.getTestFailure()).end();
                            } else {
                                renderDetailsTable(scenario);
                            }
                        end();
                    end();
                end();
            }

            private void renderDetailsTable(ScenarioBuildResultData scenario) {
                table().classAttr("table table-condensed table-bordered table-striped");
                    tr();
                        th().text("Date").end();
                        th().text("Commit").end();
                        th().colspan("2").text(scenario.getExecutions().isEmpty() ? "" : scenario.getExecutions().get(0).getBaseVersion().getName()).end();
                        th().colspan("2").text(scenario.getExecutions().isEmpty() ? "" : scenario.getExecutions().get(0).getCurrentVersion().getName()).end();
                        th().text("Difference").end();
                        th().text("Confidence").end();
                    end();
                    scenario.getExecutions().forEach(execution -> {
                        tr();
                            DataSeries<Duration> baseVersion = execution.getBaseVersion().getTotalTime();
                            DataSeries<Duration> currentVersion = execution.getCurrentVersion().getTotalTime();
                            td().text(format.timestamp(execution.getTime())).end();
                            td().a().target("_blank").href("https://github.com/gradle/gradle/commits/" + execution.getCommitId()).text(execution.getCommitId()).end().end();
                            td().classAttr(baseVersion.getMedian().compareTo(currentVersion.getMedian()) < 0 ? "text-success" : "text-danger").text(baseVersion.getMedian().format()).end();
                            td().classAttr("text-muted").text("se: " + baseVersion.getStandardError().format()).end();
                            td().classAttr(baseVersion.getMedian().compareTo(currentVersion.getMedian()) >= 0 ? "text-success" : "text-danger").text(currentVersion.getMedian().format()).end();
                            td().classAttr("text-muted").text("se: " + currentVersion.getStandardError().format()).end();
                            td().classAttr(getTextColorCss(execution)).text(execution.getFormattedDifferencePercentage()).end();
                            td().classAttr(getTextColorCss(execution)).text(execution.getFormattedConfidence()).end();
                        end();
                });
                end();
            }
            // @formatter:on
        };
    }
}
