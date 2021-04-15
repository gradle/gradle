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
import org.gradle.api.internal.BuildType;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.deployment.internal.DefaultDeploymentRegistry;
import org.gradle.execution.BuildWorkExecutor;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.initialization.internal.InternalBuildFinishedListener;
import org.gradle.internal.InternalBuildAdapter;
import org.gradle.internal.build.BuildLifecycleController;
import org.gradle.internal.build.BuildModelController;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.DefaultBuildLifecycleController;
import org.gradle.internal.build.NestedBuildState;
import org.gradle.internal.build.NestedRootBuild;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.buildtree.BuildTreeController;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler;
import org.gradle.internal.featurelifecycle.ScriptUsageLocationReporter;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.BuildScopeListenerManagerAction;
import org.gradle.internal.service.scopes.BuildScopeServices;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.internal.session.BuildSessionController;
import org.gradle.internal.session.CrossBuildSessionState;
import org.gradle.internal.time.Time;
import org.gradle.invocation.DefaultGradle;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.function.Function;

public class DefaultGradleLauncherFactory implements GradleLauncherFactory {
    private final GradleUserHomeScopeServiceRegistry userHomeDirServiceRegistry;
    private final CrossBuildSessionState crossBuildSessionState;
    private BuildLifecycleController rootBuild;

    public DefaultGradleLauncherFactory(
        GradleUserHomeScopeServiceRegistry userHomeDirServiceRegistry,
        CrossBuildSessionState crossBuildSessionState
    ) {
        this.userHomeDirServiceRegistry = userHomeDirServiceRegistry;
        this.crossBuildSessionState = crossBuildSessionState;
    }

    @Override
    public BuildLifecycleController newInstance(BuildDefinition buildDefinition, RootBuildState build, BuildTreeController owner) {
        // This should only be used for top-level builds
        if (rootBuild != null) {
            throw new IllegalStateException("Cannot have a current root build");
        }

        BuildLifecycleController launcher = doNewInstance(buildDefinition, build, null, owner, ImmutableList.of((Stoppable) () -> rootBuild = null));
        rootBuild = launcher;

        final DefaultDeploymentRegistry deploymentRegistry = owner.getServices().get(DefaultDeploymentRegistry.class);
        launcher.getGradle().addBuildListener(new InternalBuildAdapter() {
            @Override
            public void buildFinished(BuildResult result) {
                deploymentRegistry.buildFinished(result);
            }
        });

        return launcher;
    }

    private BuildLifecycleController doNewInstance(
        BuildDefinition buildDefinition,
        BuildState owner,
        @Nullable BuildLifecycleController parent,
        BuildTreeController buildTree,
        List<?> servicesToStop
    ) {
        return doNewInstance(
            buildDefinition,
            owner,
            parent,
            buildTree,
            servicesToStop,
            this::createDefaultGradleLauncher,
            BuildScopeServices::new
        );
    }

    private BuildLifecycleController doNewInstance(
        BuildDefinition buildDefinition,
        BuildState owner,
        @Nullable BuildLifecycleController parent,
        BuildTreeController buildTree,
        List<?> servicesToStop,
        GradleLauncherInstantiator gradleLauncherInstantiator,
        Function<ServiceRegistry, BuildScopeServices> buildScopeServicesInstantiator
    ) {
        final BuildScopeServices serviceRegistry = buildScopeServicesInstantiator.apply(buildTree.getServices());
        serviceRegistry.add(BuildDefinition.class, buildDefinition);
        serviceRegistry.add(BuildState.class, owner);
        NestedBuildFactoryImpl nestedBuildFactory = new NestedBuildFactoryImpl(buildTree);
        serviceRegistry.add(NestedBuildFactory.class, nestedBuildFactory);

        final ListenerManager listenerManager = serviceRegistry.get(ListenerManager.class);
        for (Action<ListenerManager> action : serviceRegistry.getAll(BuildScopeListenerManagerAction.class)) {
            action.execute(listenerManager);
        }

        ScriptUsageLocationReporter usageLocationReporter = new ScriptUsageLocationReporter();
        listenerManager.addListener(usageLocationReporter);

        StartParameter startParameter = buildDefinition.getStartParameter();
        ShowStacktrace showStacktrace = startParameter.getShowStacktrace();
        switch (showStacktrace) {
            case ALWAYS:
            case ALWAYS_FULL:
                LoggingDeprecatedFeatureHandler.setTraceLoggingEnabled(true);
                break;
            default:
                LoggingDeprecatedFeatureHandler.setTraceLoggingEnabled(false);
        }

        DeprecationLogger.init(usageLocationReporter, startParameter.getWarningMode(), serviceRegistry.get(BuildOperationProgressEventEmitter.class));
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

        GradleInternal parentBuild = parent == null ? null : parent.getGradle();

        GradleInternal gradle = serviceRegistry.get(Instantiator.class).newInstance(
            DefaultGradle.class,
            parentBuild,
            startParameter,
            serviceRegistry.get(ServiceRegistryFactory.class)
        );

        BuildModelControllerFactory buildModelControllerFactory = gradle.getServices().get(BuildModelControllerFactory.class);
        BuildModelController buildModelController = buildModelControllerFactory.create(gradle);

        BuildLifecycleController buildLifecycleController = gradleLauncherInstantiator.gradleLauncherFor(gradle, buildModelController, serviceRegistry, servicesToStop);
        nestedBuildFactory.setParent(buildLifecycleController);
        nestedBuildFactory.setBuildCancellationToken(
            buildTree.getServices().get(BuildCancellationToken.class)
        );
        return buildLifecycleController;
    }

    private BuildLifecycleController createDefaultGradleLauncher(
        GradleInternal gradle,
        BuildModelController buildModelController,
        BuildScopeServices serviceRegistry,
        List<?> servicesToStop
    ) {
        final ListenerManager listenerManager = serviceRegistry.get(ListenerManager.class);

        return new DefaultBuildLifecycleController(
            gradle,
            buildModelController,
            serviceRegistry.get(ExceptionAnalyser.class),
            gradle.getBuildListenerBroadcaster(),
            listenerManager.getBroadcaster(BuildCompletionListener.class),
            listenerManager.getBroadcaster(InternalBuildFinishedListener.class),
            gradle.getServices().get(BuildWorkExecutor.class),
            serviceRegistry,
            servicesToStop,
            new BuildOptionBuildOperationProgressEventsEmitter(
                gradle.getServices().get(BuildOperationProgressEventEmitter.class)
            )
        );
    }

    @FunctionalInterface
    public interface GradleLauncherInstantiator {
        BuildLifecycleController gradleLauncherFor(
            GradleInternal gradle,
            BuildModelController buildModelController,
            BuildScopeServices serviceRegistry,
            List<?> servicesToStop
        );
    }

    public interface NestedBuildFactoryInternal extends NestedBuildFactory {
        BuildLifecycleController nestedInstance(
            BuildDefinition buildDefinition,
            NestedBuildState build,
            Function<ServiceRegistry, BuildScopeServices> buildScopeServicesInstantiator,
            GradleLauncherInstantiator gradleLauncherInstantiator
        );
    }

    private class NestedBuildFactoryImpl implements NestedBuildFactoryInternal {
        private final BuildTreeController buildTree;
        private BuildLifecycleController parent;
        private BuildCancellationToken buildCancellationToken;

        NestedBuildFactoryImpl(BuildTreeController buildTree) {
            this.buildTree = buildTree;
        }

        @Override
        public BuildLifecycleController nestedInstance(
            BuildDefinition buildDefinition,
            NestedBuildState build,
            Function<ServiceRegistry, BuildScopeServices> buildScopeServicesInstantiator,
            GradleLauncherInstantiator gradleLauncherInstantiator
        ) {
            return doNewInstance(buildDefinition, build, parent, buildTree, ImmutableList.of(), gradleLauncherInstantiator, buildScopeServicesInstantiator);
        }

        @Override
        public BuildLifecycleController nestedInstance(BuildDefinition buildDefinition, NestedBuildState build) {
            return doNewInstance(buildDefinition, build, parent, buildTree, ImmutableList.of());
        }

        @Override
        public BuildLifecycleController nestedBuildTree(BuildDefinition buildDefinition, NestedRootBuild build) {
            StartParameterInternal startParameter = buildDefinition.getStartParameter();
            BuildRequestMetaData buildRequestMetaData = new DefaultBuildRequestMetaData(Time.currentTimeMillis());
            BuildSessionController buildSessionController = new BuildSessionController(userHomeDirServiceRegistry, crossBuildSessionState, startParameter, buildRequestMetaData, ClassPath.EMPTY, buildCancellationToken, buildRequestMetaData.getClient(), new NoOpBuildEventConsumer());
            // Configuration cache is not supported for nested build trees
            startParameter.setConfigurationCache(false);
            BuildTreeController nestedBuildTree = new BuildTreeController(buildSessionController.getServices(), BuildType.TASKS);
            return doNewInstance(buildDefinition, build, parent, nestedBuildTree, ImmutableList.of(nestedBuildTree, buildSessionController));
        }

        private void setParent(BuildLifecycleController parent) {
            this.parent = parent;
        }

        private void setBuildCancellationToken(BuildCancellationToken buildCancellationToken) {
            this.buildCancellationToken = buildCancellationToken;
        }
    }
}
