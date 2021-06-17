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
package org.gradle.api.internal.tasks.execution;

import org.gradle.api.internal.changedetection.TaskExecutionMode;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.properties.TaskProperties;
import org.gradle.execution.plan.LocalTaskNode;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.operations.BuildOperationContext;

import java.util.Optional;

public class DefaultTaskExecutionContext implements TaskExecutionContext {

    private final LocalTaskNode localTaskNode;
    private final TaskProperties properties;
    private final WorkValidationContext validationContext;
    private final ValidationAction validationAction;
    private TaskExecutionMode taskExecutionMode;
    private BuildOperationContext snapshotTaskInputsBuildOperationContext;

    public DefaultTaskExecutionContext(LocalTaskNode localTaskNode, TaskProperties taskProperties, WorkValidationContext validationContext, ValidationAction validationAction) {
        this.localTaskNode = localTaskNode;
        this.properties = taskProperties;
        this.validationContext = validationContext;
        this.validationAction = validationAction;
    }

    @Override
    public LocalTaskNode getLocalTaskNode() {
        return localTaskNode;
    }

    @Override
    public TaskExecutionMode getTaskExecutionMode() {
        return taskExecutionMode;
    }

    @Override
    public WorkValidationContext getValidationContext() {
        return validationContext;
    }

    @Override
    public ValidationAction getValidationAction() {
        return validationAction;
    }

    @Override
    public void setTaskExecutionMode(TaskExecutionMode taskExecutionMode) {
        this.taskExecutionMode = taskExecutionMode;
    }

    @Override
    public TaskProperties getTaskProperties() {
        return properties;
    }

    @Override
    public Optional<BuildOperationContext> removeSnapshotTaskInputsBuildOperationContext() {
        Optional<BuildOperationContext> result = Optional.ofNullable(snapshotTaskInputsBuildOperationContext);
        snapshotTaskInputsBuildOperationContext = null;
        return result;
    }

    @Override
    public void setSnapshotTaskInputsBuildOperationContext(BuildOperationContext snapshotTaskInputsBuildOperation) {
        this.snapshotTaskInputsBuildOperationContext = snapshotTaskInputsBuildOperation;
    }
}
