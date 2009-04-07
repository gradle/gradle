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

import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.internal.artifacts.ConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.DefaultConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.DefaultResolverContainer;
import org.gradle.api.internal.artifacts.dsl.dependencies.ModuleDependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectDependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultClientModuleFactory;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandlerFactory;
import org.gradle.api.internal.artifacts.dsl.DefaultPublishArtifactFactory;
import org.gradle.api.internal.artifacts.ivyservice.DefaultResolverFactory;
import org.gradle.api.internal.artifacts.ivyservice.ResolverFactory;
import org.gradle.api.internal.artifacts.repositories.DefaultInternalRepository;
import org.gradle.api.internal.project.*;
import org.gradle.api.logging.LogLevel;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.configuration.ProjectDependencies2TaskResolver;
import org.gradle.groovy.scripts.*;
import org.gradle.initialization.*;
import org.gradle.logging.AntLoggingAdapter;
import org.gradle.util.WrapUtil;

/**
 * @author Hans Dockter
*/
public class DefaultGradleFactory implements GradleFactory {
    private LoggingConfigurer loggingConfigurer;

    public DefaultGradleFactory(LoggingConfigurer loggingConfigurer) {
        this.loggingConfigurer = loggingConfigurer;
    }

    public LoggingConfigurer getLoggingConfigurer() {
        return loggingConfigurer;
    }

    public void setLoggingConfigurer(LoggingConfigurer loggingConfigurer) {
        this.loggingConfigurer = loggingConfigurer;
    }

    public Gradle newInstance(StartParameter startParameter) {
        loggingConfigurer.configure(startParameter.getLogLevel());
        ImportsReader importsReader = new ImportsReader(startParameter.getDefaultImportsFile());
        IScriptProcessor scriptProcessor = new DefaultScriptProcessor(
                new DefaultScriptCompilationHandler(new DefaultCachePropertiesHandler()),
                startParameter.getCacheUsage());

        ISettingsFinder settingsFinder = new EmbeddedScriptSettingsFinder(
                new DefaultSettingsFinder(WrapUtil.<ISettingsFileSearchStrategy>toList(
                        new MasterDirSettingsFinderStrategy(),
                        new ParentDirSettingsFinderStrategy()))
        );
        ConfigurationContainerFactory configurationContainerFactory = new DefaultConfigurationContainerFactory();
        DefaultInternalRepository internalRepository = new DefaultInternalRepository();
        DependencyFactory dependencyFactory = new DefaultDependencyFactory(
                WrapUtil.toSet(new ModuleDependencyFactory(), new ProjectDependencyFactory()),
                new DefaultClientModuleFactory());
        ResolverFactory resolverFactory = new DefaultResolverFactory();
        Gradle gradle = new Gradle(
                startParameter,
                settingsFinder,
                new DefaultGradlePropertiesLoader(),
                new ScriptLocatingSettingsProcessor(
                        new PropertiesLoadingSettingsProcessor(
                                new ScriptEvaluatingSettingsProcessor(
                                        new DefaultSettingsScriptMetaData(),
                                        scriptProcessor,
                                        importsReader,
                                        new SettingsFactory(
                                        new DefaultProjectDescriptorRegistry(),
                                        dependencyFactory,
                                        new DefaultResolverContainer(resolverFactory, null),
                                        configurationContainerFactory,
                                        internalRepository,
                                        new BuildSourceBuilder(new DefaultGradleFactory(
                                                new LoggingConfigurer() {
                                                    public void configure(LogLevel logLevel) {
                                                        // do nothing
                                                    }
                                                }
                                        ), new DefaultCacheInvalidationStrategy())))
                )),
                new BuildLoader(
                        new ProjectFactory(
                                new TaskFactory(),
                                configurationContainerFactory,
                                dependencyFactory,
                                new DefaultRepositoryHandlerFactory(resolverFactory),
                                new DefaultPublishArtifactFactory(),
                                internalRepository,
                                new BuildScriptProcessor(
                                        scriptProcessor,
                                        new DefaultProjectScriptMetaData(),
                                        importsReader
                                ),
                                new PluginRegistry(
                                        startParameter.getPluginPropertiesFile()),
                                startParameter.getBuildScriptSource(),
                                new DefaultAntBuilderFactory(new AntLoggingAdapter()))
                ),
                new BuildConfigurer(new ProjectDependencies2TaskResolver()));
        gradle.addBuildListener(internalRepository);
        return gradle;
    }
}
