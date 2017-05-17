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
import com.google.common.hash.HashCode;
import org.gradle.api.Nullable;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.ImplementationSnapshot;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.api.internal.tasks.ContextAwareTaskAction;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

class TaskTypeTaskStateChanges extends SimpleTaskStateChanges {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskTypeTaskStateChanges.class);
    private final String taskPath;
    private final ImplementationSnapshot taskImplementation;
    private final ImmutableList<ImplementationSnapshot> taskActionImplementations;
    private final TaskExecution previousExecution;

    public TaskTypeTaskStateChanges(@Nullable TaskExecution previousExecution, TaskExecution currentExecution, String taskPath, Class<? extends TaskInternal> taskClass, Collection<ContextAwareTaskAction> taskActions, ClassLoaderHierarchyHasher classLoaderHierarchyHasher) {
        ImplementationSnapshot taskImplementation = new ImplementationSnapshot(taskClass.getName(), classLoaderHierarchyHasher.getClassLoaderHash(taskClass.getClassLoader()));
        ImmutableList<ImplementationSnapshot> taskActionImplementations = collectActionImplementations(taskActions, classLoaderHierarchyHasher);

        currentExecution.setTaskImplementation(taskImplementation);
        currentExecution.setTaskActionImplementations(taskActionImplementations);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Task {} implementation: {}", taskPath, taskImplementation);
            LOGGER.debug("Task {} action implementations: {}", taskPath, taskActionImplementations);
        }

        this.taskPath = taskPath;
        this.taskImplementation = taskImplementation;
        this.taskActionImplementations = taskActionImplementations;
        this.previousExecution = previousExecution;
    }

    private ImmutableList<ImplementationSnapshot> collectActionImplementations(Collection<ContextAwareTaskAction> taskActions, ClassLoaderHierarchyHasher classLoaderHierarchyHasher) {
        if (taskActions.isEmpty()) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<ImplementationSnapshot> actionImpls = ImmutableList.builder();
        for (ContextAwareTaskAction taskAction : taskActions) {
            String typeName = taskAction.getActionClassName();
            HashCode classLoaderHash = classLoaderHierarchyHasher.getClassLoaderHash(taskAction.getClassLoader());
            actionImpls.add(new ImplementationSnapshot(typeName, classLoaderHash));
        }
        return actionImpls.build();
    }

    @Override
    protected void addAllChanges(List<TaskStateChange> changes) {
        ImplementationSnapshot prevImplementation = previousExecution.getTaskImplementation();
        if (!taskImplementation.getTypeName().equals(prevImplementation.getTypeName())) {
            changes.add(new DescriptiveChange("Task '%s' has changed type from '%s' to '%s'.",
                    taskPath, prevImplementation.getTypeName(), taskImplementation.getTypeName()));
            return;
        }
        if (taskImplementation.hasUnknownClassLoader()) {
            changes.add(new DescriptiveChange("Task '%s' was loaded with an unknown classloader", taskPath));
            return;
        }
        if (prevImplementation.hasUnknownClassLoader()) {
            changes.add(new DescriptiveChange("Task '%s' was loaded with an unknown classloader during the previous execution", taskPath));
            return;
        }
        if (!taskImplementation.getClassLoaderHash().equals(prevImplementation.getClassLoaderHash())) {
            changes.add(new DescriptiveChange("Task '%s' class path has changed from %s to %s.", taskPath, prevImplementation.getClassLoaderHash(), taskImplementation.getClassLoaderHash()));
            return;
        }

        if (hasAnyUnknownClassLoader(taskActionImplementations)) {
            changes.add(new DescriptiveChange("Task '%s' has an additional action that was loaded with an unknown classloader", taskPath));
            return;
        }
        if (hasAnyUnknownClassLoader(previousExecution.getTaskActionImplementations())) {
            changes.add(new DescriptiveChange("Task '%s' had an additional action that was loaded with an unknown classloader during the previous execution", taskPath));
            return;
        }
        if (!taskActionImplementations.equals(previousExecution.getTaskActionImplementations())) {
            changes.add(new DescriptiveChange("Task '%s' has additional actions that have changed", taskPath));
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
