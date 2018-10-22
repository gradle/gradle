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

import org.gradle.api.internal.changedetection.TaskArtifactState;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.caching.internal.tasks.TaskOutputCachingBuildCacheKey;
import org.gradle.internal.execution.history.AfterPreviousExecutionState;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;

import javax.annotation.Nullable;
import java.util.List;

public class DefaultTaskExecutionContext implements TaskExecutionContext {

    private AfterPreviousExecutionState afterPreviousExecution;
    private TaskArtifactState taskArtifactState;
    private TaskOutputCachingBuildCacheKey buildCacheKey;
    private List<String> upToDateMessages;
    private TaskProperties taskProperties;
    private boolean taskCachingEnabled;
    private Long executionTime;

    private final Timer executionTimer;

    public DefaultTaskExecutionContext() {
        this.executionTimer = Time.startTimer();
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
    public TaskArtifactState getTaskArtifactState() {
        return taskArtifactState;
    }

    @Override
    public void setTaskArtifactState(TaskArtifactState taskArtifactState) {
        this.taskArtifactState = taskArtifactState;
    }

    @Override
    public TaskOutputCachingBuildCacheKey getBuildCacheKey() {
        return buildCacheKey;
    }

    @Override
    public void setBuildCacheKey(TaskOutputCachingBuildCacheKey buildCacheKey) {
        this.buildCacheKey = buildCacheKey;
    }

    public long markExecutionTime() {
        if (this.executionTime != null) {
            throw new IllegalStateException("execution time already set");
        }

        return this.executionTime = executionTimer.getElapsedMillis();
    }

    @Override
    public long getExecutionTime() {
        if (this.executionTime == null) {
            throw new IllegalStateException("execution time not yet set");
        }

        return executionTime;
    }

    @Override
    @Nullable
    public List<String> getUpToDateMessages() {
        return upToDateMessages;
    }

    @Override
    public void setUpToDateMessages(List<String> upToDateMessages) {
        this.upToDateMessages = upToDateMessages;
    }

    @Override
    public void setTaskProperties(TaskProperties taskProperties) {
        this.taskProperties = taskProperties;
    }

    @Override
    public TaskProperties getTaskProperties() {
        return taskProperties;
    }

    @Override
    public boolean isTaskCachingEnabled() {
        return taskCachingEnabled;
    }

    @Override
    public void setTaskCachingEnabled(boolean taskCachingEnabled) {
        this.taskCachingEnabled = taskCachingEnabled;
    }
}
