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

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.tooling.provider.model.UnknownModelException;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderLookup;

class DefaultBuildToolingModelController implements BuildToolingModelController {
    private final BuildLifecycleController buildController;
    private final Object treeMutableStateLock;
    private final BuildState buildState;
    private final ToolingModelBuilderLookup buildScopeLookup;

    public DefaultBuildToolingModelController(
        BuildLifecycleController buildController,
        Object treeMutableStateLock
    ) {
        this.treeMutableStateLock = treeMutableStateLock;
        this.buildController = buildController;
        this.buildState = this.buildController.getGradle().getOwner();
        this.buildScopeLookup = this.buildController.getGradle().getServices().get(ToolingModelBuilderLookup.class);
    }

    @Override
    public GradleInternal getConfiguredModel() {
        return buildController.getConfiguredBuild();
    }

    @Override
    public ToolingModelBuilderLookup.Builder locateBuilderForTarget(String modelName, boolean param) throws UnknownModelException {
        synchronized (treeMutableStateLock) {
            // Look for a build scoped builder
            ToolingModelBuilderLookup.Builder builder = buildScopeLookup.maybeLocateForBuildScope(modelName, param, buildState);
            if (builder != null) {
                return builder;
            }

            // Force configuration of the build and locate builder for default project
            buildState.ensureProjectsConfigured();
        }
        ProjectInternal targetProject = buildController.getGradle().getDefaultProject();
        return doLocate(targetProject.getOwner(), modelName, param);
    }

    @Override
    public ToolingModelBuilderLookup.Builder locateBuilderForTarget(ProjectState target, String modelName, boolean param) throws UnknownModelException {
        synchronized (treeMutableStateLock) {
            // Force configuration of the containing build and then locate the builder for target project
            buildState.ensureProjectsConfigured();
        }
        return doLocate(target, modelName, param);
    }

    private ToolingModelBuilderLookup.Builder doLocate(ProjectState target, String modelName, boolean param) {
        if (target.getOwner() != buildState) {
            throw new IllegalArgumentException("Project has unexpected owner.");
        }
        // Force configuration of the target project to ensure all builders have been registered
        target.ensureConfigured();
        ToolingModelBuilderLookup lookup = target.getMutableModel().getServices().get(ToolingModelBuilderLookup.class);
        return lookup.locateForClientOperation(modelName, param, target);
    }
}
