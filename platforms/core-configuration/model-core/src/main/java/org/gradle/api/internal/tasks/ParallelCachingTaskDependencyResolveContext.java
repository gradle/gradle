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

package org.gradle.api.internal.tasks;

import org.gradle.api.Task;
import org.gradle.util.Path;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.BiFunction;

/**
 * A project-scoped variant of {@link CachingTaskDependencyResolveContext} used during
 * parallel task dependency resolution. Intercepts {@link DeferredCrossProjectDependency}
 * markers in {@link #add} and converts them to placeholder nodes using a provided factory.
 *
 * <p>The placeholder nodes are injected directly into the result set returned by
 * {@link #getDependencies}, so callers get dependency sets that already contain
 * the placeholders — no post-processing needed.</p>
 */
@NullMarked
public class ParallelCachingTaskDependencyResolveContext<T> extends CachingTaskDependencyResolveContext<T> {

    private final Path buildPath;
    private final Path currentProjectPath;
    private final Path currentProjectIdentityPath;
    private final BiFunction<DeferredCrossProjectDependency, Task, T> placeholderFactory;
    private final List<T> createdPlaceholders = new ArrayList<>();

    public ParallelCachingTaskDependencyResolveContext(
        Collection<? extends WorkDependencyResolver<T>> workResolvers,
        Path buildPath,
        Path currentProjectPath,
        Path currentProjectIdentityPath,
        BiFunction<DeferredCrossProjectDependency, Task, T> placeholderFactory
    ) {
        super(workResolvers);
        this.buildPath = buildPath;
        this.currentProjectPath = currentProjectPath;
        this.currentProjectIdentityPath = currentProjectIdentityPath;
        this.placeholderFactory = placeholderFactory;
    }

    @Override
    public boolean deferCrossProjectTaskVisitIfNeeded(Path targetProjectIdentityPath, String taskName) {
        if (currentProjectIdentityPath.equals(targetProjectIdentityPath)) {
            return false;
        }
        add(new DeferredCrossProjectDependency.ByProjectTask(targetProjectIdentityPath, taskName));
        return true;
    }

    @Override
    public boolean deferCrossProjectTaskPathIfNeeded(Path taskPath) {
        if (taskPath.segmentCount() <= 1) {
            return false;
        }
        Path taskParent = taskPath.getParent();
        Path targetProjectPath = taskParent != null
            ? currentProjectPath.absolutePath(taskParent)
            : Path.ROOT;
        if (currentProjectPath.equals(targetProjectPath)) {
            return false;
        }
        Path targetIdentityPath = buildPath.append(targetProjectPath);
        String taskName = taskPath.getName();
        add(new DeferredCrossProjectDependency.ByProjectTask(targetIdentityPath, taskName));
        return true;
    }

    @Override
    public boolean deferAllProjectsDependencyVisitIfNeeded(Consumer<TaskDependencyResolveContext> resolutionAction) {
        add(new DeferredCrossProjectDependency.AllProjectsSearch(resolutionAction));
        return true;
    }

    @Override
    public void add(Object dependency) {
        if (dependency instanceof DeferredCrossProjectDependency) {
            createdPlaceholders.add(placeholderFactory.apply((DeferredCrossProjectDependency) dependency, getTask()));
        } else {
            super.add(dependency);
        }
    }

    @Override
    public Set<T> getDependencies(@Nullable Task task, Object dependencies) {
        Set<T> result = super.getDependencies(task, dependencies);
        if (!createdPlaceholders.isEmpty()) {
            Set<T> merged = new HashSet<>(result);
            merged.addAll(createdPlaceholders);
            createdPlaceholders.clear();
            return merged;
        }
        return result;
    }
}
