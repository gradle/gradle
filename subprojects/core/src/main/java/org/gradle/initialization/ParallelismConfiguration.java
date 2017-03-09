/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.api.Incubating;

/**
 * A {@code ParallelismConfiguration} defines the parallel settings for a Gradle build.
 */
public interface ParallelismConfiguration {
    /**
     * Returns true if parallel project execution is enabled.
     *
     * @see #getMaxWorkerCount()
     */
    @Incubating
    boolean isParallelProjectExecutionEnabled();

    /**
     * Enables/disables parallel project execution.
     *
     * @see #isParallelProjectExecutionEnabled()
     */
    @Incubating
    void setParallelProjectExecutionEnabled(boolean parallelProjectExecution);

    /**
     * Returns the maximum number of concurrent workers used for underlying build operations.
     *
     * Workers can be threads, processes or whatever Gradle considers a "worker". Some examples:
     *
     * <ul>
     *     <li>A thread running a task</li>
     *     <li>A test process</li>
     *     <li>A language compiler in a forked process</li>
     * </ul>
     *
     * Defaults to the number of processors available to the Java virtual machine.
     *
     * @return maximum number of concurrent workers, always >= 1.
     * @see java.lang.Runtime#availableProcessors()
     */
    @Incubating
    int getMaxWorkerCount();

    /**
     * Specifies the maximum number of concurrent workers used for underlying build operations.
     *
     * @throws IllegalArgumentException if {@code maxWorkerCount} is &lt; 1
     * @see #getMaxWorkerCount()
     */
    @Incubating
    void setMaxWorkerCount(int maxWorkerCount);

}
