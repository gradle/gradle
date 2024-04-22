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

import org.gradle.performance.results.PerformanceTestHistory;

import java.io.IOException;
import java.io.Writer;

public class FlakinessScenarioPageGenerator extends HtmlPageGenerator<PerformanceTestHistory> implements PerformanceExecutionGraphRenderer {
    @Override
    public int getDepth() {
        return 1;
    }

    @Override
    public void render(PerformanceTestHistory history, Writer writer) throws IOException {
        // @formatter:off
        new MetricsHtml(writer) {{
            html();
                head();
                    headSection(this);
                    title().text("Flaky report for "+ history.getDisplayName()).end();
                end();
                body();
                    h2().text("Flaky report for " + history.getDisplayName()).end();
                    getGraphs(history).forEach(graph -> graph.render(this));
                end();
            end();
        }
        };
        // @formatter:on
    }
}
