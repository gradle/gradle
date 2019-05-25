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

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.internal.OverlappingOutputs;
import org.gradle.api.internal.changedetection.TaskExecutionMode;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.properties.TaskProperties;
import org.gradle.execution.plan.LocalTaskNode;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.execution.history.BeforeExecutionState;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.operations.ExecutingBuildOperation;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;

import javax.annotation.Nullable;
import java.util.Optional;

public class DefaultTaskExecutionContext implements TaskExecutionContext {

    private final LocalTaskNode localTaskNode;
    private AfterPreviousExecutionState afterPreviousExecution;
    private OverlappingOutputs overlappingOutputs;
    private ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFilesBeforeExecution;
    private BeforeExecutionState beforeExecutionState;
    private TaskExecutionMode taskExecutionMode;
    private TaskProperties properties;
    private boolean taskCachingEnabled;
    private Long executionTime;
    private ExecutingBuildOperation snapshotTaskInputsBuildOperation;

    private final Timer executionTimer;

    public DefaultTaskExecutionContext(LocalTaskNode localTaskNode) {
        this.localTaskNode = localTaskNode;
        this.executionTimer = Time.startTimer();
    }

    @Override
    public LocalTaskNode getLocalTaskNode() {
        return localTaskNode;
    }

    @Nullable
    @Override
    public AfterPreviousExecutionState getAfterPreviousExecution() {
        return afterPreviousExecution;
    }

    @Override
    public void setAfterPreviousExecution(@Nullable AfterPreviousExecutionState afterPreviousExecution) {
        this.afterPreviousExecution = afterPreviousExecution;
    }

    @Override
    public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> getOutputFilesBeforeExecution() {
        return outputFilesBeforeExecution;
    }

    @Override
    public void setOutputFilesBeforeExecution(ImmutableSortedMap<String, CurrentFileCollectionFingerprint> outputFilesBeforeExecution) {
        this.outputFilesBeforeExecution = outputFilesBeforeExecution;
    }

    @Override
    public Optional<OverlappingOutputs> getOverlappingOutputs() {
        return Optional.ofNullable(overlappingOutputs);
    }

    @Override
    public void setOverlappingOutputs(OverlappingOutputs overlappingOutputs) {
        this.overlappingOutputs = overlappingOutputs;
    }

    @Override
    public Optional<BeforeExecutionState> getBeforeExecutionState() {
        return Optional.ofNullable(beforeExecutionState);
    }

    @Override
    public void setBeforeExecutionState(BeforeExecutionState beforeExecutionState) {
        this.beforeExecutionState = beforeExecutionState;
    }

    @Override
    public TaskExecutionMode getTaskExecutionMode() {
        return taskExecutionMode;
    }

    @Override
    public void setTaskExecutionMode(TaskExecutionMode taskExecutionMode) {
        this.taskExecutionMode = taskExecutionMode;
    }

    @Override
    public long markExecutionTime() {
        if (this.executionTime != null) {
            throw new IllegalStateException("execution time already set");
        }

        return this.executionTime = executionTimer.getElapsedMillis();
    }

    @Override
    public void setTaskProperties(TaskProperties properties) {
        this.properties = properties;
    }

    @Override
    public TaskProperties getTaskProperties() {
        return properties;
    }

    @Override
    public boolean isTaskCachingEnabled() {
        return taskCachingEnabled;
    }

    @Override
    public void setTaskCachingEnabled(boolean taskCachingEnabled) {
        this.taskCachingEnabled = taskCachingEnabled;
    }

    @Override
    public Optional<ExecutingBuildOperation> removeSnapshotTaskInputsBuildOperation() {
        Optional<ExecutingBuildOperation> result = Optional.ofNullable(snapshotTaskInputsBuildOperation);
        snapshotTaskInputsBuildOperation = null;
        return result;
    }

    @Override
    public void setSnapshotTaskInputsBuildOperation(ExecutingBuildOperation snapshotTaskInputsBuildOperation) {
        this.snapshotTaskInputsBuildOperation = snapshotTaskInputsBuildOperation;
    }
}
