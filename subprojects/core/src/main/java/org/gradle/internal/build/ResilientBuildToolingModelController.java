/*
 * Copyright 2025 the original author or authors.
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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.GradleException;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.tooling.provider.model.UnknownModelException;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderLookup;
import org.gradle.tooling.provider.model.internal.ToolingModelScope;

import java.util.Set;

public class ResilientBuildToolingModelController extends DefaultBuildToolingModelController {

    private static final Set<String> RESILIENT_MODELS = ImmutableSet.of(
        // TODO: Is there a better way to identify resilient models?
        "org.gradle.tooling.model.kotlin.dsl.ResilientKotlinDslScriptsModel"
    );

    public ResilientBuildToolingModelController(
        BuildState buildState,
        BuildLifecycleController buildController,
        ToolingModelBuilderLookup buildScopeLookup) {
        super(buildState, buildController, buildScopeLookup);
    }

    @Override
    protected void configureProjectsForModel(String modelName) {
        try {
            super.configureProjectsForModel(modelName);
        } catch (GradleException e) {
            rethrowExceptionIfNotResilientModel(modelName, e);
        }
    }

    private static void rethrowExceptionIfNotResilientModel(String modelName, GradleException e) {
        // For resilient models, ignore configuration failures
        if (!RESILIENT_MODELS.contains(modelName)) {
            throw e;
        }
    }

    @Override
    protected ToolingModelScope doLocate(ProjectState target, String modelName, boolean param) {
        return new ResilientProjectToolingScope(target, modelName, param);
    }

    private static class ResilientProjectToolingScope extends ProjectToolingScope {
        public ResilientProjectToolingScope(ProjectState target, String modelName, boolean parameter) {
            super(target, modelName, parameter);
        }

        @Override
        ToolingModelBuilderLookup.Builder locateBuilder() throws UnknownModelException {
            // Force configuration of the target project to ensure all builders have been registered, but ignore failures
            try {
                target.ensureConfigured();
            } catch (GradleException e) {
                rethrowExceptionIfNotResilientModel(modelName, e);
            }

            ProjectInternal project = target.getMutableModelEvenAfterFailure();
            ToolingModelBuilderLookup lookup = project.getServices().get(ToolingModelBuilderLookup.class);
            return lookup.locateForClientOperation(modelName, parameter, target, project);
        }
    }
}
