/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.internal.initialization;

import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.composite.internal.BuildTreeWorkGraphController;
import org.gradle.composite.internal.TaskIdentifier;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.classpath.ClassPath;

import java.util.ArrayList;
import java.util.List;

import static org.gradle.api.internal.tasks.TaskDependencyUtil.getDependenciesForInternalUse;

public class DefaultBuildLogicBuilder implements BuildLogicBuilder {
    private final BuildState currentBuild;
    private final BuildTreeWorkGraphController buildTreeWorkGraphController;
    private final ScriptClassPathResolver scriptClassPathResolver;

    public DefaultBuildLogicBuilder(
        BuildState currentBuild,
        BuildTreeWorkGraphController buildTreeWorkGraphController,
        ScriptClassPathResolver scriptClassPathResolver
    ) {
        this.buildTreeWorkGraphController = buildTreeWorkGraphController;
        this.currentBuild = currentBuild;
        this.scriptClassPathResolver = scriptClassPathResolver;
    }

    @Override
    public void prepareClassPath(Configuration classpathConfiguration, DependencyHandler dependencyHandler) {
        scriptClassPathResolver.prepareClassPath(classpathConfiguration, dependencyHandler);
    }

    @Override
    public ClassPath resolveClassPath(Configuration classpathConfiguration) {
        initialize(classpathConfiguration);
        return scriptClassPathResolver.resolveClassPath(classpathConfiguration);
    }

    private void initialize(Configuration classpath) {
        List<TaskIdentifier.TaskBasedTaskIdentifier> tasksToBuild = taskIdentifiersForBuildDependenciesOf(classpath);
        if (!tasksToBuild.isEmpty()) {
            buildTreeWorkGraphController.withNewWorkGraph(graph -> {
                graph
                    .scheduleWork(builder -> builder.scheduleTasks(tasksToBuild))
                    .runWork()
                    .rethrow();
                return null;
            });
        }
    }

    private List<TaskIdentifier.TaskBasedTaskIdentifier> taskIdentifiersForBuildDependenciesOf(Configuration classpath) {
        List<TaskIdentifier.TaskBasedTaskIdentifier> tasksToBuild = new ArrayList<>();
        for (Task task : getDependenciesForInternalUse(classpath)) {
            BuildState targetBuild = owningBuildOf(task);
            assert targetBuild != currentBuild;
            tasksToBuild.add(TaskIdentifier.of(targetBuild.getBuildIdentifier(), (TaskInternal) task));
        }
        return tasksToBuild;
    }

    private static BuildState owningBuildOf(Task task) {
        return ((ProjectInternal) task.getProject()).getOwner().getOwner();
    }
}
