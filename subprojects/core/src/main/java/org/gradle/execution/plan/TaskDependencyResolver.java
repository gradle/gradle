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

package org.gradle.execution.plan;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.CachingTaskDependencyResolveContext;
import org.gradle.api.internal.tasks.ProjectScopedCachingTaskDependencyResolveContext.PlaceholderHandler;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

@NullMarked
@ServiceScope(Scope.Build.class)
public class TaskDependencyResolver {
    private final List<DependencyResolver> dependencyResolvers;
    private CachingTaskDependencyResolveContext<Node> context;

    public TaskDependencyResolver(List<DependencyResolver> dependencyResolvers) {
        this.dependencyResolvers = dependencyResolvers;
        this.context = new CachingTaskDependencyResolveContext<Node>(dependencyResolvers);
    }

    public void clear() {
        context = new CachingTaskDependencyResolveContext<Node>(dependencyResolvers);
    }

    public Set<Node> resolveDependenciesFor(@Nullable TaskInternal task, Object dependencies) {
        return context.getDependencies(task, dependencies);
    }

    /**
     * Creates a new {@link ProjectScopedTaskDependencyResolver} scoped to a specific project.
     * Used for parallel resolution where each worker thread needs its own context and
     * cross-project access is deferred via placeholder nodes created by the factory.
     */
    ProjectScopedTaskDependencyResolver newProjectScopedResolver(
        ProjectInternal project,
        PlaceholderHandler<Node> placeholderHandler
    ) {
        return new ProjectScopedTaskDependencyResolver(dependencyResolvers, project, placeholderHandler);
    }
}
