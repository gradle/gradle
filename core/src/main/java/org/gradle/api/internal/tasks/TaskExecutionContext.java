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
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.reflect.validation.TypeValidationContext;

import java.util.Optional;

public interface TaskExecutionContext {

    LocalTaskNode getLocalTaskNode();

    TaskExecutionMode getTaskExecutionMode();

    WorkValidationContext getValidationContext();

    ValidationAction getValidationAction();

    void setTaskExecutionMode(TaskExecutionMode taskExecutionMode);

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

    interface ValidationAction {
        void validate(boolean historyMaintained, TypeValidationContext validationContext);
    }
}
