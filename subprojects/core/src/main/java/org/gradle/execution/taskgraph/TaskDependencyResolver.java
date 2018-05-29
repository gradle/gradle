/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.execution.taskgraph;

import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.CachingTaskDependencyResolveContext;
import org.gradle.api.tasks.TaskDependency;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TaskDependencyResolver {
    private CachingTaskDependencyResolveContext context = new CachingTaskDependencyResolveContext();
    private final TaskInfoFactory taskInfoFactory;

    public TaskDependencyResolver(TaskInfoFactory taskInfoFactory) {
        this.taskInfoFactory = taskInfoFactory;
    }

    public void clear() {
        context = new CachingTaskDependencyResolveContext();
    }

    public Collection<? extends TaskInfo> resolveDependenciesFor(TaskInternal task, TaskDependency dependencySet) {
        Set<? extends Task> dependencies = context.getDependencies(task, dependencySet);
        if (dependencies.isEmpty()) {
            return Collections.emptySet();
        }
        List<TaskInfo> dependencyNodes = new ArrayList<TaskInfo>(dependencies.size());
        for (Task dependencyTask : dependencies) {
            dependencyNodes.add(taskInfoFactory.getOrCreateNode(dependencyTask));
        }
        return dependencyNodes;
    }
}
