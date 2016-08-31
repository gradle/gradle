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

package org.gradle.composite.internal;

import com.google.common.base.Preconditions;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.TaskReferenceResolver;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskInstantiationException;
import org.gradle.api.tasks.TaskReference;

public class IncludedBuildTaskReferenceResolver implements TaskReferenceResolver {

    @Override
    public Task constructTask(final TaskReference reference, TaskContainer tasks) {
        if (!(reference instanceof IncludedBuildTaskReference)) {
            return null;
        }

        final IncludedBuildTaskReference ref = (IncludedBuildTaskReference) reference;
        String delegateTaskName = ref.getBuildName();

        Task task = tasks.findByName(delegateTaskName);

        if (task == null) {
            return tasks.create(delegateTaskName, CompositeBuildTaskDelegate.class, new Action<CompositeBuildTaskDelegate>() {
                @Override
                public void execute(CompositeBuildTaskDelegate compositeBuildTaskDelegate) {
                    compositeBuildTaskDelegate.setBuild(ref.getBuildName());
                    compositeBuildTaskDelegate.addTask(ref.getTaskPath());
                }
            });
        }

        if (task instanceof CompositeBuildTaskDelegate) {
            CompositeBuildTaskDelegate delegateTask = (CompositeBuildTaskDelegate) task;
            Preconditions.checkState(((CompositeBuildTaskDelegate) task).getBuild().equals(ref.getBuildName()));
            delegateTask.addTask(ref.getTaskPath());
            return task;
        }

        throw new TaskInstantiationException("Cannot create delegating task '" + delegateTaskName + "' as task with same name already exists.");
    }
}
