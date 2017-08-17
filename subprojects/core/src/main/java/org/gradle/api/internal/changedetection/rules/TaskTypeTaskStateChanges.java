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

package org.gradle.api.internal.changedetection.rules;

import com.google.common.collect.ImmutableList;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.ImplementationSnapshot;
import org.gradle.api.internal.changedetection.state.TaskExecution;

import javax.annotation.Nullable;
import java.util.List;

class TaskTypeTaskStateChanges extends SimpleTaskStateChanges {
    private final TaskExecution previousExecution;
    private final TaskExecution currentExecution;
    private final TaskInternal task;

    public TaskTypeTaskStateChanges(@Nullable TaskExecution previousExecution, TaskExecution currentExecution, TaskInternal task) {
        this.previousExecution = previousExecution;
        this.currentExecution = currentExecution;
        this.task = task;
    }

    @Override
    protected void addAllChanges(List<TaskStateChange> changes) {
        ImplementationSnapshot prevImplementation = previousExecution.getTaskImplementation();
        ImplementationSnapshot taskImplementation = currentExecution.getTaskImplementation();
        if (!taskImplementation.getTypeName().equals(prevImplementation.getTypeName())) {
            changes.add(new DescriptiveChange("Task '%s' has changed type from '%s' to '%s'.",
                    task.getIdentityPath(), prevImplementation.getTypeName(), taskImplementation.getTypeName()));
            return;
        }
        if (taskImplementation.hasUnknownClassLoader()) {
            changes.add(new DescriptiveChange("Task '%s' was loaded with an unknown classloader", task.getIdentityPath()));
            return;
        }
        if (prevImplementation.hasUnknownClassLoader()) {
            changes.add(new DescriptiveChange("Task '%s' was loaded with an unknown classloader during the previous execution", task.getIdentityPath()));
            return;
        }
        if (!taskImplementation.getClassLoaderHash().equals(prevImplementation.getClassLoaderHash())) {
            changes.add(new DescriptiveChange("Task '%s' class path has changed from %s to %s.", task.getIdentityPath(), prevImplementation.getClassLoaderHash(), taskImplementation.getClassLoaderHash()));
            return;
        }

        ImmutableList<ImplementationSnapshot> taskActionImplementations = currentExecution.getTaskActionImplementations();
        if (hasAnyUnknownClassLoader(taskActionImplementations)) {
            changes.add(new DescriptiveChange("Task '%s' has an additional action that was loaded with an unknown classloader", task.getIdentityPath()));
            return;
        }
        if (hasAnyUnknownClassLoader(previousExecution.getTaskActionImplementations())) {
            changes.add(new DescriptiveChange("Task '%s' had an additional action that was loaded with an unknown classloader during the previous execution", task.getIdentityPath()));
            return;
        }
        if (!taskActionImplementations.equals(previousExecution.getTaskActionImplementations())) {
            changes.add(new DescriptiveChange("Task '%s' has additional actions that have changed", task.getIdentityPath()));
        }
    }

    private static boolean hasAnyUnknownClassLoader(Iterable<ImplementationSnapshot> implementations) {
        for (ImplementationSnapshot implementation : implementations) {
            if (implementation.hasUnknownClassLoader()) {
                return true;
            }
        }
        return false;
    }
}
