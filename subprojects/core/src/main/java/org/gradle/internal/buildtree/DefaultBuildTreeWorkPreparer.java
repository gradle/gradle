/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.buildtree;

import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.composite.internal.TaskIdentifier;
import org.gradle.execution.EntryTaskSelector;
import org.gradle.execution.selection.BuildTaskSelector;
import org.gradle.internal.build.BuildLifecycleController;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.service.ServiceRegistry;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DefaultBuildTreeWorkPreparer implements BuildTreeWorkPreparer {
    private final BuildState targetBuild;
    private final BuildLifecycleController targetBuildController;

    public DefaultBuildTreeWorkPreparer(BuildState targetBuild, BuildLifecycleController buildLifecycleController) {
        this.targetBuild = targetBuild;
        this.targetBuildController = buildLifecycleController;
    }

    @Override
    public BuildTreeWorkGraph.FinalizedGraph scheduleRequestedTasks(BuildTreeWorkGraph graph, @Nullable EntryTaskSelector selector) {
        ServiceRegistry services = targetBuildController.getGradle().getServices();
        ResilientTaskScheduling resilientTaskScheduling = services.get(ResilientTaskScheduling.class);
        // A client-provided selector needs the target build's work graph, so it cannot be applied when the target build is broken
        if (resilientTaskScheduling.isEnabled() && selector == null && !resilientTaskScheduling.canScheduleTasksFor(targetBuild)) {
            return scheduleRequestedTasksOfOtherBuilds(graph, services.get(BuildTaskSelector.class), resilientTaskScheduling);
        }
        targetBuildController.prepareToScheduleTasks();
        return graph.scheduleWork(graphBuilder -> {
            if (selector != null) {
                graphBuilder.addFinalization(targetBuild, selector::postProcessExecutionPlan);
            }
            graphBuilder.withWorkGraph(targetBuild, builder -> builder.addRequestedTasks(selector));
        });
    }

    /**
     * The target build failed to configure, so its requested tasks cannot run. The requested tasks that live in other builds of the
     * tree can still be resolved by path without the target build's projects, and are scheduled directly in their own builds.
     */
    private BuildTreeWorkGraph.FinalizedGraph scheduleRequestedTasksOfOtherBuilds(BuildTreeWorkGraph graph, BuildTaskSelector taskSelector, ResilientTaskScheduling resilientTaskScheduling) {
        List<String> taskNames = targetBuildController.getGradle().getStartParameter().getTaskNames();
        return graph.scheduleWork(graphBuilder -> {
            List<TaskIdentifier> tasksInOtherBuilds = new ArrayList<>();
            for (String taskName : taskNames) {
                try {
                    for (Task task : taskSelector.resolveTaskName(null, null, targetBuild, taskName).getTasks()) {
                        BuildState owner = ((ProjectInternal) task.getProject()).getOwner().getOwner();
                        if (owner != targetBuild) {
                            tasksInOtherBuilds.add(new TaskIdentifier(owner.getBuildIdentity(), (TaskInternal) task));
                        }
                    }
                } catch (RuntimeException e) {
                    // Selecting in the broken target build rethrows its configuration failure, which is already recorded
                    resilientTaskScheduling.recordBrokenBuild(targetBuild, e);
                }
            }
            graphBuilder.scheduleTasks(tasksInOtherBuilds);
        });
    }
}
