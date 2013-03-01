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

import org.gradle.*;
import org.gradle.api.internal.ExceptionAnalyser;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.project.GlobalServicesRegistry;
import org.gradle.api.internal.project.TopLevelBuildServiceRegistry;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.cache.CacheRepository;
import org.gradle.cli.CommandLineConverter;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.execution.BuildExecuter;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.invocation.DefaultGradle;
import org.gradle.listener.ListenerManager;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.logging.StyledTextOutputFactory;
import org.gradle.profile.ProfileEventAdapter;
import org.gradle.profile.ReportGeneratingProfileListener;

import java.util.Arrays;

/**
 * @author Hans Dockter
 */
public class DefaultGradleLauncherFactory implements GradleLauncherFactory {
    private final ServiceRegistry sharedServices;
    private final NestedBuildTracker tracker;
    private CommandLineConverter<StartParameter> commandLineConverter;

    public DefaultGradleLauncherFactory(ServiceRegistry loggingServices) {
        this(new GlobalServicesRegistry(loggingServices));
    }
    
    public DefaultGradleLauncherFactory() {
        this(new GlobalServicesRegistry());
    }

    private DefaultGradleLauncherFactory(GlobalServicesRegistry globalServices) {
        sharedServices = globalServices;
        tracker = new NestedBuildTracker();

        // Register default loggers 
        ListenerManager listenerManager = sharedServices.get(ListenerManager.class);
        listenerManager.addListener(new BuildProgressLogger(sharedServices.get(ProgressLoggerFactory.class)));
        listenerManager.useLogger(new DependencyResolutionLogger(sharedServices.get(ProgressLoggerFactory.class)));

        GradleLauncher.injectCustomFactory(this);
    }

    public void addListener(Object listener) {
        sharedServices.get(ListenerManager.class).addListener(listener);
    }

    public void removeListener(Object listener) {
        sharedServices.get(ListenerManager.class).removeListener(listener);
    }

    public StartParameter createStartParameter(String... commandLineArgs) {
        if (commandLineConverter == null) {
            commandLineConverter = sharedServices.get(CommandLineConverter.class);
        }
        return commandLineConverter.convert(Arrays.asList(commandLineArgs));
    }

    public DefaultGradleLauncher newInstance(StartParameter startParameter) {
        BuildRequestMetaData requestMetaData;
        if (tracker.getCurrentBuild() != null) {
            requestMetaData = new DefaultBuildRequestMetaData(tracker.getCurrentBuild().getServices().get(BuildClientMetaData.class), System.currentTimeMillis());
        } else {
            requestMetaData = new DefaultBuildRequestMetaData(System.currentTimeMillis());
        }
        return doNewInstance(startParameter, requestMetaData);
    }

    public DefaultGradleLauncher newInstance(StartParameter startParameter, BuildRequestMetaData requestMetaData) {
        // This should only be used for top-level builds
        assert tracker.getCurrentBuild() == null;
        return doNewInstance(startParameter, requestMetaData);
    }

    private DefaultGradleLauncher doNewInstance(StartParameter startParameter, BuildRequestMetaData requestMetaData) {
        final TopLevelBuildServiceRegistry serviceRegistry = new TopLevelBuildServiceRegistry(sharedServices, startParameter);
        serviceRegistry.add(BuildRequestMetaData.class, requestMetaData);
        serviceRegistry.add(BuildClientMetaData.class, requestMetaData.getClient());
        ListenerManager listenerManager = serviceRegistry.get(ListenerManager.class);
        LoggingManagerInternal loggingManager = serviceRegistry.newInstance(LoggingManagerInternal.class);
        loggingManager.setLevel(startParameter.getLogLevel());

        //this hooks up the ListenerManager and LoggingConfigurer so you can call Gradle.addListener() with a StandardOutputListener.
        loggingManager.addStandardOutputListener(listenerManager.getBroadcaster(StandardOutputListener.class));
        loggingManager.addStandardErrorListener(listenerManager.getBroadcaster(StandardOutputListener.class));

        listenerManager.useLogger(new TaskExecutionLogger(serviceRegistry.get(ProgressLoggerFactory.class)));
        if (tracker.getCurrentBuild() == null) {
            listenerManager.useLogger(new BuildLogger(Logging.getLogger(BuildLogger.class), serviceRegistry.get(StyledTextOutputFactory.class), startParameter, requestMetaData));
        }
        listenerManager.addListener(tracker);
        listenerManager.addListener(new BuildCleanupListener(serviceRegistry));

        listenerManager.addListener(serviceRegistry.get(ProfileEventAdapter.class));
        if (startParameter.isProfile()) {
            listenerManager.addListener(new ReportGeneratingProfileListener());
        }

        GradleInternal gradle = serviceRegistry.get(Instantiator.class).newInstance(DefaultGradle.class, tracker.getCurrentBuild(), startParameter, serviceRegistry);
        return new DefaultGradleLauncher(
                gradle,
                serviceRegistry.get(InitScriptHandler.class),
                new SettingsHandler(
                        new DefaultSettingsFinder(
                                new BuildLayoutFactory()),
                        serviceRegistry.get(SettingsProcessor.class),
                        new BuildSourceBuilder(
                                this,
                                serviceRegistry.get(ClassLoaderRegistry.class),
                                serviceRegistry.get(CacheRepository.class))),
                serviceRegistry.get(BuildLoader.class),
                serviceRegistry.get(BuildConfigurer.class),
                gradle.getBuildListenerBroadcaster(),
                serviceRegistry.get(ExceptionAnalyser.class),
                loggingManager,
                listenerManager.getBroadcaster(ModelConfigurationListener.class),
                listenerManager.getBroadcaster(TasksCompletionListener.class),
                gradle.getServices().get(BuildExecuter.class));
    }

    public void setCommandLineConverter(
            CommandLineConverter<StartParameter> commandLineConverter) {
        this.commandLineConverter = commandLineConverter;
    }

    private static class BuildCleanupListener extends BuildAdapter {
        private final TopLevelBuildServiceRegistry services;

        private BuildCleanupListener(TopLevelBuildServiceRegistry services) {
            this.services = services;
        }

        @Override
        public void buildFinished(BuildResult result) {
            services.close();
        }
    }

}
