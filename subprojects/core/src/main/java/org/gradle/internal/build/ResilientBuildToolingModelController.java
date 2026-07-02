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
import org.gradle.internal.buildtree.ResilientModelBuildingFailureCollector;
import org.gradle.internal.buildtree.ToolingModelRequestContext;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.internal.problems.failure.FailureFactory;
import org.gradle.tooling.provider.model.UnknownModelException;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderLookup;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderResultInternal;
import org.gradle.tooling.provider.model.internal.ToolingModelParameterCarrier;
import org.gradle.tooling.provider.model.internal.ToolingModelScope;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkArgument;

public class ResilientBuildToolingModelController extends DefaultBuildToolingModelController {

    private static final Set<String> MODELS_ALLOWED_TO_RUN_FOR_PARTIALLY_CONFIGURED_PROJECTS = ImmutableSet.of(
        // TODO: Is there a better way to identify such models?
        "org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel",
        "org.gradle.kotlin.dsl.tooling.builders.internal.IsolatedScriptsModel"
    );

    private final FailureFactory failureFactory;
    private final ResilientModelBuildingFailureCollector modelBuildingFailureCollector;

    public ResilientBuildToolingModelController(
        BuildState buildState,
        BuildLifecycleController buildController,
        ToolingModelBuilderLookup buildScopeLookup,
        FailureFactory failureFactory,
        ResilientModelBuildingFailureCollector modelBuildingFailureCollector
    ) {
        super(buildState, buildController, buildScopeLookup);
        this.failureFactory = failureFactory;
        this.modelBuildingFailureCollector = modelBuildingFailureCollector;
    }

    @Override
    protected Try<Void> configureBuild() {
        Try<Void> configuration = tryRunConfiguration(buildController::configureProjectsIgnoringLaterFailures);
        // A build-level configuration failure (e.g. a failing settings script) is not tied to a specific project, so
        // it is not recorded by the per-project capture below - the default project may not even exist. Record it
        // here so the build still fails at finish, while partial models are returned. The failure is deduplicated, so
        // recording it again per project is harmless.
        configuration.getFailure().ifPresent(modelBuildingFailureCollector::addConfigurationFailure);
        return configuration;
    }

    @Override
    protected Try<ToolingModelScope> doLocate(ProjectState targetProject, ToolingModelRequestContext toolingModelContext, Try<Void> buildConfiguration) {
        return Try.successful(new ResilientProjectToolingScope(targetProject, toolingModelContext, buildConfiguration, failureFactory, modelBuildingFailureCollector));
    }

    private static class ResilientProjectToolingScope extends ProjectToolingScope {

        private final FailureFactory failureFactory;
        private final ResilientModelBuildingFailureCollector modelBuildingFailureCollector;
        private final Try<Void> ownerBuildConfiguration;

        public ResilientProjectToolingScope(
            ProjectState targetProject,
            ToolingModelRequestContext toolingModelRequestContext,
            Try<Void> ownerBuildConfiguration,
            FailureFactory failureFactory,
            ResilientModelBuildingFailureCollector modelBuildingFailureCollector
        ) {
            super(targetProject, toolingModelRequestContext);
            this.ownerBuildConfiguration = ownerBuildConfiguration;
            this.failureFactory = failureFactory;
            this.modelBuildingFailureCollector = modelBuildingFailureCollector;
        }

        @Override
        public ToolingModelBuilderResultInternal getModel(ToolingModelRequestContext modelName, @Nullable ToolingModelParameterCarrier parameter) {
            // If evaluation of settings fails, a project could not be created, and we should return the failure before locateBuilder is called
            if (!targetProject.isCreated()) {
                checkArgument(!ownerBuildConfiguration.isSuccessful(), "Project has not been created, but build configuration has succeeded, this is a bug, please report.");
                // Record the configuration failure so the build fails when it finishes, while still returning it in the fetch result
                ownerBuildConfiguration.getFailure().ifPresent(modelBuildingFailureCollector::addConfigurationFailure);
                return ToolingModelBuilderResultInternal.of(getConfigurationFailure(failureFactory, ownerBuildConfiguration));
            }
            return super.getModel(modelName, parameter);
        }

        @Override
        ToolingModelBuilderLookup.Builder locateBuilder() throws UnknownModelException {
            // Force configuration of the target project to ensure all builders have been registered
            Try<Void> projectConfiguration = ownerBuildConfiguration.isSuccessful()
                ? tryRunConfiguration(targetProject::ensureConfigured)
                : ownerBuildConfiguration;

            // We need to query the delegate builder lazily, since builders may not be registered if project configuration fails
            ProjectInternal project = targetProject.getMutableModelEvenAfterFailure();
            ToolingModelBuilderLookup lookup = project.getServices().get(ToolingModelBuilderLookup.class);

            Supplier<ToolingModelBuilderLookup.Builder> builder = () -> lookup.locateForClientOperation(modelName, parameter, targetProject, project);
            boolean canRunEvenIfProjectNotFullyConfigured = canRunEvenIfProjectNotFullyConfigured(modelName);
            return new ResilientToolingModelBuilder(builder, projectConfiguration, failureFactory, modelBuildingFailureCollector, canRunEvenIfProjectNotFullyConfigured);
        }
    }

    private static boolean canRunEvenIfProjectNotFullyConfigured(String modelName) {
        // Some internal model builders can run even if the project is not fully configured.
        return MODELS_ALLOWED_TO_RUN_FOR_PARTIALLY_CONFIGURED_PROJECTS.contains(modelName);
    }

    private static List<Failure> getConfigurationFailure(FailureFactory failureFactory, Try<Void> configuration) {
        Optional<Throwable> failure = configuration.getFailure();
        return failure.map(e -> ImmutableList.of(failureFactory.create(e))).orElseGet(ImmutableList::of);
    }

    private static class ResilientToolingModelBuilder implements ToolingModelBuilderLookup.Builder {

        private final Lazy<ToolingModelBuilderLookup.Builder> delegate;
        private final Try<Void> projectConfiguration;
        private final FailureFactory failureFactory;
        private final ResilientModelBuildingFailureCollector modelBuildingFailureCollector;
        private final boolean canRunEvenIfProjectNotFullyConfigured;

        public ResilientToolingModelBuilder(
            Supplier<ToolingModelBuilderLookup.Builder> delegate,
            Try<Void> projectConfiguration,
            FailureFactory failureFactory,
            ResilientModelBuildingFailureCollector modelBuildingFailureCollector,
            boolean canRunEvenIfProjectNotFullyConfigured
        ) {
            this.delegate = Lazy.unsafe().of(delegate);
            this.projectConfiguration = projectConfiguration;
            this.failureFactory = failureFactory;
            this.modelBuildingFailureCollector = modelBuildingFailureCollector;
            this.canRunEvenIfProjectNotFullyConfigured = canRunEvenIfProjectNotFullyConfigured;
        }

        @Override
        public @Nullable Class<?> getParameterType() {
            return delegate.get().getParameterType();
        }

        @Override
        public Object build(@Nullable Object parameter) {
            if (projectConfiguration.isSuccessful()) {
                // The project configured successfully, but the model builder itself may still fail. Such a failure is
                // not a configuration failure, so record it to fail the build, while still returning failure in a fetch result to the client
                return tryBuildModel(parameter).getOrMapFailure(this::recordAsSoftFailure);
            }

            // Configuration failed. Record it so the build fails when it finishes (mirroring non-resilient sync),
            // while still returning the failure in the fetch result. For models that tolerate a partially-configured
            // project, still build the model on a best-effort basis, but never let a model builder failure mask the
            // configuration failure that is reported below.
            projectConfiguration.getFailure().ifPresent(modelBuildingFailureCollector::addConfigurationFailure);
            Object model = canRunEvenIfProjectNotFullyConfigured ? tryBuildModel(parameter).getOrMapFailure(failure -> null) : null;
            return ToolingModelBuilderResultInternal.attachFailures(model, getConfigurationFailure(failureFactory, projectConfiguration));
        }

        private Try<Object> tryBuildModel(@Nullable Object parameter) {
            return Try.ofFailable(() -> delegate.get().build(parameter));
        }

        private ToolingModelBuilderResultInternal recordAsSoftFailure(Throwable failure) {
            // A missing model builder (UnknownModelException) is a legitimate per-model result for the client, not a
            // build failure. Let it propagate so the caller reports it as a per-model failure without failing the build.
            if (failure instanceof UnknownModelException) {
                throw (UnknownModelException) failure;
            }
            modelBuildingFailureCollector.addModelBuilderFailure(failure);
            return ToolingModelBuilderResultInternal.of(null, ImmutableList.of(failureFactory.create(failure)));
        }
    }
}
