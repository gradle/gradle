/*
 * Copyright 2007-2008 the original author or authors.
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

import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.AsmBackedClassGenerator;
import org.gradle.api.internal.artifacts.ConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.DefaultConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.dsl.DefaultPublishArtifactFactory;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandlerFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.*;
import org.gradle.api.internal.artifacts.ivyservice.DefaultResolverFactory;
import org.gradle.api.internal.artifacts.ivyservice.ResolverFactory;
import org.gradle.api.internal.artifacts.repositories.DefaultInternalRepository;
import org.gradle.api.internal.project.*;
import org.gradle.configuration.*;
import org.gradle.groovy.scripts.*;
import org.gradle.initialization.*;
import org.gradle.invocation.DefaultGradle;
import org.gradle.util.WrapUtil;
import org.gradle.listener.ListenerManager;
import org.gradle.listener.DefaultListenerManager;

import java.util.Map;
import java.util.HashMap;

/**
 * @author Hans Dockter
*/
public class DefaultGradleFactory implements GradleFactory {
    private LoggingConfigurer loggingConfigurer;
    private CommandLine2StartParameterConverter commandLine2StartParameterConverter;

    public DefaultGradleFactory(LoggingConfigurer loggingConfigurer, CommandLine2StartParameterConverter commandLine2StartParameterConverter) {
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
        loggingConfigurer.initialize(listenerManager);
        loggingConfigurer.configure(startParameter.getLogLevel());
        ImportsReader importsReader = new ImportsReader(startParameter.getDefaultImportsFile());

        ISettingsFinder settingsFinder = new EmbeddedScriptSettingsFinder(
                new DefaultSettingsFinder(WrapUtil.<ISettingsFileSearchStrategy>toList(
                        new MasterDirSettingsFinderStrategy(),
                        new ParentDirSettingsFinderStrategy()))
        );
        Map clientModuleRegistry = new HashMap();
        ConfigurationContainerFactory configurationContainerFactory = new DefaultConfigurationContainerFactory(clientModuleRegistry);
        DefaultInternalRepository internalRepository = new DefaultInternalRepository(listenerManager);
        DependencyFactory dependencyFactory = new DefaultDependencyFactory(
                WrapUtil.<IDependencyImplementationFactory>toSet(new ModuleDependencyFactory(),
                        new SelfResolvingDependencyFactory()),
                new DefaultClientModuleFactory(),
                new DefaultProjectDependencyFactory(startParameter.getProjectDependenciesBuildInstruction()));
        ResolverFactory resolverFactory = new DefaultResolverFactory();
        ScriptCompilerFactory scriptCompilerFactory = new DefaultScriptCompilerFactory(
                new DefaultScriptCompilationHandler(
                        new DefaultCachePropertiesHandler()),
                startParameter.getCacheUsage(),
                startParameter.getGradleUserHomeDir(),
                new DefaultScriptRunnerFactory(
                        new DefaultScriptMetaData()));
        DefaultProjectEvaluator projectEvaluator = new DefaultProjectEvaluator(
                new BuildScriptProcessor(
                        importsReader, scriptCompilerFactory));
        ClassGenerator classGenerator = new AsmBackedClassGenerator();
        ServiceRegistryFactory serviceRegistryFactory = new DefaultServiceRegistryFactory(
                new DefaultRepositoryHandlerFactory(resolverFactory, classGenerator),
                configurationContainerFactory,
                new DefaultPublishArtifactFactory(),
                dependencyFactory, projectEvaluator,
                classGenerator);
        InitScriptHandler initScriptHandler = new InitScriptHandler(
                new UserHomeInitScriptFinder(
                        new DefaultInitScriptFinder()),
                new DefaultInitScriptProcessor(scriptCompilerFactory, importsReader));
        DefaultGradle gradle = new DefaultGradle(
                startParameter,
                internalRepository,
                serviceRegistryFactory,
                new DefaultStandardOutputRedirector(),
                listenerManager);
        return new GradleLauncher(
                gradle,
                initScriptHandler,
                new SettingsHandler(
                        settingsFinder,
                        new PropertiesLoadingSettingsProcessor(
                                new ScriptEvaluatingSettingsProcessor(scriptCompilerFactory,
                                        importsReader,
                                        new SettingsFactory(new DefaultProjectDescriptorRegistry()))
                        ),
                        new BuildSourceBuilder(
                                new DefaultGradleFactory(
                                        loggingConfigurer,
                                        commandLine2StartParameterConverter),
                                new DefaultCacheInvalidationStrategy()
                        )),
                new DefaultGradlePropertiesLoader(),
                new BuildLoader(
                        new ProjectFactory(serviceRegistryFactory,
                                startParameter.getBuildScriptSource())),
                new BuildConfigurer(new ProjectDependencies2TaskResolver()),
                loggingConfigurer,
                listenerManager);
    }
}
