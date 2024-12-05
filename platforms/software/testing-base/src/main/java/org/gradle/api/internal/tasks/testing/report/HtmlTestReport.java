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
package org.gradle.api.internal.tasks.testing.report;

import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.testing.junit.result.PersistentTestFailure;
import org.gradle.api.internal.tasks.testing.junit.result.PersistentTestResult;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider;
import org.gradle.api.internal.tasks.testing.junit.result.TestVisitingResultsProviderAction;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.gradle.reporting.HtmlReportBuilder;
import org.gradle.reporting.HtmlReportRenderer;
import org.gradle.reporting.ReportRenderer;
import org.gradle.util.internal.GFileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.api.tasks.testing.TestResult.ResultType.SKIPPED;

/**
 * Generates an HTML report based on test class results from a {@link TestResultsProvider}.
 */
public class HtmlTestReport {

    private static final class TestCollector extends TestVisitingResultsProviderAction {
        private final List<PersistentTestResult> results = new ArrayList<>();

        @Override
        protected void visitTest(TestResultsProvider provider) {
            results.add(provider.getResult());
        }
    }

    private final BuildOperationRunner buildOperationRunner;
    private final BuildOperationExecutor buildOperationExecutor;
    private final static Logger LOG = Logging.getLogger(HtmlTestReport.class);

    public HtmlTestReport(BuildOperationRunner buildOperationRunner, BuildOperationExecutor buildOperationExecutor) {
        this.buildOperationRunner = buildOperationRunner;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    public void generateReport(TestResultsProvider resultsProvider, File reportDir) {
        LOG.info("Generating HTML test report...");

        Timer clock = Time.startTimer();
        AllTestResults model = loadModelFromProvider(resultsProvider);
        generateFiles(model, reportDir);
        LOG.info("Finished generating test html results ({}) into: {}", clock.getElapsed(), reportDir);
    }

    private AllTestResults loadModelFromProvider(TestResultsProvider resultsProvider) {
        final AllTestResults model = new AllTestResults();
        resultsProvider.visitChildren(provider -> {
            model.addTestClass(provider);
            TestCollector tests = new TestCollector();
            provider.visitChildren(tests);
            for (PersistentTestResult collectedResult : tests.results) {
                final TestResult testResult = model.addTest(provider, collectedResult.getName(), collectedResult.getDisplayName(), collectedResult.getDuration());
                if (collectedResult.getResultType() == SKIPPED) {
                    testResult.setIgnored();
                } else {
                    List<PersistentTestFailure> failures = collectedResult.getFailures();
                    for (PersistentTestFailure failure : failures) {
                        testResult.addFailure(failure);
                    }
                }
            }
        });
        return model;
    }

    private void generateFiles(AllTestResults model, final File reportDir) {
        try {
            HtmlReportRenderer htmlRenderer = new HtmlReportRenderer();
            buildOperationRunner.run(new RunnableBuildOperation() {
                @Override
                public void run(BuildOperationContext context) {
                    // Clean-up old HTML report directories
                    GFileUtils.deleteQuietly(new File(reportDir, "packages"));
                    GFileUtils.deleteQuietly(new File(reportDir, "classes"));
                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName("Delete old HTML results");
                }
            });

            htmlRenderer.render(model, new ReportRenderer<AllTestResults, HtmlReportBuilder>() {
                @Override
                public void render(final AllTestResults model, final HtmlReportBuilder output) throws IOException {
                    buildOperationExecutor.runAll(queue -> {
                        queue.add(generator("index.html", model, new OverviewPageRenderer(), output));
                        for (PackageTestResults packageResults : model.getPackages()) {
                            queue.add(generator(packageResults.getBaseUrl(), packageResults, new PackagePageRenderer(), output));
                            for (ClassTestResults classResults : packageResults.getClasses()) {
                                queue.add(generator(classResults.getBaseUrl(), classResults, new ClassPageRenderer(), output));
                            }
                        }
                    });
                }
            }, reportDir);
        } catch (Exception e) {
            throw new GradleException(String.format("Could not generate test report to '%s'.", reportDir), e);
        }
    }

    private static <T extends CompositeTestResults> HtmlReportFileGenerator<T> generator(String fileUrl, T results, PageRenderer<T> renderer, HtmlReportBuilder output) {
        return new HtmlReportFileGenerator<>(fileUrl, results, renderer, output);
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
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName("Generate HTML test report for ".concat(results.getTitle()));
        }

        @Override
        public void run(BuildOperationContext context) {
            output.renderHtmlPage(fileUrl, results, renderer);
        }
    }
}
