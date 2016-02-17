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

import org.gradle.api.tasks.testing.TestResult;

import java.io.Serializable;
import java.util.List;

public class DefaultTestResult implements TestResult, Serializable {
    private final List<Throwable> failures;
    private final ResultType resultType;
    private final long startTime;
    private final long endTime;
    private final long testCount;
    private final long successfulCount;
    private final long failedCount;

    public DefaultTestResult(TestState state) {
        this(state.resultType, state.getStartTime(), state.getEndTime(), state.testCount, state.successfulCount, state.failedCount, state.failures);
    }

    public DefaultTestResult(ResultType resultType, long startTime, long endTime, long testCount, long successfulCount, long failedCount, List<Throwable> failures) {
        this.resultType = resultType;
        this.startTime = startTime;
        this.endTime = endTime;
        this.testCount = testCount;
        this.successfulCount = successfulCount;
        this.failedCount = failedCount;
        this.failures = failures;
    }

    @Override
    public ResultType getResultType() {
        return resultType;
    }

    @Override
    public Throwable getException() {
        return failures.isEmpty() ? null : failures.get(0);
    }

    @Override
    public List<Throwable> getExceptions() {
        return failures;
    }

    @Override
    public long getStartTime() {
        return startTime;
    }

    @Override
    public long getEndTime() {
        return endTime;
    }

    @Override
    public long getTestCount() {
        return testCount;
    }

    @Override
    public long getSuccessfulTestCount() {
        return successfulCount;
    }

    @Override
    public long getSkippedTestCount() {
        return testCount - successfulCount - failedCount;
    }

    @Override
    public long getFailedTestCount() {
        return failedCount;
    }

    @Override
    public String toString() {
        return resultType.toString();
    }
}
