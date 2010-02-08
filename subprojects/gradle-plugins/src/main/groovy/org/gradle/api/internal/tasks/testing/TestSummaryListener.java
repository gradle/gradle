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
package org.gradle.api.internal.tasks.testing;

import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.api.tasks.testing.TestSuite;
import org.slf4j.Logger;

public class TestSummaryListener implements TestListener {
    private final Logger logger;
    private boolean hadFailures;

    public TestSummaryListener(Logger logger) {
        this.logger = logger;
    }

    public boolean hadFailures() {
        return hadFailures;
    }

    public void beforeSuite(TestSuite suite) {
    }

    public void afterSuite(TestSuite suite) {
    }

    public void beforeTest(Test test) {
    }

    public void afterTest(Test test, TestResult result) {
        switch (result.getResultType()) {
            case SUCCESS:
                logger.info("Test {} PASSED", test.getName());
                break;
            case SKIPPED:
                logger.info("Test {} SKIPPED", test.getName());
                break;
            case FAILURE:
                logger.error("Test {} FAILED", test.getName());
                hadFailures = true;
                break;
            default:
                throw new IllegalArgumentException();
        }
    }
}
