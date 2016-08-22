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

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Transformer;
import org.gradle.performance.measure.DataAmount;
import org.gradle.performance.measure.DataSeries;
import org.gradle.performance.measure.Duration;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

public class TestPageGenerator extends HtmlPageGenerator<PerformanceTestHistory> {
    @Override
    protected int getDepth() {
        return 1;
    }

    @Override
    public void render(final PerformanceTestHistory testHistory, Writer writer) throws IOException {
        // TODO: Add test name to the report
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

            addPerformanceGraph("Average total time", "totalTimeChart", "totalTime", "total time", "s");
            addPerformanceGraph("Average configuration time", "configurationTimeChart", "configurationTime", "configuration time", "s");
            addPerformanceGraph("Average execution time", "executionTimeChart", "executionTime", "execution time", "s");
            addPerformanceGraph("Average setup/teardown time", "miscTimeChart", "miscTime", "setup/teardown time", "s");
            addPerformanceGraph("Average heap usage", "heapUsageChart", "heapUsage", "heap usage", "mb");
            addPerformanceGraph("Average JIT compiler cpu time", "compileTotalTimeChart", "compileTotalTime", "jit compiler cpu time", "s");
            addPerformanceGraph("Average GC cpu time", "gcTotalTimeChart", "gcTotalTime", "GC cpu time", "s");

            div().id("tooltip").end();
            div().id("controls").end();

            h3().text("Test details").end();
            table().classAttr("test-details");
            tr();
                th().text("Scenario").end();
                th().text("Test project").end();
                th().text("Tasks").end();
                th().text("Gradle args").end();
                th().text("Gradle JVM args").end();
                th().text("Daemon").end();
            end();
            for (ScenarioDefinition scenario : testHistory.getScenarios()) {
                tr();
                    textCell(scenario.getDisplayName());
                    textCell(scenario.getTestProject());
                    textCell(scenario.getTasks());
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
            th().colspan(colspanForField).text("Average configuration time").end();
            th().colspan(colspanForField).text("Average execution time").end();
            th().colspan(colspanForField).text("Average jit compiler cpu time").end();
            th().colspan(colspanForField).text("Average gc cpu time").end();
            th().colspan(colspanForField).text("Average heap usage (old measurement)").end();
            th().colspan(colspanForField).text("Average total heap usage").end();
            th().colspan(colspanForField).text("Average max heap usage").end();
            th().colspan(colspanForField).text("Average max uncollected heap").end();
            th().colspan(colspanForField).text("Average max committed heap").end();
            th().colspan("8").text("Details").end();
            end();
            tr();
            th().text("Date").end();
            th().text("Branch").end();
            th().text("Git commit").end();
            for (int i = 0; i < 8; i++) {
                for (String label : testHistory.getScenarioLabels()) {
                    renderHeaderForSamples(label);
                }
            }
            th().text("Test version").end();
            th().text("Operating System").end();
            th().text("JVM").end();
            th().text("Test project").end();
            th().text("Tasks").end();
            th().text("Gradle args").end();
            th().text("Gradle JVM opts").end();
            th().text("Daemon").end();
            end();
            for (PerformanceTestExecution results : testHistory.getExecutions()) {
                tr();
                textCell(format.timestamp(new Date(results.getStartTime())));
                textCell(results.getVcsBranch());

                td();
                List<Link> vcsCommits = urlify(results.getVcsCommits());
                for (int i = 0; i < vcsCommits.size(); i++) {
                    Link vcsCommit = vcsCommits.get(i);
                    a().href(vcsCommit.getValue()).text(vcsCommit.getLabel()).end();
                    if (i != vcsCommits.size() - 1) {
                        text(" | ");
                    }
                }
                end();

                final List<MeasuredOperationList> scenarios = results.getScenarios();
                renderSamplesForExperiment(scenarios, new Transformer<DataSeries<Duration>, MeasuredOperationList>() {
                    public DataSeries<Duration> transform(MeasuredOperationList original) {
                        return original.getTotalTime();
                    }
                });
                renderSamplesForExperiment(scenarios, new Transformer<DataSeries<Duration>, MeasuredOperationList>() {
                    public DataSeries<Duration> transform(MeasuredOperationList original) {
                        return original.getConfigurationTime();
                    }
                });
                renderSamplesForExperiment(scenarios, new Transformer<DataSeries<Duration>, MeasuredOperationList>() {
                    public DataSeries<Duration> transform(MeasuredOperationList original) {
                        return original.getExecutionTime();
                    }
                });
                renderSamplesForExperiment(scenarios, new Transformer<DataSeries<Duration>, MeasuredOperationList>() {
                    public DataSeries<Duration> transform(MeasuredOperationList original) {
                        return original.getCompileTotalTime();
                    }
                });
                renderSamplesForExperiment(scenarios, new Transformer<DataSeries<Duration>, MeasuredOperationList>() {
                    public DataSeries<Duration> transform(MeasuredOperationList original) {
                        return original.getGcTotalTime();
                    }
                });
                renderSamplesForExperiment(scenarios, new Transformer<DataSeries<DataAmount>, MeasuredOperationList>() {
                    public DataSeries<DataAmount> transform(MeasuredOperationList original) {
                        return original.getTotalMemoryUsed();
                    }
                });
                renderSamplesForExperiment(scenarios, new Transformer<DataSeries<DataAmount>, MeasuredOperationList>() {
                    public DataSeries<DataAmount> transform(MeasuredOperationList original) {
                        return original.getTotalHeapUsage();
                    }
                });
                renderSamplesForExperiment(scenarios, new Transformer<DataSeries<DataAmount>, MeasuredOperationList>() {
                    public DataSeries<DataAmount> transform(MeasuredOperationList original) {
                        return original.getMaxHeapUsage();
                    }
                });
                renderSamplesForExperiment(scenarios, new Transformer<DataSeries<DataAmount>, MeasuredOperationList>() {
                    public DataSeries<DataAmount> transform(MeasuredOperationList original) {
                        return original.getMaxUncollectedHeap();
                    }
                });
                renderSamplesForExperiment(scenarios, new Transformer<DataSeries<DataAmount>, MeasuredOperationList>() {
                    public DataSeries<DataAmount> transform(MeasuredOperationList original) {
                        return original.getMaxCommittedHeap();
                    }
                });
                textCell(results.getVersionUnderTest());
                textCell(results.getOperatingSystem());
                textCell(results.getJvm());
                textCell(results.getTestProject());
                textCell(results.getTasks());
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

            private void addPerformanceGraph(String heading, String chartId, String jsonFieldName, String fieldLabel, String fieldUnit) {
                h3().text(heading).end();
                div().id(chartId).classAttr("chart");
                p().text("Loading...").end();
                script();
                text("performanceTests.createPerformanceGraph('" + testHistory.getId() + ".json', function(data) { return data." + jsonFieldName + "}, '" + fieldLabel + "', '" + fieldUnit + "', '" + chartId + "');");
                end();
                end();
            }
        };
    }

    private String getTasks(PerformanceTestHistory testHistory) {
        List<? extends PerformanceTestExecution> executions = testHistory.getExecutions();
        if (executions.isEmpty()) {
            return "";
        }
        PerformanceTestExecution performanceTestExecution = executions.get(0);
        if (performanceTestExecution == null || performanceTestExecution.getTasks() == null) {
            return "";
        }
        return Joiner.on(" ").join(performanceTestExecution.getTasks());
    }

    private String getReproductionInstructions(PerformanceTestHistory history) {
        Set<String> templates = Sets.newHashSet();
        Set<String> cleanTasks = Sets.newHashSet();
        for (ScenarioDefinition scenario : history.getScenarios()) {
            templates.add(scenario.getTestProject());
            cleanTasks.add("clean" + StringUtils.capitalize(scenario.getTestProject()));
        }

        return "To reproduce, run ./gradlew "
            + Joiner.on(' ').join(cleanTasks)
            + " "
            + Joiner.on(' ').join(templates)
            + " cleanPerformanceAdhocTest performanceAdhocTest --scenarios "
            + "'" + history.getDisplayName() + "'"
            + " -x prepareSamples";
    }

    private static class Link {
        private final String label;
        private final String value;

        public Link(String label, String value) {
            this.label = label;
            this.value = value;
        }

        public String getLabel() {
            return label;
        }

        public String getValue() {
            return value;
        }
    }

    private List<Link> urlify(List<String> commits) {
        if (null == commits || commits.size() == 0) {
            return Collections.emptyList();
        }
        Link gradleUrl = new Link(shorten(commits.get(0), 7), String.format("https://github.com/gradle/gradle/commit/%s", commits.get(0).trim()));
        if (commits.size() == 1) {
            return Collections.singletonList(gradleUrl);
        } else if (commits.size() == 2) {
            Link dotComUrl = new Link(shorten(commits.get(1), 7), String.format("https://github.com/gradle/dotcom/commit/%s", commits.get(1).trim()));
            List<Link> links = Lists.newArrayList();
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
