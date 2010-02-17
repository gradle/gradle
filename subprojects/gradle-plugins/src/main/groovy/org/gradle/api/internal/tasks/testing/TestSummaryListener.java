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

import org.apache.commons.lang.StringUtils;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;
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

    public void beforeSuite(Test suite) {
        logger.debug("Started {}", suite);
    }

    public void afterSuite(Test suite) {
        logger.debug("Finished {}", suite);
    }

    public void beforeTest(Test test) {
        logger.debug("Started {}", test);
    }

    public void afterTest(Test test, TestResult result) {
        String testDescription = StringUtils.capitalize(test.toString());
        switch (result.getResultType()) {
            case SUCCESS:
                logger.info("{} PASSED", testDescription);
                break;
            case SKIPPED:
                logger.info("{} SKIPPED", testDescription);
                break;
            case FAILURE:
                logger.error("{} FAILED", testDescription);
                hadFailures = true;
                break;
            default:
                throw new IllegalArgumentException();
        }
    }
}
