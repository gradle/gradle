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
import com.googlecode.jatl.Html;
import org.gradle.api.Transformer;
import org.gradle.performance.fixture.MeasuredOperationList;
import org.gradle.performance.fixture.PerformanceResults;
import org.gradle.performance.fixture.VersionResults;
import org.gradle.performance.measure.Amount;
import org.gradle.performance.measure.DataAmount;
import org.gradle.performance.measure.DataSeries;
import org.gradle.performance.measure.Duration;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class TestPageGenerator extends HtmlPageGenerator<TestExecutionHistory> {
    @Override
    public void render(final TestExecutionHistory testHistory, Writer writer) throws IOException {
        new Html(writer) {{
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
                        text("    $.plot('#executionTimeChart', data.executionTime, options);\n");
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
                            th().colspan(String.valueOf(testHistory.getKnownVersions().size())).text("Average execution time").end();
                            th().colspan(String.valueOf(testHistory.getKnownVersions().size())).text("Average heap usage (old measurement)").end();
                            th().colspan(String.valueOf(testHistory.getKnownVersions().size())).text("Average total heap usage").end();
                            th().colspan(String.valueOf(testHistory.getKnownVersions().size())).text("Average max heap usage").end();
                            th().colspan(String.valueOf(testHistory.getKnownVersions().size())).text("Average max uncollected heap").end();
                            th().colspan(String.valueOf(testHistory.getKnownVersions().size())).text("Average max committed heap").end();
                            th().colspan("4").text("Details").end();
                        end();
                        tr();
                            th().text("Date").end();
                            th().text("Test version").end();
                            th().text("Branch").end();
                            th().text("Git commit").end();
                            for (String version : testHistory.getKnownVersions()) {
                                th().classAttr("numeric").text(version).end();
                            }
                            for (String version : testHistory.getKnownVersions()) {
                                th().classAttr("numeric").text(version).end();
                            }
                            for (String version : testHistory.getKnownVersions()) {
                                th().classAttr("numeric").text(version).end();
                            }
                            for (String version : testHistory.getKnownVersions()) {
                                th().classAttr("numeric").text(version).end();
                            }
                            for (String version : testHistory.getKnownVersions()) {
                                th().classAttr("numeric").text(version).end();
                            }
                            for (String version : testHistory.getKnownVersions()) {
                                th().classAttr("numeric").text(version).end();
                            }
                            th().text("Test project").end();
                            th().text("Tasks").end();
                            th().text("Operating System").end();
                            th().text("JVM").end();
                        end();
                        for (PerformanceResults performanceResults : testHistory.getResults()) {
                            tr();
                                td().text(format.timestamp(new Date(performanceResults.getTestTime()))).end();
                                td().text(performanceResults.getVersionUnderTest()).end();
                                td().text(performanceResults.getVcsBranch()).end();
                                td().text(performanceResults.getVcsCommit()).end();
                                renderMetricForVersions(testHistory, performanceResults, new Transformer<DataSeries<Duration>, MeasuredOperationList>() {
                                    public DataSeries<Duration> transform(MeasuredOperationList original) {
                                        return original.getExecutionTime();
                                    }
                                });
                                renderMetricForVersions(testHistory, performanceResults, new Transformer<DataSeries<DataAmount>, MeasuredOperationList>() {
                                    public DataSeries<DataAmount> transform(MeasuredOperationList original) {
                                        return original.getTotalMemoryUsed();
                                    }
                                });
                                renderMetricForVersions(testHistory, performanceResults, new Transformer<DataSeries<DataAmount>, MeasuredOperationList>() {
                                    public DataSeries<DataAmount> transform(MeasuredOperationList original) {
                                        return original.getTotalHeapUsage();
                                    }
                                });
                                renderMetricForVersions(testHistory, performanceResults, new Transformer<DataSeries<DataAmount>, MeasuredOperationList>() {
                                    public DataSeries<DataAmount> transform(MeasuredOperationList original) {
                                        return original.getMaxHeapUsage();
                                    }
                                });
                                renderMetricForVersions(testHistory, performanceResults, new Transformer<DataSeries<DataAmount>, MeasuredOperationList>() {
                                    public DataSeries<DataAmount> transform(MeasuredOperationList original) {
                                        return original.getMaxUncollectedHeap();
                                    }
                                });
                                renderMetricForVersions(testHistory, performanceResults, new Transformer<DataSeries<DataAmount>, MeasuredOperationList>() {
                                    public DataSeries<DataAmount> transform(MeasuredOperationList original) {
                                        return original.getMaxCommittedHeap();
                                    }
                                });
                                td().text(performanceResults.getTestProject()).end();
                                td();
                                    text(Joiner.on(", ").join(performanceResults.getArgs()));
                                    text(" ");
                                    text(Joiner.on(", ").join(performanceResults.getTasks()));
                                end();
                                td().text(performanceResults.getOperatingSystem()).end();
                                td().text(performanceResults.getJvm()).end();
                            end();
                        }
                    end();
                end();
                footer(this);
            endAll();
        }

            private <T> void renderMetricForVersions(TestExecutionHistory testHistory, PerformanceResults testExecution, Transformer<DataSeries<T>, MeasuredOperationList> transformer) {
                List<Amount<T>> values = new ArrayList<Amount<T>>();
                Amount<T> min = null;
                Amount<T> max = null;
                for (String version : testHistory.getKnownVersions()) {
                    VersionResults versionResults = testExecution.version(version);
                    DataSeries<T> data = transformer.transform(versionResults.getResults());
                    if (data.isEmpty()) {
                        values.add(null);
                    } else {
                        Amount<T> value = data.getAverage();
                        values.add(value);
                        if (min == null || value.compareTo(min) < 0) {
                            min = value;
                        }
                        if (max == null || value.compareTo(max) > 0) {
                            max = value;
                        }
                    }
                }
                if (min != null && min.equals(max)) {
                    min = null;
                    max = null;
                }

                for (Amount<?> value : values) {
                    if (value == null) {
                        td().text("").end();
                    } else {
                        String classAttr = "numeric";
                        if (value.equals(min)) {
                            classAttr += " min-value";
                        }
                        if (value.equals(max)) {
                            classAttr += " max-value";
                        }
                        td().classAttr(classAttr).text(value.format()).end();
                    }
                }
            }
        };
    }

}
