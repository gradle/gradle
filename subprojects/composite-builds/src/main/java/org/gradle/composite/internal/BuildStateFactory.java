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

package org.gradle.composite.internal;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.build.BuildModelControllerServices;
import org.gradle.internal.build.BuildLifecycleControllerFactory;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.build.StandAloneNestedBuild;
import org.gradle.internal.buildtree.BuildTreeController;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.session.CrossBuildSessionState;
import org.gradle.util.Path;

@ServiceScope(Scopes.BuildTree.class)
public class BuildStateFactory {
    private final BuildTreeController buildTreeController;
    private final BuildLifecycleControllerFactory buildLifecycleControllerFactory;
    private final BuildModelControllerServices buildModelControllerServices;
    private final ListenerManager listenerManager;
    private final GradleUserHomeScopeServiceRegistry userHomeDirServiceRegistry;
    private final CrossBuildSessionState crossBuildSessionState;
    private final BuildCancellationToken buildCancellationToken;

    public BuildStateFactory(BuildTreeController buildTreeController,
                             BuildLifecycleControllerFactory buildLifecycleControllerFactory,
                             BuildModelControllerServices buildModelControllerServices,
                             ListenerManager listenerManager,
                             GradleUserHomeScopeServiceRegistry userHomeDirServiceRegistry,
                             CrossBuildSessionState crossBuildSessionState,
                             BuildCancellationToken buildCancellationToken) {
        this.buildTreeController = buildTreeController;
        this.buildLifecycleControllerFactory = buildLifecycleControllerFactory;
        this.buildModelControllerServices = buildModelControllerServices;
        this.listenerManager = listenerManager;
        this.userHomeDirServiceRegistry = userHomeDirServiceRegistry;
        this.crossBuildSessionState = crossBuildSessionState;
        this.buildCancellationToken = buildCancellationToken;
    }

    public RootBuildState createRootBuild(BuildDefinition buildDefinition) {
        return new DefaultRootBuildState(buildDefinition, buildTreeController, buildLifecycleControllerFactory, buildModelControllerServices, listenerManager);
    }

    public StandAloneNestedBuild createNestedBuild(BuildIdentifier buildIdentifier, Path identityPath, BuildDefinition buildDefinition, BuildState owner) {
        return new DefaultNestedBuild(buildIdentifier, identityPath, buildDefinition, owner, buildTreeController, buildLifecycleControllerFactory, buildModelControllerServices);
    }

    public RootOfNestedBuildTree createNestedTree(BuildDefinition buildDefinition,
                                                  BuildIdentifier buildIdentifier,
                                                  Path identityPath,
                                                  BuildState owner) {
        return new RootOfNestedBuildTree(buildDefinition, buildIdentifier, identityPath, owner, buildModelControllerServices, userHomeDirServiceRegistry, crossBuildSessionState, buildCancellationToken);
    }
}
