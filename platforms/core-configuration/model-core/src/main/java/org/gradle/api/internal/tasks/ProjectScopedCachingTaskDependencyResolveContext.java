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

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.util.Path;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A project-scoped variant of {@link CachingTaskDependencyResolveContext} used during
 * parallel task dependency resolution. Intercepts {@link DeferredCrossProjectDependency}
 * markers in {@link #add} and converts them to placeholder nodes using the provided
 * {@link PlaceholderHandler}.
 *
 * <p>Placeholders are injected into the underlying graph walker via {@link #add} so they
 * participate in the walker's cache. When cached placeholders appear in subsequent results,
 * the source task is registered via the handler so that placeholder substitution knows
 * which tasks need post-processing.</p>
 */
@NullMarked
public class ProjectScopedCachingTaskDependencyResolveContext<T> extends CachingTaskDependencyResolveContext<T> {

    private final Path buildPath;
    private final Path currentProjectPath;
    private final Path currentProjectIdentityPath;
    private final PlaceholderHandler<T> placeholderHandler;

    /**
     * All placeholders ever created via the handler, used to detect whether a
     * result set contains placeholders that require source task registration.
     */
    private final Set<T> createdPlaceholders = Collections.newSetFromMap(new IdentityHashMap<>());

    public ProjectScopedCachingTaskDependencyResolveContext(
        Collection<? extends WorkDependencyResolver<T>> workResolvers,
        Path buildPath,
        Path currentProjectPath,
        Path currentProjectIdentityPath,
        PlaceholderHandler<T> placeholderHandler
    ) {
        super(prependPlaceholderResolver(workResolvers));
        this.buildPath = buildPath;
        this.currentProjectPath = currentProjectPath;
        this.currentProjectIdentityPath = currentProjectIdentityPath;
        this.placeholderHandler = placeholderHandler;
    }

    private static <T> List<WorkDependencyResolver<T>> prependPlaceholderResolver(Collection<? extends WorkDependencyResolver<T>> workResolvers) {
        return ImmutableList.<WorkDependencyResolver<T>>builderWithExpectedSize(workResolvers.size() + 1)
            .add(new PlaceholderResolver<>())
            .addAll(workResolvers)
            .build();
    }

    @Override
    public boolean deferCrossProjectResolution(Path targetProjectIdentityPath, String taskName) {
        if (currentProjectIdentityPath.equals(targetProjectIdentityPath)) {
            return false;
        }
        add(new DeferredCrossProjectDependency.ByProjectTask(targetProjectIdentityPath, taskName));
        return true;
    }

    @Override
    public boolean deferCrossProjectResolution(Path taskPath) {
        if (!taskPath.isAbsolute() && taskPath.segmentCount() <= 1) {
            // Relative single-segment path like "compileJava" — always targets current project
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
    public boolean deferAllProjectsSearch(Consumer<TaskDependencyResolveContext> resolutionAction) {
        add(new DeferredCrossProjectDependency.AllProjectsSearch(resolutionAction, getTask()));
        return true;
    }

    @Override
    public void add(Object dependency) {
        if (dependency instanceof DeferredCrossProjectDependency) {
            T placeholder = placeholderHandler.createPlaceholder((DeferredCrossProjectDependency) dependency);
            createdPlaceholders.add(placeholder);
            // Wrap in a PlaceholderMarker and feed into the walker so the placeholder
            // becomes a cached value of the parent container node.
            super.add(new PlaceholderMarker<>(placeholder));
        } else {
            super.add(dependency);
        }
    }

    @Override
    public Set<T> getDependencies(@Nullable Task task, Object dependencies) {
        Set<T> result = super.getDependencies(task, dependencies);
        if (task != null && resultContainsPlaceholder(result)) {
            placeholderHandler.registerSourceTaskWithPlaceholder(task);
        }
        return result;
    }

    private boolean resultContainsPlaceholder(Set<T> result) {
        if (createdPlaceholders.isEmpty()) {
            return false;
        }
        for (T value : result) {
            if (createdPlaceholders.contains(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Thin wrapper that carries a placeholder value through the graph walker.
     * The walker visits it as a node, and {@link PlaceholderResolver} unwraps it
     * into a resolved value that gets cached alongside same-project dependencies.
     */
    private static class PlaceholderMarker<T> {
        final T placeholder;

        PlaceholderMarker(T placeholder) {
            this.placeholder = placeholder;
        }
    }

    /**
     * {@link WorkDependencyResolver} that handles {@link PlaceholderMarker} nodes
     * by unwrapping and emitting the placeholder as a resolved value.
     */
    private static class PlaceholderResolver<T> implements WorkDependencyResolver<T> {
        @Override
        @SuppressWarnings("unchecked")
        public boolean resolve(Task task, Object node, Action<? super T> resolveAction) {
            if (node instanceof PlaceholderMarker) {
                resolveAction.execute(((PlaceholderMarker<T>) node).placeholder);
                return true;
            }
            return false;
        }
    }

    /**
     * Handles cross-project dependency placeholders during parallel task dependency resolution.
     *
     * @param <T> the dependency node type
     */
    public interface PlaceholderHandler<T> {

        /**
         * Creates a placeholder node for a deferred cross-project dependency and
         * registers it for later resolution.
         */
        T createPlaceholder(DeferredCrossProjectDependency dep);

        /**
         * Registers the given task as having placeholder nodes in its resolved dependencies,
         * so that placeholder substitution knows to process it.
         */
        void registerSourceTaskWithPlaceholder(Task task);
    }
}
