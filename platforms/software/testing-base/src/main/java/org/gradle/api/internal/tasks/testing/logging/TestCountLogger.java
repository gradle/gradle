/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.logging;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.tasks.testing.TestWorkerFailureException;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.util.internal.TextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Reports a live-updating total of tests run, failed and skipped.
 * <p>
 * Output text appears in the console like so:
 * <pre>
 * > :project-name:testTaskName > 6659 tests completed, 1 failed, 2 skipped
 * </pre>
 */
public class TestCountLogger implements TestListener {
    private final ProgressLoggerFactory factory;
    private ProgressLogger progressLogger;
    private final Logger logger;

    private long totalTests;
    private long totalDiscoveredItems;
    private long failedTests;
    private long skippedTests;
    private boolean hadFailures;
    private List<TestFailure> workerFailures;

    public TestCountLogger(ProgressLoggerFactory factory) {
        this(factory, LoggerFactory.getLogger(TestCountLogger.class));
    }

    TestCountLogger(ProgressLoggerFactory factory, Logger logger) {
        this.factory = factory;
        this.logger = logger;
        this.workerFailures = new ArrayList<>();
    }

    @Override
    public void beforeTest(TestDescriptor testDescriptor) {
    }

    @Override
    public void afterTest(TestDescriptor testDescriptor, TestResult result) {
        totalTests += result.getTestCount();
        totalDiscoveredItems += result.getTestCount();
        failedTests += result.getFailedTestCount();
        skippedTests += result.getSkippedTestCount();
        progressLogger.progress(summary());
    }

    private String summary() {
        StringBuilder builder = new StringBuilder();
        append(builder, totalTests, "test");
        builder.append(" completed");
        if (failedTests > 0) {
            builder.append(", ");
            builder.append(failedTests);
            builder.append(" failed");
        }
        if (skippedTests > 0) {
            builder.append(", ");
            builder.append(skippedTests);
            builder.append(" skipped");
        }
        return builder.toString();
    }

    private void append(StringBuilder dest, long count, String noun) {
        dest.append(count);
        dest.append(" ");
        dest.append(noun);
        if (count != 1) {
            dest.append("s");
        }
    }

    @Override
    public void beforeSuite(TestDescriptor suite) {
        if (suite.getParent() == null) {
            progressLogger = factory.newOperation(TestCountLogger.class);
            progressLogger.setDescription("Run tests");
            progressLogger.started();
            progressLogger.progress(summary());
        }
    }

    @Override
    public void afterSuite(TestDescriptor suite, TestResult result) {
        if (suite.getParent() == null) {
            if (failedTests > 0) {
                logger.error(TextUtil.getPlatformLineSeparator() + summary());
            }
            progressLogger.completed();

            if (result.getResultType() == TestResult.ResultType.FAILURE) {
                hadFailures = true;
                // TODO: Instead of assuming all failures at the root suite are worker failures, we should
                // identify worker failures in the TestFailureDetails
                workerFailures.addAll(result.getFailures());
            }
        } else {
            // Looks like a test worker suite
            // TODO: Instead of assuming all failures at this level are worker failures, we should
            // identify worker failures in the TestFailureDetails
            if (suite.getParent().getParent() == null) {
                if (result.getResultType() == TestResult.ResultType.FAILURE) {
                    workerFailures.addAll(result.getFailures());
                }
            }
            // Only count suites that can be attributed to a discovered class such as test class suites, explicit test suites,
            // parameterized tests, test templates, dynamic tests, etc, and ignore the "test worker" and root suites.
            if (suite.getClassName() != null) {
                totalDiscoveredItems++;
            }
        }
    }

    public boolean hadFailures() {
        return hadFailures;
    }

    public long getTotalTests() {
        return totalTests;
    }

    /**
     * Returns the total number of test methods, test suites, test classes and/or dynamic tests that were discovered before
     * or during test execution.  This does not include the "test worker" or root suites.
     */
    public long getTotalDiscoveredItems() {
        return totalDiscoveredItems;
    }

    /**
     * Checks if any test failures were reported during test worker startup and throws an exception if so.
     */
    public void assertNoStartupFailures() {
        if (!workerFailures.isEmpty()) {
            // TODO: We should expand this into different kinds of failures based on the situation we've detected.
            // e.g., test workers can fail to start due to command-line misconfiguration
            // or test workers can start, but fail to run any tests due to a bad classpath
            // or test workers can start, run some tests and then fail due to OOM or System.exit(...)
            //
            // We currently treat all of these as the same kind of failure.
            //
            // It would be better to emit these as problems instead of packing everything into the exception.
            throw new TestWorkerFailureException("Test process encountered an unexpected problem.",
                workerFailures.stream().map(TestFailure::getRawFailure).collect(Collectors.toList()),
                Collections.singletonList("Check common problems " + new DocumentationRegistry().getDocumentationFor("java_testing", "sec:java_testing_troubleshooting") + "."));
        }
    }
}
