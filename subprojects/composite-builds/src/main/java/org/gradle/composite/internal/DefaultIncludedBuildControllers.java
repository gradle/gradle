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

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.ExecutionResult;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.work.WorkerLeaseService;

import java.util.LinkedHashMap;
import java.util.Map;

class DefaultIncludedBuildControllers implements IncludedBuildControllers {
    private final Map<BuildIdentifier, IncludedBuildController> buildControllers = new LinkedHashMap<>();
    private final ManagedExecutor executorService;
    private final ProjectStateRegistry projectStateRegistry;
    private final WorkerLeaseService workerLeaseService;
    private final BuildStateRegistry buildRegistry;

    DefaultIncludedBuildControllers(ManagedExecutor executorService, BuildStateRegistry buildRegistry, ProjectStateRegistry projectStateRegistry, WorkerLeaseService workerLeaseService) {
        this.executorService = executorService;
        this.buildRegistry = buildRegistry;
        this.projectStateRegistry = projectStateRegistry;
        this.workerLeaseService = workerLeaseService;
    }

    @Override
    public IncludedBuildController getBuildController(BuildIdentifier buildId) {
        IncludedBuildController buildController = buildControllers.get(buildId);
        if (buildController != null) {
            return buildController;
        }

        IncludedBuildController newBuildController;
        if (buildId.equals(DefaultBuildIdentifier.ROOT)) {
            newBuildController = new RootBuildController(buildRegistry.getRootBuild());
        } else {
            IncludedBuildState build = buildRegistry.getIncludedBuild(buildId);
            newBuildController = new DefaultIncludedBuildController(build, projectStateRegistry, workerLeaseService);
        }
        buildControllers.put(buildId, newBuildController);
        return newBuildController;
    }

    @Override
    public void populateTaskGraphs() {
        boolean tasksDiscovered = true;
        while (tasksDiscovered) {
            tasksDiscovered = false;
            for (IncludedBuildController buildController : ImmutableList.copyOf(buildControllers.values())) {
                if (buildController.populateTaskGraph()) {
                    tasksDiscovered = true;
                }
            }
        }
        for (IncludedBuildController buildController : buildControllers.values()) {
            buildController.prepareForExecution();
        }
    }

    @Override
    public void startTaskExecution() {
        for (IncludedBuildController buildController : buildControllers.values()) {
            buildController.startTaskExecution(executorService);
        }
    }

    @Override
    public ExecutionResult<Void> awaitTaskCompletion() {
        ExecutionResult<Void> result = ExecutionResult.succeeded();
        for (IncludedBuildController buildController : buildControllers.values()) {
            result = result.withFailures(buildController.awaitTaskCompletion());
        }
        return result;
    }

    @Override
    public void close() {
        CompositeStoppable.stoppable(buildControllers.values()).stop();
    }
}
