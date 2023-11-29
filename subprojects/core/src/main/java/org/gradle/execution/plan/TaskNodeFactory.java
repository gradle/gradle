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


import com.google.common.collect.Maps;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.plugins.PluginManagerInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.composite.internal.BuildTreeWorkGraphController;
import org.gradle.internal.Cast;
import org.gradle.internal.execution.WorkValidationContext;
import org.gradle.internal.execution.impl.DefaultWorkValidationContext;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.internal.DefaultPluginId;

import javax.annotation.Nullable;
import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

@ServiceScope(Scopes.Build.class)
public class TaskNodeFactory {
    private final Map<Task, TaskNode> nodes = new HashMap<>();
    private final BuildTreeWorkGraphController workGraphController;
    private final GradleInternal thisBuild;
    private final DefaultTypeOriginInspectorFactory typeOriginInspectorFactory;
    private final Function<LocalTaskNode, ResolveMutationsNode> resolveMutationsNodeFactory;

    public TaskNodeFactory(
        GradleInternal thisBuild,
        BuildTreeWorkGraphController workGraphController,
        NodeValidator nodeValidator,
        BuildOperationRunner buildOperationRunner,
        ExecutionNodeAccessHierarchies accessHierarchies
    ) {
        this.thisBuild = thisBuild;
        this.workGraphController = workGraphController;
        this.typeOriginInspectorFactory = new DefaultTypeOriginInspectorFactory();
        resolveMutationsNodeFactory = localTaskNode -> new ResolveMutationsNode(localTaskNode, nodeValidator, buildOperationRunner, accessHierarchies);
    }

    public Set<Task> getTasks() {
        return nodes.keySet();
    }

    @Nullable
    public TaskNode getNode(Task task) {
        return nodes.get(task);
    }

    public TaskNode getOrCreateNode(Task task) {
        TaskNode node = nodes.get(task);
        if (node == null) {
            if (((ProjectInternal) task.getProject()).getGradle().getIdentityPath().equals(thisBuild.getIdentityPath())) {
                node = new LocalTaskNode((TaskInternal) task, new DefaultWorkValidationContext(typeOriginInspectorFactory.forTask(task)), resolveMutationsNodeFactory);
            } else {
                node = TaskInAnotherBuild.of((TaskInternal) task, workGraphController);
            }
            nodes.put(task, node);
        }
        return node;
    }

    public void resetState() {
        typeOriginInspectorFactory.resetState();
        nodes.clear();
    }

    private static class DefaultTypeOriginInspectorFactory {
        private final Map<Project, ProjectScopedTypeOriginInspector> projectToInspector = Maps.newConcurrentMap();
        private final Map<Class<?>, File> clazzToFile = Maps.newConcurrentMap();

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
            private final Map<Class<?>, Optional<PluginId>> classToPlugin = Maps.newConcurrentMap();

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
