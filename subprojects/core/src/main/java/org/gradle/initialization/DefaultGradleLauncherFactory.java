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

import org.gradle.BuildLogger;
import org.gradle.StartParameter;
import org.gradle.TaskExecutionLogger;
import org.gradle.api.internal.ExceptionAnalyser;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.deployment.internal.DeploymentRegistry;
import org.gradle.execution.BuildConfigurationActionExecuter;
import org.gradle.execution.BuildExecuter;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.featurelifecycle.ScriptUsageLocationReporter;
import org.gradle.internal.progress.BuildOperationExecutor;
import org.gradle.internal.progress.BuildProgressFilter;
import org.gradle.internal.progress.BuildProgressLogger;
import org.gradle.internal.progress.LoggerProvider;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.BuildScopeServices;
import org.gradle.internal.service.scopes.BuildSessionScopeServices;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.invocation.DefaultGradle;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.profile.ProfileEventAdapter;
import org.gradle.profile.ReportGeneratingProfileListener;
import org.gradle.util.DeprecationLogger;

public class DefaultGradleLauncherFactory implements GradleLauncherFactory {
    private final ServiceRegistry sharedServices;
    private final NestedBuildTracker tracker;
    private final BuildProgressLogger buildProgressLogger;

    public DefaultGradleLauncherFactory(ServiceRegistry sharedServices) {
        this.sharedServices = sharedServices;
        tracker = new NestedBuildTracker();

        // Register default loggers
        ListenerManager listenerManager = sharedServices.get(ListenerManager.class);
        buildProgressLogger = new BuildProgressLogger(sharedServices.get(ProgressLoggerFactory.class));
        listenerManager.addListener(new BuildProgressFilter(buildProgressLogger));
        listenerManager.useLogger(new DependencyResolutionLogger(sharedServices.get(ProgressLoggerFactory.class)));
    }

    public void addListener(Object listener) {
        sharedServices.get(ListenerManager.class).addListener(listener);
    }

    public void removeListener(Object listener) {
        sharedServices.get(ListenerManager.class).removeListener(listener);
    }

    @Override
    public GradleLauncher newInstance(StartParameter startParameter) {
        return newInstance(startParameter, sharedServices);
    }

    public GradleLauncher newInstance(StartParameter startParameter, ServiceRegistry parentRegistry) {
        BuildRequestMetaData requestMetaData;
        BuildCancellationToken cancellationToken;
        BuildEventConsumer buildEventConsumer;
        if (tracker.getCurrentBuild() != null) {
            ServiceRegistry services = tracker.getCurrentBuild().getServices();
            requestMetaData = new DefaultBuildRequestMetaData(services.get(BuildClientMetaData.class), System.currentTimeMillis());
            cancellationToken = services.get(BuildCancellationToken.class);
            buildEventConsumer = services.get(BuildEventConsumer.class);
        } else {
            requestMetaData = new DefaultBuildRequestMetaData(System.currentTimeMillis());
            cancellationToken = new DefaultBuildCancellationToken();
            buildEventConsumer = new NoOpBuildEventConsumer();
        }

        final BuildScopeServices buildScopeServices = BuildScopeServices.singleSession(parentRegistry, startParameter);
        return doNewInstance(startParameter, cancellationToken, requestMetaData, buildEventConsumer, buildScopeServices);
    }

    @Override
    public GradleLauncher newInstance(StartParameter startParameter, BuildRequestContext requestContext, ServiceRegistry parentRegistry) {
        // This should only be used for top-level builds
        assert tracker.getCurrentBuild() == null;

        if (!(parentRegistry instanceof BuildSessionScopeServices)) {
            throw new IllegalArgumentException("Service registry must be of build session scope");
        }

        BuildScopeServices buildScopeServices = BuildScopeServices.forSession((BuildSessionScopeServices) parentRegistry);
        DefaultGradleLauncher launcher = doNewInstance(startParameter, requestContext.getCancellationToken(), requestContext, requestContext.getEventConsumer(), buildScopeServices);
        DeploymentRegistry deploymentRegistry = parentRegistry.get(DeploymentRegistry.class);
        deploymentRegistry.onNewBuild(launcher.getGradle());
        return launcher;
    }

    private DefaultGradleLauncher doNewInstance(StartParameter startParameter, BuildCancellationToken cancellationToken, BuildRequestMetaData requestMetaData, BuildEventConsumer buildEventConsumer, BuildScopeServices serviceRegistry) {
        serviceRegistry.add(BuildRequestMetaData.class, requestMetaData);
        serviceRegistry.add(BuildClientMetaData.class, requestMetaData.getClient());
        serviceRegistry.add(BuildEventConsumer.class, buildEventConsumer);
        serviceRegistry.add(BuildCancellationToken.class, cancellationToken);
        ListenerManager listenerManager = serviceRegistry.get(ListenerManager.class);
        LoggingManagerInternal loggingManager = serviceRegistry.newInstance(LoggingManagerInternal.class);
        loggingManager.setLevelInternal(startParameter.getLogLevel());

        //this hooks up the ListenerManager and LoggingConfigurer so you can call Gradle.addListener() with a StandardOutputListener.
        loggingManager.addStandardOutputListener(listenerManager.getBroadcaster(StandardOutputListener.class));
        loggingManager.addStandardErrorListener(listenerManager.getBroadcaster(StandardOutputListener.class));

        LoggerProvider loggerProvider = (tracker.getCurrentBuild() == null) ? buildProgressLogger : LoggerProvider.NO_OP;
        listenerManager.useLogger(new TaskExecutionLogger(serviceRegistry.get(ProgressLoggerFactory.class), loggerProvider));
        if (tracker.getCurrentBuild() == null) {
            listenerManager.useLogger(new BuildLogger(Logging.getLogger(BuildLogger.class), serviceRegistry.get(StyledTextOutputFactory.class), startParameter, requestMetaData));
        }
        listenerManager.addListener(tracker);

        listenerManager.addListener(serviceRegistry.get(ProfileEventAdapter.class));
        if (startParameter.isProfile()) {
            listenerManager.addListener(new ReportGeneratingProfileListener());
        }
        ScriptUsageLocationReporter usageLocationReporter = new ScriptUsageLocationReporter();
        listenerManager.addListener(usageLocationReporter);
        DeprecationLogger.useLocationReporter(usageLocationReporter);

        GradleInternal gradle = serviceRegistry.get(Instantiator.class).newInstance(DefaultGradle.class, tracker.getCurrentBuild(), startParameter, serviceRegistry.get(ServiceRegistryFactory.class));
        return new DefaultGradleLauncher(
            gradle,
            serviceRegistry.get(InitScriptHandler.class),
            serviceRegistry.get(SettingsLoader.class),
            serviceRegistry.get(BuildConfigurer.class),
            serviceRegistry.get(ExceptionAnalyser.class),
            loggingManager,
            gradle.getBuildListenerBroadcaster(),
            listenerManager.getBroadcaster(ModelConfigurationListener.class),
            listenerManager.getBroadcaster(BuildCompletionListener.class),
            serviceRegistry.get(BuildOperationExecutor.class),
            gradle.getServices().get(BuildConfigurationActionExecuter.class),
            gradle.getServices().get(BuildExecuter.class),
            serviceRegistry
        );
    }
}
