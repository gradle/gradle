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

package org.gradle.performance.results;

import java.io.File;
import java.io.IOException;

public class FlakinessReportGenerator extends AbstractReportGenerator<CrossVersionResultsStore> {
    public static void main(String[] args) throws Exception {
        getGenerator(FlakinessReportGenerator.class).generateReport(args);
    }

    @Override
    protected void renderIndexPage(ResultsStore store, File resultJson, File outputDirectory) throws IOException {
        FlakinessIndexPageGenerator reporter = new FlakinessIndexPageGenerator(store, resultJson);
        new FileRenderer().render(store, reporter, new File(outputDirectory, "index.html"));

        reporter.reportToIssueTracker();
    }

    @Override
    protected void renderScenarioPage(String projectName, File outputDirectory, PerformanceTestHistory testResults) throws IOException {
        new FileRenderer().render(testResults, new FlakinessScenarioPageGenerator(), new File(outputDirectory, "tests/" + testResults.getId() + ".html"));
    }
}
