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

import org.gradle.api.tasks.testing.TestResult;

import java.io.Serializable;

public class TestCompleteEvent implements Serializable {
    private final long endTime;
    private TestResult.ResultType resultType;
    private Throwable failure;

    public TestCompleteEvent(long endTime) {
        this.endTime = endTime;
    }

    public TestCompleteEvent(long endTime, TestResult.ResultType resultType, Throwable failure) {
        this.endTime = endTime;
        this.resultType = resultType;
        this.failure = failure;
    }

    public long getEndTime() {
        return endTime;
    }

    public Throwable getFailure() {
        return failure;
    }

    public void setFailure(Throwable failure) {
        this.failure = failure;
    }

    public TestResult.ResultType getResultType() {
        return resultType;
    }

    public void setResultType(TestResult.ResultType resultType) {
        this.resultType = resultType;
    }
}
