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

import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.logging.progress.ProgressLogger;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.util.internal.TextUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestCountLogger implements TestListener {
    private final ProgressLoggerFactory factory;
    private ProgressLogger progressLogger;
    private final Logger logger;

    private long totalTests;
    private long failedTests;
    private long skippedTests;
    private boolean hadFailures;

    public TestCountLogger(ProgressLoggerFactory factory) {
        this(factory, LoggerFactory.getLogger(TestCountLogger.class));
    }

    TestCountLogger(ProgressLoggerFactory factory, Logger logger) {
        this.factory = factory;
        this.logger = logger;
    }

    @Override
    public void beforeTest(TestDescriptor testDescriptor) {
    }

    @Override
    public void afterTest(TestDescriptor testDescriptor, TestResult result) {
        totalTests += result.getTestCount();
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
            }
        }
    }

    public boolean hadFailures() {
        return hadFailures;
    }

    public long getTotalTests() {
        return totalTests;
    }
}
