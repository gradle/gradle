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
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier;
import org.gradle.deployment.internal.DefaultDeploymentRegistry;
import org.gradle.initialization.IncludedBuildSpec;
import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.internal.InternalBuildAdapter;
import org.gradle.internal.build.BuildLifecycleController;
import org.gradle.internal.build.BuildLifecycleControllerFactory;
import org.gradle.internal.build.BuildModelControllerServices;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.buildtree.BuildTreeController;
import org.gradle.internal.buildtree.BuildTreeLifecycleController;
import org.gradle.internal.buildtree.DefaultBuildTreeLifecycleController;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.service.scopes.BuildScopeServices;
import org.gradle.util.Path;

import java.io.File;
import java.util.function.Function;

class DefaultRootBuildState extends AbstractCompositeParticipantBuildState implements RootBuildState, Stoppable {
    private final ListenerManager listenerManager;
    private final BuildLifecycleController buildLifecycleController;
    private final DefaultBuildTreeLifecycleController buildController;
    private boolean completed;

    DefaultRootBuildState(BuildDefinition buildDefinition,
                          BuildTreeController buildTree,
                          BuildLifecycleControllerFactory buildLifecycleControllerFactory,
                          BuildModelControllerServices buildModelControllerServices,
                          ListenerManager listenerManager) {
        this.listenerManager = listenerManager;
        BuildScopeServices buildScopeServices = new BuildScopeServices(buildTree.getServices());
        buildModelControllerServices.supplyBuildScopeServices(buildScopeServices);
        this.buildLifecycleController = buildLifecycleControllerFactory.newInstance(buildDefinition, this, null, buildScopeServices);
        this.buildController = new DefaultBuildTreeLifecycleController(buildLifecycleController);
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
                return action.apply(buildController);
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
    public SettingsInternal getLoadedSettings() {
        return buildLifecycleController.getGradle().getSettings();
    }

    @Override
    public Path getCurrentPrefixForProjectsInChildBuilds() {
        return Path.ROOT;
    }

    @Override
    public Path getIdentityPathForProject(Path path) {
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
