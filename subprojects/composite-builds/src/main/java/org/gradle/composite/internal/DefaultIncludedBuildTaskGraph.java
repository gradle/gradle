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
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.CompositeBuildParticipantBuildState;

import java.util.function.Consumer;


public class DefaultIncludedBuildTaskGraph implements IncludedBuildTaskGraph {
    private final IncludedBuildControllers includedBuilds;
    private final BuildStateRegistry buildRegistry;

    public DefaultIncludedBuildTaskGraph(IncludedBuildControllers includedBuilds, BuildStateRegistry buildRegistry) {
        this.includedBuilds = includedBuilds;
        this.buildRegistry = buildRegistry;
    }

    private boolean isRoot(BuildIdentifier targetBuild) {
        return targetBuild.equals(DefaultBuildIdentifier.ROOT);
    }

    private CompositeBuildParticipantBuildState rootBuild() {
        return (CompositeBuildParticipantBuildState) buildRegistry.getRootBuild();
    }

    @Override
    public synchronized void addTask(BuildIdentifier requestingBuild, BuildIdentifier targetBuild, String taskPath) {
        if (isRoot(targetBuild)) {
            if (findTaskInRootBuild(taskPath) == null) {
                rootBuild().getBuild().getTaskGraph().addAdditionalEntryTask(taskPath);
            }
        } else {
            buildControllerFor(targetBuild).queueForExecution(taskPath);
        }
    }

    @Override
    public void awaitTaskCompletion(Consumer<? super Throwable> taskFailures) {
        // Start task execution if necessary: this is required for building plugin artifacts,
        // since these are built on-demand prior to the regular start signal for included builds.
        includedBuilds.populateTaskGraphs();
        includedBuilds.startTaskExecution();
        includedBuilds.awaitTaskCompletion(taskFailures);
    }

    @Override
    public IncludedBuildTaskResource.State getTaskState(BuildIdentifier targetBuild, String taskPath) {
        if (isRoot(targetBuild)) {
            TaskInternal task = getTask(targetBuild, taskPath);
            if (task.getState().getFailure() != null) {
                return IncludedBuildTaskResource.State.FAILED;
            } else if (task.getState().getExecuted()) {
                return IncludedBuildTaskResource.State.SUCCESS;
            } else {
                return IncludedBuildTaskResource.State.WAITING;
            }
        } else {
            return buildControllerFor(targetBuild).getTaskState(taskPath);
        }
    }

    @Override
    public TaskInternal getTask(BuildIdentifier targetBuild, String taskPath) {
        if (isRoot(targetBuild)) {
            TaskInternal task = findTaskInRootBuild(taskPath);
            if (task == null) {
                throw new IllegalStateException("Root build task '" + taskPath + "' was never scheduled for execution.");
            }
            return task;
        } else {
            return buildControllerFor(targetBuild).getTask(taskPath);
        }
    }

    private IncludedBuildController buildControllerFor(BuildIdentifier buildId) {
        return includedBuilds.getBuildController(buildId);
    }

    private TaskInternal findTaskInRootBuild(String taskPath) {
        for (Task task : rootBuild().getBuild().getTaskGraph().getAllTasks()) {
            if (task.getPath().equals(taskPath)) {
                return (TaskInternal) task;
            }
        }
        return null;
    }

}
