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
import org.gradle.performance.measure.DataAmount;
import org.gradle.performance.measure.DataSeries;
import org.gradle.performance.measure.Duration;

import java.io.IOException;
import java.io.Writer;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class IndexPageGenerator extends HtmlPageGenerator<ResultsStore> {
    private final List<NavigationItem> navigationItems;

    public IndexPageGenerator(List<NavigationItem> navigationItems) {
        this.navigationItems = navigationItems;
    }

    @Override
    public void render(final ResultsStore store, Writer writer) throws IOException {
        new MetricsHtml(writer) {{
            html();
                head();
                    headSection(this);
                    title().text("Profile report for channel " + ResultsStoreHelper.determineChannel()).end();
                end();
                body();

                navigation(navigationItems);

                div().id("content");

                    Map<String, String> archived = new LinkedHashMap<String, String>();
                    List<String> testNames = store.getTestNames();
                    div().id("controls").end();
                    for (String testName : testNames) {
                        PerformanceTestHistory testHistory = store.getTestResults(testName, 5, 14, ResultsStoreHelper.determineChannel());
                        List<? extends PerformanceTestExecution> results = testHistory.getExecutions();
                        if (results.isEmpty()) {
                            archived.put(testHistory.getId(), testHistory.getDisplayName());
                            continue;
                        }
                        h2().classAttr("test-execution");
                            text("Test: " + testName);
                        end();
                        table().classAttr("history");
                        tr().classAttr("control-groups");
                            th().colspan("2").end();
                            th().colspan(String.valueOf(testHistory.getScenarioCount() * getColumnsForSamples())).text("Average execution time").end();
                            th().colspan(String.valueOf(testHistory.getScenarioCount() * getColumnsForSamples())).text("Average heap usage").end();
                        end();
                        tr();
                            th().text("Date").end();
                            th().text("Branch").end();
                            for (String label : testHistory.getScenarioLabels()) {
                                renderHeaderForSamples(label);
                            }
                            for (String label : testHistory.getScenarioLabels()) {
                                renderHeaderForSamples(label);
                            }
                        end();
                        for (PerformanceTestExecution performanceTestExecution : results) {
                            tr();
                                td().text(format.timestamp(new Date(performanceTestExecution.getStartTime()))).end();
                                td().text(performanceTestExecution.getVcsBranch()).end();
                                renderSamplesForExperiment(performanceTestExecution.getScenarios(), new Transformer<DataSeries<Duration>, MeasuredOperationList>() {
                                    @Override
                                    public DataSeries<Duration> transform(MeasuredOperationList measuredOperations) {
                                        return measuredOperations.getTotalTime();
                                    }
                                });
                                renderSamplesForExperiment(performanceTestExecution.getScenarios(), new Transformer<DataSeries<DataAmount>, MeasuredOperationList>() {
                                    @Override
                                    public DataSeries<DataAmount> transform(MeasuredOperationList measuredOperations) {
                                        return measuredOperations.getTotalMemoryUsed();
                                    }
                                });
                            end();
                        }
                        end();
                        div().classAttr("details");
                            String url = "tests/" + testHistory.getId() + ".html";
                            a().href(url).text("details...").end();
                        end();
                    }
                    if (!archived.isEmpty()) {
                        h2().text("Archived tests").end();
                        div();
                            ul();
                                for (Map.Entry<String, String> entry : archived.entrySet()) {
                                    String url = "tests/" + entry.getKey() + ".html";
                                    li().a().href(url).text(entry.getValue()).end().end();
                                }
                            end();
                        end();
                    }
                end();
                footer(this);
            endAll();
        }};
    }
}
