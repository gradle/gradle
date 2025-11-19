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
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.problems.failure.Failure;
import org.gradle.internal.problems.failure.FailureFactory;
import org.gradle.tooling.provider.model.UnknownModelException;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderLookup;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderResultInternal;
import org.gradle.tooling.provider.model.internal.ToolingModelScope;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static com.google.common.base.Preconditions.checkNotNull;

public class ResilientBuildToolingModelController extends DefaultBuildToolingModelController {

    private static final Set<String> RESILIENT_MODELS = ImmutableSet.of(
        // TODO: Is there a better way to identify resilient models?
        "org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel"
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
    protected ConfigurationResult configureProjects() {
        return tryRunConfiguration(buildController::configureProjectsIgnoringLaterFailures);
    }

    @Override
    protected ToolingModelScope doLocate(ProjectState target, String modelName, boolean param, ConfigurationResult configurationResult) {
        return new ResilientProjectToolingScope(target, failureFactory, modelName, param, configurationResult);
    }

    private static class ResilientProjectToolingScope extends ProjectToolingScope {

        private final FailureFactory failureFactory;
        private final ConfigurationResult ownerBuildConfigurationResult;

        public ResilientProjectToolingScope(
            ProjectState target,
            FailureFactory failureFactory,
            String modelName,
            boolean parameter,
            ConfigurationResult ownerBuildConfigurationResult
        ) {
            super(target, modelName, parameter);
            this.failureFactory = failureFactory;
            this.ownerBuildConfigurationResult = ownerBuildConfigurationResult;
        }

        @Override
        ToolingModelBuilderLookup.Builder locateBuilder() throws UnknownModelException {
            // Force configuration of the target project to ensure all builders have been registered
            ConfigurationResult configurationResult = ownerBuildConfigurationResult.isSuccess()
                ? tryRunConfiguration(target::ensureConfigured)
                : ownerBuildConfigurationResult;

            ProjectInternal project = target.getMutableModelEvenAfterFailure();
            ToolingModelBuilderLookup lookup = project.getServices().get(ToolingModelBuilderLookup.class);

            // We need to query the delegate builder lazily, since builders may not be registered if project configuration fails
            Supplier<ToolingModelBuilderLookup.Builder> builder = () -> lookup.locateForClientOperation(modelName, parameter, target, project);
            boolean canRunEvenIfProjectNotFullyConfigured = canRunEvenIfProjectNotFullyConfigured(modelName);
            return new ResilientToolingModelBuilder(builder, configurationResult, failureFactory, canRunEvenIfProjectNotFullyConfigured);
        }
    }

    private static boolean canRunEvenIfProjectNotFullyConfigured(String modelName) {
        // Some internal model builders can run even if the project is not fully configured.
        return RESILIENT_MODELS.contains(modelName);
    }

    private static class ResilientToolingModelBuilder implements ToolingModelBuilderLookup.Builder {

        private final Lazy<ToolingModelBuilderLookup.Builder> delegate;
        private final ConfigurationResult configurationResult;
        private final FailureFactory failureFactory;
        private final boolean canRunEvenIfProjectNotFullyConfigured;

        public ResilientToolingModelBuilder(
            Supplier<ToolingModelBuilderLookup.Builder> delegate,
            ConfigurationResult result,
            FailureFactory failureFactory,
            boolean canRunEvenIfProjectNotFullyConfigured
        ) {
            this.delegate = Lazy.unsafe().of(delegate);
            this.configurationResult = result;
            this.failureFactory = failureFactory;
            this.canRunEvenIfProjectNotFullyConfigured = canRunEvenIfProjectNotFullyConfigured;
        }

        @Override
        public @Nullable Class<?> getParameterType() {
            return delegate.get().getParameterType();
        }

        @Override
        public Object build(@Nullable Object parameter) {
            if (configurationResult.isSuccess()) {
                return delegate.get().build(parameter);
            }

            Object model = canRunEvenIfProjectNotFullyConfigured ? delegate.get().build(parameter) : null;
            List<Failure> failures = configurationExceptionAsFailure(configurationResult);
            return ToolingModelBuilderResultInternal.attachFailures(model, failures);
        }

        private List<Failure> configurationExceptionAsFailure(ConfigurationResult configurationResult) {
            return configurationResult.isFailure()
                ? ImmutableList.of(failureFactory.create(checkNotNull(configurationResult.getException())))
                : ImmutableList.of();
        }
    }
}
