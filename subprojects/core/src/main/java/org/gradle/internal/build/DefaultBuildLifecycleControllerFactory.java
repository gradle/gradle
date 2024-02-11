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

import org.gradle.StartParameter;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.GradleInternal;
import org.gradle.execution.BuildWorkExecutor;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.deprecation.DeprecationMessageBuilder;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.model.StateTransitionControllerFactory;
import org.gradle.internal.service.scopes.BuildScopeServices;

import java.io.File;

public class DefaultBuildLifecycleControllerFactory implements BuildLifecycleControllerFactory {
    private final StateTransitionControllerFactory stateTransitionControllerFactory;
    private final BuildToolingModelControllerFactory buildToolingModelControllerFactory;
    private final ExceptionAnalyser exceptionAnalyser;

    public DefaultBuildLifecycleControllerFactory(
        StateTransitionControllerFactory stateTransitionControllerFactory,
        BuildToolingModelControllerFactory buildToolingModelControllerFactory,
        ExceptionAnalyser exceptionAnalyser
    ) {
        this.stateTransitionControllerFactory = stateTransitionControllerFactory;
        this.buildToolingModelControllerFactory = buildToolingModelControllerFactory;
        this.exceptionAnalyser = exceptionAnalyser;
    }

    @Override
    public BuildLifecycleController newInstance(BuildDefinition buildDefinition, BuildScopeServices buildScopeServices) {
        StartParameter startParameter = buildDefinition.getStartParameter();

        @SuppressWarnings("deprecation")
        File customSettingsFile = DeprecationLogger.whileDisabled(startParameter::getSettingsFile);
        if (customSettingsFile != null) {
            logFileDeprecationWarning(DeprecationLogger.deprecateAction("Specifying custom settings file location"));
        }
        @SuppressWarnings("deprecation")
        File customBuildFile = DeprecationLogger.whileDisabled(startParameter::getBuildFile);
        if (customBuildFile != null) {
            logFileDeprecationWarning(DeprecationLogger.deprecateAction("Specifying custom build file location"));
        }

        GradleInternal gradle = buildScopeServices.get(GradleInternal.class);
        ListenerManager listenerManager = buildScopeServices.get(ListenerManager.class);

        BuildModelController buildModelController = buildScopeServices.get(BuildModelController.class);

        return new DefaultBuildLifecycleController(
            gradle,
            buildModelController,
            exceptionAnalyser,
            gradle.getBuildListenerBroadcaster(),
            listenerManager.getBroadcaster(BuildModelLifecycleListener.class),
            gradle.getServices().get(BuildWorkPreparer.class),
            gradle.getServices().get(BuildWorkExecutor.class),
            buildToolingModelControllerFactory,
            stateTransitionControllerFactory
        );
    }

    private static void logFileDeprecationWarning(DeprecationMessageBuilder<?> specifyingCustomFileLocation) {
        specifyingCustomFileLocation
            .willBeRemovedInGradle9()
            .withUpgradeGuideSection(7, "configuring_custom_build_layout")
            .nagUser();
    }
}
