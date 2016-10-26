/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.tasks.testing.junit.report;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.testing.junit.result.TestClassResult;
import org.gradle.api.internal.tasks.testing.junit.result.TestFailure;
import org.gradle.api.internal.tasks.testing.junit.result.TestMethodResult;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.operations.BuildOperationProcessor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.time.Timer;
import org.gradle.internal.time.Timers;
import org.gradle.reporting.HtmlReportBuilder;
import org.gradle.reporting.HtmlReportRenderer;
import org.gradle.reporting.ReportRenderer;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.gradle.api.tasks.testing.TestResult.ResultType.SKIPPED;

public class DefaultTestReport implements TestReporter {
    private final BuildOperationProcessor buildOperationProcessor;
    private final static Logger LOG = Logging.getLogger(DefaultTestReport.class);

    public DefaultTestReport(BuildOperationProcessor buildOperationProcessor) {
        this.buildOperationProcessor = buildOperationProcessor;
    }

    @Override
    public void generateReport(TestResultsProvider resultsProvider, File reportDir) {
        LOG.info("Generating HTML test report...");

        Timer clock = Timers.startTimer();
        AllTestResults model = loadModelFromProvider(resultsProvider);
        generateFiles(model, resultsProvider, reportDir);
        LOG.info("Finished generating test html results ({}) into: {}", clock.getElapsed(), reportDir);
    }

    private AllTestResults loadModelFromProvider(TestResultsProvider resultsProvider) {
        final AllTestResults model = new AllTestResults();
        resultsProvider.visitClasses(new Action<TestClassResult>() {
            public void execute(TestClassResult classResult) {
                model.addTestClass(classResult.getId(), classResult.getClassName());
                List<TestMethodResult> collectedResults = classResult.getResults();
                for (TestMethodResult collectedResult : collectedResults) {
                    final TestResult testResult = model.addTest(classResult.getId(), classResult.getClassName(), collectedResult.getName(), collectedResult.getDuration());
                    if (collectedResult.getResultType() == SKIPPED) {
                        testResult.setIgnored();
                    } else {
                        List<TestFailure> failures = collectedResult.getFailures();
                        for (TestFailure failure : failures) {
                            testResult.addFailure(failure);
                        }
                    }
                }
            }
        });
        return model;
    }

    private void generateFiles(AllTestResults model, final TestResultsProvider resultsProvider, File reportDir) {
        try {
            HtmlReportRenderer htmlRenderer = new HtmlReportRenderer();
            htmlRenderer.render(model, new ReportRenderer<AllTestResults, HtmlReportBuilder>() {
                @Override
                public void render(final AllTestResults model, final HtmlReportBuilder output) throws IOException {
                    buildOperationProcessor.run(new Action<BuildOperationQueue<HtmlReportFileGenerator<? extends CompositeTestResults>>>() {
                        @Override
                        public void execute(BuildOperationQueue<HtmlReportFileGenerator<? extends CompositeTestResults>> queue) {
                            queue.add(generator("index.html", model, new OverviewPageRenderer(), output));
                            for (PackageTestResults packageResults : model.getPackages()) {
                                queue.add(generator(packageResults.getBaseUrl(), packageResults, new PackagePageRenderer(), output));
                                for (ClassTestResults classResults : packageResults.getClasses()) {
                                    queue.add(generator(classResults.getBaseUrl(), classResults, new ClassPageRenderer(resultsProvider), output));
                                }
                            }
                        }
                    });
                }
            }, reportDir);
        } catch (Exception e) {
            throw new GradleException(String.format("Could not generate test report to '%s'.", reportDir), e);
        }
    }

    public static <T extends CompositeTestResults> HtmlReportFileGenerator<T> generator(String fileUrl, T results, PageRenderer<T> renderer, HtmlReportBuilder output) {
        return new HtmlReportFileGenerator<T>(fileUrl, results, renderer, output);
    }

    private static class HtmlReportFileGenerator<T extends CompositeTestResults> implements RunnableBuildOperation {
        private final String fileUrl;
        private final T results;
        private final PageRenderer<T> renderer;
        private final HtmlReportBuilder output;

        HtmlReportFileGenerator(String fileUrl, T results, PageRenderer<T> renderer, HtmlReportBuilder output) {
            this.fileUrl = fileUrl;
            this.results = results;
            this.renderer = renderer;
            this.output = output;
        }

        @Override
        public String getDescription() {
            return "generating html test report for ".concat(results.getTitle());
        }

        @Override
        public void run() {
            output.renderHtmlPage(fileUrl, results, renderer);
        }
    }
}
