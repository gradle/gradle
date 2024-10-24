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

import org.gradle.api.NonNullApi;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.CachingTaskDependencyResolveContext;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@NonNullApi
@ServiceScope(Scope.Build.class)
public class TaskDependencyResolver {
    private final List<DependencyResolver> dependencyResolvers;
    private CachingTaskDependencyResolveContext<NodePromise> context;

    public TaskDependencyResolver(List<DependencyResolver> dependencyResolvers) {
        this.dependencyResolvers = dependencyResolvers;
        this.context = createTaskDependencyResolverContext(dependencyResolvers);
    }

    public void clear() {
        context = createTaskDependencyResolverContext(dependencyResolvers);
    }

    private static CachingTaskDependencyResolveContext<NodePromise> createTaskDependencyResolverContext(
        List<DependencyResolver> workResolvers
    ) {
        return new CachingTaskDependencyResolveContext<>(workResolvers);
    }

    public Set<Node> resolveDependenciesFor(@Nullable TaskInternal task, Object dependencies) {
        // TODO:configuration batch NodePromises by owning Project and run build operations in parallel
        return context.getDependencies(task, dependencies)
            .parallelStream()
            .map(NodePromise::get)
            .collect(Collectors.toSet());
    }
}
