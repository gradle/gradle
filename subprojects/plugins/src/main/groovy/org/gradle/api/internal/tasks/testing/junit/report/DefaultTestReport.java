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

import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.testing.junit.result.TestClassResult;
import org.gradle.api.internal.tasks.testing.junit.result.TestMethodResult;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.testing.Test;
import org.gradle.reporting.HtmlReportRenderer;
import org.gradle.util.Clock;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultTestReport implements TestReporter {
    private final HtmlReportRenderer htmlRenderer = new HtmlReportRenderer();
    private File reportDir;
    private final static Logger LOG = Logging.getLogger(DefaultTestReport.class);
    private final Test task;

    public DefaultTestReport(Test task) {
        this.task = task;
        htmlRenderer.requireResource(getClass().getResource("/org/gradle/reporting/report.js"));
        htmlRenderer.requireResource(getClass().getResource("/org/gradle/reporting/base-style.css"));
        htmlRenderer.requireResource(getClass().getResource("/org/gradle/reporting/css3-pie-1.0beta3.htc"));
        htmlRenderer.requireResource(getClass().getResource("style.css"));
    }

    public void setTestReportDir(File reportDir) {
        this.reportDir = reportDir;
    }

    public void generateReport(TestResultsProvider resultsProvider) {
        if (!task.isTestReport()) {
            LOG.info("Test report disabled, omitting generation of the HTML test report.");
            return;
        }
        LOG.info("Generating HTML test report...");
        setTestReportDir(task.getTestReportDir());

        Clock clock = new Clock();
        AllTestResults model = loadModelFromProvider(resultsProvider);
        generateFiles(model, resultsProvider);
        LOG.info("Finished generating test html results (" + clock.getTime() + ")");
    }

    private AllTestResults loadModelFromProvider(TestResultsProvider resultsProvider) {
        Map<String, TestClassResult> results = resultsProvider.getResults();
        AllTestResults model = new AllTestResults();
        for (Map.Entry<String, TestClassResult> stringTestClassResultEntry : results.entrySet()) {
            final String suiteClassName = stringTestClassResultEntry.getKey();
            final TestClassResult value = stringTestClassResultEntry.getValue();
            final Set<TestMethodResult> collectedResults = value.getResults();
            for (TestMethodResult collectedResult : collectedResults) {
                final TestResult testResult = model.addTest(suiteClassName, collectedResult.name, collectedResult.getDuration());
                if (collectedResult.result.getResultType() == org.gradle.api.tasks.testing.TestResult.ResultType.SKIPPED) {
                    testResult.ignored();
                } else {
                    final List<Throwable> failures = collectedResult.result.getExceptions();
                    for (Throwable throwable : failures) {
                        testResult.addFailure(throwable.getMessage(), stackTrace(throwable));
                    }
                }
            }
        }
        return model;
    }

    private String stackTrace(Throwable throwable) {
        try {
            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);
            throwable.printStackTrace(writer);
            writer.close();
            return stringWriter.toString();
        } catch (Throwable t) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(stringWriter);
            t.printStackTrace(writer);
            writer.close();
            return stringWriter.toString();
        }
    }

    private void generateFiles(AllTestResults model, TestResultsProvider resultsProvider) {
        try {
            generatePage(model, new OverviewPageRenderer(), new File(reportDir, "index.html"));
            for (PackageTestResults packageResults : model.getPackages()) {
                generatePage(packageResults, new PackagePageRenderer(), new File(reportDir, packageResults.getName() + ".html"));
                for (ClassTestResults classResults : packageResults.getClasses()) {
                    generatePage(classResults, new ClassPageRenderer(classResults.getName(), resultsProvider), new File(reportDir, classResults.getName() + ".html"));
                }
            }
        } catch (Exception e) {
            throw new GradleException(String.format("Could not generate test report to '%s'.", reportDir), e);
        }
    }

    private <T extends CompositeTestResults> void generatePage(T model, PageRenderer<T> renderer, File outputFile) throws Exception {
        htmlRenderer.renderer(renderer).writeTo(model, outputFile);
    }
}
