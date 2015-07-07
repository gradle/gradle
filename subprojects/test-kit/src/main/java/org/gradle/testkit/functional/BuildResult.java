/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.functional;

import org.gradle.api.Incubating;

import java.util.List;

/**
 * Result of a build execution.
 *
 * @since 2.6
 */
@Incubating
public interface BuildResult {
    /**
     * Returns the standard output of a build execution.
     *
     * @return Standard output
     */
    String getStandardOutput();

    /**
     * Returns the standard error messages of a build execution.
     *
     * @return Standard error messages
     */
    String getStandardError();

    /**
     * Returns the executed tasks of a build execution. The execution status of a task can either be SKIPPED, UP-TO-DATE, successful or failed.
     *
     * @return Executed tasks
     */
    List<String> getExecutedTasks();

    /**
     * Returns executed tasks that were marked SKIPPED, UP-TO-DATE or failed during build execution.
     *
     * @return Skipped tasks
     */
    List<String> getSkippedTasks();
}
