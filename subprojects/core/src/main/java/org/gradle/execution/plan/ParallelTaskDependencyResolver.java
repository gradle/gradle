/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.DeferredCrossProjectDependency;
import org.gradle.api.internal.tasks.ParallelCachingTaskDependencyResolveContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * A project-scoped resolver used during parallel task dependency resolution.
 * Extends {@link TaskDependencyResolver} so it can be passed to
 * {@link LocalTaskNode#resolveRelationships}, but uses a
 * {@link ParallelCachingTaskDependencyResolveContext} that converts cross-project
 * dependencies into placeholder nodes via the provided factory.
 */
@NullMarked
class ParallelTaskDependencyResolver extends TaskDependencyResolver {

    private final ParallelCachingTaskDependencyResolveContext<Node> parallelContext;

    ParallelTaskDependencyResolver(
        List<DependencyResolver> dependencyResolvers,
        ProjectInternal project,
        BiFunction<DeferredCrossProjectDependency, Task, Node> placeholderFactory
    ) {
        super(dependencyResolvers);
        this.parallelContext = new ParallelCachingTaskDependencyResolveContext<>(
            dependencyResolvers,
            project.getGradle().getIdentityPath(),
            project.getProjectPath(),
            project.getIdentityPath(),
            placeholderFactory
        );
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("ParallelTaskDependencyResolver should not be cleared");
    }

    @Override
    public Set<Node> resolveDependenciesFor(@Nullable TaskInternal task, Object dependencies) {
        return parallelContext.getDependencies(task, dependencies);
    }
}
