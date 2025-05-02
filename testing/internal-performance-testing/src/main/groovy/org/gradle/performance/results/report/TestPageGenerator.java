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

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.googlecode.jatl.Html;
import groovy.json.JsonOutput;
import org.apache.commons.lang.StringUtils;
import org.gradle.performance.results.CrossVersionPerformanceTestHistory;
import org.gradle.performance.results.FormatSupport;
import org.gradle.performance.results.PerformanceScenario;
import org.gradle.performance.results.PerformanceTestExecution;
import org.gradle.performance.results.PerformanceTestHistory;
import org.gradle.performance.results.ScenarioDefinition;
import org.gradle.performance.util.Git;

import javax.annotation.Nullable;
import java.io.Writer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.gradle.performance.results.report.AbstractTablePageGenerator.getTeamCityWebUrlFromBuildId;

public class TestPageGenerator extends HtmlPageGenerator<PerformanceTestHistory> implements PerformanceExecutionGraphRenderer {
    private final String projectName;

    public TestPageGenerator(String projectName) {
        this.projectName = projectName;
    }

    @Override
    protected int getDepth() {
        return 1;
    }

    @Override
    public void render(final PerformanceTestHistory testHistory, Writer writer) {
        // @formatter:off
        new MetricsHtml(writer) {{
            html();
            head();
            headSection(this);
            title().text(String.format("Performance test %s", testHistory.getDisplayName())).end();
            end();
            body();
            div().id("content");
            h2().text(String.format("Test: %s", testHistory.getDisplayName())).end();
            text(getReproductionInstructions(testHistory));
            p().text("Tasks: " + getTasks(testHistory)).end();
            p().text("Clean tasks: " + getCleanTasks(testHistory)).end();

            div().id("tooltip").end();

            addPerformanceGraphs();

            h3().text("Test details").end();
            table().classAttr("test-details");
            tr();
                th().text("Scenario").end();
                th().text("Test project").end();
                th().text("Tasks").end();
                th().text("Clean tasks").end();
                th().text("Gradle args").end();
                th().text("Gradle JVM args").end();
                th().text("Daemon").end();
            end();
            for (ScenarioDefinition scenario : testHistory.getScenarios()) {
                tr();
                    textCell(scenario.getDisplayName());
                    textCell(scenario.getTestProject());
                    textCell(scenario.getTasks());
                    textCell(scenario.getCleanTasks());
                    textCell(scenario.getArgs());
                    textCell(scenario.getGradleOpts());
                    textCell(scenario.getDaemon());
                end();
            }
            end();

            h3().text("Test history").end();
            table().classAttr("history");
            tr().classAttr("control-groups");
            th().colspan("3").end();
            final String colspanForField = String.valueOf(testHistory.getScenarioCount() * getColumnsForSamples());
            th().colspan(colspanForField).text("Average build time").end();
            th().colspan("8").text("Details").end();
            end();
            tr();
            th().text("Date").end();
            th().text("Branch").end();
            th().text("Git commit").end();
            for (String label : testHistory.getScenarioLabels()) {
                renderHeaderForSamples(label);
            }
            th().text("Confidence").end();
            th().text("Difference").end();
            th().text("Test version").end();
            th().text("Operating System").end();
            th().text("Host").end();
            th().text("JVM").end();
            th().text("Test project").end();
            th().text("Tasks").end();
            th().text("Clean tasks").end();
            th().text("Gradle args").end();
            th().text("Gradle JVM opts").end();
            th().text("Daemon").end();
            end();
            final int executionsLen = testHistory.getExecutions().size();
            for (int i = 0; i < executionsLen; i++) {
                PerformanceTestExecution results = testHistory.getExecutions().get(i);
                tr();
                id("result" + results.getExecutionId());
                renderDateAndLink(results);
                textCell(results.getVcsBranch());

                td();
                renderVcsLinks(results, findPreviousExecutionInSameBranch(results, testHistory, i));
                end();

                renderSamplesForExperiment(results.getScenarios());
                textCell(results.getVersionUnderTest());
                textCell(results.getOperatingSystem());
                textCell(results.getHost());
                textCell(results.getJvm());
                textCell(results.getTestProject());
                textCell(results.getTasks());
                textCell(results.getCleanTasks());
                textCell(results.getArgs());
                textCell(results.getGradleOpts());
                textCell(results.getDaemon());
                end();
            }
            end();
            end();
            footer(this);
            endAll();
        }

            private void renderDateAndLink(PerformanceTestExecution results) {
                td();
                    String date = FormatSupport.timestamp(Instant.ofEpochMilli(results.getStartTime()));
                    if (results.getTeamCityBuildId() == null) {
                        text(date);
                    } else {
                        a().href(getTeamCityWebUrlFromBuildId(results.getTeamCityBuildId())).target("_blank").text(date).end();
                    }
                end();
            }

            private void renderVcsLinks(PerformanceTestExecution results, PerformanceTestExecution previousResults) {
                List<GitHubLink> vcsCommits = createGitHubLinks(results.getVcsCommits());
                for (int i = 0; i < vcsCommits.size(); i++) {
                    GitHubLink vcsCommit = vcsCommits.get(i);
                    vcsCommit.renderCommitLink(this);
                    if (previousResults != null) {
                        text(" ");
                        vcsCommit.renderCompareLink(this, previousResults.getVcsCommits().get(i));
                    }
                    if (i != vcsCommits.size() - 1) {
                        text(" | ");
                    }
                }
            }

            @Nullable
            private PerformanceTestExecution findPreviousExecutionInSameBranch(PerformanceTestExecution results, PerformanceTestHistory testHistory, int currentIndex) {
                int executionsLen = testHistory.getExecutions().size();
                PerformanceTestExecution previousResults = null;
                if (currentIndex < executionsLen - 1 && results.getVcsBranch() != null) {
                    for (PerformanceTestExecution execution : testHistory.getExecutions().subList(currentIndex + 1, executionsLen)) {
                        if (results.getVcsBranch().equals(execution.getVcsBranch())) {
                            previousResults = execution;
                            break;
                        }
                    }
                }
                return previousResults;
            }

            private void addPerformanceGraphs() {
                List<Chart> charts = Lists.newArrayList(new Chart("totalTime", "total time", "s", "totalTimeChart", false));
                if(testHistory instanceof CrossVersionPerformanceTestHistory) {
                    charts.add(new Chart("confidence", "confidence", "%", "confidenceChart", false));
                    charts.add(new Chart("difference", "difference", "%", "differenceChart", true));
                }

                charts.forEach(chart -> {
                    h3().text(StringUtils.capitalize(chart.getLabel()) + " (" + chart.getUnit() + ")").end();
                    div().classAttr("chart").id(chart.getChartId());
                        p().text("Loading...").end();
                    end();
                    div().id(chart.getChartId() + "Legend").end();
                });

                script();
                    raw("performanceTests.createPerformanceGraph('" + urlEncode(testHistory.getId()) + ".json', " + JsonOutput.toJson(charts) + ")");
                end();

                renderExecutions();
            }

            private void renderExecutions() {
                h3().text("Executions for commit " + Git.current().getCommitId()).end();
                getGraphs(testHistory).forEach(graph -> graph.render(this));
            }
        };
    }
    // @formatter:on

    private static class Chart {
        private String field;
        private String label;
        private String unit;
        private String chartId;
        private boolean renderBackground;

        private Chart(String field, String label, String unit, String chartId, boolean renderBackground) {
            this.field = field;
            this.label = label;
            this.unit = unit;
            this.chartId = chartId;
            this.renderBackground = renderBackground;
        }

        public String getField() {
            return field;
        }

        public String getLabel() {
            return label;
        }

        public String getUnit() {
            return unit;
        }

        public String getChartId() {
            return chartId;
        }

        public boolean isRenderBackground() {
            return renderBackground;
        }
    }

    private static String getTasks(PerformanceTestHistory testHistory) {
        PerformanceTestExecution performanceTestExecution = getExecution(testHistory);
        if (performanceTestExecution == null || performanceTestExecution.getTasks() == null) {
            return "";
        }
        return Joiner.on(" ").join(performanceTestExecution.getTasks());
    }

    private static String getCleanTasks(PerformanceTestHistory testHistory) {
        PerformanceTestExecution performanceTestExecution = getExecution(testHistory);
        if (performanceTestExecution == null || performanceTestExecution.getCleanTasks() == null) {
            return "";
        }
        return Joiner.on(" ").join(performanceTestExecution.getCleanTasks());
    }

    private static PerformanceTestExecution getExecution(PerformanceTestHistory testHistory) {
        List<? extends PerformanceTestExecution> executions = testHistory.getExecutions();
        if (executions.isEmpty()) {
            return null;
        }
        return executions.get(0);
    }

    private String getReproductionInstructions(PerformanceTestHistory history) {
        String baseline = "";
        if (history instanceof CrossVersionPerformanceTestHistory) {
            baseline = ((CrossVersionPerformanceTestHistory) history).getResults().stream().findFirst()
                .flatMap(result -> result.getBaselineVersions().stream().findFirst())
                .map(baselineVersion -> "-PperformanceBaselines='" + baselineVersion.getVersion() + "'")
                .orElse("");
        }
        PerformanceScenario scenario = history.getExperiment().getScenario();
        return String.format("To reproduce, run ./gradlew :%s:%sPerformanceAdhocTest --tests '%s' %s",
            projectName,
            history.getExperiment().getTestProject(),
            scenario.getClassName() + "." + scenario.getTestName(),
            baseline
        );
    }

    private static class GitHubLink {
        private final String repo;
        private final String hash;

        public GitHubLink(String repo, String hash) {
            this.repo = repo;
            this.hash = hash;
        }

        public void renderCommitLink(Html html) {
            html.a().classAttr("commit-link").href(getUrl()).text(getLabel()).end();
        }

        public String getUrl() {
            return String.format("https://github.com/%s/commit/%s", repo, hash);
        }

        public String getLabel() {
            return formatHash(hash);
        }

        private String formatHash(String hash) {
            return shorten(hash, 7);
        }

        public void renderCompareLink(Html html, String previousHash) {
            String range = String.format("%s...%s", formatHash(previousHash), formatHash(hash));
            html.a().classAttr("compare-link").href(String.format("https://github.com/%s/compare/%s", repo, range)).text("changes").end();
        }
    }

    private List<GitHubLink> createGitHubLinks(List<String> commits) {
        if (null == commits || commits.size() == 0) {
            return Collections.emptyList();
        }
        GitHubLink gradleUrl = new GitHubLink("gradle/gradle", commits.get(0));
        if (commits.size() == 1) {
            return Collections.singletonList(gradleUrl);
        } else if (commits.size() == 2) {
            GitHubLink dotComUrl = new GitHubLink("gradle/dotcom", commits.get(1));
            List<GitHubLink> links = new ArrayList<>();
            links.add(gradleUrl);
            links.add(dotComUrl);
            return links;
        } else {
            throw new IllegalArgumentException("No more than 2 commit SHAs are supported");
        }
    }

    private static String shorten(String string, int maxLength) {
        return string.substring(0, Math.min(maxLength, string.length()));
    }

}
