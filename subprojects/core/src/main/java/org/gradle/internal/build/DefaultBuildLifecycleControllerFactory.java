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
import org.gradle.api.Action;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.execution.BuildWorkExecutor;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.initialization.internal.InternalBuildFinishedListener;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler;
import org.gradle.internal.featurelifecycle.ScriptUsageLocationReporter;
import org.gradle.internal.model.StateTransitionControllerFactory;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.service.scopes.BuildScopeListenerManagerAction;
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

        final ListenerManager listenerManager = buildScopeServices.get(ListenerManager.class);
        for (Action<ListenerManager> action : buildScopeServices.getAll(BuildScopeListenerManagerAction.class)) {
            action.execute(listenerManager);
        }

        ScriptUsageLocationReporter usageLocationReporter = new ScriptUsageLocationReporter();
        listenerManager.addListener(usageLocationReporter);

        ShowStacktrace showStacktrace = startParameter.getShowStacktrace();
        switch (showStacktrace) {
            case ALWAYS:
            case ALWAYS_FULL:
                LoggingDeprecatedFeatureHandler.setTraceLoggingEnabled(true);
                break;
            default:
                LoggingDeprecatedFeatureHandler.setTraceLoggingEnabled(false);
        }

        DeprecationLogger.init(usageLocationReporter, startParameter.getWarningMode(), buildScopeServices.get(BuildOperationProgressEventEmitter.class));
        @SuppressWarnings("deprecation")
        File customSettingsFile = startParameter.getSettingsFile();
        if (customSettingsFile != null) {
            DeprecationLogger.deprecateAction("Specifying custom settings file location")
                .willBeRemovedInGradle8()
                .withUpgradeGuideSection(7, "configuring_custom_build_layout")
                .nagUser();
        }
        @SuppressWarnings("deprecation")
        File customBuildFile = startParameter.getBuildFile();
        if (customBuildFile != null) {
            DeprecationLogger.deprecateAction("Specifying custom build file location")
                .willBeRemovedInGradle8()
                .withUpgradeGuideSection(7, "configuring_custom_build_layout")
                .nagUser();
        }

        GradleInternal gradle = buildScopeServices.get(GradleInternal.class);

        BuildModelController buildModelController = buildScopeServices.get(BuildModelController.class);

        return new DefaultBuildLifecycleController(
            gradle,
            buildModelController,
            exceptionAnalyser,
            gradle.getBuildListenerBroadcaster(),
            listenerManager.getBroadcaster(InternalBuildFinishedListener.class),
            gradle.getServices().get(BuildWorkPreparer.class),
            gradle.getServices().get(BuildWorkExecutor.class),
            buildToolingModelControllerFactory,
            stateTransitionControllerFactory
        );
    }
}
