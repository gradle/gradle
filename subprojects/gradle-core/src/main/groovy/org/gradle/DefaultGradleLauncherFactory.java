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
package org.gradle;

import org.gradle.api.internal.project.DefaultServiceRegistryFactory;
import org.gradle.api.internal.project.ImportsReader;
import org.gradle.api.internal.project.ProjectFactory;
import org.gradle.api.internal.project.ServiceRegistryFactory;
import org.gradle.api.logging.Logging;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.configuration.DefaultInitScriptProcessor;
import org.gradle.configuration.ProjectDependencies2TaskResolver;
import org.gradle.groovy.scripts.ScriptCompilerFactory;
import org.gradle.initialization.*;
import org.gradle.invocation.DefaultGradle;
import org.gradle.listener.DefaultListenerManager;
import org.gradle.listener.ListenerManager;
import org.gradle.util.WrapUtil;



/**
 * @author Hans Dockter
 */
public class DefaultGradleLauncherFactory implements GradleLauncherFactory {
    private LoggingConfigurer loggingConfigurer;
    private CommandLine2StartParameterConverter commandLine2StartParameterConverter;

    public DefaultGradleLauncherFactory(LoggingConfigurer loggingConfigurer, CommandLine2StartParameterConverter commandLine2StartParameterConverter) {
        this.loggingConfigurer = loggingConfigurer;
        this.commandLine2StartParameterConverter = commandLine2StartParameterConverter;
    }

    public LoggingConfigurer getLoggingConfigurer() {
        return loggingConfigurer;
    }

    public void setLoggingConfigurer(LoggingConfigurer loggingConfigurer) {
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
                                new DefaultGradleLauncherFactory(
                                        loggingConfigurer,
                                        commandLine2StartParameterConverter),
                                new DefaultCacheInvalidationStrategy()
                        )),
                new DefaultGradlePropertiesLoader(),
                new BuildLoader(
                        new ProjectFactory(
                                startParameter.getBuildScriptSource())),
                new BuildConfigurer(new ProjectDependencies2TaskResolver()),
                loggingConfigurer);
    }
}
