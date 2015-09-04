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

import org.gradle.api.Transformer;
import org.gradle.performance.fixture.MeasuredOperationList;
import org.gradle.performance.measure.DataAmount;
import org.gradle.performance.measure.DataSeries;
import org.gradle.performance.measure.Duration;

import java.io.IOException;
import java.io.Writer;
import java.util.Date;

public class TestPageGenerator extends HtmlPageGenerator<TestExecutionHistory> {
    @Override
    protected int getDepth() {
        return 1;
    }

    @Override
    public void render(final TestExecutionHistory testHistory, Writer writer) throws IOException {
        new MetricsHtml(writer) {{
            html();
                head();
                    headSection(this);
                    title().text(String.format("Profile test %s report", testHistory.getName())).end();
                    script();
                        text("$(function() {\n");
                        text("$.ajax({ url:'" + testHistory.getId() + ".json\', dataType: 'json',");
                        text("  success: function(data) {\n");
                        text("    var labels = data.labels;\n");
                        text("    var options = { series: { points: { show: true }, lines: { show: true } }, legend: { noColumns: 0, margin: 1 }, grid: { hoverable: true, clickable: true }, xaxis: { tickFormatter: function(index, value) { return labels[index]; } } };\n");
                        text("    $.plot('#executionTimeChart', data.totalTime, options);\n");
                        text("    $.plot('#heapUsageChart', data.heapUsage, options);\n");
                        text("    $('#executionTimeChart').bind('plothover', function (event, pos, item) {\n");
                        text("      if (!item) {\n");
                        text("        $('#tooltip').hide();\n");
                        text("      } else {\n");
                        text("        var text = 'Version: ' + item.series.label + ', date: ' + labels[item.dataIndex] + ', execution time: ' + item.datapoint[1] + 's';\n");
                        text("        $('#tooltip').html(text).css({top: item.pageY - 10, left: item.pageX + 10}).show();\n");
                        text("      }\n");
                        text("    });\n");
                        text("    $('#heapUsageChart').bind('plothover', function (event, pos, item) {\n");
                        text("      if (!item) {\n");
                        text("        $('#tooltip').hide();\n");
                        text("      } else {\n");
                        text("        var text = 'Version: ' + item.series.label + ', date: ' + labels[item.dataIndex] + ', heap usage: ' + item.datapoint[1] + 'mb';\n");
                        text("        $('#tooltip').html(text).css({top: item.pageY - 10, left: item.pageX + 10}).show();\n");
                        text("      }\n");
                        text("    });\n");
                        text("  }\n");
                        text("});\n");
                        text("});");
                    end();
                end();
                body();
                div().id("content");
                    h2().text(String.format("Test %s", testHistory.getName())).end();
                    h3().text("Average execution time").end();
                    div().id("executionTimeChart").classAttr("chart");
                        p().text("Loading...").end();
                    end();
                    h3().text("Average heap usage").end();
                    div().id("heapUsageChart").classAttr("chart");
                        p().text("Loading...").end();
                    end();
                    div().id("tooltip").end();
                    h3().text("Test history").end();
                    div().id("controls").end();
                    table().classAttr("history");
                        tr().classAttr("control-groups");
                            th().colspan("4").end();
                            th().colspan(String.valueOf(testHistory.getExperimentCount())).text("Average build time").end();
                            th().colspan(String.valueOf(testHistory.getExperimentCount())).text("Average configuration time").end();
                            th().colspan(String.valueOf(testHistory.getExperimentCount())).text("Average execution time").end();
                            th().colspan(String.valueOf(testHistory.getExperimentCount())).text("Average heap usage (old measurement)").end();
                            th().colspan(String.valueOf(testHistory.getExperimentCount())).text("Average total heap usage").end();
                            th().colspan(String.valueOf(testHistory.getExperimentCount())).text("Average max heap usage").end();
                            th().colspan(String.valueOf(testHistory.getExperimentCount())).text("Average max uncollected heap").end();
                            th().colspan(String.valueOf(testHistory.getExperimentCount())).text("Average max committed heap").end();
                            th().colspan("4").text("Details").end();
                        end();
                        tr();
                            th().text("Date").end();
                            th().text("Test version").end();
                            th().text("Branch").end();
                            th().text("Git commit").end();
                            for (int i = 0; i < 8; i++) {
                                for (String label : testHistory.getExperimentLabels()) {
                                    th().classAttr("numeric").text(label).end();
                                }
                            }
                            th().text("Operating System").end();
                            th().text("JVM").end();
                        end();
                        for (PerformanceResults results : testHistory.getPerformanceResults()) {
                            tr();
                                td().text(format.timestamp(new Date(results.getTestTime()))).end();
                                td().text(results.getVersionUnderTest()).end();
                                td().text(results.getVcsBranch()).end();
                                td().text(results.getVcsCommit()).end();
                                renderSamplesForExperiment(results.getExperiments(), new Transformer<DataSeries<Duration>, MeasuredOperationList>() {
                                    public DataSeries<Duration> transform(MeasuredOperationList original) {
                                        return original.getTotalTime();
                                    }
                                });
                                renderSamplesForExperiment(results.getExperiments(), new Transformer<DataSeries<Duration>, MeasuredOperationList>() {
                                    public DataSeries<Duration> transform(MeasuredOperationList original) {
                                        return original.getConfigurationTime();
                                    }
                                });
                                renderSamplesForExperiment(results.getExperiments(), new Transformer<DataSeries<Duration>, MeasuredOperationList>() {
                                    public DataSeries<Duration> transform(MeasuredOperationList original) {
                                        return original.getExecutionTime();
                                    }
                                });
                                renderSamplesForExperiment(results.getExperiments(), new Transformer<DataSeries<DataAmount>, MeasuredOperationList>() {
                                    public DataSeries<DataAmount> transform(MeasuredOperationList original) {
                                        return original.getTotalMemoryUsed();
                                    }
                                });
                                renderSamplesForExperiment(results.getExperiments(), new Transformer<DataSeries<DataAmount>, MeasuredOperationList>() {
                                    public DataSeries<DataAmount> transform(MeasuredOperationList original) {
                                        return original.getTotalHeapUsage();
                                    }
                                });
                                renderSamplesForExperiment(results.getExperiments(), new Transformer<DataSeries<DataAmount>, MeasuredOperationList>() {
                                    public DataSeries<DataAmount> transform(MeasuredOperationList original) {
                                        return original.getMaxHeapUsage();
                                    }
                                });
                                renderSamplesForExperiment(results.getExperiments(), new Transformer<DataSeries<DataAmount>, MeasuredOperationList>() {
                                    public DataSeries<DataAmount> transform(MeasuredOperationList original) {
                                        return original.getMaxUncollectedHeap();
                                    }
                                });
                                renderSamplesForExperiment(results.getExperiments(), new Transformer<DataSeries<DataAmount>, MeasuredOperationList>() {
                                    public DataSeries<DataAmount> transform(MeasuredOperationList original) {
                                        return original.getMaxCommittedHeap();
                                    }
                                });
                                td().text(results.getOperatingSystem()).end();
                                td().text(results.getJvm()).end();
                            end();
                        }
                    end();
                end();
                footer(this);
            endAll();
        }};
    }

}
