/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.results;

import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;

public class TestLogger implements TestListener {
    private final ProgressLoggerFactory factory;
    private ProgressLogger logger;
    private long totalTests;
    private long failedTests;

    public TestLogger(ProgressLoggerFactory factory) {
        this.factory = factory;
    }

    public void beforeTest(TestDescriptor testDescriptor) {
    }

    public void afterTest(TestDescriptor testDescriptor, TestResult result) {
        totalTests += result.getTestCount();
        failedTests += result.getFailedTestCount();
        logger.progress(summary());
    }

    private String summary() {
        StringBuilder builder = new StringBuilder();
        append(builder, totalTests, "test");
        builder.append(" completed");
        if (failedTests > 0) {
            builder.append(", ");
            append(builder, failedTests, "failure");
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

    public void beforeSuite(TestDescriptor suite) {
        if (suite.getParent() == null) {
            logger = factory.start(TestLogger.class.getName());
        }
    }

    public void afterSuite(TestDescriptor suite, TestResult result) {
        if (suite.getParent() == null) {
            logger.completed(failedTests > 0 ? summary() : "");
        }
    }
}
