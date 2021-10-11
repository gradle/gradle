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

package org.gradle.internal.build;

import org.gradle.internal.buildtree.BuildModelParameters;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.resources.ProjectLeaseRegistry;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scopes.BuildTree.class)
public class BuildToolingModelControllerFactory {
    private final BuildOperationExecutor buildOperationExecutor;
    private final ProjectLeaseRegistry projectLeaseRegistry;
    private final boolean parallelActions;
    // Apply some locking around configuration and tooling model lookup. This should move deeper into the build tree and build state controllers
    private final Object treeMutableStateLock = new Object();

    public BuildToolingModelControllerFactory(
        BuildModelParameters buildModelParameters,
        BuildOperationExecutor buildOperationExecutor,
        ProjectLeaseRegistry projectLeaseRegistry
    ) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.projectLeaseRegistry = projectLeaseRegistry;
        this.parallelActions = buildModelParameters.isParallelToolingApiActions();
    }

    BuildToolingModelController createController(BuildLifecycleController owner) {
        return new DefaultBuildToolingModelController(owner, projectLeaseRegistry, buildOperationExecutor, treeMutableStateLock, parallelActions);
    }
}
