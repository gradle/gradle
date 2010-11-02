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
import org.gradle.api.internal.project.GlobalServicesRegistry;
import org.gradle.api.internal.project.IProjectFactory;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.api.internal.project.TopLevelBuildServiceRegistry;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logging;
import org.gradle.api.logging.StandardOutputListener;
import org.gradle.cache.CacheRepository;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.invocation.DefaultGradle;
import org.gradle.listener.ListenerManager;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.logging.StyledTextOutputFactory;
import org.gradle.profile.ProfileListener;
import org.gradle.util.Clock;
import org.gradle.util.WrapUtil;

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

        // Start logging system
        sharedServices.newInstance(LoggingManagerInternal.class).setLevel(LogLevel.LIFECYCLE).start();

        commandLineConverter = sharedServices.get(CommandLineConverter.class);
        tracker = new NestedBuildTracker();

        // Register default loggers 
        ListenerManager listenerManager = sharedServices.get(ListenerManager.class);
        listenerManager.addListener(new BuildProgressLogger(sharedServices.get(ProgressLoggerFactory.class)));

        GradleLauncher.injectCustomFactory(this);
    }

    public StartParameter createStartParameter(String... commandLineArgs) {
        return commandLineConverter.convert(Arrays.asList(commandLineArgs));
    }

    public GradleLauncher newInstance(String... commandLineArgs) {
        return newInstance(createStartParameter(commandLineArgs));
    }

    public GradleLauncher newInstance(StartParameter startParameter) {
        Clock buildClock = new Clock();
        TopLevelBuildServiceRegistry serviceRegistry = new TopLevelBuildServiceRegistry(sharedServices, startParameter);
        ListenerManager listenerManager = serviceRegistry.get(ListenerManager.class);
        LoggingManagerInternal loggingManager = serviceRegistry.newInstance(LoggingManagerInternal.class);
        loggingManager.setLevel(startParameter.getLogLevel());
        loggingManager.colorStdOutAndStdErr(startParameter.isColorOutput());

        //this hooks up the ListenerManager and LoggingConfigurer so you can call Gradle.addListener() with a StandardOutputListener.
        loggingManager.addStandardOutputListener(listenerManager.getBroadcaster(StandardOutputListener.class));
        loggingManager.addStandardErrorListener(listenerManager.getBroadcaster(StandardOutputListener.class));

        listenerManager.useLogger(new TaskExecutionLogger(serviceRegistry.get(ProgressLoggerFactory.class)));
        if (tracker.getCurrentBuild() == null) {
            listenerManager.useLogger(new BuildLogger(Logging.getLogger(BuildLogger.class), serviceRegistry.get(StyledTextOutputFactory.class), buildClock, startParameter));
        }
        listenerManager.addListener(tracker);
        listenerManager.addListener(new BuildCleanupListener(serviceRegistry));

        if (startParameter.isProfile()) {
            listenerManager.addListener(new ProfileListener(System.currentTimeMillis()));
        }

        DefaultGradle gradle = new DefaultGradle(
                tracker.getCurrentBuild(),
                startParameter, serviceRegistry);
        return new DefaultGradleLauncher(
                gradle,
                serviceRegistry.get(InitScriptHandler.class),
                new SettingsHandler(
                        new EmbeddedScriptSettingsFinder(
                                new DefaultSettingsFinder(WrapUtil.<ISettingsFileSearchStrategy>toList(
                                        new MasterDirSettingsFinderStrategy(),
                                        new ParentDirSettingsFinderStrategy()))),
                        serviceRegistry.get(SettingsProcessor.class),
                        new BuildSourceBuilder(
                                this,
                                serviceRegistry.get(ClassLoaderFactory.class),
                                serviceRegistry.get(CacheRepository.class))),
                new DefaultGradlePropertiesLoader(),
                new BuildLoader(
                        serviceRegistry.get(IProjectFactory.class)
                ),
                serviceRegistry.get(BuildConfigurer.class),
                gradle.getBuildListenerBroadcaster(),
                serviceRegistry.get(ExceptionAnalyser.class),
                loggingManager);
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
