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

package org.gradle.testkit.runner;

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
     * @return the standard output
     */
    String getStandardOutput();

    /**
     * Returns the standard error messages of a build execution.
     *
     * @return the standard error messages
     */
    String getStandardError();

    /**
     * Returns all tasks of the build execution independent of their result.
     *
     * @return all tasks
     */
    List<BuildTask> getTasks();

    /**
     * Returns tasks of the build execution for a given result.
     *
     * @param result the given task result
     * @return the filtered tasks
     */
    List<BuildTask> tasks(TaskResult result);

    /**
     * Returns task paths of the build execution for a given result.
     *
     * @param result the given task result
     * @return the filtered task paths
     */
    List<String> taskPaths(TaskResult result);
}
