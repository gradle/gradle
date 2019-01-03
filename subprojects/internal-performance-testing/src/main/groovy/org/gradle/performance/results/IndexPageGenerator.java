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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

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
            .thenComparing(comparing(ScenarioBuildResultData::isBuildFailed).reversed())
            .thenComparing(comparing(ScenarioBuildResultData::isAboutToRegress).reversed())
            .thenComparing(comparing(ScenarioBuildResultData::getDifferenceSortKey).reversed())
            .thenComparing(comparing(ScenarioBuildResultData::getDifferencePercentage).reversed())
            .thenComparing(ScenarioBuildResultData::getScenarioName);
        return data.collect(() -> new TreeSet<>(comparator), TreeSet::add, TreeSet::addAll);
    }

    private ScenarioBuildResultData queryExecutionData(ScenarioBuildResultData scenario) {
        PerformanceTestHistory history = resultsStore.getTestResults(scenario.getScenarioName(), DEFAULT_RETRY_COUNT, PERFORMANCE_DATE_RETRIEVE_DAYS, ResultsStoreHelper.determineChannel());
        List<? extends PerformanceTestExecution> recentExecutions = history.getExecutions();
        List<? extends PerformanceTestExecution> currentBuildExecutions = recentExecutions.stream().filter(execution -> Objects.equals(execution.getTeamCityBuildId(), scenario.getTeamCityBuildId())).collect(toList());
        if (currentBuildExecutions.isEmpty()) {
            scenario.setRecentExecutions(recentExecutions.stream().map(this::extractExecutionData).filter(Objects::nonNull).collect(toList()));
        } else {
            scenario.setCurrentBuildExecutions(currentBuildExecutions.stream().map(this::extractExecutionData).filter(Objects::nonNull).collect(toList()));
        }

        scenario.setCrossBuild(history instanceof CrossBuildPerformanceTestHistory);

        return scenario;
    }

    private ScenarioBuildResultData.ExecutionData extractExecutionData(PerformanceTestExecution performanceTestExecution) {
        List<MeasuredOperationList> nonEmptyExecutions = performanceTestExecution
            .getScenarios()
            .stream()
            .filter(testExecution -> !testExecution.getTotalTime().isEmpty())
            .collect(toList());
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
            AtomicInteger counter = new AtomicInteger(0);
            // @formatter:off
            {
                html();
                    head();
                        metaTag(this);
                        link().rel("stylesheet").type("text/css").href("https://stackpath.bootstrapcdn.com/bootstrap/4.1.3/css/bootstrap.min.css").end();
                        link().rel("stylesheet").type("text/css").href("https://cdnjs.cloudflare.com/ajax/libs/font-awesome/4.7.0/css/font-awesome.min.css").end();
                        script().src("https://code.jquery.com/jquery-3.3.1.min.js").end();
                        script().src("https://stackpath.bootstrapcdn.com/bootstrap/4.1.3/js/bootstrap.bundle.min.js").end();
                        script().src("js/performanceReport.js").end();
                        title().text("Profile report for channel " + ResultsStoreHelper.determineChannel()).end();
                    end();
                    body();
                        div().id("acoordion").classAttr("mx-auto");
                        renderHeader();
                        renderTable("Cross version scenarios", "Compare the performance of the same build on different code versions.", ScenarioBuildResultData::isCrossVersion);
                        renderTable("Cross build scenarios", "Compare the performance of different builds", ScenarioBuildResultData::isCrossBuild);
                        renderPopoverDiv();
                    end();
                footer(this);
                endAll();
            }

            private void renderHeader() {
                long successCount = scenarios.stream().filter(ScenarioBuildResultData::isSuccessful).count();
                long failureCount = scenarios.size() - successCount;
                div().classAttr("row alert alert-primary m-0");
                    div().classAttr("col p-0");
                        a().classAttr("btn btn-sm btn-outline-primary").attr("data-toggle", "tooltip").title("Go back to Performance Coordinator Build")
                            .href("https://builds.gradle.org/viewLog.html?buildId=" + System.getenv("BUILD_ID")).target("_blank").text("<-").end();
                    end();
                    div().classAttr("col-6 p-0");
                        text("Scenarios (" + successCount + " successful");
                        if (failureCount > 0) {
                            text(", " + failureCount + " failed");
                        }
                        text(")");
                        a().target("_blank").href("https://github.com/gradle/gradle/commits/"+ commitId).small().classAttr("text-muted").text(commitId).end().end();
                    end();
                    div().classAttr("col-2 p-0");
                        if(failureCount > 0) {
                            button().id("failed-scenarios").classAttr("btn-sm btn-danger").text("Failed scenarios").end();
                            button().id("all-scenarios").classAttr("btn-sm btn-primary").text("All scenarios").end();
                        }
                    end();
                    div().classAttr("col text-right mt-1");
                        i().classAttr("fa fa-filter").attr("data-toggle", "popover", "data-placement", "bottom").title("Filter by tag").style("cursor: pointer").text(" ").end();
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

            private void renderPopoverDiv() {
                div().id("filter-popover").style("display: none");
                    Stream.of(Tag.values()).forEach(tag -> {
                        div().classAttr("form-check");
                            label().classAttr("form-check-label");
                                input().classAttr("form-check-input").type("checkbox").checked("true").value(tag.name).end();
                                if(tag.isValid()) {
                                    span().classAttr(tag.classAttr).text(tag.name).end();
                                } else {
                                    span().text(tag.name).end();
                                }
                            end();
                        end();
                    });
                end();
            }

            private void renderTable(String title, String description, Predicate<ScenarioBuildResultData> predicate) {
                div().classAttr("row alert alert-primary m-0");
                    div().classAttr("col-12 p-0").text(title);
                i().classAttr("fa fa-info-circle").attr("data-toggle", "tooltip").title(description).text(" ").end();
                    end();
                end();
                scenarios.stream().filter(predicate).forEach(scenario -> renderScenario(counter.incrementAndGet(), scenario));
            }

            private String determineScenarioBackgroundColorCss(ScenarioBuildResultData scenario) {
                if(scenario.isUnknown()) {
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

            private String getTextColorCss(ScenarioBuildResultData scenario, ScenarioBuildResultData.ExecutionData executionData) {
                if(scenario.isCrossBuild()) {
                    return "text-dark";
                }

                if (executionData.confidentToSayBetter()) {
                    return "text-success";
                } else if (executionData.confidentToSayWorse()) {
                    return "text-danger";
                } else {
                    return "text-dark";
                }
            }

            private void renderScenario(int index, ScenarioBuildResultData scenario) {
                Set<Tag> tags = Tag.determineTags(scenario);
                div().classAttr("card m-0 p-0 alert " + determineScenarioBackgroundColorCss(scenario)).attr("tag", tags.stream().map(Tag::getName).collect(joining(","))).id("scenario" + index);
                    div().id("heading" + index).classAttr("card-header");
                        div().classAttr("row align-items-center data-row").attr("scenario", String.valueOf(index));
                            div().classAttr("col").text(String.valueOf(index)).
                                a().attr("data-toggle", "tooltip").classAttr("section-sign").title("Click to copy url of this scenario to clipboard").href("#scenario" + index).style("opacity:0")
                                    .id("section-sign-" + index).text("§");
                                end();
                            end();
                            div().classAttr("col-7");
                                big().text(scenario.getScenarioName()).end();
                                tags.stream().filter(Tag::isValid).forEach(tag -> span().classAttr(tag.classAttr).attr("data-toggle", "tooltip").title(tag.title).text(tag.name).end());
                            end();
                            div().classAttr("col-2");
                                a().target("_blank").classAttr("btn btn-primary btn-sm").href(scenario.getWebUrl()).text("Build").end();
                                a().target("_blank").classAttr("btn btn-primary btn-sm").href("tests/" + urlEncode(PerformanceTestHistory.convertToId(scenario.getScenarioName()) + ".html")).text("Graph").end();
                                a().classAttr("btn btn-primary btn-sm collapsed").href("#").attr("data-toggle", "collapse", "data-target", "#collapse" + index).text("Detail ▼").end();
                            end();
                            div().classAttr("col-2 p-0");
                                if(scenario.isBuildFailed()) {
                                    text("N/A");
                                } else {
                                    scenario.getExecutionsToDisplayInRow().forEach(execution -> {
                                        div().classAttr("row p-0");
                                            div().classAttr("p-0 col " + getTextColorCss(scenario, execution)).text(execution.getDifferenceDisplay()).end();
                                            div().classAttr("p-0 col " + getTextColorCss(scenario, execution)).text(execution.getFormattedConfidence()).end();
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
                        renderVersionHeader(scenario.getExecutions().isEmpty() ? "" : scenario.getExecutions().get(0).getBaseVersion().getName());
                        renderVersionHeader(scenario.getExecutions().isEmpty() ? "" : scenario.getExecutions().get(0).getCurrentVersion().getName());
                        th().text("Difference").end();
                        th().text("Confidence").end();
                    end();
                    scenario.getExecutions().forEach(execution -> {
                        tr();
                            DataSeries<Duration> baseVersion = execution.getBaseVersion().getTotalTime();
                            DataSeries<Duration> currentVersion = execution.getCurrentVersion().getTotalTime();
                            td().text(FormatSupport.timestamp(execution.getTime())).end();
                            td().a().target("_blank").href("https://github.com/gradle/gradle/commits/" + execution.getCommitId()).text(execution.getCommitId()).end().end();
                            td().classAttr(baseVersion.getMedian().compareTo(currentVersion.getMedian()) < 0 ? "text-success" : "text-danger").text(baseVersion.getMedian().format()).end();
                            td().classAttr("text-muted").text("se: " + baseVersion.getStandardError().format()).end();
                            td().classAttr(baseVersion.getMedian().compareTo(currentVersion.getMedian()) >= 0 ? "text-success" : "text-danger").text(currentVersion.getMedian().format()).end();
                            td().classAttr("text-muted").text("se: " + currentVersion.getStandardError().format()).end();
                            td().classAttr(getTextColorCss(scenario, execution)).text(execution.getFormattedDifferencePercentage()).end();
                            td().classAttr(getTextColorCss(scenario, execution)).text(execution.getFormattedConfidence()).end();
                        end();
                });
                end();
            }

            private void renderVersionHeader(String version) {
                th();
                    colspan("2").text(version);
                    if (version.contains("-commit-")) {
                        i().classAttr("fa fa-info-circle").attr("data-toggle", "tooltip").title("The test is executed against the commit where your branch forks from master.").text(" ").end();
                    }
                end();
            }
            // @formatter:on
        };
    }

    private enum Tag {
        FROM_CACHE("FROM-CACHE", "badge badge-info", "The test is not really executed - its results are fetched from build cache."),
        FAILED("FAILED", "badge badge-danger", "Regression confidence > 99% despite retries."),
        NEARLY_FAILED("NEARLY-FAILED", "badge badge-warning", "Regression confidence > 90%, we're going to fail soon."),
        REGRESSED("REGRESSED", "badge badge-danger", "Regression confidence > 99% despite retries."),
        IMPROVED("IMPROVED", "badge badge-success", "Improvement confidence > 90%, rebaseline it to keep this improvement! :-)"),
        UNKNOWN("UNKNOWN", "badge badge-dark", "The status is unknown, may be it's cancelled?"),
        UNTAGGED("UNTAGGED", null, null);

        private String name;
        private String classAttr;
        private String title;

        Tag(String name, String classAttr, String title) {
            this.name = name;
            this.classAttr = classAttr;
            this.title = title;
        }

        private boolean isValid() {
            return this != UNTAGGED;
        }

        private String getName() {
            return name;
        }

        private static Set<Tag> determineTags(ScenarioBuildResultData scenario) {
            Set<Tag> result = new HashSet<>();
            if (scenario.isFromCache()) {
                result.add(FROM_CACHE);
            }
            if(scenario.isUnknown()) {
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
    }
}
