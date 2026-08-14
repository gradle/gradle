/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.project.HoldsProjectState;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.problems.internal.ProblemsInternal;
import org.gradle.composite.internal.BuildTreeWorkGraphController;
import org.gradle.internal.Cast;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.execution.impl.DefaultWorkValidationContext;
import org.gradle.internal.model.InMemoryCacheFactory;
import org.gradle.internal.model.InMemoryLoadingCache;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.internal.DefaultPluginId;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@ServiceScope(Scope.BuildTree.class)
public class TaskNodeFactory implements HoldsProjectState {

    private final DefaultTypeOriginInspectorFactory typeOriginInspectorFactory;
    private final Function<LocalTaskNode, ResolveMutationsNode> resolveMutationsNodeFactory;

    private final InMemoryLoadingCache<TaskInternal, LocalTaskNode> localTaskNodes;
    private final InMemoryLoadingCache<ExternalTaskKey, TaskInAnotherBuild> externalTaskNodes;

    public TaskNodeFactory(
        BuildTreeWorkGraphController workGraphController,
        BuildStateRegistry buildRegistry,
        NodeValidator nodeValidator,
        BuildOperationRunner buildOperationRunner,
        ExecutionNodeAccessHierarchies accessHierarchies,
        ProblemsInternal problems,
        InMemoryCacheFactory inMemoryCacheFactory
    ) {
        this.typeOriginInspectorFactory = new DefaultTypeOriginInspectorFactory();
        this.resolveMutationsNodeFactory = localTaskNode -> new ResolveMutationsNode(localTaskNode, nodeValidator, buildOperationRunner, accessHierarchies);

        this.localTaskNodes = inMemoryCacheFactory.create(task ->
            new LocalTaskNode(
                task,
                new DefaultWorkValidationContext(typeOriginInspectorFactory.forTask(task), problems),
                resolveMutationsNodeFactory
            )
        );
        this.externalTaskNodes = inMemoryCacheFactory.create(key -> {
            Path targetBuildPath = buildPathOf(key.task);
            BuildState targetBuild = buildRegistry.getBuild(targetBuildPath);
            TaskNode targetNode = localTaskNodes.get(key.task);
            return TaskInAnotherBuild.of(targetNode, targetBuild, workGraphController);
        });
    }

    private static Path buildPathOf(TaskInternal task) {
        return task.getTaskIdentity().getProjectIdentity().getBuildPath();
    }

    public @Nullable TaskNode getNode(TaskInternal task, Path sourceBuildPath) {
        return isLocalTo(task, sourceBuildPath)
            ? localTaskNodes.getIfPresent(task)
            : externalTaskNodes.getIfPresent(new ExternalTaskKey(sourceBuildPath, task));
    }

    public LocalTaskNode getOrCreateLocalNode(TaskInternal task) {
        return localTaskNodes.get(task);
    }

    public TaskNode getOrCreateNode(TaskInternal task, Path sourceBuildPath) {
        return isLocalTo(task, sourceBuildPath)
            ? localTaskNodes.get(task)
            : externalTaskNodes.get(new ExternalTaskKey(sourceBuildPath, task));
    }

    private static boolean isLocalTo(TaskInternal task, Path sourceBuildPath) {
        return buildPathOf(task).equals(sourceBuildPath);
    }

    /**
     * A reference to a task in another build, from the perspective of a particular build.
     * <p>
     * Each referencing build gets its own {@link TaskInAnotherBuild} node for a given target task, as a node
     * carries state that belongs to the execution plan that contains it, such as its dependency predecessors.
     * Sharing one node between the plans of two builds would let the completion of that node in one plan make
     * the other plan's nodes ready in the wrong plan.
     */
    private static final class ExternalTaskKey {

        private final Path sourceBuildPath;
        private final TaskInternal task;

        private final int hashCode;

        ExternalTaskKey(Path sourceBuildPath, TaskInternal task) {
            this.sourceBuildPath = sourceBuildPath;
            this.task = task;

            this.hashCode = computeHashCode(sourceBuildPath, task);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ExternalTaskKey)) {
                return false;
            }
            ExternalTaskKey other = (ExternalTaskKey) obj;
            return task == other.task && sourceBuildPath.equals(other.sourceBuildPath);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        private static int computeHashCode(Path sourceBuildPath, TaskInternal task) {
            return 31 * sourceBuildPath.hashCode() + task.hashCode();
        }

    }

    @Override
    public void discardAll() {
        typeOriginInspectorFactory.resetState();
        localTaskNodes.invalidate();
        externalTaskNodes.invalidate();
    }

    private static class DefaultTypeOriginInspectorFactory {
        private final Map<Project, ProjectScopedTypeOriginInspector> projectToInspector = new ConcurrentHashMap<>();
        private final Map<Class<?>, File> clazzToFile = new ConcurrentHashMap<>();

        public ProjectScopedTypeOriginInspector forTask(Task task) {
            return projectToInspector.computeIfAbsent(task.getProject(), ProjectScopedTypeOriginInspector::new);
        }

        void resetState() {
            projectToInspector.clear();
        }

        @Nullable
        private File jarFileFor(Class<?> pluginClass) {
            return clazzToFile.computeIfAbsent(pluginClass, clazz -> toFile(pluginClass.getProtectionDomain().getCodeSource().getLocation()));
        }

        @Nullable
        private static File toFile(@Nullable URL url) {
            if (url == null) {
                return null;
            }
            try {
                return new File(url.toURI());
            } catch (URISyntaxException e) {
                return null;
            }
        }

        private class ProjectScopedTypeOriginInspector implements WorkValidationContext.TypeOriginInspector {
            private final PluginContainer plugins;
            private final PluginManagerInternal pluginManager;
            private final Map<Class<?>, Optional<PluginId>> classToPlugin = new ConcurrentHashMap<>();

            private ProjectScopedTypeOriginInspector(Project project) {
                this.plugins = project.getPlugins();
                this.pluginManager = (PluginManagerInternal) project.getPluginManager();
            }

            @Override
            public Optional<PluginId> findPluginDefining(Class<?> type) {
                return classToPlugin.computeIfAbsent(type, clazz -> {
                    File taskJar = jarFileFor(type);
                    return plugins.stream()
                        .map(plugin -> Cast.<Class<Plugin<?>>>uncheckedNonnullCast(plugin.getClass()))
                        .filter(pluginType -> Objects.equals(jarFileFor(pluginType), taskJar))
                        .map(pluginType -> pluginManager.findPluginIdForClass(pluginType)
                            .orElseGet(() -> new DefaultPluginId(pluginType.getName())))
                        .findFirst();
                });
            }
        }
    }

}
