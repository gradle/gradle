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
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.composite.internal.IncludedBuildControllers;
import org.gradle.configuration.ProjectsPreparer;
import org.gradle.deployment.internal.DefaultDeploymentRegistry;
import org.gradle.execution.BuildWorkExecutor;
import org.gradle.initialization.exception.ExceptionAnalyser;
import org.gradle.initialization.internal.InternalBuildFinishedListener;
import org.gradle.internal.InternalBuildAdapter;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.NestedBuildState;
import org.gradle.internal.build.NestedRootBuild;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.buildtree.BuildTreeBuildPath;
import org.gradle.internal.buildtree.BuildTreeState;
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
import org.gradle.internal.session.BuildSessionState;
import org.gradle.internal.session.CrossBuildSessionState;
import org.gradle.internal.time.Time;
import org.gradle.invocation.DefaultGradle;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;

public class DefaultGradleLauncherFactory implements GradleLauncherFactory {
    private final GradleUserHomeScopeServiceRegistry userHomeDirServiceRegistry;
    private final CrossBuildSessionState crossBuildSessionState;
    private GradleLauncher rootBuild;

    public DefaultGradleLauncherFactory(
        GradleUserHomeScopeServiceRegistry userHomeDirServiceRegistry,
        CrossBuildSessionState crossBuildSessionState
    ) {
        this.userHomeDirServiceRegistry = userHomeDirServiceRegistry;
        this.crossBuildSessionState = crossBuildSessionState;
    }

    @Override
    public GradleLauncher newInstance(BuildDefinition buildDefinition, RootBuildState build, BuildTreeState owner) {
        // This should only be used for top-level builds
        if (rootBuild != null) {
            throw new IllegalStateException("Cannot have a current root build");
        }

        GradleLauncher launcher = doNewInstance(buildDefinition, build, null, owner, ImmutableList.of((Stoppable) () -> rootBuild = null));
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

    private GradleLauncher doNewInstance(
        BuildDefinition buildDefinition,
        BuildState owner,
        @Nullable GradleLauncher parent,
        BuildTreeState buildTree,
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

    private GradleLauncher doNewInstance(
        BuildDefinition buildDefinition,
        BuildState owner,
        @Nullable GradleLauncher parent,
        BuildTreeState buildTree,
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

        GradleInternal parentBuild = parent == null ? null : parent.getGradle();

        GradleInternal gradle = serviceRegistry.get(Instantiator.class).newInstance(
            DefaultGradle.class,
            parentBuild,
            startParameter,
            serviceRegistry.get(ServiceRegistryFactory.class)
        );

        GradleLauncher gradleLauncher = gradleLauncherInstantiator.gradleLauncherFor(gradle, serviceRegistry, servicesToStop);
        nestedBuildFactory.setParent(gradleLauncher);
        nestedBuildFactory.setBuildCancellationToken(
            buildTree.getServices().get(BuildCancellationToken.class)
        );
        return gradleLauncher;
    }

    private GradleLauncher createDefaultGradleLauncher(
        GradleInternal gradle,
        BuildScopeServices serviceRegistry,
        List<?> servicesToStop
    ) {

        IncludedBuildControllers includedBuildControllers = gradle.getServices().get(IncludedBuildControllers.class);
        ProjectsPreparer projectsPreparer = serviceRegistry.get(ProjectsPreparer.class);
        SettingsPreparer settingsPreparer = serviceRegistry.get(SettingsPreparer.class);
        TaskExecutionPreparer taskExecutionPreparer = gradle.getServices().get(TaskExecutionPreparer.class);
        final ListenerManager listenerManager = serviceRegistry.get(ListenerManager.class);

        return new DefaultGradleLauncher(
            gradle,
            projectsPreparer,
            serviceRegistry.get(ExceptionAnalyser.class),
            gradle.getBuildListenerBroadcaster(),
            listenerManager.getBroadcaster(BuildCompletionListener.class),
            listenerManager.getBroadcaster(InternalBuildFinishedListener.class),
            gradle.getServices().get(BuildWorkExecutor.class),
            serviceRegistry,
            servicesToStop,
            includedBuildControllers,
            settingsPreparer,
            taskExecutionPreparer,
            gradle.getServices().get(ConfigurationCache.class),
            new BuildOptionBuildOperationProgressEventsEmitter(
                gradle.getServices().get(BuildOperationProgressEventEmitter.class)
            )
        );
    }

    @FunctionalInterface
    public interface GradleLauncherInstantiator {
        GradleLauncher gradleLauncherFor(
            GradleInternal gradle,
            BuildScopeServices serviceRegistry,
            List<?> servicesToStop
        );
    }

    public interface NestedBuildFactoryInternal extends NestedBuildFactory {
        GradleLauncher nestedInstance(
            BuildDefinition buildDefinition,
            NestedBuildState build,
            Function<ServiceRegistry, BuildScopeServices> buildScopeServicesInstantiator,
            GradleLauncherInstantiator gradleLauncherInstantiator
        );
    }

    private class NestedBuildFactoryImpl implements NestedBuildFactoryInternal {
        private final BuildTreeState buildTree;
        private GradleLauncher parent;
        private BuildCancellationToken buildCancellationToken;

        NestedBuildFactoryImpl(BuildTreeState buildTree) {
            this.buildTree = buildTree;
        }

        @Override
        public GradleLauncher nestedInstance(
            BuildDefinition buildDefinition,
            NestedBuildState build,
            Function<ServiceRegistry, BuildScopeServices> buildScopeServicesInstantiator,
            GradleLauncherInstantiator gradleLauncherInstantiator
        ) {
            return doNewInstance(buildDefinition, build, parent, buildTree, ImmutableList.of(), gradleLauncherInstantiator, buildScopeServicesInstantiator);
        }

        @Override
        public GradleLauncher nestedInstance(BuildDefinition buildDefinition, NestedBuildState build) {
            return doNewInstance(buildDefinition, build, parent, buildTree, ImmutableList.of());
        }

        @Override
        public GradleLauncher nestedBuildTree(BuildDefinition buildDefinition, NestedRootBuild build) {
            StartParameter startParameter = buildDefinition.getStartParameter();
            BuildRequestMetaData buildRequestMetaData = new DefaultBuildRequestMetaData(Time.currentTimeMillis());
            BuildSessionState buildSessionState = new BuildSessionState(userHomeDirServiceRegistry, crossBuildSessionState, startParameter, buildRequestMetaData, ClassPath.EMPTY, buildCancellationToken, buildRequestMetaData.getClient(), new NoOpBuildEventConsumer());
            BuildTreeState nestedBuildTree = new BuildTreeState(buildSessionState.getServices(), BuildType.TASKS, new BuildTreeBuildPath(build.getIdentityPath()));
            return doNewInstance(buildDefinition, build, parent, nestedBuildTree, ImmutableList.of(nestedBuildTree, buildSessionState));
        }

        private void setParent(GradleLauncher parent) {
            this.parent = parent;
        }

        private void setBuildCancellationToken(BuildCancellationToken buildCancellationToken) {
            this.buildCancellationToken = buildCancellationToken;
        }
    }
}
