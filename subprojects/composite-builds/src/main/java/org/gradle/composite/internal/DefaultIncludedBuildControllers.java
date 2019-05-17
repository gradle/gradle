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
import com.google.common.collect.Maps;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ManagedExecutor;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.resources.ResourceLockCoordinationService;

import java.util.Collection;
import java.util.Map;

class DefaultIncludedBuildControllers implements Stoppable, IncludedBuildControllers {
    private final Map<BuildIdentifier, IncludedBuildController> buildControllers = Maps.newHashMap();
    private final ManagedExecutor executorService;
    private final ResourceLockCoordinationService coordinationService;
    private final BuildStateRegistry buildRegistry;
    private BuildOperationRef rootBuildOperation;

    DefaultIncludedBuildControllers(ExecutorFactory executorFactory, BuildStateRegistry buildRegistry, ResourceLockCoordinationService coordinationService) {
        this.buildRegistry = buildRegistry;
        this.executorService = executorFactory.create("included builds");
        this.coordinationService = coordinationService;
    }

    @Override
    public void rootBuildOperationStarted() {
        rootBuildOperation = CurrentBuildOperationRef.instance().get();
    }

    @Override
    public IncludedBuildController getBuildController(BuildIdentifier buildId) {
        IncludedBuildController buildController = buildControllers.get(buildId);
        if (buildController != null) {
            return buildController;
        }

        IncludedBuildState build = buildRegistry.getIncludedBuild(buildId);
        DefaultIncludedBuildController newBuildController = new DefaultIncludedBuildController(build, coordinationService);
        buildControllers.put(buildId, newBuildController);
        executorService.submit(new BuildOpRunnable(newBuildController, rootBuildOperation));
        return newBuildController;
    }

    @Override
    public void startTaskExecution() {
        for (IncludedBuildController buildController : buildControllers.values()) {
            buildController.startTaskExecution();
        }
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
    }

    @Override
    public void awaitTaskCompletion(Collection<? super Throwable> taskFailures) {
        for (IncludedBuildController buildController : buildControllers.values()) {
            buildController.awaitTaskCompletion(taskFailures);
        }
    }

    @Override
    public void finishBuild(Collection<? super Throwable> failures) {
        CompositeStoppable.stoppable(buildControllers.values()).stop();
        buildControllers.clear();
        for (IncludedBuildState includedBuild : buildRegistry.getIncludedBuilds()) {
            try {
                includedBuild.finishBuild();
            } catch (Exception e) {
                failures.add(e);
            }
        }
    }

    @Override
    public void stop() {
        CompositeStoppable.stoppable(buildControllers.values()).stop();
        executorService.stop();
    }

    private static class BuildOpRunnable implements Runnable {
        private final DefaultIncludedBuildController newBuildController;
        private final BuildOperationRef rootBuildOperation;

        BuildOpRunnable(DefaultIncludedBuildController newBuildController, BuildOperationRef rootBuildOperation) {
            this.newBuildController = newBuildController;
            this.rootBuildOperation = rootBuildOperation;
        }

        @Override
        public void run() {
            CurrentBuildOperationRef.instance().set(rootBuildOperation);
            try {
                newBuildController.run();
            } finally {
                CurrentBuildOperationRef.instance().set(null);
            }
        }
    }
}
