/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.tasks.testing;

import org.gradle.api.internal.tasks.testing.TestExecuter;

public class FailFastTestListener implements TestListener {
    private final TestExecuter testExecuter;

    public FailFastTestListener(TestExecuter testExecuter) {
        this.testExecuter = testExecuter;
    }

    @Override
    public void beforeSuite(TestDescriptor suite) {
        // do nothing
    }

    @Override
    public void afterSuite(TestDescriptor suite, TestResult result) {
        if (result.getResultType() == TestResult.ResultType.FAILURE) {
            testExecuter.stopNow();
        }
    }

    @Override
    public void beforeTest(TestDescriptor testDescriptor) {
        // do nothing
    }

    @Override
    public void afterTest(TestDescriptor testDescriptor, TestResult result) {
        if (result.getResultType() == TestResult.ResultType.FAILURE) {
            testExecuter.stopNow();
        }
    }
}
