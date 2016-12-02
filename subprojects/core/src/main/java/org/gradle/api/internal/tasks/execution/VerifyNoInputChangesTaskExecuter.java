/*
 * Copyright 2016 the original author or authors.
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

import org.gradle.api.GradleException;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.caching.BuildCacheKey;

public class VerifyNoInputChangesTaskExecuter implements TaskExecuter {

    private TaskExecuter delegate;
    private TaskArtifactStateRepository repository;

    public VerifyNoInputChangesTaskExecuter(TaskArtifactStateRepository repository, TaskExecuter delegate) {
        this.delegate = delegate;
        this.repository = repository;
    }

    @Override
    public void execute(TaskInternal task, TaskStateInternal state, TaskExecutionContext context) {
        BuildCacheKey beforeExecution = context.getTaskArtifactState().calculateCacheKey();
        delegate.execute(task, state, context);
        if (beforeExecution != null) {
            BuildCacheKey afterExecution = repository.getStateFor(task).calculateCacheKey();
            if (afterExecution == null || !beforeExecution.getHashCode().equals(afterExecution.getHashCode())) {
                throw new TaskExecutionException(task, new GradleException("The inputs for the task changed during the execution! Check if you have a `doFirst` changing the inputs."));
            }
        }
    }
}
