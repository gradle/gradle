/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.testretry;

import org.gradle.api.Action;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.testing.Test;

/**
 * Allows configuring test retry mechanics.
 * <p>
 * This extension is added with the name 'retry' to all {@link Test} tasks.
 */
public interface TestRetryTaskExtension {

    /**
     * The name of the extension added to each test task.
     */
    String NAME = "retry";

    /**
     * Whether tests that initially fail and then pass on retry should fail the task.
     * <p>
     * This setting defaults to {@code false},
     * which results in the task not failing if all tests pass on retry.
     * <p>
     * This setting has no effect if {@link Test#getIgnoreFailures()} is set to true.
     *
     * @return whether tests that initially fails and then pass on retry should fail the task
     */
    Property<Boolean> getFailOnPassedAfterRetry();

    /**
     * Whether tests that initially fail and then are skipped on retry should fail the task.
     * <p>
     * This setting defaults to {@code true} (for backward compatibility),
     * which results in the task failing if any of tests skip on retry.
     * <p>
     * This setting has no effect if {@link Test#getIgnoreFailures()} is set to true.
     *
     * @return whether tests that initially fails and then are skipped on retry should fail the task
     */
    Property<Boolean> getFailOnSkippedAfterRetry();

    /**
     * The maximum number of times to retry an individual test.
     * <p>
     * This setting defaults to {@code 0}, which results in no retries.
     * Any value less than 1 disables retrying.
     *
     * @return the maximum number of times to retry an individual test
     */
    Property<Integer> getMaxRetries();

    /**
     * The maximum number of test failures that are allowed before retrying is disabled.
     * <p>
     * The count applies to each round of test execution.
     * For example, if maxFailures is 5 and 4 tests initially fail and then 3 again on retry,
     * this will not be considered too many failures and retrying will continue (if maxRetries {@literal >} 1).
     * If 5 or more tests were to fail initially then no retry would be attempted.
     * <p>
     * This setting defaults to {@code 0}, which results in no limit.
     * Any value less than 1 results in no limit.
     *
     * @return the maximum number of test failures that are allowed before retrying is disabled
     */
    Property<Integer> getMaxFailures();

    /**
     * The filter for specifying which tests may be retried.
     */
    Filter getFilter();

    /**
     * The filter for specifying which tests may be retried.
     */
    void filter(Action<? super Filter> action);

    /**
     * A filter for specifying which tests may be retried.
     * <p>
     * By default, all tests are eligible for retrying.
     */
    interface Filter {

        /**
         * The patterns used to include tests based on their class name.
         * <p>
         * The pattern string matches against qualified class names.
         * It may contain '*' characters, which match zero or more of any character.
         * <p>
         * A class name only has to match one pattern to be included.
         * <p>
         * If no patterns are specified, all classes (that also meet other configured filters) will be included.
         */
        SetProperty<String> getIncludeClasses();

        /**
         * The patterns used to include tests based on their class level annotations.
         * <p>
         * The pattern string matches against the qualified class names of a test class's annotations.
         * It may contain '*' characters, which match zero or more of any character.
         * <p>
         * A class need only have one annotation matching any of the patterns to be included.
         * <p>
         * Annotations present on super classes that are {@code @Inherited} are considered when inspecting subclasses.
         * <p>
         * If no patterns are specified, all classes (that also meet other configured filters) will be included.
         */
        SetProperty<String> getIncludeAnnotationClasses();

        /**
         * The patterns used to exclude tests based on their class name.
         * <p>
         * The pattern string matches against qualified class names.
         * It may contain '*' characters, which match zero or more of any character.
         * <p>
         * A class name only has to match one pattern to be excluded.
         * <p>
         * If no patterns are specified, all classes (that also meet other configured filters) will be included.
         */
        SetProperty<String> getExcludeClasses();

        /**
         * The patterns used to exclude tests based on their class level annotations.
         * <p>
         * The pattern string matches against the qualified class names of a test class's annotations.
         * It may contain '*' characters, which match zero or more of any character.
         * <p>
         * A class need only have one annotation matching any of the patterns to be excluded.
         * <p>
         * Annotations present on super classes that are {@code @Inherited} are considered when inspecting subclasses.
         * <p>
         * If no patterns are specified, all classes (that also meet other configured filters) will be included.
         */
        SetProperty<String> getExcludeAnnotationClasses();

    }


    /**
     * The set of criteria specifying which test classes must be retried as a whole unit
     * if retries are enabled and the test class passes the configured {@linkplain TestRetryTaskExtension#getFilter filter}.
     */
    ClassRetryCriteria getClassRetry();

    /**
     * The set of criteria specifying which test classes must be retried as a whole unit
     * if retries are enabled and the test class passes the configured {@linkplain TestRetryTaskExtension#getFilter filter}.
     */
    void classRetry(Action<? super ClassRetryCriteria> action);

    /**
     * The set of criteria specifying which test classes must be retried as a whole unit
     * if retries are enabled and the test class passes the configured {@linkplain TestRetryTaskExtension#getFilter filter}.
     */
    interface ClassRetryCriteria {

        /**
         * The patterns used to include tests based on their class name.
         * <p>
         * The pattern string matches against qualified class names.
         * It may contain '*' characters, which match zero or more of any character.
         * <p>
         * A class name only has to match one pattern to be included.
         * <p>
         * If no patterns are specified, all classes (that also meet other configured filters) will be included.
         */
        SetProperty<String> getIncludeClasses();

        /**
         * The patterns used to include tests based on their class level annotations.
         * <p>
         * The pattern string matches against the qualified class names of a test class's annotations.
         * It may contain '*' characters, which match zero or more of any character.
         * <p>
         * A class need only have one annotation matching any of the patterns to be included.
         * <p>
         * Annotations present on super classes that are {@code @Inherited} are considered when inspecting subclasses.
         * <p>
         * If no patterns are specified, all classes (that also meet other configured filters) will be included.
         */
        SetProperty<String> getIncludeAnnotationClasses();

    }

}
