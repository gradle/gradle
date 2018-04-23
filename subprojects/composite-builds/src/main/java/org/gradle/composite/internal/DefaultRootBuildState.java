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

import org.gradle.api.Action;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier;
import org.gradle.initialization.BuildRequestContext;
import org.gradle.initialization.GradleLauncher;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.invocation.BuildController;
import org.gradle.internal.invocation.GradleBuildController;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.util.Path;

class DefaultRootBuildState implements RootBuildState {
    private final BuildDefinition buildDefinition;
    private final BuildRequestContext requestContext;
    private final GradleLauncherFactory gradleLauncherFactory;
    private final ListenerManager listenerManager;
    private final ServiceRegistry services;
    private SettingsInternal settings;

    DefaultRootBuildState(BuildDefinition buildDefinition, BuildRequestContext requestContext, GradleLauncherFactory gradleLauncherFactory, ListenerManager listenerManager, ServiceRegistry services) {
        this.buildDefinition = buildDefinition;
        this.requestContext = requestContext;
        this.gradleLauncherFactory = gradleLauncherFactory;
        this.listenerManager = listenerManager;
        this.services = services;
    }

    @Override
    public Object run(Action<? super BuildController> buildAction) {
        GradleLauncher gradleLauncher = gradleLauncherFactory.newInstance(buildDefinition, requestContext, services);
        GradleBuildController buildController = new GradleBuildController(gradleLauncher);
        try {
            RootBuildLifecycleListener buildLifecycleListener = listenerManager.getBroadcaster(RootBuildLifecycleListener.class);
            buildLifecycleListener.afterStart();
            try {
                buildAction.execute(buildController);
                return buildController.getResult();
            } finally {
                buildLifecycleListener.beforeComplete();
            }
        } finally {
            buildController.stop();
        }
    }

    public void setSettings(SettingsInternal settings) {
        this.settings = settings;
    }

    @Override
    public SettingsInternal getLoadedSettings() {
        if (settings == null) {
            throw new IllegalStateException("Settings have not been attached to this build yet.");
        }
        return settings;
    }

    @Override
    public Path getIdentityPathForProject(Path path) {
        return path;
    }

    @Override
    public BuildIdentifier getBuildIdentifier() {
        return DefaultBuildIdentifier.ROOT;
    }

    @Override
    public boolean isImplicitBuild() {
        return false;
    }
}
