/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.initialization;

import com.google.common.collect.ImmutableList;
import org.gradle.BuildResult;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.composite.internal.IncludedBuildControllers;
import org.gradle.configuration.ProjectsPreparer;
import org.gradle.deployment.internal.DefaultDeploymentRegistry;
import org.gradle.execution.BuildWorkExecutor;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.internal.InternalBuildAdapter;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.NestedBuildState;
import org.gradle.internal.build.NestedRootBuild;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.featurelifecycle.DeprecatedUsageBuildOperationProgressBroadaster;
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler;
import org.gradle.internal.featurelifecycle.ScriptUsageLocationReporter;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.BuildScopeListenerManagerAction;
import org.gradle.internal.service.scopes.BuildScopeServices;
import org.gradle.internal.service.scopes.BuildSessionScopeServices;
import org.gradle.internal.service.scopes.BuildTreeScopeServices;
import org.gradle.internal.service.scopes.CrossBuildSessionScopeServices;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.internal.time.Time;
import org.gradle.invocation.DefaultGradle;
import org.gradle.util.DeprecationLogger;

import javax.annotation.Nullable;
import java.util.List;

public class DefaultGradleLauncherFactory implements GradleLauncherFactory {
    private final GradleUserHomeScopeServiceRegistry userHomeDirServiceRegistry;
    private final CrossBuildSessionScopeServices crossBuildSessionScopeServices;
    private DefaultGradleLauncher rootBuild;

    public DefaultGradleLauncherFactory(
        GradleUserHomeScopeServiceRegistry userHomeDirServiceRegistry,
        CrossBuildSessionScopeServices crossBuildSessionScopeServices
    ) {
        this.userHomeDirServiceRegistry = userHomeDirServiceRegistry;
        this.crossBuildSessionScopeServices = crossBuildSessionScopeServices;
    }

    @Override
    public GradleLauncher newInstance(BuildDefinition buildDefinition, RootBuildState build, BuildTreeScopeServices parentRegistry) {
        // This should only be used for top-level builds
        if (rootBuild != null) {
            throw new IllegalStateException("Cannot have a current root build");
        }

        DefaultGradleLauncher launcher = doNewInstance(buildDefinition, build, null, parentRegistry, ImmutableList.of(new Stoppable() {
                @Override
                public void stop() {
                    rootBuild = null;
                }
            }));
        rootBuild = launcher;

        final DefaultDeploymentRegistry deploymentRegistry = parentRegistry.get(DefaultDeploymentRegistry.class);
        launcher.getGradle().addBuildListener(new InternalBuildAdapter() {
            @Override
            public void buildFinished(BuildResult result) {
                deploymentRegistry.buildFinished(result);
            }
        });

        return launcher;
    }

    private DefaultGradleLauncher doNewInstance(BuildDefinition buildDefinition,
                                                BuildState build,
                                                @Nullable GradleLauncher parent,
                                                BuildTreeScopeServices buildTreeScopeServices,
                                                List<?> servicesToStop) {
        BuildScopeServices serviceRegistry = new BuildScopeServices(buildTreeScopeServices);
        serviceRegistry.add(BuildDefinition.class, buildDefinition);
        serviceRegistry.add(BuildState.class, build);
        NestedBuildFactoryImpl nestedBuildFactory = new NestedBuildFactoryImpl(buildTreeScopeServices);
        serviceRegistry.add(NestedBuildFactory.class, nestedBuildFactory);

        StartParameter startParameter = buildDefinition.getStartParameter();
        ListenerManager listenerManager = serviceRegistry.get(ListenerManager.class);
        for (Action<ListenerManager> action : serviceRegistry.getAll(BuildScopeListenerManagerAction.class)) {
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

        DeprecatedUsageBuildOperationProgressBroadaster deprecationWarningBuildOperationProgressBroadaster = serviceRegistry.get(DeprecatedUsageBuildOperationProgressBroadaster.class);
        DeprecationLogger.init(usageLocationReporter, startParameter.getWarningMode(), deprecationWarningBuildOperationProgressBroadaster);

        GradleInternal parentBuild = parent == null ? null : parent.getGradle();

        SettingsPreparer settingsPreparer = serviceRegistry.get(SettingsPreparer.class);

        GradleInternal gradle = serviceRegistry.get(Instantiator.class).newInstance(DefaultGradle.class, parentBuild, startParameter, serviceRegistry.get(ServiceRegistryFactory.class));

        IncludedBuildControllers includedBuildControllers = gradle.getServices().get(IncludedBuildControllers.class);
        TaskExecutionPreparer taskExecutionPreparer = gradle.getServices().get(TaskExecutionPreparer.class);

        DefaultGradleLauncher gradleLauncher = new DefaultGradleLauncher(
            gradle,
            serviceRegistry.get(ProjectsPreparer.class),
            serviceRegistry.get(ExceptionAnalyser.class),
            gradle.getBuildListenerBroadcaster(),
            listenerManager.getBroadcaster(BuildCompletionListener.class),
            gradle.getServices().get(BuildWorkExecutor.class),
            serviceRegistry,
            servicesToStop,
            includedBuildControllers,
            settingsPreparer,
            taskExecutionPreparer,
            gradle.getServices().get(InstantExecution.class)
        );
        nestedBuildFactory.setParent(gradleLauncher);
        nestedBuildFactory.setBuildCancellationToken(buildTreeScopeServices.get(BuildCancellationToken.class));
        return gradleLauncher;
    }

    private class NestedBuildFactoryImpl implements NestedBuildFactory {
        private final BuildTreeScopeServices buildTreeScopeServices;
        private DefaultGradleLauncher parent;
        private BuildCancellationToken buildCancellationToken;

        NestedBuildFactoryImpl(BuildTreeScopeServices buildTreeScopeServices) {
            this.buildTreeScopeServices = buildTreeScopeServices;
        }

        @Override
        public GradleLauncher nestedInstance(BuildDefinition buildDefinition, NestedBuildState build) {
            return doNewInstance(buildDefinition, build, parent, buildTreeScopeServices, ImmutableList.of());
        }

        @Override
        public GradleLauncher nestedBuildTree(BuildDefinition buildDefinition, NestedRootBuild build) {
            StartParameter startParameter = buildDefinition.getStartParameter();
            final ServiceRegistry userHomeServices = userHomeDirServiceRegistry.getServicesFor(startParameter.getGradleUserHomeDir());
            BuildRequestMetaData buildRequestMetaData = new DefaultBuildRequestMetaData(Time.currentTimeMillis());
            BuildSessionScopeServices sessionScopeServices = new BuildSessionScopeServices(userHomeServices, crossBuildSessionScopeServices, startParameter, buildRequestMetaData, ClassPath.EMPTY, buildCancellationToken, buildRequestMetaData.getClient(), new NoOpBuildEventConsumer());
            BuildTreeScopeServices buildTreeScopeServices = new BuildTreeScopeServices(sessionScopeServices);
            buildTreeScopeServices.get(BuildStateRegistry.class).attachRootBuild(build);
            return doNewInstance(buildDefinition, build, parent, buildTreeScopeServices, ImmutableList.of(buildTreeScopeServices, sessionScopeServices, new Stoppable() {
                @Override
                public void stop() {
                    userHomeDirServiceRegistry.release(userHomeServices);
                }
            }));
        }

        private void setParent(DefaultGradleLauncher parent) {
            this.parent = parent;
        }

        private void setBuildCancellationToken(BuildCancellationToken buildCancellationToken) {
            this.buildCancellationToken = buildCancellationToken;
        }
    }
}
