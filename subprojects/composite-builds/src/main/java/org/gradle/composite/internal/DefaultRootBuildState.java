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

import org.gradle.StartParameter;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier;
import org.gradle.initialization.GradleLauncher;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.initialization.IncludedBuildSpec;
import org.gradle.initialization.NestedBuildFactory;
import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.internal.build.AbstractBuildState;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.buildtree.BuildTreeState;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.invocation.BuildController;
import org.gradle.internal.invocation.GradleBuildController;
import org.gradle.util.Path;

import java.io.File;

class DefaultRootBuildState extends AbstractBuildState implements RootBuildState, Stoppable {
    private final ListenerManager listenerManager;
    private final GradleLauncher gradleLauncher;

    DefaultRootBuildState(BuildDefinition buildDefinition, GradleLauncherFactory gradleLauncherFactory, ListenerManager listenerManager, BuildTreeState owner) {
        this.listenerManager = listenerManager;
        this.gradleLauncher = gradleLauncherFactory.newInstance(buildDefinition, this, owner);
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
        return gradleLauncher.getBuildRootDir();
    }

    @Override
    public void stop() {
        gradleLauncher.stop();
    }

    @Override
    public <T> T run(Transformer<T, ? super BuildController> buildAction) {
        final GradleBuildController buildController = new GradleBuildController(gradleLauncher);
        RootBuildLifecycleListener buildLifecycleListener = listenerManager.getBroadcaster(RootBuildLifecycleListener.class);
        GradleInternal gradle = buildController.getGradle();
        buildLifecycleListener.afterStart(gradle);
        try {
            return buildAction.transform(buildController);
        } finally {
            buildLifecycleListener.beforeComplete(gradle);
        }
    }

    @Override
    public StartParameter getStartParameter() {
        return gradleLauncher.getGradle().getStartParameter();
    }

    @Override
    public SettingsInternal getLoadedSettings() {
        return gradleLauncher.getGradle().getSettings();
    }

    @Override
    public NestedBuildFactory getNestedBuildFactory() {
        return gradleLauncher.getGradle().getServices().get(NestedBuildFactory.class);
    }

    @Override
    public Path getCurrentPrefixForProjectsInChildBuilds() {
        return Path.ROOT;
    }

    @Override
    public Path getIdentityPathForProject(Path path) {
        return path;
    }
}
