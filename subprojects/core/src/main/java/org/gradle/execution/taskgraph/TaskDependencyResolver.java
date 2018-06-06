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

import org.gradle.api.NonNullApi;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.CachingTaskDependencyResolveContext;
import org.gradle.api.internal.tasks.CachingTaskDependencyResolveContext.WorkResolver;

import javax.annotation.Nullable;
import java.util.Set;

@NonNullApi
public class TaskDependencyResolver {
    private CachingTaskDependencyResolveContext<WorkInfo> context;
    private final TaskInfoFactory taskInfoFactory;

    public TaskDependencyResolver(TaskInfoFactory taskInfoFactory) {
        this.taskInfoFactory = taskInfoFactory;
        this.context = createTaskDependencyResolverContext(taskInfoFactory);
    }

    public void clear() {
        context = createTaskDependencyResolverContext(taskInfoFactory);
    }

    private static CachingTaskDependencyResolveContext<WorkInfo> createTaskDependencyResolverContext(final TaskInfoFactory taskInfoFactory) {
        return new CachingTaskDependencyResolveContext<WorkInfo>(new WorkResolver<WorkInfo>() {
            @Override
            public WorkInfo resolve(Task task) {
                return taskInfoFactory.getOrCreateNode(task);
            }
        });
    }

    public Set<WorkInfo> resolveDependenciesFor(@Nullable TaskInternal task, Object dependencies) {
        return context.getDependencies(task, dependencies);
    }
}
