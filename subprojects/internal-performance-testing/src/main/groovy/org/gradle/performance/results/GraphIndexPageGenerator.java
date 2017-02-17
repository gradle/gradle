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
import java.util.List;

public class GraphIndexPageGenerator extends HtmlPageGenerator<ResultsStore> {
    private final List<NavigationItem> navigationItems;

    public GraphIndexPageGenerator(List<NavigationItem> navigationItems) {
        this.navigationItems = navigationItems;
    }

    @Override
    public void render(final ResultsStore store, Writer writer) throws IOException {
        new MetricsHtml(writer) {{
            html();
            head();
                headSection(this);
                title().text("Performance test graphs").end();
            end();
            body();

            navigation(navigationItems);

            div().id("content");
                List<String> testNames = store.getTestNames();
                div().id("controls").end();
                for (String testName : testNames) {
                    String channel = ResultsStoreHelper.determineChannel();
                    PerformanceTestHistory testHistory = store.getTestResults(testName, 5, 14, channel);
                    List<? extends PerformanceTestExecution> results = testHistory.getExecutions();
                    results = filterForRequestedCommit(results);
                    if (results.isEmpty()) {
                        continue;
                    }
                    h2().classAttr("test-execution");
                        text("Test: " + testName);
                    end();
                    div().classAttr("charts");
                        h3().text("Average total time").end();
                        String totalTimeChartId = "totalTimeChart" + testHistory.getId().replaceAll("[^a-zA-Z]", "_");
                        div().id(totalTimeChartId).classAttr("chart");
                            p().text("Loading...").end();
                        end();
                        script();
                            text("performanceTests.createPerformanceGraph('tests/" + testHistory.getId() + ".json', function(data) { return data.totalTime }, 'total time', 's', '" + totalTimeChartId + "');");
                        end();
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
