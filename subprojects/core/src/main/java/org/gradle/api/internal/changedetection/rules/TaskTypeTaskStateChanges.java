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
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.internal.changes.TaskStateChangeVisitor;
import org.gradle.internal.snapshot.impl.ImplementationSnapshot;

import javax.annotation.Nullable;

class TaskTypeTaskStateChanges implements TaskStateChanges {
    private final TaskExecution previousExecution;
    private final TaskExecution currentExecution;
    private final TaskInternal task;

    public TaskTypeTaskStateChanges(TaskExecution previousExecution, TaskExecution currentExecution, TaskInternal task) {
        this.previousExecution = previousExecution;
        this.currentExecution = currentExecution;
        this.task = task;
    }

    @Override
    public boolean accept(TaskStateChangeVisitor visitor) {
        ImplementationSnapshot prevImplementation = previousExecution.getTaskImplementation();
        ImplementationSnapshot taskImplementation = currentExecution.getTaskImplementation();
        if (!taskImplementation.getTypeName().equals(prevImplementation.getTypeName())) {
            return visitor.visitChange(new DescriptiveChange("Task '%s' has changed type from '%s' to '%s'.",
                task.getIdentityPath(), prevImplementation.getTypeName(), taskImplementation.getTypeName()));
        }
        if (taskImplementation.isUnknown()) {
            return visitor.visitChange(new DescriptiveChange("Task '%s' %s", task.getIdentityPath(), taskImplementation.getUnknownReason()));
        }
        if (prevImplementation.isUnknown()) {
            return visitor.visitChange(new DescriptiveChange("During the previous execution of the task '%s', it %s", task.getIdentityPath(), prevImplementation.getUnknownReason()));
        }
        if (!taskImplementation.getClassLoaderHash().equals(prevImplementation.getClassLoaderHash())) {
            return visitor.visitChange(new DescriptiveChange("Task '%s' class path has changed from %s to %s.", task.getIdentityPath(), prevImplementation.getClassLoaderHash(), taskImplementation.getClassLoaderHash()));
        }

        ImmutableList<ImplementationSnapshot> taskActionImplementations = currentExecution.getTaskActionImplementations();
        ImplementationSnapshot unknownImplementation = findUnknownImplementation(taskActionImplementations);
        if (unknownImplementation != null) {
            return visitor.visitChange(new DescriptiveChange("Task '%s' has an additional action that %s", task.getIdentityPath(), unknownImplementation.getUnknownReason()));
        }
        ImplementationSnapshot previousUnknownImplementation = findUnknownImplementation(previousExecution.getTaskActionImplementations());
        if (previousUnknownImplementation != null) {
            return visitor.visitChange(new DescriptiveChange("During the previous execution of the task '%s', it had an additional action that %s", task.getIdentityPath(), previousUnknownImplementation.getUnknownReason()));
        }
        if (!taskActionImplementations.equals(previousExecution.getTaskActionImplementations())) {
            return visitor.visitChange(new DescriptiveChange("Task '%s' has additional actions that have changed", task.getIdentityPath()));
        }
        return true;
    }

    @Nullable
    private static ImplementationSnapshot findUnknownImplementation(Iterable<ImplementationSnapshot> implementations) {
        for (ImplementationSnapshot implementation : implementations) {
            if (implementation.isUnknown()) {
                return implementation;
            }
        }
        return null;
    }
}
