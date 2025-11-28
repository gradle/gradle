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
import org.gradle.internal.buildtree.ToolingModelRequestContext;
import org.gradle.tooling.provider.model.UnknownModelException;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderLookup;
import org.gradle.tooling.provider.model.internal.ToolingModelScope;

import java.util.Set;

public class ResilientBuildToolingModelController extends DefaultBuildToolingModelController {

    private static final Set<String> RESILIENT_MODELS = ImmutableSet.of(
        // TODO: Is there a better way to identify resilient models?
        "org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel"
    );

    public ResilientBuildToolingModelController(
        BuildState buildState,
        BuildLifecycleController buildController,
        ToolingModelBuilderLookup buildScopeLookup
    ) {
        super(buildState, buildController, buildScopeLookup);
    }

    @Override
    protected void configureProjectsForModel(ProjectState target, String modelName) {
        try {
            super.configureProjectsForModel(target, modelName);
        } catch (GradleException e) {
            rethrowExceptionIfNotResilientModel(target, modelName, e);
        }
    }

    private static void rethrowExceptionIfNotResilientModel(ProjectState target, String modelName, GradleException e) {
        if (!target.isCreated()) {
            // mutable models weren't created, no point in pushing further
            throw e;
        }

        if (!RESILIENT_MODELS.contains(modelName)) {
            // the model we are building is not a resilient one, no point in pushing further
            throw e;
        }
        // swallowing the exception, there is hope of going further
    }

    @Override
    protected ToolingModelScope doLocate(ProjectState target, ToolingModelRequestContext toolingModelContext) {
        return new ResilientProjectToolingScope(target, toolingModelContext);
    }

    private static class ResilientProjectToolingScope extends ProjectToolingScope {
        public ResilientProjectToolingScope(ProjectState target, ToolingModelRequestContext toolingModelContext) {
            super(target, toolingModelContext.getModelName(), toolingModelContext.getParameter().isPresent());
        }

        @Override
        ToolingModelBuilderLookup.Builder locateBuilder() throws UnknownModelException {
            // Force configuration of the target project to ensure all builders have been registered, but ignore failures
            try {
                targetProject.ensureConfigured();
            } catch (GradleException e) {
                rethrowExceptionIfNotResilientModel(targetProject, modelName, e);
            }

            ProjectInternal project = targetProject.getMutableModelEvenAfterFailure();
            ToolingModelBuilderLookup lookup = project.getServices().get(ToolingModelBuilderLookup.class);
            return lookup.locateForClientOperation(modelName, parameter, targetProject, project);
        }
    }
}
