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

import com.google.common.collect.Lists;
import org.gradle.api.Transformer;
import org.gradle.performance.fixture.MeasuredOperationList;
import org.gradle.performance.measure.DataAmount;
import org.gradle.performance.measure.DataSeries;
import org.gradle.performance.measure.Duration;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.Date;
import java.util.List;

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
            script();
            text("$(function() {\n");
            text("$.ajax({ url:'" + testHistory.getId() + ".json\', dataType: 'json',");
            text("  success: function(data) {\n");
            text("    var labels = data.labels;\n");
            text("    var options = { series: { points: { show: true }, lines: { show: true } }, legend: { noColumns: 0, margin: 1 }, grid: { hoverable: true, clickable: true }, xaxis: { tickFormatter: function(index, value) { return labels[index]; } }, selection: { mode: 'x' } };\n");
            text("    var executionChart = $.plot('#executionTimeChart', data.totalTime, options);\n");
            text("    var heapChart = $.plot('#heapUsageChart', data.heapUsage, options);\n");
            text("    var zoomFunction = function(plot, reset) {\n");
            text("      var reset = reset || false;\n");
            text("      return function (event, ranges) {\n");
            text("        $.each(plot.getXAxes(), function(_, axis) {\n");
            text("          var opts = axis.options;\n");
            text("          opts.min = reset ? null : ranges.xaxis.from;\n");
            text("          opts.max = reset ? null : ranges.xaxis.to;\n");
            text("        });\n");
            text("        plot.setupGrid();\n");
            text("        plot.draw();\n");
            text("        plot.clearSelection();\n");
            text("      };\n");
            text("    };\n");
            text("    $('#executionTimeChart').bind('plothover', function (event, pos, item) {\n");
            text("      if (!item) {\n");
            text("        $('#tooltip').hide();\n");
            text("      } else {\n");
            text("        var text = 'Version: ' + item.series.label + ', date: ' + labels[item.datapoint[0]] + ', execution time: ' + item.datapoint[1] + 's';\n");
            text("        $('#tooltip').html(text).css({top: item.pageY - 10, left: item.pageX + 10}).show();\n");
            text("      }\n");
            text("    }).bind('plotselected', zoomFunction(executionChart)).bind('dblclick', zoomFunction(executionChart, true));\n");
            text("    $('#heapUsageChart').bind('plothover', function (event, pos, item) {\n");
            text("      if (!item) {\n");
            text("        $('#tooltip').hide();\n");
            text("      } else {\n");
            text("        var text = 'Version: ' + item.series.label + ', date: ' + labels[item.datapoint[0]] + ', heap usage: ' + item.datapoint[1] + 'mb';\n");
            text("        $('#tooltip').html(text).css({top: item.pageY - 10, left: item.pageX + 10}).show();\n");
            text("      }\n");
            text("    }).bind('plotselected', zoomFunction(heapChart)).bind('dblclick', zoomFunction(heapChart, true));\n");
            text("  }\n");
            text("});\n");
            text("});");
            end();
            end();
            body();
            div().id("content");
            h2().text(String.format("Test: %s", testHistory.getDisplayName())).end();
            h3().text("Average execution time").end();
            div().id("executionTimeChart").classAttr("chart");
            p().text("Loading...").end();
            end();
            h3().text("Average heap usage").end();
            div().id("heapUsageChart").classAttr("chart");
            p().text("Loading...").end();
            end();
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
            th().colspan(String.valueOf(testHistory.getScenarioCount() * getColumnsForSamples())).text("Average build time").end();
            th().colspan(String.valueOf(testHistory.getScenarioCount() * getColumnsForSamples())).text("Average configuration time").end();
            th().colspan(String.valueOf(testHistory.getScenarioCount() * getColumnsForSamples())).text("Average execution time").end();
            th().colspan(String.valueOf(testHistory.getScenarioCount() * getColumnsForSamples())).text("Average heap usage (old measurement)").end();
            th().colspan(String.valueOf(testHistory.getScenarioCount() * getColumnsForSamples())).text("Average total heap usage").end();
            th().colspan(String.valueOf(testHistory.getScenarioCount() * getColumnsForSamples())).text("Average max heap usage").end();
            th().colspan(String.valueOf(testHistory.getScenarioCount() * getColumnsForSamples())).text("Average max uncollected heap").end();
            th().colspan(String.valueOf(testHistory.getScenarioCount() * getColumnsForSamples())).text("Average max committed heap").end();
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
                textCell(format.timestamp(new Date(results.getTestTime())));
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

                renderSamplesForExperiment(results.getScenarios(), new Transformer<DataSeries<Duration>, MeasuredOperationList>() {
                    public DataSeries<Duration> transform(MeasuredOperationList original) {
                        return original.getTotalTime();
                    }
                });
                renderSamplesForExperiment(results.getScenarios(), new Transformer<DataSeries<Duration>, MeasuredOperationList>() {
                    public DataSeries<Duration> transform(MeasuredOperationList original) {
                        return original.getConfigurationTime();
                    }
                });
                renderSamplesForExperiment(results.getScenarios(), new Transformer<DataSeries<Duration>, MeasuredOperationList>() {
                    public DataSeries<Duration> transform(MeasuredOperationList original) {
                        return original.getExecutionTime();
                    }
                });
                renderSamplesForExperiment(results.getScenarios(), new Transformer<DataSeries<DataAmount>, MeasuredOperationList>() {
                    public DataSeries<DataAmount> transform(MeasuredOperationList original) {
                        return original.getTotalMemoryUsed();
                    }
                });
                renderSamplesForExperiment(results.getScenarios(), new Transformer<DataSeries<DataAmount>, MeasuredOperationList>() {
                    public DataSeries<DataAmount> transform(MeasuredOperationList original) {
                        return original.getTotalHeapUsage();
                    }
                });
                renderSamplesForExperiment(results.getScenarios(), new Transformer<DataSeries<DataAmount>, MeasuredOperationList>() {
                    public DataSeries<DataAmount> transform(MeasuredOperationList original) {
                        return original.getMaxHeapUsage();
                    }
                });
                renderSamplesForExperiment(results.getScenarios(), new Transformer<DataSeries<DataAmount>, MeasuredOperationList>() {
                    public DataSeries<DataAmount> transform(MeasuredOperationList original) {
                        return original.getMaxUncollectedHeap();
                    }
                });
                renderSamplesForExperiment(results.getScenarios(), new Transformer<DataSeries<DataAmount>, MeasuredOperationList>() {
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
        }};
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
