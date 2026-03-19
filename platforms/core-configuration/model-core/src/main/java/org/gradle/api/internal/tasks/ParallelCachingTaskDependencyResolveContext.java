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

import com.google.common.collect.ImmutableList;
import org.gradle.util.Path;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * A project-scoped variant of {@link CachingTaskDependencyResolveContext} used during
 * parallel task dependency resolution. Intercepts {@link DeferredCrossProjectDependency}
 * markers in {@link #add} before they reach the graph walker's queue.
 *
 * <p>Since {@link CachingTaskDependencyResolveContext} passes {@code this} to
 * {@link TaskDependencyContainer#visitDependencies}, implementations automatically
 * receive this subclass and can use the {@code defer*IfNeeded} methods to defer
 * cross-project access.</p>
 */
@NullMarked
public class ParallelCachingTaskDependencyResolveContext<T> extends CachingTaskDependencyResolveContext<T> {

    private final Path buildPath;                  // build identity path (e.g., ":build" or ":")
    private final Path currentProjectPath;         // build-scoped (e.g., ":projectA")
    private final Path currentProjectIdentityPath; // build-tree-scoped (e.g., ":build:projectA")
    private final List<DeferredCrossProjectDependency> deferredItems = new ArrayList<>();

    public ParallelCachingTaskDependencyResolveContext(
        Collection<? extends WorkDependencyResolver<T>> workResolvers,
        Path buildPath,
        Path currentProjectPath,
        Path currentProjectIdentityPath
    ) {
        super(workResolvers);
        this.buildPath = buildPath;
        this.currentProjectPath = currentProjectPath;
        this.currentProjectIdentityPath = currentProjectIdentityPath;
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
            deferredItems.add((DeferredCrossProjectDependency) dependency);
        } else {
            super.add(dependency);
        }
    }

    @SuppressWarnings("MixedMutabilityReturnType")
    public List<DeferredCrossProjectDependency> collectAndClearDeferred() {
        if (deferredItems.isEmpty()) {
            return ImmutableList.of();
        }
        ImmutableList<DeferredCrossProjectDependency> result = ImmutableList.copyOf(deferredItems);
        deferredItems.clear();
        return result;
    }
}
