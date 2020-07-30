/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.tasks.testing.TestExecutionException;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestListener;
import org.gradle.api.tasks.testing.TestResult;

import static org.gradle.api.tasks.testing.TestResult.ResultType.FAILURE;

public class NoMatchingTestsReporter implements TestListener {
    private final String message;

    public NoMatchingTestsReporter(String message) {
        this.message = message;
    }

    @Override
    public void beforeSuite(TestDescriptor suite) {}

    @Override
    public void afterSuite(TestDescriptor suite, TestResult result) {
        if (suite.getParent() == null && result.getTestCount() == 0 && result.getResultType() != FAILURE) {
            throw new TestExecutionException(message);
        }
    }

    @Override
    public void beforeTest(TestDescriptor testDescriptor) {}

    @Override
    public void afterTest(TestDescriptor testDescriptor, TestResult result) {}
}
