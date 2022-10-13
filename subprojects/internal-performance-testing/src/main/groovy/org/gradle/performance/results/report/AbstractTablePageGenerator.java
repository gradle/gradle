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

import org.gradle.performance.measure.DataSeries;
import org.gradle.performance.measure.Duration;
import org.gradle.performance.results.FormatSupport;
import org.gradle.performance.results.PerformanceReportScenario;
import org.gradle.performance.results.PerformanceReportScenarioHistoryExecution;
import org.gradle.performance.results.PerformanceTestExecutionResult;
import org.gradle.performance.results.PerformanceTestHistory;
import org.gradle.performance.results.ResultsStore;

import java.io.Writer;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static org.gradle.performance.results.report.Tag.FixedTag;

public abstract class AbstractTablePageGenerator extends HtmlPageGenerator<ResultsStore> {
    protected final PerformanceFlakinessDataProvider flakinessDataProvider;
    protected final PerformanceExecutionDataProvider executionDataProvider;

    public AbstractTablePageGenerator(PerformanceFlakinessDataProvider flakinessDataProvider, PerformanceExecutionDataProvider executionDataProvider) {
        this.flakinessDataProvider = flakinessDataProvider;
        this.executionDataProvider = executionDataProvider;
    }

    public static String getTeamCityWebUrlFromBuildId(String buildId) {
        return "https://builds.gradle.org/viewLog.html?buildId=" + buildId;
    }

    protected abstract class TableHtml extends MetricsHtml {
        AtomicInteger counter = new AtomicInteger();

        public TableHtml(Writer writer) {
            super(writer);
        }

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
                        title().text(getPageTitle());
                    end();
                    body();
                        div().id("accordion").classAttr("mx-auto");
                        renderTableHeader();
                        renderTable("Cross version scenarios", "Compare the performance of the same build on different code versions.", determineBaseline(), getCrossVersionScenarios());
                        renderTable("Cross build scenarios", "Compare the performance of different builds", Optional.empty(), getCrossBuildScenarios());
                        renderPopoverDiv();
                    end();
                footer(this);
                endAll();
            }

            protected abstract String getPageTitle();

            protected abstract String getTableTitle();

            protected abstract boolean renderFailureSelectButton();

            protected abstract List<PerformanceReportScenario> getCrossVersionScenarios();

            protected abstract List<PerformanceReportScenario> getCrossBuildScenarios();

            private void renderTableHeader() {
                div().classAttr("row alert alert-primary m-0");
                    div().classAttr("col p-0");
                        a().classAttr("btn btn-sm btn-outline-primary").attr("data-toggle", "tooltip").title("Go back to Performance Coordinator Build")
                            .href(getTeamCityWebUrlFromBuildId(System.getenv("BUILD_ID"))).target("_blank").text("<-").end();
                    end();
                    div().classAttr("col-5 p-0");
                        text(getTableTitle());
                        a().target("_blank").href("https://github.com/gradle/gradle/commits/" + executionDataProvider.getCommitId()).small().classAttr("text-muted").text(executionDataProvider.getCommitId()).end().end();
                    end();
                    div().classAttr("col-3 p-0");
                        if(renderFailureSelectButton()) {
                            button().id("failed-scenarios").classAttr("btn-sm btn-danger").text("Failed scenarios").end();
                            button().id("all-scenarios").classAttr("btn-sm btn-primary").text("All scenarios").end();
                        }
                    end();
                    div().classAttr("col text-right mt-1");
                        i().classAttr("fa fa-filter").attr("data-toggle", "popover", "data-placement", "bottom").title("Filter by tag").style("cursor: pointer").text(" ").end();
                    end();
                    div().classAttr("col p-0")
                        .attr("data-toggle", "tooltip")
                        .style("font-size: smaller")
                        .title("The difference between two series of execution data (usually baseline vs current Gradle), positive numbers indicate current Gradle is slower, and vice versa.")
                        .text("Difference");
                            i().classAttr("fa fa-info-circle").text(" ").end()
                    .end();
                    div().classAttr("col p-0")
                        .attr("data-toggle", "tooltip")
                        .style("font-size: smaller")
                        .title("The confidence with which these two data series are different. E.g. 90% means they're different with 90% confidence. Currently we fail the test if the confidence > 99.9%.")
                        .text("Confidence");
                            i().classAttr("fa fa-info-circle").text(" ").end()
                    .end();
                end();
            }

            private void renderPopoverDiv() {
                div().id("filter-popover").style("display: none");
                    Stream.of(FixedTag.values()).forEach(tag -> {
                        div().classAttr("form-check");
                            label().classAttr("form-check-label");
                                input().classAttr("form-check-input").type("checkbox").checked("true").value(tag.getName()).end();
                                if(tag.isValid()) {
                                    span().classAttr(tag.getClassAttr()).text(tag.getName()).end();
                                } else {
                                    span().text(tag.getName()).end();
                                }
                            end();
                        end();
                    });
                end();
            }

            private void renderTable(String title, String description, Optional<String> baselineVersion, List<PerformanceReportScenario> scenarios) {
                div().classAttr("row alert alert-primary m-0");
                    div().classAttr("col-12 p-0").text(title);
                        i().classAttr("fa fa-info-circle").attr("data-toggle", "tooltip").title(description).text(" ").end();
                        baselineVersion.ifPresent(version -> b().text("(vs " + version + ")").end());
                    end();
                end();
                scenarios.forEach(scenario -> renderScenario(counter.incrementAndGet(), scenario));
            }

            private String getTextColorCss(PerformanceReportScenario scenario, PerformanceReportScenarioHistoryExecution executionData) {
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

            private void renderScenario(int index, PerformanceReportScenario scenario) {
                Set<Tag> tags = determineTags(scenario);
                div().classAttr("card m-0 p-0 alert " + determineScenarioBackgroundColorCss(scenario)).attr("tag", tags.stream().map(Tag::getName).collect(joining(","))).id("scenario" + index);
                    div().id("heading" + index).classAttr("card-header");
                        div().classAttr("row align-items-center data-row").attr("scenario", String.valueOf(index));
                            div().classAttr("col").text(String.valueOf(index)).
                                a().attr("data-toggle", "tooltip").classAttr("section-sign").title("Click to copy url of this scenario to clipboard").href("#scenario" + index).style("opacity:0")
                                    .id("section-sign-" + index).text("§");
                                end();
                            end();
                            div().classAttr("col-6");
                                big().text(scenario.getName()).end();
                                tags.stream().filter(Tag::isValid).forEach(this::renderTag);
                            end();
                            div().classAttr("col-3");
                                renderScenarioButtons(index, scenario);
                                a().target("_blank").classAttr("btn btn-primary btn-sm").href("tests/" + urlEncode(PerformanceTestHistory.convertToId(scenario.getName()) + ".html")).text("Graph").end();
                                a().classAttr("btn btn-primary btn-sm collapsed").href("#").attr("data-toggle", "collapse", "data-target", "#collapse" + index).text("Detail").end();
                            end();
                            div().classAttr("col-2 p-0");
                                if(scenario.isBuildFailed()) {
                                    text("N/A");
                                } else {
                                    scenario.getCurrentExecutions().forEach(execution -> {
                                        div().classAttr("row p-0").style("font-size: smaller");
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
                                pre().text(scenario.getTeamCityExecutions().stream().map(PerformanceTestExecutionResult::getTestFailure).collect(joining("\n"))).end();
                            } else {
                                renderDetailsTable(scenario);
                            }
                        end();
                    end();
                end();
            }

            private void renderTag(Tag tag) {
                if (tag.getUrl() == null) {
                    span().classAttr(tag.getClassAttr()).attr("data-toggle", "tooltip").title(tag.getTitle()).text(tag.getName()).end();
                } else {
                    span().classAttr(tag.getClassAttr()).attr("data-toggle", "tooltip").title(tag.getTitle());
                    a().target("_blank").href(tag.getUrl()).text(tag.getName()).end();
                    end();
                }
            }

            protected abstract String determineScenarioBackgroundColorCss(PerformanceReportScenario scenario);

            protected abstract Set<Tag> determineTags(PerformanceReportScenario scenario);

            protected abstract void renderScenarioButtons(int index, PerformanceReportScenario scenario);

            private void renderDetailsTable(PerformanceReportScenario scenario) {
                table().classAttr("table table-condensed table-bordered table-striped");
                    tr();
                        th().text("Date").end();
                        th().text("Commit").end();
                        renderVersionHeader(scenario.getHistoryExecutions().isEmpty() ? "" : scenario.getHistoryExecutions().get(0).getBaseVersion().getName());
                        renderVersionHeader(scenario.getHistoryExecutions().isEmpty() ? "" : scenario.getHistoryExecutions().get(0).getCurrentVersion().getName());
                        th().text("Difference").end();
                        th().text("Confidence").end();
                    end();
                    scenario.getHistoryExecutions().forEach(execution -> {
                        tr();
                            DataSeries<Duration> baseVersion = execution.getBaseVersion().getTotalTime();
                            DataSeries<Duration> currentVersion = execution.getCurrentVersion().getTotalTime();
                            td().text(FormatSupport.timestamp(execution.getTime())).end();
                            td().a().target("_blank").href("https://github.com/gradle/gradle/commits/" + execution.getShortCommitId()).text(execution.getShortCommitId()).end().end();
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
                end();
            }
            // @formatter:on
    }

    private Optional<String> determineBaseline() {
        return executionDataProvider.getReportScenarios().stream()
            .filter(scenario -> !scenario.getCrossBuild())
            .findFirst()
            .filter(scenario -> !scenario.getHistoryExecutions().isEmpty())
            .map(scenario -> scenario.getHistoryExecutions().get(0).getBaseVersion().getName());
    }
}
