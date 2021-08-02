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
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.build.BuildLifecycleControllerFactory;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.build.StandAloneNestedBuild;
import org.gradle.internal.buildtree.BuildTreeState;
import org.gradle.internal.buildtree.NestedBuildTree;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.session.CrossBuildSessionState;
import org.gradle.util.Path;

@ServiceScope(Scopes.BuildTree.class)
public class BuildStateFactory {
    private final BuildTreeState buildTreeState;
    private final BuildLifecycleControllerFactory buildLifecycleControllerFactory;
    private final ListenerManager listenerManager;
    private final GradleUserHomeScopeServiceRegistry userHomeDirServiceRegistry;
    private final CrossBuildSessionState crossBuildSessionState;
    private final BuildCancellationToken buildCancellationToken;
    private final ProjectStateRegistry projectStateRegistry;

    public BuildStateFactory(BuildTreeState buildTreeState,
                             BuildLifecycleControllerFactory buildLifecycleControllerFactory,
                             ListenerManager listenerManager,
                             GradleUserHomeScopeServiceRegistry userHomeDirServiceRegistry,
                             CrossBuildSessionState crossBuildSessionState,
                             BuildCancellationToken buildCancellationToken,
                             ProjectStateRegistry projectStateRegistry) {
        this.buildTreeState = buildTreeState;
        this.buildLifecycleControllerFactory = buildLifecycleControllerFactory;
        this.listenerManager = listenerManager;
        this.userHomeDirServiceRegistry = userHomeDirServiceRegistry;
        this.crossBuildSessionState = crossBuildSessionState;
        this.buildCancellationToken = buildCancellationToken;
        this.projectStateRegistry = projectStateRegistry;
    }

    public RootBuildState createRootBuild(BuildDefinition buildDefinition) {
        return new DefaultRootBuildState(buildDefinition, buildTreeState, buildLifecycleControllerFactory, listenerManager, projectStateRegistry);
    }

    public StandAloneNestedBuild createNestedBuild(BuildIdentifier buildIdentifier, Path identityPath, BuildDefinition buildDefinition, BuildState owner) {
        return new DefaultNestedBuild(buildIdentifier, identityPath, buildDefinition, owner, buildTreeState, buildLifecycleControllerFactory, projectStateRegistry);
    }

    public NestedBuildTree createNestedTree(BuildDefinition buildDefinition,
                                            BuildIdentifier buildIdentifier,
                                            Path identityPath,
                                            BuildState owner) {
        return new DefaultNestedBuildTree(buildDefinition, buildIdentifier, identityPath, owner, userHomeDirServiceRegistry, crossBuildSessionState, buildCancellationToken);
    }
}
