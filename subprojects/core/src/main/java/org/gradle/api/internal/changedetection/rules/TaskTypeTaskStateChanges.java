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

import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.changedetection.state.TaskExecution;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

class TaskTypeTaskStateChanges extends SimpleTaskStateChanges {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskTypeTaskStateChanges.class);
    private static final HashCode NO_ACTION_LOADERS = Hashing.md5().hashString("no-action-loaders", Charsets.UTF_8);
    private final String taskPath;
    private final String taskClass;
    private final HashCode taskClassLoaderHash;
    private final HashCode taskActionsClassLoaderHash;
    private final TaskExecution previousExecution;

    public TaskTypeTaskStateChanges(TaskExecution previousExecution, TaskExecution currentExecution, String taskPath, Class<? extends TaskInternal> taskClass, Collection<ClassLoader> taskActionClassLoaders, ClassLoaderHierarchyHasher classLoaderHierarchyHasher) {
        String taskClassName = taskClass.getName();
        currentExecution.setTaskClass(taskClassName);
        HashCode taskClassLoaderHash = classLoaderHierarchyHasher.getClassLoaderHash(taskClass.getClassLoader());
        currentExecution.setTaskClassLoaderHash(taskClassLoaderHash);
        HashCode taskActionsClassLoaderHash = calculateActionClassLoaderHash(taskActionClassLoaders, classLoaderHierarchyHasher);
        currentExecution.setTaskActionsClassLoaderHash(taskActionsClassLoaderHash);
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Task {} class loader hash: {}", taskPath, taskClassLoaderHash);
            LOGGER.info("Task {} actions class loader hash: {}", taskPath, taskActionsClassLoaderHash);
        }
        this.taskPath = taskPath;
        this.taskClass = taskClassName;
        this.taskClassLoaderHash = taskClassLoaderHash;
        this.taskActionsClassLoaderHash = taskActionsClassLoaderHash;
        this.previousExecution = previousExecution;
    }

    private static HashCode calculateActionClassLoaderHash(Collection<ClassLoader> taskActionClassLoaders, ClassLoaderHierarchyHasher classLoaderHierarchyHasher) {
        if (taskActionClassLoaders.isEmpty()) {
            return NO_ACTION_LOADERS;
        }
        Hasher hasher = Hashing.md5().newHasher();
        for (ClassLoader taskActionClassLoader : taskActionClassLoaders) {
            HashCode actionLoaderHash = classLoaderHierarchyHasher.getClassLoaderHash(taskActionClassLoader);
            if (actionLoaderHash == null) {
                return null;
            }
            hasher.putBytes(actionLoaderHash.asBytes());
        }
        return hasher.hash();
    }

    @Override
    protected void addAllChanges(List<TaskStateChange> changes) {
        if (!taskClass.equals(previousExecution.getTaskClass())) {
            changes.add(new DescriptiveChange("Task '%s' has changed type from '%s' to '%s'.",
                    taskPath, previousExecution.getTaskClass(), taskClass));
            return;
        }
        if (taskClassLoaderHash == null) {
            changes.add(new DescriptiveChange("Task '%s' was loaded with an unknown classloader", taskPath));
            return;
        }
        if (previousExecution.getTaskClassLoaderHash() == null) {
            changes.add(new DescriptiveChange("Task '%s' was loaded with an unknown classloader during the previous execution", taskPath));
            return;
        }
        if (taskActionsClassLoaderHash == null) {
            changes.add(new DescriptiveChange("Task '%s' has a custom action that was loaded with an unknown classloader", taskPath));
            return;
        }
        if (previousExecution.getTaskActionsClassLoaderHash() == null) {
            changes.add(new DescriptiveChange("Task '%s' had a custom action that was loaded with an unknown classloader during the previous execution", taskPath));
            return;
        }
        if (!taskClassLoaderHash.equals(previousExecution.getTaskClassLoaderHash())) {
            changes.add(new DescriptiveChange("Task '%s' class path has changed from %s to %s.", taskPath, previousExecution.getTaskClassLoaderHash(), taskClassLoaderHash));
            return;
        }
        if (!taskActionsClassLoaderHash.equals(previousExecution.getTaskActionsClassLoaderHash())) {
            changes.add(new DescriptiveChange("Task '%s' additional action class path has changed from %s to %s.", taskPath, previousExecution.getTaskActionsClassLoaderHash(), taskActionsClassLoaderHash));
        }
    }
}
