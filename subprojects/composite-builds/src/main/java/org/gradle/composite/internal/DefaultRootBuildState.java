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

import org.gradle.BuildResult;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.deployment.internal.DefaultDeploymentRegistry;
import org.gradle.initialization.IncludedBuildSpec;
import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.internal.InternalBuildAdapter;
import org.gradle.internal.build.BuildLifecycleController;
import org.gradle.internal.build.BuildLifecycleControllerFactory;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.BuildWorkGraph;
import org.gradle.internal.build.DefaultBuildWorkGraph;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.buildtree.BuildOperationFiringBuildTreeWorkExecutor;
import org.gradle.internal.buildtree.BuildTreeFinishExecutor;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;
import org.gradle.internal.buildtree.BuildTreeLifecycleControllerFactory;
import org.gradle.internal.buildtree.BuildTreeState;
import org.gradle.internal.buildtree.BuildTreeWorkExecutor;
import org.gradle.internal.buildtree.DefaultBuildTreeFinishExecutor;
import org.gradle.internal.buildtree.DefaultBuildTreeWorkExecutor;
import org.gradle.internal.composite.IncludedBuildInternal;
import org.gradle.internal.composite.IncludedRootBuild;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.service.scopes.BuildScopeServices;
import org.gradle.util.Path;

import java.io.File;
import java.util.function.Function;

class DefaultRootBuildState extends AbstractCompositeParticipantBuildState implements RootBuildState, Stoppable {
    private final ListenerManager listenerManager;
    private final ProjectStateRegistry projectStateRegistry;
    private final BuildLifecycleController buildLifecycleController;
    private final BuildTreeLifecycleController buildTreeLifecycleController;
    private final BuildWorkGraph workGraph;
    private boolean completed;

    DefaultRootBuildState(
        BuildDefinition buildDefinition,
        BuildTreeState buildTree,
        BuildLifecycleControllerFactory buildLifecycleControllerFactory,
        ListenerManager listenerManager,
        ProjectStateRegistry projectStateRegistry
    ) {
        this.listenerManager = listenerManager;
        this.projectStateRegistry = projectStateRegistry;

        BuildScopeServices buildScopeServices = new BuildScopeServices(buildTree.getServices());
        this.buildLifecycleController = buildLifecycleControllerFactory.newInstance(buildDefinition, this, null, buildScopeServices);

        IncludedBuildTaskGraph controllers = buildScopeServices.get(IncludedBuildTaskGraph.class);
        ExceptionAnalyser exceptionAnalyser = buildScopeServices.get(ExceptionAnalyser.class);
        BuildOperationExecutor buildOperationExecutor = buildScopeServices.get(BuildOperationExecutor.class);
        BuildStateRegistry buildStateRegistry = buildScopeServices.get(BuildStateRegistry.class);
        BuildTreeLifecycleControllerFactory buildTreeLifecycleControllerFactory = buildScopeServices.get(BuildTreeLifecycleControllerFactory.class);
        BuildTreeWorkExecutor workExecutor = new BuildOperationFiringBuildTreeWorkExecutor(new DefaultBuildTreeWorkExecutor(controllers, buildLifecycleController), buildOperationExecutor);
        BuildTreeFinishExecutor finishExecutor = new DefaultBuildTreeFinishExecutor(buildStateRegistry, exceptionAnalyser, buildLifecycleController);
        this.buildTreeLifecycleController = buildTreeLifecycleControllerFactory.createController(buildLifecycleController, workExecutor, finishExecutor);
        this.workGraph = new DefaultBuildWorkGraph(buildLifecycleController.getGradle().getTaskGraph(), projectStateRegistry, buildLifecycleController);
    }

    @Override
    protected BuildLifecycleController getBuildController() {
        return buildLifecycleController;
    }

    @Override
    protected ProjectStateRegistry getProjectStateRegistry() {
        return projectStateRegistry;
    }

    @Override
    public BuildWorkGraph getWorkGraph() {
        return workGraph;
    }

    @Override
    public BuildIdentifier getBuildIdentifier() {
        return DefaultBuildIdentifier.ROOT;
    }

    @Override
    public Path getIdentityPath() {
        return Path.ROOT;
    }

    @Override
    public boolean isImplicitBuild() {
        return false;
    }

    @Override
    public void assertCanAdd(IncludedBuildSpec includedBuildSpec) {
    }

    @Override
    public File getBuildRootDir() {
        return buildLifecycleController.getGradle().getServices().get(BuildLayout.class).getRootDirectory();
    }

    @Override
    public IncludedBuildInternal getModel() {
        return new IncludedRootBuild(this);
    }

    @Override
    public void stop() {
        buildLifecycleController.stop();
    }

    @Override
    public <T> T run(Function<? super BuildTreeLifecycleController, T> action) {
        if (completed) {
            throw new IllegalStateException("Cannot run more than one action for a build.");
        }
        try {
            RootBuildLifecycleListener buildLifecycleListener = listenerManager.getBroadcaster(RootBuildLifecycleListener.class);
            buildLifecycleListener.afterStart();
            try {
                GradleInternal gradle = buildLifecycleController.getGradle();
                DefaultDeploymentRegistry deploymentRegistry = gradle.getServices().get(DefaultDeploymentRegistry.class);
                gradle.addBuildListener(new InternalBuildAdapter() {
                    @Override
                    public void buildFinished(BuildResult result) {
                        deploymentRegistry.buildFinished(result);
                    }
                });
                return action.apply(buildTreeLifecycleController);
            } finally {
                buildLifecycleListener.beforeComplete();
            }
        } finally {
            completed = true;
        }
    }

    @Override
    public StartParameterInternal getStartParameter() {
        return buildLifecycleController.getGradle().getStartParameter();
    }

    @Override
    public Path getCurrentPrefixForProjectsInChildBuilds() {
        return Path.ROOT;
    }

    @Override
    public Path calculateIdentityPathForProject(Path path) {
        return path;
    }

    @Override
    public GradleInternal getBuild() {
        return buildLifecycleController.getGradle();
    }

    @Override
    public GradleInternal getMutableModel() {
        return buildLifecycleController.getGradle();
    }
}
