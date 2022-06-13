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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.Incubating;
import org.gradle.internal.exceptions.MergeableException;
import org.gradle.api.tasks.VerificationException;
import org.gradle.testing.base.TestSuiteTarget;

import javax.annotation.Nullable;

/**
 * Extension of {@link VerificationException} indicated that the failing tests were part of a test suite, and records the failing target.
 *
 * This implements {@link MergeableException}, so upon failure it is possible to combine multiple test suite failures into a
 * single, consolidated report.
 *
 * @since 7.6
 */
@Incubating
public class TestSuiteVerificationException extends VerificationException implements MergeableException {
    private final TestSuiteTarget testSuiteTarget;
    @Nullable private final String reportUrl;

    public TestSuiteVerificationException(String message, @Nullable String reportUrl, TestSuiteTarget testSuiteTarget) {
        super(message);
        this.reportUrl = reportUrl;
        this.testSuiteTarget = testSuiteTarget;
    }

    public TestSuiteTarget getTestSuiteTarget() {
        return testSuiteTarget;
    }

    @Override
    public TestSuiteExceptionMerger getMerger() {
        return new TestSuiteExceptionMerger();
    }

    @Nullable
    public String getReportUrl() {
        return reportUrl;
    }

    public String getSuiteName() {
        return testSuiteTarget.getTestSuite().getName();
    }

    public String getTargetName() {
        return testSuiteTarget.getName();
    }

    public String getTaskName() {
        return testSuiteTarget.getTestTask().getName();
    }
}
