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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.api.internal.GradleInternal;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.resources.ProjectLeaseRegistry;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

@ServiceScope(Scopes.BuildTree.class)
public class BuildControllerFactory {
    private final BuildCancellationToken buildCancellationToken;
    private final BuildOperationExecutor buildOperationExecutor;
    private final ProjectLeaseRegistry projectLeaseRegistry;
    private final BuildStateRegistry buildStateRegistry;

    public BuildControllerFactory(BuildCancellationToken buildCancellationToken, BuildOperationExecutor buildOperationExecutor, ProjectLeaseRegistry projectLeaseRegistry, BuildStateRegistry buildStateRegistry) {
        this.buildCancellationToken = buildCancellationToken;
        this.buildOperationExecutor = buildOperationExecutor;
        this.projectLeaseRegistry = projectLeaseRegistry;
        this.buildStateRegistry = buildStateRegistry;
    }

    public DefaultBuildController controllerFor(GradleInternal gradle) {
        return new DefaultBuildController(gradle, buildCancellationToken, buildOperationExecutor, projectLeaseRegistry, buildStateRegistry);
    }
}
