/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.testing.base;

import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.AbstractTestTask;

/**
 * Base test suite target.
 *
 * A test suite target is a collection of tests that run in a particular context (operating system, Java runtime, etc).
 *
 * @since 7.3
 */
@Incubating
public interface TestSuiteTarget extends Named {
    /**
     * Provider of {@link AbstractTestTask} test class that will run this target.
     *
     * @return test task instance
     * @since 7.6
     */
    TaskProvider<? extends AbstractTestTask> getTestTask();

    /**
     * The {@link TestSuite} that created this target.
     *
     * @return name of the test suite that created this target
     * @since 7.6
     */
    String getTestSuiteName();

    /**
     * Gets a formatted summary of the result of running this target for display on the console.
     *
     * @return summary
     * @since 7.6
     */
    String getFormattedSummary();
}
