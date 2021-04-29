/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.internal.build.AbstractBuildState;
import org.gradle.internal.build.BuildLifecycleController;
import org.gradle.internal.build.BuildLifecycleControllerFactory;
import org.gradle.internal.build.BuildModelControllerServices;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.StandAloneNestedBuild;
import org.gradle.internal.buildtree.BuildTreeController;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;
import org.gradle.internal.buildtree.BuildTreeWorkExecutor;
import org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController;
import org.gradle.internal.buildtree.DefaultBuildTreeWorkExecutor;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.service.scopes.BuildScopeServices;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.util.Path;

import java.io.File;
import java.util.function.Consumer;
import java.util.function.Function;

class DefaultNestedBuild extends AbstractBuildState implements StandAloneNestedBuild, Stoppable {
    private final Path identityPath;
    private final BuildState owner;
    private final BuildIdentifier buildIdentifier;
    private final BuildDefinition buildDefinition;
    private final BuildLifecycleController buildLifecycleController;

    DefaultNestedBuild(BuildIdentifier buildIdentifier,
                       Path identityPath,
                       BuildDefinition buildDefinition,
                       BuildState owner,
                       BuildTreeController buildTree,
                       BuildLifecycleControllerFactory buildLifecycleControllerFactory,
                       BuildModelControllerServices buildModelControllerServices) {
        this.buildIdentifier = buildIdentifier;
        this.identityPath = identityPath;
        this.buildDefinition = buildDefinition;
        this.owner = owner;
        BuildScopeServices buildScopeServices = new BuildScopeServices(buildTree.getServices());
        buildModelControllerServices.supplyBuildScopeServices(buildScopeServices);
        this.buildLifecycleController = buildLifecycleControllerFactory.newInstance(buildDefinition, this, owner.getMutableModel(), buildScopeServices);
    }

    @Override
    public BuildIdentifier getBuildIdentifier() {
        return buildIdentifier;
    }

    @Override
    public Path getIdentityPath() {
        return identityPath;
    }

    @Override
    public boolean isImplicitBuild() {
        return true;
    }

    @Override
    public void stop() {
        buildLifecycleController.stop();
    }

    @Override
    public <T> T run(Function<? super BuildTreeLifecycleController, T> buildAction) {
        IncludedBuildControllers controllers = buildLifecycleController.getGradle().getServices().get(IncludedBuildControllers.class);
        WorkerLeaseService workerLeaseService = buildLifecycleController.getGradle().getServices().get(WorkerLeaseService.class);
        ExceptionAnalyser exceptionAnalyser = buildLifecycleController.getGradle().getServices().get(ExceptionAnalyser.class);
        // Create a wrapper for the controllers, to prevent the build controller from finishing any other builds
        IncludedBuildControllers noFinishController = new DoNoFinishIncludedBuildControllers(controllers);
        BuildTreeWorkExecutor workExecutor = new DefaultBuildTreeWorkExecutor(controllers, buildLifecycleController);
        DefaultBuildTreeLifecycleController buildController = new DefaultBuildTreeLifecycleController(buildLifecycleController, workerLeaseService, workExecutor, noFinishController, exceptionAnalyser);
        return buildAction.apply(buildController);
    }

    @Override
    public SettingsInternal getLoadedSettings() {
        return buildLifecycleController.getGradle().getSettings();
    }

    @Override
    public Path getCurrentPrefixForProjectsInChildBuilds() {
        return owner.getCurrentPrefixForProjectsInChildBuilds().child(buildDefinition.getName());
    }

    @Override
    public Path getIdentityPathForProject(Path projectPath) {
        return buildLifecycleController.getGradle().getIdentityPath().append(projectPath);
    }

    @Override
    public File getBuildRootDir() {
        return buildLifecycleController.getGradle().getServices().get(BuildLayout.class).getRootDirectory();
    }

    @Override
    public GradleInternal getBuild() {
        return buildLifecycleController.getGradle();
    }

    @Override
    public GradleInternal getMutableModel() {
        return buildLifecycleController.getGradle();
    }

    private static class DoNoFinishIncludedBuildControllers implements IncludedBuildControllers {
        private final IncludedBuildControllers controllers;

        public DoNoFinishIncludedBuildControllers(IncludedBuildControllers controllers) {
            this.controllers = controllers;
        }

        @Override
        public void populateTaskGraphs() {
            controllers.populateTaskGraphs();
        }

        @Override
        public void startTaskExecution() {
            controllers.startTaskExecution();
        }

        @Override
        public void awaitTaskCompletion(Consumer<? super Throwable> taskFailures) {
            controllers.awaitTaskCompletion(taskFailures);
        }

        @Override
        public void finishBuild(Consumer<? super Throwable> collector) {
            // Do not finish any other builds
        }

        @Override
        public IncludedBuildController getBuildController(BuildIdentifier buildIdentifier) {
            return controllers.getBuildController(buildIdentifier);
        }
    }
}
