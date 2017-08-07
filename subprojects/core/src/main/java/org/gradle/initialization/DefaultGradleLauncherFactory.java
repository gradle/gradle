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
import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.StartParameter;
import org.gradle.api.internal.ExceptionAnalyser;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.tasks.execution.statistics.TaskExecutionStatisticsEventAdapter;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.deployment.internal.DefaultDeploymentRegistry;
import org.gradle.execution.BuildConfigurationActionExecuter;
import org.gradle.execution.BuildExecuter;
import org.gradle.internal.buildevents.BuildLogger;
import org.gradle.internal.buildevents.ProjectEvaluationLogger;
import org.gradle.internal.buildevents.TaskExecutionLogger;
import org.gradle.internal.buildevents.TaskExecutionStatisticsReporter;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.featurelifecycle.LoggingDeprecatedFeatureHandler;
import org.gradle.internal.featurelifecycle.ScriptUsageLocationReporter;
import org.gradle.internal.invocation.BuildController;
import org.gradle.internal.invocation.GradleBuildController;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.progress.BuildProgressFilter;
import org.gradle.internal.progress.BuildProgressLogger;
import org.gradle.internal.progress.LoggerProvider;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.BuildScopeServices;
import org.gradle.internal.service.scopes.BuildSessionScopeServices;
import org.gradle.internal.service.scopes.BuildTreeScopeServices;
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.invocation.DefaultGradle;
import org.gradle.profile.ProfileEventAdapter;
import org.gradle.profile.ReportGeneratingProfileListener;
import org.gradle.util.DeprecationLogger;

import javax.annotation.Nullable;
import java.util.List;

public class DefaultGradleLauncherFactory implements GradleLauncherFactory {
    private final GradleUserHomeScopeServiceRegistry userHomeDirServiceRegistry;
    private final BuildProgressLogger buildProgressLogger;
    private DefaultGradleLauncher rootBuild;

    public DefaultGradleLauncherFactory(
        ListenerManager listenerManager, ProgressLoggerFactory progressLoggerFactory, GradleUserHomeScopeServiceRegistry userHomeDirServiceRegistry) {
        this.userHomeDirServiceRegistry = userHomeDirServiceRegistry;

        // Register default loggers
        buildProgressLogger = new BuildProgressLogger(progressLoggerFactory);
        listenerManager.addListener(new BuildProgressFilter(buildProgressLogger));
        listenerManager.useLogger(new ProjectEvaluationLogger(progressLoggerFactory));
    }

    private GradleLauncher createChildInstance(StartParameter startParameter, GradleLauncher parent, BuildTreeScopeServices buildTreeScopeServices, List<?> servicesToStop) {
        ServiceRegistry services = parent.getGradle().getServices();
        BuildRequestMetaData requestMetaData = new DefaultBuildRequestMetaData(services.get(BuildClientMetaData.class));
        BuildCancellationToken cancellationToken = services.get(BuildCancellationToken.class);
        BuildGateToken buildGate = services.get(BuildGateToken.class);
        BuildEventConsumer buildEventConsumer = services.get(BuildEventConsumer.class);
        return doNewInstance(startParameter, parent, cancellationToken, buildGate, requestMetaData, buildEventConsumer, buildTreeScopeServices, servicesToStop);
    }

    @Override
    public GradleLauncher newInstance(StartParameter startParameter, BuildRequestContext requestContext, ServiceRegistry parentRegistry) {
        // This should only be used for top-level builds
        if (rootBuild != null) {
            throw new IllegalStateException("Cannot have a current build");
        }

        if (!(parentRegistry instanceof BuildTreeScopeServices)) {
            throw new IllegalArgumentException("Service registry must be of build-tree scope");
        }
        BuildTreeScopeServices buildTreeScopeServices = (BuildTreeScopeServices) parentRegistry;

        DefaultGradleLauncher launcher = doNewInstance(startParameter, null,
            requestContext.getCancellationToken(), requestContext.getGateToken(),
            requestContext, requestContext.getEventConsumer(), buildTreeScopeServices,
            ImmutableList.of(new Stoppable() {
            @Override
            public void stop() {
                rootBuild = null;
            }
        }));
        rootBuild = launcher;

        final DefaultDeploymentRegistry deploymentRegistry = parentRegistry.get(DefaultDeploymentRegistry.class);
        launcher.getGradle().addBuildListener(new BuildAdapter() {
            @Override
            public void buildFinished(BuildResult result) {
                deploymentRegistry.buildFinished(result);
            }
        });

        return launcher;
    }

    private DefaultGradleLauncher doNewInstance(StartParameter startParameter, GradleLauncher parent,
                                                BuildCancellationToken cancellationToken, BuildGateToken buildGate,
                                                BuildRequestMetaData requestMetaData, BuildEventConsumer buildEventConsumer,
                                                final BuildTreeScopeServices buildTreeScopeServices, List<?> servicesToStop) {
        BuildScopeServices serviceRegistry = new BuildScopeServices(buildTreeScopeServices);
        serviceRegistry.add(BuildRequestMetaData.class, requestMetaData);
        serviceRegistry.add(BuildClientMetaData.class, requestMetaData.getClient());
        serviceRegistry.add(BuildEventConsumer.class, buildEventConsumer);
        serviceRegistry.add(BuildCancellationToken.class, cancellationToken);
        serviceRegistry.add(BuildGateToken.class, buildGate);
        NestedBuildFactoryImpl nestedBuildFactory = new NestedBuildFactoryImpl(buildTreeScopeServices);
        serviceRegistry.add(NestedBuildFactory.class, nestedBuildFactory);

        ListenerManager listenerManager = serviceRegistry.get(ListenerManager.class);

        LoggerProvider loggerProvider = (parent == null) ? buildProgressLogger : LoggerProvider.NO_OP;
        listenerManager.useLogger(new TaskExecutionLogger(serviceRegistry.get(ProgressLoggerFactory.class), loggerProvider));
        if (parent == null) {
            listenerManager.useLogger(new BuildLogger(Logging.getLogger(BuildLogger.class), serviceRegistry.get(StyledTextOutputFactory.class), startParameter, requestMetaData));
        }

        listenerManager.addListener(serviceRegistry.get(TaskExecutionStatisticsEventAdapter.class));
        listenerManager.addListener(new TaskExecutionStatisticsReporter(serviceRegistry.get(StyledTextOutputFactory.class)));

        listenerManager.addListener(serviceRegistry.get(ProfileEventAdapter.class));
        if (startParameter.isProfile()) {
            listenerManager.addListener(new ReportGeneratingProfileListener(serviceRegistry.get(StyledTextOutputFactory.class)));
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
        DeprecationLogger.useLocationReporter(usageLocationReporter);

        SettingsLoaderFactory settingsLoaderFactory = serviceRegistry.get(SettingsLoaderFactory.class);
        SettingsLoader settingsLoader = parent != null ? settingsLoaderFactory.forNestedBuild() : settingsLoaderFactory.forTopLevelBuild();
        GradleInternal parentBuild = parent == null ? null : parent.getGradle();

        GradleInternal gradle = serviceRegistry.get(Instantiator.class).newInstance(DefaultGradle.class, parentBuild, startParameter, serviceRegistry.get(ServiceRegistryFactory.class));
        DefaultGradleLauncher gradleLauncher = new DefaultGradleLauncher(
            gradle,
            serviceRegistry.get(InitScriptHandler.class),
            settingsLoader,
            serviceRegistry.get(BuildConfigurer.class),
            serviceRegistry.get(ExceptionAnalyser.class),
            gradle.getBuildListenerBroadcaster(),
            listenerManager.getBroadcaster(ModelConfigurationListener.class),
            listenerManager.getBroadcaster(BuildCompletionListener.class),
            serviceRegistry.get(BuildOperationExecutor.class),
            gradle.getServices().get(BuildConfigurationActionExecuter.class),
            gradle.getServices().get(BuildExecuter.class),
            serviceRegistry,
            servicesToStop
        );
        nestedBuildFactory.setParent(gradleLauncher);
        return gradleLauncher;
    }

    private class NestedBuildFactoryImpl implements NestedBuildFactory {
        private final BuildTreeScopeServices buildTreeScopeServices;
        private DefaultGradleLauncher parent;

        public NestedBuildFactoryImpl(BuildTreeScopeServices buildTreeScopeServices) {
            this.buildTreeScopeServices = buildTreeScopeServices;
        }

        @Override
        public GradleLauncher nestedInstance(StartParameter startParameter) {
            return createChildInstance(startParameter, parent, buildTreeScopeServices, ImmutableList.of());
        }

        @Override
        public BuildController nestedBuildController(StartParameter startParameter) {
            final ServiceRegistry userHomeServices = userHomeDirServiceRegistry.getServicesFor(startParameter.getGradleUserHomeDir());
            BuildSessionScopeServices sessionScopeServices = new BuildSessionScopeServices(userHomeServices, startParameter, ClassPath.EMPTY);
            BuildTreeScopeServices buildTreeScopeServices = new BuildTreeScopeServices(sessionScopeServices);
            GradleLauncher childInstance = createChildInstance(startParameter, parent, buildTreeScopeServices, ImmutableList.of(buildTreeScopeServices, sessionScopeServices, new Stoppable() {
                @Override
                public void stop() {
                    userHomeDirServiceRegistry.release(userHomeServices);
                }
            }));
            return new NestedBuildController(new GradleBuildController(childInstance));
        }

        public void setParent(DefaultGradleLauncher parent) {
            this.parent = parent;
        }

        private class NestedBuildController implements BuildController {
            private final BuildController delegate;

            NestedBuildController(BuildController delegate) {
                this.delegate = delegate;
            }

            @Override
            public void stop() {
                delegate.stop();
            }

            @Override
            public GradleInternal getGradle() {
                return delegate.getGradle();
            }

            @Override
            public GradleInternal run() {
                BuildOperationExecutor executor = getGradle().getServices().get(BuildOperationExecutor.class);
                return executor.call(new CallableBuildOperation<GradleInternal>() {
                    @Override
                    public GradleInternal call(BuildOperationContext context) {
                        return delegate.run();
                    }

                    @Override
                    public BuildOperationDescriptor.Builder description() {
                        return BuildOperationDescriptor.displayName("Run nested build").parent(parent.getGradle().getBuildOperation());
                    }
                });
            }

            @Override
            public GradleInternal configure() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean hasResult() {
                return delegate.hasResult();
            }

            @Nullable
            @Override
            public Object getResult() {
                return delegate.getResult();
            }

            @Override
            public void setResult(@Nullable Object result) {
                delegate.setResult(result);
            }
        }
    }
}
