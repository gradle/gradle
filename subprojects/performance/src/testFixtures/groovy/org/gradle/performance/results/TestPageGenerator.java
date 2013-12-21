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
import org.gradle.performance.fixture.BaselineVersion;
import org.gradle.performance.fixture.PerformanceResults;

import java.io.IOException;
import java.io.Writer;
import java.util.Date;

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
                    table().classAttr("history");
                        tr();
                            th().colspan("3").end();
                            th().colspan(String.valueOf(testHistory.getBaselineVersions().size() + 1)).text("Average execution time").end();
                            th().colspan(String.valueOf(testHistory.getBaselineVersions().size() + 1)).text("Average heap usage").end();
                            th().colspan("5").text("Details").end();
                        end();
                        tr();
                            th().text("Date").end();
                            th().text("Test version").end();
                            th().text("Branch").end();
                            for (String version : testHistory.getBaselineVersions()) {
                                th().classAttr("numeric").text(version).end();
                            }
                            th().classAttr("numeric").text("Current").end();
                            for (String version : testHistory.getBaselineVersions()) {
                                th().classAttr("numeric").text(version).end();
                            }
                            th().classAttr("numeric").text("Current").end();
                            th().text("Test project").end();
                            th().text("Tasks").end();
                            th().text("Operating System").end();
                            th().text("JVM").end();
                            th().text("Commit Id").end();
                        end();
                        for (PerformanceResults performanceResults : testHistory.getResults()) {
                            tr();
                                td().text(format.timestamp(new Date(performanceResults.getTestTime()))).end();
                                td().text(performanceResults.getVersionUnderTest()).end();
                                td().text(performanceResults.getVcsBranch()).end();
                                for (String version : testHistory.getBaselineVersions()) {
                                    BaselineVersion baselineVersion = performanceResults.baseline(version);
                                    if (baselineVersion.getResults().isEmpty()) {
                                        td().text("").end();
                                    } else {
                                        td().classAttr("numeric").text(baselineVersion.getResults().avgTime().format()).end();
                                    }
                                }
                                td().classAttr("numeric").text(performanceResults.getCurrent().avgTime().format()).end();
                                for (String version : testHistory.getBaselineVersions()) {
                                    BaselineVersion baselineVersion = performanceResults.baseline(version);
                                    if (baselineVersion.getResults().isEmpty()) {
                                        td().text("").end();
                                    } else {
                                        td().classAttr("numeric").text(baselineVersion.getResults().avgMemory().format()).end();
                                    }
                                }
                                td().classAttr("numeric").text(performanceResults.getCurrent().avgMemory().format()).end();
                                td().text(performanceResults.getTestProject()).end();
                                td();
                                    text(Joiner.on(", ").join(performanceResults.getArgs()));
                                    text(" ");
                                    text(Joiner.on(", ").join(performanceResults.getTasks()));
                                end();
                                td().text(performanceResults.getOperatingSystem()).end();
                                td().text(performanceResults.getJvm()).end();
                                td().text(performanceResults.getVcsCommit()).end();
                            end();
                        }
                    end();
                end();
                footer(this);
            endAll();
        }};
    }
}
