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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.internal.tasks.testing.TestSuiteExecutionException;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

import static org.gradle.api.tasks.testing.TestResult.*;

public class TestSummaryListener implements TestListener {
    private final Logger logger;
    private boolean hadFailures;
    private final Set<String> failedClasses = new HashSet<String>();

    public TestSummaryListener(Logger logger) {
        this.logger = logger;
    }

    public boolean hadFailures() {
        return hadFailures;
    }

    public void beforeSuite(TestDescriptor suite) {
        logger.debug("Started {}", suite);
    }

    public void afterSuite(TestDescriptor suite, TestResult result) {
        if (result.getResultType() == ResultType.FAILURE && result.getException() != null) {
            reportFailure(suite, toString(suite), result);
        } else {
            logger.debug("Finished {}", suite);
        }
        if (suite.getParent() == null && result.getResultType() == ResultType.FAILURE) {
            hadFailures = true;
        }
    }

    public void beforeTest(TestDescriptor testDescriptor) {
        logger.debug("Started {}", testDescriptor);
    }

    public void afterTest(TestDescriptor testDescriptor, TestResult result) {
        String testDescription = toString(testDescriptor);
        switch (result.getResultType()) {
            case SUCCESS:
                logger.info("{} PASSED", testDescription);
                break;
            case SKIPPED:
                logger.info("{} SKIPPED", testDescription);
                break;
            case FAILURE:
                reportFailure(testDescriptor, testDescription, result);
                break;
            default:
                throw new IllegalArgumentException();
        }
    }

    private void reportFailure(TestDescriptor testDescriptor, String testDescription, TestResult result) {
        String testClass = testDescriptor.getClassName();
        if (result.getException() instanceof TestSuiteExecutionException) {
            logger.error(String.format("Execution for %s FAILED", testDescription), result.getException());
        } else if (testClass == null) {
            logger.error("{} FAILED: {}", testDescription, result.getException());
        } else {
            logger.info("{} FAILED: {}", testDescription, result.getException());
            if (failedClasses.add(testClass)) {
                logger.error("Test {} FAILED", testClass);
            }
        }
    }

    private String toString(TestDescriptor testDescriptor) {
        return StringUtils.capitalize(testDescriptor.toString());
    }
}
