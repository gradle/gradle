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

import org.gradle.api.internal.artifacts.ConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.DefaultConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.dsl.DefaultPublishArtifactFactory;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandlerFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.*;
import org.gradle.api.internal.artifacts.ivyservice.DefaultResolverFactory;
import org.gradle.api.internal.artifacts.ivyservice.ResolverFactory;
import org.gradle.api.internal.artifacts.repositories.DefaultInternalRepository;
import org.gradle.api.internal.project.DefaultProjectServiceRegistryFactory;
import org.gradle.api.internal.project.ImportsReader;
import org.gradle.api.internal.project.ProjectFactory;
import org.gradle.api.logging.LogLevel;
import org.gradle.configuration.*;
import org.gradle.groovy.scripts.*;
import org.gradle.initialization.*;
import org.gradle.util.WrapUtil;

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

    public Gradle newInstance(String[] commandLineArgs) {
        return newInstance(commandLine2StartParameterConverter.convert(commandLineArgs));
    }

    public Gradle newInstance(StartParameter startParameter) {
        loggingConfigurer.configure(startParameter.getLogLevel());
        ImportsReader importsReader = new ImportsReader(startParameter.getDefaultImportsFile());
        CachePropertiesHandler cachePropertiesHandler = new DefaultCachePropertiesHandler();

        ISettingsFinder settingsFinder = new EmbeddedScriptSettingsFinder(
                new DefaultSettingsFinder(WrapUtil.<ISettingsFileSearchStrategy>toList(
                        new MasterDirSettingsFinderStrategy(),
                        new ParentDirSettingsFinderStrategy()))
        );
        ConfigurationContainerFactory configurationContainerFactory = new DefaultConfigurationContainerFactory(startParameter.getProjectDependenciesBuildInstruction());
        DefaultInternalRepository internalRepository = new DefaultInternalRepository();
        DependencyFactory dependencyFactory = new DefaultDependencyFactory(
                WrapUtil.<IDependencyImplementationFactory>toSet(new ModuleDependencyFactory(), new SelfResolvingDependencyFactory()),
                new DefaultClientModuleFactory(),
                new DefaultProjectDependencyFactory());
        ResolverFactory resolverFactory = new DefaultResolverFactory();
        DefaultProjectEvaluator projectEvaluator = new DefaultProjectEvaluator(
                new BuildScriptCompiler(
                        importsReader,
                        new DefaultScriptProcessorFactory(
                                new DefaultScriptCompilationHandler(
                                        cachePropertiesHandler),
                                startParameter.getCacheUsage()),
                        new DefaultProjectScriptMetaData()),
                new BuildScriptEvaluator());
        Gradle gradle = new Gradle(
                startParameter,
                settingsFinder,
                new DefaultGradlePropertiesLoader(),
                new ScriptLocatingSettingsProcessor(
                        new PropertiesLoadingSettingsProcessor(
                                new ScriptEvaluatingSettingsProcessor(
                                        new DefaultSettingsScriptMetaData(),
                                        new DefaultScriptProcessorFactory(
                                                new DefaultScriptCompilationHandler(cachePropertiesHandler),
                                                startParameter.getCacheUsage()),
                                        importsReader,
                                        new SettingsFactory(
                                                new DefaultProjectDescriptorRegistry(),
                                                new BuildSourceBuilder(new DefaultGradleFactory(
                                                        new LoggingConfigurer() {
                                                            public void configure(LogLevel logLevel) {
                                                                // do nothing
                                                            }
                                                        },
                                                        commandLine2StartParameterConverter), new DefaultCacheInvalidationStrategy())))
                )),
                new BuildLoader(
                        new ProjectFactory(
                                new DefaultProjectServiceRegistryFactory(
                                        new DefaultRepositoryHandlerFactory(resolverFactory),
                                        configurationContainerFactory,
                                        new DefaultPublishArtifactFactory(),
                                        dependencyFactory,
                                        projectEvaluator),
                                startParameter.getBuildScriptSource()),
                        internalRepository),
                new BuildConfigurer(new ProjectDependencies2TaskResolver()));
        gradle.addBuildListener(internalRepository);
        gradle.addBuildListener(projectEvaluator);
        return gradle;
    }
}
