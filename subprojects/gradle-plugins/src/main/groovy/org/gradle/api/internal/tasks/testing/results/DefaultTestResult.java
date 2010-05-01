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

public class DefaultTestResult implements TestResult, Serializable {
    private final Throwable error;
    private final ResultType result;
    private final long startTime;
    private final long endTime;
    private final long testCount;
    private final long successfulCount;
    private final long failedCount;

    public DefaultTestResult(ResultType result, Throwable error, long startTime, long endTime, long testCount, long successfulCount, long failedCount) {
        this.error = error;
        this.result = result;
        this.startTime = startTime;
        this.endTime = endTime;
        this.testCount = testCount;
        this.successfulCount = successfulCount;
        this.failedCount = failedCount;
    }

    public ResultType getResultType() {
        return result;
    }

    public Throwable getException() {
        if (result != ResultType.FAILURE) {
            throw new IllegalStateException("No exception to return");
        }
        return error;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public long getTestCount() {
        return testCount;
    }

    public long getSuccessfulTestCount() {
        return successfulCount;
    }

    public long getSkippedTestCount() {
        return testCount - successfulCount - failedCount;
    }

    public long getFailedTestCount() {
        return failedCount;
    }

    @Override
    public String toString() {
        return result.toString();
    }
}
