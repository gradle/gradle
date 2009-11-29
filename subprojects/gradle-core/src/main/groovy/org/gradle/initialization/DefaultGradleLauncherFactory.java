/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.GradleLauncher;
import org.gradle.GradleLauncherFactory;
import org.gradle.StartParameter;
import org.gradle.TaskExecutionLogger;
import org.gradle.api.internal.project.DefaultServiceRegistryFactory;
import org.gradle.api.internal.project.ImportsReader;
import org.gradle.api.internal.project.ProjectFactory;
import org.gradle.api.internal.project.ServiceRegistryFactory;
import org.gradle.api.logging.Logging;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.configuration.DefaultInitScriptProcessor;
import org.gradle.configuration.ProjectDependencies2TaskResolver;
import org.gradle.groovy.scripts.ScriptCompilerFactory;
import org.gradle.invocation.DefaultGradle;
import org.gradle.listener.DefaultListenerManager;
import org.gradle.listener.ListenerManager;
import org.gradle.util.WrapUtil;

/**
 * @author Hans Dockter
 */
public class DefaultGradleLauncherFactory implements GradleLauncherFactory {
    private LoggingConfigurer loggingConfigurer;
    private NestedBuildTracker tracker = new NestedBuildTracker();
    private CommandLine2StartParameterConverter commandLine2StartParameterConverter = new DefaultCommandLine2StartParameterConverter();

    public DefaultGradleLauncherFactory() {
        this(new DefaultLoggingConfigurer());
    }

    DefaultGradleLauncherFactory(LoggingConfigurer loggingConfigurer) {
        this.loggingConfigurer = loggingConfigurer;
    }

    public StartParameter createStartParameter(String[] commandLineArgs) {
        return commandLine2StartParameterConverter.convert(commandLineArgs);
    }

    public GradleLauncher newInstance(String[] commandLineArgs) {
        return newInstance(commandLine2StartParameterConverter.convert(commandLineArgs));
    }

    public GradleLauncher newInstance(StartParameter startParameter) {
        ListenerManager listenerManager = new DefaultListenerManager();
        loggingConfigurer.configure(startParameter.getLogLevel());

        listenerManager.useLogger(new TaskExecutionLogger(Logging.getLogger(TaskExecutionLogger.class)));
        listenerManager.addListener(tracker);

        ServiceRegistryFactory serviceRegistryFactory = new DefaultServiceRegistryFactory(startParameter, listenerManager);
        ISettingsFinder settingsFinder = new EmbeddedScriptSettingsFinder(
                new DefaultSettingsFinder(WrapUtil.<ISettingsFileSearchStrategy>toList(
                        new MasterDirSettingsFinderStrategy(),
                        new ParentDirSettingsFinderStrategy()))
        );
        ScriptCompilerFactory scriptCompilerFactory = serviceRegistryFactory.get(ScriptCompilerFactory.class);
        InitScriptHandler initScriptHandler = new InitScriptHandler(
                new UserHomeInitScriptFinder(
                        new DefaultInitScriptFinder()),
                new DefaultInitScriptProcessor(scriptCompilerFactory, serviceRegistryFactory.get(ImportsReader.class)));
        DefaultGradle gradle = new DefaultGradle(
                tracker.getCurrentBuild(),
                startParameter,
                serviceRegistryFactory);
        return new GradleLauncher(
                gradle,
                initScriptHandler,
                new SettingsHandler(
                        settingsFinder,
                        new PropertiesLoadingSettingsProcessor(
                                new ScriptEvaluatingSettingsProcessor(scriptCompilerFactory,
                                        serviceRegistryFactory.get(ImportsReader.class),
                                        new SettingsFactory(new DefaultProjectDescriptorRegistry()))
                        ),
                        new BuildSourceBuilder(
                                this,
                                new DefaultCacheInvalidationStrategy()
                        )),
                new DefaultGradlePropertiesLoader(),
                new BuildLoader(
                        new ProjectFactory(
                                startParameter.getBuildScriptSource())),
                new BuildConfigurer(new ProjectDependencies2TaskResolver()),
                loggingConfigurer);
    }

    public void setCommandLine2StartParameterConverter(
            CommandLine2StartParameterConverter commandLine2StartParameterConverter) {
        this.commandLine2StartParameterConverter = commandLine2StartParameterConverter;
    }
}
