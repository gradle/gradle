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
import org.gradle.api.internal.project.ProjectState;
import org.gradle.tooling.provider.model.UnknownModelException;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderLookup;
import org.gradle.tooling.provider.model.internal.ToolingModelScope;

import javax.annotation.Nullable;
import java.util.function.Function;

public class DefaultBuildToolingModelController implements BuildToolingModelController {
    private final BuildLifecycleController buildController;
    private final BuildState buildState;
    private final ToolingModelBuilderLookup buildScopeLookup;

    public DefaultBuildToolingModelController(
        BuildState buildState,
        BuildLifecycleController buildController,
        ToolingModelBuilderLookup buildScopeLookup
    ) {
        this.buildState = buildState;
        this.buildController = buildController;
        this.buildScopeLookup = buildScopeLookup;
    }

    @Override
    public GradleInternal getConfiguredModel() {
        return buildController.getConfiguredBuild();
    }

    @Override
    public ToolingModelScope locateBuilderForTarget(String modelName, boolean param) {
        // Look for a build scoped builder
        ToolingModelBuilderLookup.Builder builder = buildScopeLookup.maybeLocateForBuildScope(modelName, param, buildState);
        if (builder != null) {
            return new BuildToolingScope(builder);
        }

        // Force configuration of the build and locate builder for default project
        ProjectState targetProject = buildController.withProjectsConfigured(gradle -> gradle.getDefaultProject().getOwner());
        return doLocate(targetProject, modelName, param);
    }

    @Override
    public ToolingModelScope locateBuilderForTarget(ProjectState target, String modelName, boolean param) {
        if (target.getOwner() != buildState) {
            throw new IllegalArgumentException("Project has unexpected owner.");
        }
        // Force configuration of the containing build and then locate the builder for target project
        buildController.configureProjects();
        return doLocate(target, modelName, param);
    }

    private ToolingModelScope doLocate(ProjectState target, String modelName, boolean param) {
        return new ProjectToolingScope(target, modelName, param);
    }

    private static abstract class AbstractToolingScope implements ToolingModelScope {
        abstract ToolingModelBuilderLookup.Builder locateBuilder() throws UnknownModelException;

        @Override
        public Object getModel(String modelName, @Nullable Function<Class<?>, Object> parameterFactory) {
            ToolingModelBuilderLookup.Builder builder = locateBuilder();
            if (parameterFactory == null) {
                return builder.build(null);
            } else {
                Object parameter = parameterFactory.apply(builder.getParameterType());
                return builder.build(parameter);
            }
        }
    }

    private static class BuildToolingScope extends AbstractToolingScope {
        private final ToolingModelBuilderLookup.Builder builder;

        public BuildToolingScope(ToolingModelBuilderLookup.Builder builder) {
            this.builder = builder;
        }

        @Nullable
        @Override
        public ProjectState getTarget() {
            return null;
        }

        @Override
        ToolingModelBuilderLookup.Builder locateBuilder() throws UnknownModelException {
            return builder;
        }
    }

    private static class ProjectToolingScope extends AbstractToolingScope {
        private final ProjectState target;
        private final String modelName;
        private final boolean parameter;

        public ProjectToolingScope(ProjectState target, String modelName, boolean parameter) {
            this.target = target;
            this.modelName = modelName;
            this.parameter = parameter;
        }

        @Nullable
        @Override
        public ProjectState getTarget() {
            return target;
        }

        @Override
        ToolingModelBuilderLookup.Builder locateBuilder() throws UnknownModelException {
            // Force configuration of the target project to ensure all builders have been registered
            target.ensureConfigured();
            ToolingModelBuilderLookup lookup = target.getMutableModel().getServices().get(ToolingModelBuilderLookup.class);
            return lookup.locateForClientOperation(modelName, parameter, target);
        }
    }
}
