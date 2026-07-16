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
import org.gradle.api.GradleException;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectState;
import org.gradle.internal.Try;
import org.gradle.internal.buildtree.ToolingModelRequestContext;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.internal.problems.failure.FailureFactory;
import org.gradle.tooling.provider.model.UnknownModelException;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderLookup;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderResultInternal;
import org.gradle.tooling.provider.model.internal.ToolingModelParameterCarrier;
import org.gradle.tooling.provider.model.internal.ToolingModelScope;
import org.gradle.tooling.provider.model.internal.ToolingModelScopeResult;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.ImmutableList.toImmutableList;

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
    protected ToolingModelScope createBuildScope(ToolingModelBuilderLookup.Builder builder) {
        return new ResilientBuildToolingScope(builder, failureFactory);
    }

    @Override
    protected Try<ToolingModelScope> doLocateForProjectScope(Try<ProjectState> targetProject, ToolingModelRequestContext toolingModelContext, Try<Void> buildConfiguration) {
        if (!targetProject.isSuccessful()) {
            // No project could be created (e.g. a failing settings script), so no project scope exists. Surface the
            // failure through a result instead of throwing, so the client still gets partial models and the build still
            // fails. Prefer a build configuration failure.
            Throwable configurationFailure = buildConfiguration.getFailure().orElseGet(() -> targetProject.getFailure().get());
            return Try.successful(new FixedResultScope(configurationFailureResult(failureFactory, configurationFailure, null)));
        }
        return Try.successful(new ResilientProjectToolingScope(targetProject.get(), toolingModelContext, buildConfiguration, failureFactory));
    }

    private static ToolingModelScopeResult configurationFailureResult(FailureFactory failureFactory, Throwable configurationFailure, @Nullable Object model) {
        return configurationFailureResult(failureFactory, configurationFailure, configurationFailure, model);
    }

    private static ToolingModelScopeResult configurationFailureResult(FailureFactory failureFactory, Throwable clientFailure, Throwable buildFailure, @Nullable Object model) {
        ToolingModelBuilderResultInternal clientResult = ToolingModelBuilderResultInternal.attachFailures(model, ImmutableList.of(failureFactory.create(clientFailure)));
        return ToolingModelScopeResult.withConfigurationFailure(clientResult, buildFailure);
    }

    private static boolean canRunEvenIfProjectNotFullyConfigured(String modelName) {
        // Some internal model builders can run even if the project is not fully configured.
        return MODELS_ALLOWED_TO_RUN_FOR_PARTIALLY_CONFIGURED_PROJECTS.contains(modelName);
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
                Throwable buildFailure = projectConfiguration.getFailure().get();
                return configurationFailureResult(failureFactory, projectOwnConfigurationFailure(), buildFailure, model);
            }

            // The project configured successfully, but the model builder itself may still fail.
            //noinspection DataFlowIssue
            return Try.ofFailable(() -> ToolingModelScopeResult.of(buildModelWithParameter(parameter))).getOrMapFailure(failure -> {
                if (failure instanceof UnknownModelException) {
                    throw (UnknownModelException) failure;
                }
                ToolingModelBuilderResultInternal clientResult = ToolingModelBuilderResultInternal.of(null, ImmutableList.of(failureFactory.create(failure)));
                return ToolingModelScopeResult.withModelBuilderFailure(clientResult, failure);
            });
        }

        /**
         * The target project's own recorded configuration failure, read from state without reconfiguring it.
         * Falls back to a general failure when the project has none of its own: it configured cleanly, or was never reached because another project aborted configuration.
         */
        private Throwable projectOwnConfigurationFailure() {
            Throwable ownFailure = targetProject.getMutableModelEvenAfterFailure().getState().getFailure();
            if (ownFailure != null) {
                return ownFailure;
            }
            return new GeneralConfigurationFailure();
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

    private static class ResilientBuildToolingScope extends BuildToolingScope {

        private final FailureFactory failureFactory;

        public ResilientBuildToolingScope(ToolingModelBuilderLookup.Builder builder, FailureFactory failureFactory) {
            super(builder);
            this.failureFactory = failureFactory;
        }

        @Override
        public ToolingModelScopeResult getModel(ToolingModelRequestContext modelRequestContext, @Nullable ToolingModelParameterCarrier parameter) {
            return Try.ofFailable(() -> buildScopeResult(parameter)).getOrMapFailure(failure -> {
                ToolingModelBuilderResultInternal clientResult = ToolingModelBuilderResultInternal.of(null, ImmutableList.of(failureFactory.create(failure)));
                return ToolingModelScopeResult.withModelBuilderFailure(clientResult, failure);
            });
        }

        private ToolingModelScopeResult buildScopeResult(@Nullable ToolingModelParameterCarrier parameter) {
            ToolingModelBuilderResultInternal clientResult = buildModelWithParameter(parameter);
            // Failures attached by a build-scoped builder (e.g. GradleBuildBuilder) are configuration failures
            // of the visited builds, so they must still fail the build.
            List<Throwable> configurationFailures = clientResult.getFailures().stream()
                .map(Failure::getOriginal)
                .collect(toImmutableList());
            return ToolingModelScopeResult.withConfigurationFailures(clientResult, configurationFailures);
        }
    }

    /**
     * The failure reported for a project that has no configuration failure of its own. Stackless, since it
     * exists only to carry this constant message to the client: the real failure travels separately as the
     * build failure, so a stack trace here is never useful.
     */
    private static final class GeneralConfigurationFailure extends GradleException {
        GeneralConfigurationFailure() {
            super("The build could not be configured; see the reported build failures for the underlying problems.");
        }

        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
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
}
