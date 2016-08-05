/*
 * Copyright 2016 the original author or authors.
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

import java.io.IOException;
import java.io.Writer;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GraphIndexPageGenerator extends HtmlPageGenerator<ResultsStore> {
    @Override
    public void render(final ResultsStore store, Writer writer) throws IOException {
        new MetricsHtml(writer) {{
            html();
            head();
                headSection(this);
                title().text("Performance test graphs").end();
            end();
            body();
            div().id("content");
                Calendar calendar = Calendar.getInstance();
                calendar.add(Calendar.DAY_OF_YEAR, -14);
                long expiry = calendar.getTime().getTime();
                Map<String, String> archived = new LinkedHashMap<String, String>();
                List<String> testNames = store.getTestNames();
                div().id("controls").end();
                for (String testName : testNames) {
                    PerformanceTestHistory testHistory = store.getTestResults(testName, 5);
                    List<? extends PerformanceTestExecution> results = testHistory.getExecutions();
                    if (results.isEmpty() || results.get(0).getTestTime() < expiry) {
                        continue;
                    }
                    h2().classAttr("test-execution");
                        text("Test: " + testName);
                    end();
                    div().classAttr("charts");
                        h3().text("Execution time").end();
                        String executionTimeChartId = "executionTimeChart" + testHistory.getId().replaceAll("[^a-zA-Z]", "_");
                        div().id(executionTimeChartId).classAttr("chart");
                            p().text("Loading...").end();
                        end();
                    end();
                    div().classAttr("charts");
                        h3().text("Average heap usage").end();
                        String heapUsageChartId = "heapUsageChart" + testHistory.getId().replaceAll("[^a-zA-Z]", "_");
                        div().id(heapUsageChartId).classAttr("chart");
                            p().text("Loading...").end();
                        end();
                    end();
                    script();
                        text("performanceTests.createPerformanceGraph('tests/" + testHistory.getId() + ".json', '" + executionTimeChartId + "', '" + heapUsageChartId + "');");
                    end();
                    div().classAttr("details");
                        String url = "tests/" + testHistory.getId() + ".html";
                        a().href(url).text("details...").end();
                    end();
                }
            end();
            footer(this);
            endAll();
        }};
    }
}
