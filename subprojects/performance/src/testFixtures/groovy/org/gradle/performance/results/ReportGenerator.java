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

import org.apache.commons.lang.StringUtils;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.net.URL;

public class ReportGenerator {
    void generate(final ResultsStore store, File outputDirectory) {
        try {
            FileRenderer fileRenderer = new FileRenderer();
            TestPageGenerator testHtmlRenderer = new TestPageGenerator();
            TestDataGenerator testDataRenderer = new TestDataGenerator();

            fileRenderer.render(store, new IndexPageGenerator(), new File(outputDirectory, "index.html"));

            File testsDir = new File(outputDirectory, "tests");
            for (String testName : store.getTestNames()) {
                PerformanceTestHistory testResults = store.getTestResults(testName, 100);
                fileRenderer.render(testResults, testHtmlRenderer, new File(testsDir, testResults.getId() + ".html"));
                fileRenderer.render(testResults, testDataRenderer, new File(testsDir, testResults.getId() + ".json"));
            }

            copyResource("jquery.min-1.11.0.js", outputDirectory);
            copyResource("flot-0.8.1-min.js", outputDirectory);
            copyResource("flot.selection.min.js", outputDirectory);
            copyResource("style.css", outputDirectory);
            copyResource("report.js", outputDirectory);
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not generate performance test report to '%s'.", outputDirectory), e);
        }
    }

    private void copyResource(String resourceName, File outputDirectory) {
        URL resource = getClass().getClassLoader().getResource("org/gradle/reporting/" + resourceName);
        String dir = StringUtils.substringAfterLast(resourceName, ".");
        GFileUtils.copyURLToFile(resource, new File(outputDirectory, dir + "/" + resourceName));
    }
}
