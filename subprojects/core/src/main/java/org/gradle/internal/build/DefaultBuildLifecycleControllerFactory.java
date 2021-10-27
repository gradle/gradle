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
import org.gradle.initialization.BuildCompletionListener;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.initialization.internal.InternalBuildFinishedListener;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler;
import org.gradle.internal.featurelifecycle.ScriptUsageLocationReporter;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.scopes.BuildScopeListenerManagerAction;
import org.gradle.internal.service.scopes.BuildScopeServices;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.invocation.DefaultGradle;

import javax.annotation.Nullable;
import java.io.File;

public class DefaultBuildLifecycleControllerFactory implements BuildLifecycleControllerFactory {
    @Override
    public BuildLifecycleController newInstance(BuildDefinition buildDefinition, BuildState owner, @Nullable BuildState parentBuild, BuildScopeServices buildScopeServices) {
        StartParameter startParameter = buildDefinition.getStartParameter();

        buildScopeServices.add(BuildDefinition.class, buildDefinition);
        buildScopeServices.add(BuildState.class, owner);
        buildScopeServices.addProvider(new GradleModelProvider(parentBuild, startParameter));

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
            buildScopeServices.get(ExceptionAnalyser.class),
            gradle.getBuildListenerBroadcaster(),
            listenerManager.getBroadcaster(BuildCompletionListener.class),
            listenerManager.getBroadcaster(InternalBuildFinishedListener.class),
            gradle.getServices().get(BuildWorkPreparer.class),
            gradle.getServices().get(BuildWorkExecutor.class),
            buildScopeServices
        );
    }

    private static class GradleModelProvider {
        @Nullable
        private final BuildState parentBuild;
        private final StartParameter startParameter;

        private GradleModelProvider(@Nullable BuildState parentBuild, StartParameter startParameter) {
            this.parentBuild = parentBuild;
            this.startParameter = startParameter;
        }

        GradleInternal createGradleModel(Instantiator instantiator, ServiceRegistryFactory serviceRegistryFactory) {
            return instantiator.newInstance(
                DefaultGradle.class,
                parentBuild,
                startParameter,
                serviceRegistryFactory
            );
        }
    }
}
