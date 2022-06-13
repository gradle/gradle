/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.testing.base.internal;

import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.testing.base.TestSuiteTarget;

/**
 * Defines an abstract base class for all test suite targets that can provide a {@link TestListener}
 * which counts successful and failed tests.
 */
public abstract class AbstractTestSuiteTarget implements TestSuiteTarget {
    protected CountingOnlyListener getTestCountLogger() {
        return new CountingOnlyListener();
    }

    /**
     * A {@link TestListener} that counts successful and failed tests as they complete.
     */
    protected static final class CountingOnlyListener implements TestListener {
        private long totalTests;
        private long failedTests;
        private long skippedTests;

        @Override public void beforeSuite(TestDescriptor suite) { /* no-op */ }
        @Override public void afterSuite(TestDescriptor suite, TestResult result) { /* no-op */ }
        @Override public void beforeTest(TestDescriptor testDescriptor) { /* no-op */ }

        @Override
        public void afterTest(TestDescriptor testDescriptor, TestResult result) {
            totalTests += result.getTestCount();
            failedTests += result.getFailedTestCount();
            skippedTests += result.getSkippedTestCount();
        }

        /**
         * Returns a string representation of the test counts which reports how many of the total tests were successful
         * for display on the console.
         *
         * If 7 tests ran and 2 failed, this method would report:
         * <pre>
         * 5/7
         * </pre>
         * if, in addition, 1 test was skipped, this method would report:
         * <pre>
         * 4/6 (1 skipped)
         * </pre>
         *
         * @return result message
         */
        public String getFormattedResultCount() {
            String result = String.format("%d/%d", totalTests - failedTests - skippedTests, totalTests - skippedTests);
            if (skippedTests > 0) {
                result += String.format(" (%d skipped)", skippedTests);
            }
            return result;
        }
    }
}

