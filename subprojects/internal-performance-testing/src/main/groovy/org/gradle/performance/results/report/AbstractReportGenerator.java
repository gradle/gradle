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
import org.gradle.api.GradleException;
import org.gradle.performance.results.FileRenderer;
import org.gradle.performance.results.NoResultsStore;
import org.gradle.performance.results.PerformanceDatabase;
import org.gradle.performance.results.PerformanceExperiment;
import org.gradle.performance.results.PerformanceTestHistory;
import org.gradle.performance.results.ResultsStore;
import org.gradle.performance.results.ResultsStoreHelper;
import org.gradle.performance.results.ScenarioBuildResultData;
import org.gradle.util.internal.GFileUtils;

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
        boolean onlyOneTestExecuted = executionDataProvider.getScenarioExecutionData().size() == 1;
        FailureCollector failureCollector = new FailureCollector();
        collectFailures(flakinessDataProvider, executionDataProvider, failureCollector);
        String resultString = onlyOneTestExecuted
            ? formatSingleResultString(executionDataProvider, failureCollector)
            : formatResultString(failureCollector);
        if (failureCollector.isFailBuild()) {
            String failedMessage = "Performance test failed" + resultString;
            if (onlyOneTestExecuted) {
                System.out.println("##teamcity[buildStatus text='" + failedMessage + "']");
            }
            throw new GradleException(failedMessage);
        }
        String successMessage = "Performance test passed" + resultString;
        System.out.println(successMessage);
        markBuildAsSuccessful(successMessage);
    }

    protected abstract void collectFailures(PerformanceFlakinessDataProvider flakinessDataProvider, PerformanceExecutionDataProvider executionDataProvider, FailureCollector failureCollector);


    private void markBuildAsSuccessful(String successMessage) {
        System.out.println("##teamcity[buildStatus status='SUCCESS' text='" + successMessage + "']");
    }

    private String formatResultString(FailureCollector failureCollector) {
        StringBuilder sb = new StringBuilder();
        if (failureCollector.getBuildFailures() != 0) {
            sb.append(", ").append(failureCollector.getBuildFailures()).append(" scenario(s) failed");
        }
        if (failureCollector.getRegressedScenarios() != 0) {
            sb.append(", ").append(failureCollector.getRegressedScenarios()).append(" stable scenario(s) regressed");
        }
        if (failureCollector.getFlakyScenarioBigRegressions() != 0) {
            sb.append(", ").append(failureCollector.getFlakyScenarioBigRegressions()).append(" flaky scenario(s) regressed badly");
        }

        if (failureCollector.getFlakyScenarioSmallRegressions() != 0) {
            sb.append(", ").append(failureCollector.getFlakyScenarioSmallRegressions()).append(" flaky scenarios(s) regressed slightly");
        }

        sb.append('.');
        return sb.toString();
    }

    private String formatSingleResultString(PerformanceExecutionDataProvider executionDataProvider, FailureCollector failureCollector) {
        PerformanceExperiment performanceExperiment = executionDataProvider.getScenarioExecutionData().first().getPerformanceExperiment();
        String messageTemplate;
        if (failureCollector.getBuildFailures() != 0) {
            messageTemplate = ", scenario %s failed.";
        } else if (failureCollector.getRegressedScenarios() != 0) {
            messageTemplate = ", stable scenario %s regressed.";
        } else if (failureCollector.getFlakyScenarioBigRegressions() != 0) {
            messageTemplate = ", flaky scenario %s regressed badly.";
        } else if (failureCollector.getFlakyScenarioSmallRegressions() != 0) {
            messageTemplate = ", flaky scenario %s regressed slightly.";
        } else {
            messageTemplate = ", scenario %s succeeded.";
        }
        return String.format(messageTemplate, performanceExperiment.getDisplayName());
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

    public static class FailureCollector {
        private int buildFailures = 0;
        private int regressedScenarios = 0;
        private int flakyScenarioBigRegressions = 0;
        private int flakyScenarioSmallRegressions = 0;
        private boolean failBuild;

        public void scenarioFailed() {
            buildFailures++;
            failBuild = true;
        }

        public void scenarioRegressed() {
            regressedScenarios++;
            failBuild = true;
        }

        public void flakyScenarioWithBigRegression() {
            flakyScenarioBigRegressions++;
            failBuild = true;
        }

        public void flakyScenarioWithSmallRegression() {
            flakyScenarioSmallRegressions++;
        }

        public boolean isFailBuild() {
            return failBuild;
        }

        public int getBuildFailures() {
            return buildFailures;
        }

        public int getRegressedScenarios() {
            return regressedScenarios;
        }

        public int getFlakyScenarioBigRegressions() {
            return flakyScenarioBigRegressions;
        }

        public int getFlakyScenarioSmallRegressions() {
            return flakyScenarioSmallRegressions;
        }
    }
}
