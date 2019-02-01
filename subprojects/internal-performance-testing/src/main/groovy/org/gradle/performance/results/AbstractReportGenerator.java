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
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;

public abstract class AbstractReportGenerator<R extends ResultsStore> {
    protected static <G extends AbstractReportGenerator> G getGenerator(Class<G> generatorClass) throws Exception {
        return generatorClass.getConstructor().newInstance();
    }

    protected void generateReport(String... args) {
        File outputDirectory = new File(args[0]);
        File resultJson = new File(args[1]);
        String projectName = args[2];
        generate(outputDirectory, resultJson, projectName);
    }

    protected void generate(File outputDirectory, File resultJson, String projectName) {
        try (ResultsStore store = getResultsStore()) {
            renderIndexPage(store, resultJson, outputDirectory);

            for (String testName : store.getTestNames()) {
                PerformanceTestHistory testResults = store.getTestResults(testName, 500, 90, ResultsStoreHelper.determineChannel());
                renderScenarioPage(projectName, outputDirectory, testResults);
            }

            copyResource("jquery.min-1.11.0.js", outputDirectory);
            copyResource("flot-0.8.1-min.js", outputDirectory);
            copyResource("flot.selection.min.js", outputDirectory);
            copyResource("style.css", outputDirectory);
            copyResource("report.js", outputDirectory);
            copyResource("performanceGraph.js", outputDirectory);
            copyResource("performanceReport.js", outputDirectory);
        } catch (Exception e) {
            throw new RuntimeException(String.format("Could not generate performance test report to '%s'.", outputDirectory), e);
        }
    }

    protected void renderIndexPage(ResultsStore store, File resultJson, File outputDirectory) throws IOException {
        new FileRenderer().render(store, new IndexPageGenerator(store, resultJson), new File(outputDirectory, "index.html"));
    }

    protected void renderScenarioPage(String projectName, File outputDirectory, PerformanceTestHistory testResults) throws IOException {
        FileRenderer fileRenderer = new FileRenderer();
        TestPageGenerator testHtmlRenderer = new TestPageGenerator(projectName);
        TestDataGenerator testDataRenderer = new TestDataGenerator();
        fileRenderer.render(testResults, testHtmlRenderer, new File(outputDirectory, "tests/" + testResults.getId() + ".html"));
        fileRenderer.render(testResults, testDataRenderer, new File(outputDirectory, "tests/" + testResults.getId() + ".json"));
    }

    protected ResultsStore getResultsStore() throws Exception {
        Type superClass = getClass().getGenericSuperclass();
        Class<? extends ResultsStore> resultsStoreClass = (Class<R>) ((ParameterizedType) superClass).getActualTypeArguments()[0];
        return resultsStoreClass.getConstructor().newInstance();
    }

    protected void copyResource(String resourceName, File outputDirectory) {
        URL resource = getClass().getClassLoader().getResource("org/gradle/reporting/" + resourceName);
        String dir = StringUtils.substringAfterLast(resourceName, ".");
        GFileUtils.copyURLToFile(resource, new File(outputDirectory, dir + "/" + resourceName));
    }
}
