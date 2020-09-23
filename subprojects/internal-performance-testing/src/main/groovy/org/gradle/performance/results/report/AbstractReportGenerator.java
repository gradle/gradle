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

import org.apache.commons.lang.StringUtils;
import org.gradle.performance.results.FileRenderer;
import org.gradle.performance.results.NoResultsStore;
import org.gradle.performance.results.PerformanceDatabase;
import org.gradle.performance.results.PerformanceTestHistory;
import org.gradle.performance.results.ResultsStore;
import org.gradle.performance.results.ResultsStoreHelper;
import org.gradle.performance.results.ScenarioBuildResultData;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.performance.results.report.PerformanceFlakinessDataProvider.EmptyPerformanceFlakinessDataProvider;

public abstract class AbstractReportGenerator<R extends ResultsStore> {
    protected void generateReport(String... args) {
        File outputDirectory = new File(args[0]);
        String projectName = args[1];
        List<File> resultJsons = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            resultJsons.add(new File(args[i]));
        }

        try (ResultsStore store = getResultsStore()) {
            PerformanceExecutionDataProvider executionDataProvider = getExecutionDataProvider(store, resultJsons);
            PerformanceFlakinessDataProvider flakinessDataProvider = getFlakinessDataProvider();
            generateReport(store, flakinessDataProvider, executionDataProvider, outputDirectory, projectName);
            checkResult(flakinessDataProvider, executionDataProvider);
        } catch (IOException | ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    protected PerformanceFlakinessDataProvider getFlakinessDataProvider() {
        return EmptyPerformanceFlakinessDataProvider.INSTANCE;
    }

    protected PerformanceExecutionDataProvider getExecutionDataProvider(ResultsStore store, List<File> resultJsons) {
        return new DefaultPerformanceExecutionDataProvider(store, resultJsons);
    }

    protected void generateReport(ResultsStore store, PerformanceFlakinessDataProvider flakinessDataProvider, PerformanceExecutionDataProvider executionDataProvider, File outputDirectory, String projectName) throws IOException {
        renderIndexPage(flakinessDataProvider, executionDataProvider, new File(outputDirectory, "index.html"));

        executionDataProvider.getScenarioExecutionData().stream()
            .map(ScenarioBuildResultData::getPerformanceExperiment)
            .distinct()
            .forEach(experiment -> {
                PerformanceTestHistory testResults = store.getTestResults(experiment, 500, 90, ResultsStoreHelper.determineChannel());
                renderScenarioPage(projectName, outputDirectory, testResults);
            });

        copyResource("jquery.min-3.5.1.js", outputDirectory);
        copyResource("flot-0.8.1-min.js", outputDirectory);
        copyResource("flot.selection.min.js", outputDirectory);
        copyResource("style.css", outputDirectory);
        copyResource("report.js", outputDirectory);
        copyResource("performanceGraph.js", outputDirectory);
        copyResource("performanceReport.js", outputDirectory);
    }

    protected void renderIndexPage(PerformanceFlakinessDataProvider flakinessDataProvider, PerformanceExecutionDataProvider executionDataProvider, File output) throws IOException {
        new FileRenderer().render(null, new IndexPageGenerator(flakinessDataProvider, executionDataProvider), output);
    }

    protected void checkResult(PerformanceFlakinessDataProvider flakinessDataProvider, PerformanceExecutionDataProvider executionDataProvider) {
    }

    protected void renderScenarioPage(String projectName, File outputDirectory, PerformanceTestHistory testResults) {
        FileRenderer fileRenderer = new FileRenderer();
        TestPageGenerator testHtmlRenderer = new TestPageGenerator(projectName);
        TestDataGenerator testDataRenderer = new TestDataGenerator();
        try {
            fileRenderer.render(testResults, testHtmlRenderer, new File(outputDirectory, "tests/" + testResults.getId() + ".html"));
            fileRenderer.render(testResults, testDataRenderer, new File(outputDirectory, "tests/" + testResults.getId() + ".json"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected ResultsStore getResultsStore() throws ReflectiveOperationException {
        if (!PerformanceDatabase.isAvailable()) {
            return NoResultsStore.getInstance();
        }
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
