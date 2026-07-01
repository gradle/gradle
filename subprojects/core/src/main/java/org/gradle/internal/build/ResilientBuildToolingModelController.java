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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.Try;
import org.gradle.internal.buildtree.DeferredBuildFailure;
import org.gradle.internal.buildtree.ToolingModelRequestContext;
import org.gradle.internal.problems.failure.FailureFactory;
import org.gradle.tooling.provider.model.UnknownModelException;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderLookup;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderResultInternal;
import org.gradle.tooling.provider.model.internal.ToolingModelParameterCarrier;
import org.gradle.tooling.provider.model.internal.ToolingModelScope;
import org.gradle.tooling.provider.model.internal.ToolingModelScopeResult;
import org.jspecify.annotations.Nullable;

import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;

public class ResilientBuildToolingModelController extends DefaultBuildToolingModelController {

    private static final Set<String> MODELS_ALLOWED_TO_RUN_FOR_PARTIALLY_CONFIGURED_PROJECTS = ImmutableSet.of(
        // TODO: Is there a better way to identify such models?
        "org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel",
        "org.gradle.kotlin.dsl.tooling.builders.internal.IsolatedScriptsModel"
    );

    private final FailureFactory failureFactory;

    public ResilientBuildToolingModelController(
        BuildState buildState,
        BuildLifecycleController buildController,
        ToolingModelBuilderLookup buildScopeLookup,
        FailureFactory failureFactory
    ) {
        super(buildState, buildController, buildScopeLookup);
        this.failureFactory = failureFactory;
    }

    @Override
    protected Try<Void> configureBuild() {
        return tryRunConfiguration(buildController::configureProjectsIgnoringLaterFailures);
    }

    @Override
    protected ToolingModelScope onDefaultScopeResolutionFailed(Throwable configurationFailure, ToolingModelRequestContext toolingModelContext) {
        // A build-level configuration failure (e.g. a failing settings script) is not tied to a project, so no project
        // scope could be created. Return it as a result instead of throwing, so the client still gets partial models.
        return new FixedResultScope(configurationFailureResult(failureFactory, configurationFailure, null));
    }

    @Override
    protected Try<ToolingModelScope> doLocate(ProjectState targetProject, ToolingModelRequestContext toolingModelContext, Try<Void> buildConfiguration) {
        return Try.successful(new ResilientProjectToolingScope(targetProject, toolingModelContext, buildConfiguration, failureFactory));
    }

    /**
     * A result that returns a configuration failure to the client and carries it as a {@link DeferredBuildFailure} so
     * the build still fails once model building finishes.
     */
    private static ToolingModelScopeResult configurationFailureResult(FailureFactory failureFactory, Throwable configurationFailure, @Nullable Object model) {
        ToolingModelBuilderResultInternal clientResult = ToolingModelBuilderResultInternal.attachFailures(model, ImmutableList.of(failureFactory.create(configurationFailure)));
        return ToolingModelScopeResult.of(clientResult, DeferredBuildFailure.ofConfiguration(configurationFailure));
    }

    private static boolean canRunEvenIfProjectNotFullyConfigured(String modelName) {
        // Some internal model builders can run even if the project is not fully configured.
        return MODELS_ALLOWED_TO_RUN_FOR_PARTIALLY_CONFIGURED_PROJECTS.contains(modelName);
    }

    /**
     * A scope that always returns the same fixed result, used when a builder cannot even be located.
     */
    private static class FixedResultScope implements ToolingModelScope {
        private final ToolingModelScopeResult result;

        public FixedResultScope(ToolingModelScopeResult result) {
            this.result = result;
        }

        @Nullable
        @Override
        public ProjectState getTarget() {
            return null;
        }

        @Override
        public ToolingModelScopeResult getModel(ToolingModelRequestContext modelRequestContext, @Nullable ToolingModelParameterCarrier parameter) {
            return result;
        }
    }

    private static class ResilientProjectToolingScope extends ProjectToolingScope {

        private final FailureFactory failureFactory;
        private final Try<Void> ownerBuildConfiguration;

        public ResilientProjectToolingScope(
            ProjectState targetProject,
            ToolingModelRequestContext toolingModelRequestContext,
            Try<Void> ownerBuildConfiguration,
            FailureFactory failureFactory
        ) {
            super(targetProject, toolingModelRequestContext);
            this.ownerBuildConfiguration = ownerBuildConfiguration;
            this.failureFactory = failureFactory;
        }

        @Override
        public ToolingModelScopeResult getModel(ToolingModelRequestContext modelRequestContext, @Nullable ToolingModelParameterCarrier parameter) {
            // If settings evaluation fails the project is never created, so return the failure before locating a builder.
            if (!targetProject.isCreated()) {
                checkArgument(!ownerBuildConfiguration.isSuccessful(), "Project has not been created, but build configuration has succeeded, this is a bug, please report.");
                return configurationFailureResult(failureFactory, ownerBuildConfiguration.getFailure().get(), null);
            }

            // Force configuration of the target project so that all builders have been registered.
            Try<Void> projectConfiguration = ownerBuildConfiguration.isSuccessful()
                ? tryRunConfiguration(targetProject::ensureConfigured)
                : ownerBuildConfiguration;

            if (!projectConfiguration.isSuccessful()) {
                // Configuration failed. Defer it so the build fails, mirroring a non-resilient sync. For models that
                // tolerate a partially-configured project, still build the model on a best-effort basis.
                Object model = canRunEvenIfProjectNotFullyConfigured(modelName)
                    ? Try.ofFailable(() -> buildModelWithParameter(parameter)).getOrMapFailure(failure -> null)
                    : null;
                return configurationFailureResult(failureFactory, projectConfiguration.getFailure().get(), model);
            }

            // The project configured successfully, but the model builder itself may still fail. Defer such a failure so
            // it fails the build; an UnknownModelException stays a per-model client failure and does not fail the build.
            return Try.ofFailable(() -> super.getModel(modelRequestContext, parameter)).getOrMapFailure(this::asModelBuilderFailure);
        }

        private ToolingModelScopeResult asModelBuilderFailure(Throwable failure) {
            if (failure instanceof UnknownModelException) {
                throw (UnknownModelException) failure;
            }
            ToolingModelBuilderResultInternal clientResult = ToolingModelBuilderResultInternal.of(null, ImmutableList.of(failureFactory.create(failure)));
            return ToolingModelScopeResult.of(clientResult, DeferredBuildFailure.ofModelBuilder(failure));
        }

        @Override
        ToolingModelBuilderLookup.Builder locateBuilder() throws UnknownModelException {
            // Configuration has already been forced by getModel, so just locate the builder. Use the mutable model even
            // after a failure, since builders may still be registered when the project configured only partially.
            ProjectInternal project = targetProject.getMutableModelEvenAfterFailure();
            ToolingModelBuilderLookup lookup = project.getServices().get(ToolingModelBuilderLookup.class);
            return lookup.locateForClientOperation(modelName, parameter, targetProject, project);
        }
    }
}
