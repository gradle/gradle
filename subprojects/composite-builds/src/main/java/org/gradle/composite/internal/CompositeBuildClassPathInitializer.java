/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.composite.internal;

import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.initialization.ScriptClassPathInitializer;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.build.BuildState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.gradle.api.internal.tasks.TaskDependencyUtil.getDependenciesForInternalUse;

public class CompositeBuildClassPathInitializer implements ScriptClassPathInitializer {
    private final BuildTreeWorkGraphController buildTreeWorkGraphController;
    private final BuildState currentBuild;

    public CompositeBuildClassPathInitializer(BuildTreeWorkGraphController buildTreeWorkGraphController, BuildState currentBuild) {
        this.buildTreeWorkGraphController = buildTreeWorkGraphController;
        this.currentBuild = currentBuild;
    }

    @Override
    public void initialize(Configuration classpath) {
        List<TaskIdentifier.TaskBasedTaskIdentifier> tasksToBuild = new ArrayList<>();
        Set<? extends Task> dependencies = getDependenciesForInternalUse(classpath.getBuildDependencies(), null);
        for (Task task : dependencies) {
            BuildState targetBuild = ((ProjectInternal) task.getProject()).getOwner().getOwner();
            assert targetBuild != currentBuild;
            tasksToBuild.add(TaskIdentifier.of(targetBuild.getBuildIdentifier(), (TaskInternal) task));
        }
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
}
