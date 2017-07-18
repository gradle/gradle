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

import com.google.common.collect.Maps;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.concurrent.Stoppable;

import java.util.Map;

class DefaultIncludedBuildControllers implements Stoppable, IncludedBuildControllers {
    private final Map<BuildIdentifier, IncludedBuildController> buildControllers = Maps.newHashMap();
    private final ManagedExecutor executorService;
    private final IncludedBuilds includedBuilds;
    private boolean taskExecutionStarted;

    DefaultIncludedBuildControllers(ExecutorFactory executorFactory, IncludedBuilds includedBuilds) {
        this.includedBuilds = includedBuilds;
        this.executorService = executorFactory.create("included builds");
    }

    public IncludedBuildController getBuildController(BuildIdentifier buildId) {
        IncludedBuildController buildController = buildControllers.get(buildId);
        if (buildController != null) {
            return buildController;
        }

        IncludedBuild build = includedBuilds.getBuild(buildId.getName());
        DefaultIncludedBuildController newBuildController = new DefaultIncludedBuildController(build);
        buildControllers.put(buildId, newBuildController);
        executorService.submit(newBuildController);

        // Required for build controllers created after initial start
        if (taskExecutionStarted) {
            newBuildController.startTaskExecution();
        }

        return newBuildController;
    }

    @Override
    public void startTaskExecution(boolean prePopulateTaskGraph) {
        this.taskExecutionStarted = true;
        // Don't pre-populate the task graph for build-on-demand
        if (prePopulateTaskGraph) {
            populateTaskGraphs();
        }
        for (IncludedBuildController buildController : buildControllers.values()) {
            buildController.startTaskExecution();
        }
    }

    private void populateTaskGraphs() {
        boolean tasksDiscovered = true;
        while (tasksDiscovered) {
            tasksDiscovered = false;
            for (IncludedBuildController buildController : buildControllers.values()) {
                if (buildController.populateTaskGraph()) {
                    tasksDiscovered = true;
                }
            }
        }
    }

    @Override
    public void stopTaskExecution() {
        for (IncludedBuildController buildController : buildControllers.values()) {
            buildController.stopTaskExecution();
        }
        buildControllers.clear();
        // TODO:DAZ Move this logic into IncludedBuildController, and register a controller for _every_ included build.
        for (IncludedBuild includedBuild : includedBuilds.getBuilds()) {
            ((IncludedBuildInternal) includedBuild).finishBuild();
        }
    }

    @Override
    public void stop() {
        CompositeStoppable.stoppable(buildControllers.values()).stop();
        executorService.stop();
    }
}
