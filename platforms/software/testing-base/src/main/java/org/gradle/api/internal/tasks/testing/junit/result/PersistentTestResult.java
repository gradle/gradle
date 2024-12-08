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

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

/**
 * Represents a test result that can be stored for a long time (potentially across process invocations).
 *
 * <p>
 * This is different from {@link org.gradle.api.tasks.testing.TestResult} which contains no identifying information about the test that produced it, and is not designed to be stored.
 * Specifically, this class does not contain exception objects.
 * </p>
 */
public final class PersistentTestResult {
    /**
     * Legacy properties used for compatibility with existing JVM test reporting behavior.
     *
     * <p>
     * These should be migrated to metadata when that is added.
     * </p>
     */
    public static final class LegacyProperties {
        private final boolean isClass;
        private final String className;
        private final String classDisplayName;

        public LegacyProperties(boolean isClass, String className, String classDisplayName) {
            this.isClass = isClass;
            this.className = className;
            this.classDisplayName = classDisplayName;
        }

        /**
         * Returns true if this test result represents a test class, rather than something else.
         *
         * <p>
         * This was added to allow reports to distinguish between test classes and other test results. In the future, this should likely be replaced with a more general mechanism for distinguishing
         * different types of test results, to allow better reporting outside of a JVM context.
         * </p>
         *
         * @return true if this test result represents a test class
         */
        public boolean isClass() {
            return isClass;
        }

        /**
         * The class name associated with this result. This may be the enclosing class of the test method, or just the same as the name if this {@link #isClass()}.
         *
         * <p>
         * This value may not represent a parent node or an existing class. It must be considered different from inspecting any parent nodes to determine the class name.
         * For example, see {@link org.gradle.testing.cucumberjvm.CucumberJVMReportIntegrationTest}.
         * </p>
         *
         * @return the class name
         */
        public String getClassName() {
            return className;
        }

        /**
         * The class display name associated with this result. This may be the enclosing class of the test method, or just the same as the display name if this {@link #isClass()}.
         *
         * <p>
         * This value may not represent a parent node or an existing class. It must be considered different from inspecting any parent nodes to determine the class display name.
         * For example, see {@link org.gradle.testing.cucumberjvm.CucumberJVMReportIntegrationTest}.
         * </p>
         *
         * @return the class display name
         */
        public String getClassDisplayName() {
            return classDisplayName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            LegacyProperties that = (LegacyProperties) o;
            return isClass == that.isClass && Objects.equals(className, that.className) && Objects.equals(classDisplayName, that.classDisplayName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(isClass, className, classDisplayName);
        }
    }

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
        @Nullable
        private LegacyProperties legacyProperties;

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

        public Builder legacyProperties(LegacyProperties legacyProperties) {
            this.legacyProperties = legacyProperties;
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
            return new PersistentTestResult(name, displayName, resultType, startTime, endTime, failures.build(), legacyProperties);
        }
    }

    private final String name;
    private final String displayName;
    private final TestResult.ResultType resultType;
    private final long startTime;
    private final long endTime;
    private final List<PersistentTestFailure> failures;
    @Nullable
    private final LegacyProperties legacyProperties;

    public PersistentTestResult(String name, String displayName, TestResult.ResultType resultType, long startTime, long endTime, List<PersistentTestFailure> failures, @Nullable LegacyProperties legacyProperties) {
        this.name = name;
        this.displayName = displayName;
        this.resultType = resultType;
        this.startTime = startTime;
        this.endTime = endTime;
        this.failures = failures;
        this.legacyProperties = legacyProperties;
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

    @Nullable
    public LegacyProperties getLegacyProperties() {
        return legacyProperties;
    }

    public Builder toBuilder() {
        Builder builder = new Builder();
        builder.name(name);
        builder.displayName(displayName);
        builder.resultType(resultType);
        builder.startTime(startTime);
        builder.endTime(endTime);
        builder.failures.addAll(failures);
        builder.legacyProperties(legacyProperties);
        return builder;
    }
}
