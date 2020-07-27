/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.internal.changedetection.TaskExecutionMode;
import org.gradle.api.internal.tasks.properties.TaskProperties;
import org.gradle.execution.plan.LocalTaskNode;
import org.gradle.internal.operations.BuildOperationContext;

import java.util.Optional;

public interface TaskExecutionContext {

    LocalTaskNode getLocalTaskNode();

    TaskExecutionMode getTaskExecutionMode();

    void setTaskExecutionMode(TaskExecutionMode taskExecutionMode);

    /**
     * Sets the execution time of the task to be the elapsed time since start to now.
     *
     * This is _only_ used for origin time tracking. It is not used to report the time taken in _this_ build.
     * If the outputs from this execution are reused, this time will be considered to be the origin execution time.
     *
     * This time includes from the very start of the task (e.g. include input snapshotting), the task actions, and output snapshotting.
     * It does not include time taken to write back to the build cache, or time to update the task history repository.
     *
     * This can only be called once per task.
     */
    long markExecutionTime();

    void setTaskProperties(TaskProperties properties);

    TaskProperties getTaskProperties();

    /**
     * Gets and clears the context of the build operation designed to measure the time taken
     * by capturing input snapshotting and cache key calculation.
     */
    Optional<BuildOperationContext> removeSnapshotTaskInputsBuildOperationContext();

    /**
     * Sets the context for the build operation designed to measure the time taken
     * by capturing input snapshotting and cache key calculation.
     */
    void setSnapshotTaskInputsBuildOperationContext(BuildOperationContext operation);
}
