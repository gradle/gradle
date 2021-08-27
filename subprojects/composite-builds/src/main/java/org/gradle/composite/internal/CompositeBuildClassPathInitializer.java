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
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.initialization.ScriptClassPathInitializer;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.Pair;
import org.gradle.internal.build.BuildState;

import java.util.ArrayList;
import java.util.List;

public class CompositeBuildClassPathInitializer implements ScriptClassPathInitializer {
    private final IncludedBuildTaskGraph includedBuildTaskGraph;
    private final BuildState currentBuild;

    public CompositeBuildClassPathInitializer(IncludedBuildTaskGraph includedBuildTaskGraph, BuildState currentBuild) {
        this.includedBuildTaskGraph = includedBuildTaskGraph;
        this.currentBuild = currentBuild;
    }

    @Override
    public void execute(Configuration classpath) {
        List<Pair<BuildIdentifier, TaskInternal>> tasksToBuild = new ArrayList<>();
        for (Task task : classpath.getBuildDependencies().getDependencies(null)) {
            if (!task.getState().getExecuted()) {
                // This check should live lower down, and should have some kind of synchronization around it, as other threads may be
                // running tasks at the same time
                BuildState targetBuild = ((ProjectInternal) task.getProject()).getOwner().getOwner();
                assert targetBuild != currentBuild;
                tasksToBuild.add(Pair.of(targetBuild.getBuildIdentifier(), (TaskInternal) task));
            }
        }
        if (!tasksToBuild.isEmpty()) {
            includedBuildTaskGraph.withNewTaskGraph(() -> {
                includedBuildTaskGraph.prepareTaskGraph(() -> {
                    for (Pair<BuildIdentifier, TaskInternal> task : tasksToBuild) {
                        includedBuildTaskGraph.locateTask(task.left, task.right).queueForExecution();
                    }
                    includedBuildTaskGraph.populateTaskGraphs();
                });
                includedBuildTaskGraph.runScheduledTasks();
                return null;
            });
        }
    }
}
