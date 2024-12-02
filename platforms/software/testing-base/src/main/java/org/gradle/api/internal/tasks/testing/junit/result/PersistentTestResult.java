/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit.result;

import com.google.common.collect.ImmutableList;
import org.gradle.api.tasks.testing.TestResult;

import java.util.List;

/**
 * Represents a test result that can be stored for a long time (potentially across process invocations).
 *
 * <p>
 * This is different from {@link org.gradle.api.tasks.testing.TestResult} which contains no identifying information about the test that produced it, and is not designed to be stored.
 * Specifically, this class does not contain exception objects.
 * </p>
 */
public final class PersistentTestResult {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private String displayName;
        private TestResult.ResultType resultType;
        private Long startTime;
        private Long endTime;
        private final ImmutableList.Builder<PersistentTestFailure> failures = ImmutableList.builder();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder resultType(TestResult.ResultType resultType) {
            this.resultType = resultType;
            return this;
        }

        public Builder startTime(long startTime) {
            this.startTime = startTime;
            return this;
        }

        public Builder endTime(long endTime) {
            this.endTime = endTime;
            return this;
        }

        public Builder addFailure(PersistentTestFailure failure) {
            failures.add(failure);
            return this;
        }

        public PersistentTestResult build() {
            if (name == null) {
                throw new IllegalStateException("name is required");
            }
            if (displayName == null) {
                throw new IllegalStateException("displayName is required");
            }
            if (resultType == null) {
                throw new IllegalStateException("resultType is required");
            }
            if (startTime == null) {
                throw new IllegalStateException("startTime is required");
            }
            if (endTime == null) {
                throw new IllegalStateException("endTime is required");
            }
            return new PersistentTestResult(name, displayName, resultType, startTime, endTime, failures.build());
        }
    }

    private static TestResult.ResultType mergeResultTypes(TestResult.ResultType ours, TestResult.ResultType theirs) {
        // Equivalent types give the same result.
        if (ours == theirs) {
            return ours;
        }
        if ((ours == TestResult.ResultType.SKIPPED && theirs == TestResult.ResultType.SUCCESS) ||
            (ours == TestResult.ResultType.SUCCESS && theirs == TestResult.ResultType.SKIPPED)) {
            // I'm not sure exactly what the semantics here would be.
            throw new IllegalArgumentException("Cannot merge a skipped result with a successful result");
        }
        if (ours != TestResult.ResultType.FAILURE && theirs != TestResult.ResultType.FAILURE) {
            // This is unreachable. At least one of the remaining must be a FAILURE.
            // SUCCESS + SKIPPED is an error.
            // SUCCESS + SUCCESS is handled above.
            // SUCCESS + FAILURE is the only remaining case with SUCCESS.
            // SKIPPED + SKIPPED is handled above.
            // SKIPPED + FAILURE is the only remaining case with SKIPPED.
            // FAILURE + FAILURE is handled above.
            throw new AssertionError("At least one of the results must be a FAILURE");
        }
        // When there is at least one FAILURE, the result is a FAILURE.
        return TestResult.ResultType.FAILURE;
    }

    private final String name;
    private final String displayName;
    private final TestResult.ResultType resultType;
    private final long startTime;
    private final long endTime;
    private final List<PersistentTestFailure> failures;

    public PersistentTestResult(String name, String displayName, TestResult.ResultType resultType, long startTime, long endTime, List<PersistentTestFailure> failures) {
        this.name = name;
        this.displayName = displayName;
        this.resultType = resultType;
        this.startTime = startTime;
        this.endTime = endTime;
        this.failures = failures;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public TestResult.ResultType getResultType() {
        return resultType;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public long getDuration() {
        return endTime - startTime;
    }

    public List<PersistentTestFailure> getFailures() {
        return failures;
    }

    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.name(name);
        builder.displayName(displayName);
        builder.resultType(resultType);
        builder.startTime(startTime);
        builder.endTime(endTime);
        builder.failures.addAll(failures);
        return builder;
    }

    public PersistentTestResult merge(PersistentTestResult other) {
        if (!name.equals(other.name)) {
            throw new IllegalArgumentException("Cannot merge results with different names");
        }
        if (!displayName.equals(other.displayName)) {
            throw new IllegalArgumentException("Cannot merge results with different display names");
        }
        TestResult.ResultType resultType = mergeResultTypes(this.resultType, other.resultType);
        long startTime = Math.min(this.startTime, other.startTime);
        long endTime = Math.max(this.endTime, other.endTime);
        ImmutableList.Builder<PersistentTestFailure> failures = ImmutableList.builderWithExpectedSize(this.failures.size() + other.failures.size());
        failures.addAll(this.failures);
        failures.addAll(other.failures);
        return new PersistentTestResult(name, displayName, resultType, startTime, endTime, failures.build());
    }
}
