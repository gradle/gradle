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
import org.gradle.internal.scan.UsedByScanPlugin;
import org.jspecify.annotations.Nullable;

@UsedByScanPlugin
public class TestCompleteEvent {
    private final long endTime;
    private final TestResult.ResultType resultType;
    @Nullable
    private final String skipReason;

    @UsedByScanPlugin("test-distribution")
    public TestCompleteEvent(long endTime) {
        this(endTime, null);
    }

    @UsedByScanPlugin("test-distribution")
    public TestCompleteEvent(long endTime, TestResult.ResultType resultType) {
        this(endTime, resultType, null);
    }

    public TestCompleteEvent(long endTime, TestResult.ResultType resultType, @Nullable String skipReason) {
        this.endTime = endTime;
        this.resultType = resultType;
        this.skipReason = skipReason;
    }

    public long getEndTime() {
        return endTime;
    }

    public TestResult.@Nullable ResultType getResultType() {
        return resultType;
    }

    /**
     * The reason the test was skipped, if reported by the test framework.
     *
     * @return the skip reason, or {@code null} if none was reported
     */
    @Nullable
    public String getSkipReason() {
        return skipReason;
    }

    @Override
    public String toString() {
        return "complete(" + resultType + ")=" + endTime;
    }
}
