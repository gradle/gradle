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
import org.gradle.cache.CacheRepository;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.execution.BuildExecuter;
import org.gradle.initialization.buildsrc.BuildSourceBuilder;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.featurelifecycle.ScriptUsageLocationReporter;
import org.gradle.internal.progress.BuildProgressFilter;
import org.gradle.internal.progress.BuildProgressLogger;
import org.gradle.internal.progress.LoggerProvider;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.BuildScopeServices;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.invocation.DefaultGradle;
import org.gradle.listener.ListenerManager;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.logging.StyledTextOutputFactory;
import org.gradle.profile.ProfileEventAdapter;
import org.gradle.profile.ReportGeneratingProfileListener;
import org.gradle.util.DeprecationLogger;

public class DefaultGradleLauncherFactory implements GradleLauncherFactory {
    private final ServiceRegistry sharedServices;
    private final NestedBuildTracker tracker;
    private final BuildProgressLogger buildProgressLogger;

    public DefaultGradleLauncherFactory(ServiceRegistry globalServices) {
        sharedServices = globalServices;
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

    public DefaultGradleLauncher newInstance(StartParameter startParameter, BuildCancellationToken cancellationToken) {
        BuildRequestMetaData requestMetaData;
        if (tracker.getCurrentBuild() != null) {
            requestMetaData = new DefaultBuildRequestMetaData(tracker.getCurrentBuild().getServices().get(BuildClientMetaData.class), System.currentTimeMillis());
        } else {
            requestMetaData = new DefaultBuildRequestMetaData(System.currentTimeMillis());
        }
        return doNewInstance(startParameter, cancellationToken, requestMetaData);
    }

    public DefaultGradleLauncher newInstance(StartParameter startParameter, BuildCancellationToken cancellationToken, BuildRequestMetaData requestMetaData) {
        // This should only be used for top-level builds
        assert tracker.getCurrentBuild() == null;
        return doNewInstance(startParameter, cancellationToken, requestMetaData);
    }

    private DefaultGradleLauncher doNewInstance(StartParameter startParameter, BuildCancellationToken cancellationToken, BuildRequestMetaData requestMetaData) {
        final BuildScopeServices serviceRegistry = new BuildScopeServices(sharedServices, startParameter);
        serviceRegistry.add(BuildRequestMetaData.class, requestMetaData);
        serviceRegistry.add(BuildClientMetaData.class, requestMetaData.getClient());
        serviceRegistry.add(BuildCancellationToken.class, cancellationToken);
        ListenerManager listenerManager = serviceRegistry.get(ListenerManager.class);
        LoggingManagerInternal loggingManager = serviceRegistry.newInstance(LoggingManagerInternal.class);
        loggingManager.setLevel(startParameter.getLogLevel());

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
                new SettingsHandler(
                        new DefaultSettingsFinder(
                                new BuildLayoutFactory()),
                        serviceRegistry.get(SettingsProcessor.class),
                        new BuildSourceBuilder(
                                this,
                                cancellationToken,
                                serviceRegistry.get(ClassLoaderScopeRegistry.class).getCoreAndPluginsScope(),
                                serviceRegistry.get(CacheRepository.class))
                ),
                serviceRegistry.get(BuildLoader.class),
                serviceRegistry.get(BuildConfigurer.class),
                gradle.getBuildListenerBroadcaster(),
                serviceRegistry.get(ExceptionAnalyser.class),
                loggingManager,
                listenerManager.getBroadcaster(ModelConfigurationListener.class),
                listenerManager.getBroadcaster(TasksCompletionListener.class),
                gradle.getServices().get(BuildExecuter.class),
                listenerManager.getBroadcaster(BuildCompletionListener.class),
                serviceRegistry
        );
    }
}
