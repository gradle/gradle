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
import org.gradle.internal.id.UniqueId;

import javax.annotation.Nullable;
import java.util.List;

public class DefaultTaskExecutionContext implements TaskExecutionContext {

    private TaskArtifactState taskArtifactState;
    private TaskOutputCachingBuildCacheKey buildCacheKey;
    private UniqueId originBuildInvocationId;
    private List<String> upToDateMessages;

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

    @Override
    public UniqueId getOriginBuildInvocationId() {
        return originBuildInvocationId;
    }

    @Override
    public void setOriginBuildInvocationId(@Nullable UniqueId originBuildInvocationId) {
        this.originBuildInvocationId = originBuildInvocationId;
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

}
