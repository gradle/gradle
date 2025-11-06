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
import org.gradle.internal.problems.failure.Failure;
import org.gradle.internal.problems.failure.FailureFactory;
import org.gradle.tooling.provider.model.UnknownModelException;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderLookup;
import org.gradle.tooling.provider.model.internal.ToolingModelBuilderResultInternal;
import org.gradle.tooling.provider.model.internal.ToolingModelScope;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

public class ResilientBuildToolingModelController extends DefaultBuildToolingModelController {

    private static final Set<String> RESILIENT_MODELS = ImmutableSet.of(
        // TODO: Is there a better way to identify resilient models?
        "org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel"
    );

    private final boolean isConfigureOnDemand;
    private final FailureFactory failureFactory;

    public ResilientBuildToolingModelController(
        BuildState buildState,
        BuildLifecycleController buildController,
        ToolingModelBuilderLookup buildScopeLookup,
        boolean isConfigureOnDemand
    ) {
        super(buildState, buildController, buildScopeLookup);
        this.isConfigureOnDemand = isConfigureOnDemand;
        this.failureFactory = buildController.getGradle().getServices().get(FailureFactory.class);
    }

    @Override
    protected ToolingModelScope doLocate(ProjectState target, String modelName, boolean param, ConfigurationResult configurationResult) {
        return new ResilientProjectToolingScope(target, failureFactory, modelName, param, isConfigureOnDemand, configurationResult);
    }

    private static class ResilientProjectToolingScope extends ProjectToolingScope {

        private final boolean isConfigureOnDemand;
        private final FailureFactory failureFactory;
        private final ConfigurationResult fullBuildConfigurationResult;

        public ResilientProjectToolingScope(
            ProjectState target,
            FailureFactory failureFactory,
            String modelName,
            boolean parameter,
            boolean isConfigureOnDemand,
            ConfigurationResult fullBuildConfigurationResult
        ) {
            super(target, modelName, parameter);
            this.isConfigureOnDemand = isConfigureOnDemand;
            this.failureFactory = failureFactory;
            this.fullBuildConfigurationResult = fullBuildConfigurationResult;
        }

        @Override
        ToolingModelBuilderLookup.Builder locateBuilder() throws UnknownModelException {
            // Force configuration of the target project to ensure all builders have been registered
            ConfigurationResult projectConfigurationResult = tryRunConfiguration(target::ensureConfigured);

            // For configure-on-demand, we care only about the project configuration result.
            ConfigurationResult configurationResult = isConfigureOnDemand
                ? projectConfigurationResult
                : ConfigurationResult.firstFailed(projectConfigurationResult, fullBuildConfigurationResult);

            ProjectInternal project = target.getMutableModelEvenAfterFailure();
            ToolingModelBuilderLookup lookup = project.getServices().get(ToolingModelBuilderLookup.class);

            ToolingModelBuilderLookup.Builder builder = lookup.locateForClientOperation(modelName, parameter, target, project);
            boolean isOwnerBuildSuccessfullyConfigured = target.getOwner().isBuildConfigured();
            return new ResilientToolingModelBuilder(builder, configurationResult, isConfigureOnDemand, isOwnerBuildSuccessfullyConfigured, failureFactory, modelName);
        }
    }

    private static class ResilientToolingModelBuilder implements ToolingModelBuilderLookup.Builder {

        private final ToolingModelBuilderLookup.Builder delegate;
        private final ConfigurationResult configurationResult;
        private final FailureFactory failureFactory;
        private final boolean isConfigurationOnDemand;
        private final boolean isOwnerBuildSuccessfullyConfigured;
        private final String modelName;

        public ResilientToolingModelBuilder(
            ToolingModelBuilderLookup.Builder delegate,
            ConfigurationResult result,
            boolean isConfigurationOnDemand,
            boolean isOwnerBuildSuccessfullyConfigured,
            FailureFactory failureFactory,
            String modelName
        ) {
            this.delegate = delegate;
            this.configurationResult = result;
            this.isConfigurationOnDemand = isConfigurationOnDemand;
            this.isOwnerBuildSuccessfullyConfigured = isOwnerBuildSuccessfullyConfigured;
            this.failureFactory = failureFactory;
            this.modelName = modelName;
        }

        @Override
        public @Nullable Class<?> getParameterType() {
            return delegate.getParameterType();
        }

        @Override
        public Object build(@Nullable Object parameter) {
            if (canAssumeProjectFullyConfigured()) {
                return delegate.build(parameter);
            }

            Object model = canRunEvenIfProjectNotFullyConfigured(modelName) ? delegate.build(parameter) : null;
            List<Failure> failures = configurationExceptionAsFailure(configurationResult);
            return ToolingModelBuilderResultInternal.attachFailures(model, failures);
        }

        private boolean canAssumeProjectFullyConfigured() {
            if (configurationResult.isSuccess()) {
                // If there was no configuration failure, then the project was configured successfully.
                return true;
            }

            // For configure-on-demand, projects are configured individually, so failure at this point means that the project failed to configure.
            // For normal mode assume project was successfully configured if the owner build was fully configured, e.g., full included build configuration was successful.
            return !isConfigurationOnDemand && isOwnerBuildSuccessfullyConfigured;
        }

        private static boolean canRunEvenIfProjectNotFullyConfigured(String modelName) {
            // Some internal model builders can run even if the project is not fully configured.
            return RESILIENT_MODELS.contains(modelName);
        }

        private List<Failure> configurationExceptionAsFailure(ConfigurationResult configurationResult) {
            return configurationResult.isFailure()
                ? ImmutableList.of(failureFactory.create(checkNotNull(configurationResult.getException())))
                : ImmutableList.of();
        }
    }
}
