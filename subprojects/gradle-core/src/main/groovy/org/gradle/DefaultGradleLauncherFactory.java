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

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.internal.AsmBackedClassGenerator;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.artifacts.ConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.DefaultConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandlerFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.*;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.*;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultClientModuleDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultDependenciesToModuleDescriptorConverter;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultDependencyDescriptorFactory;
import org.gradle.api.internal.project.DefaultServiceRegistryFactory;
import org.gradle.api.internal.project.ImportsReader;
import org.gradle.api.internal.project.ProjectFactory;
import org.gradle.api.internal.project.ServiceRegistryFactory;
import org.gradle.api.logging.StandardOutputLogging;
import org.gradle.configuration.*;
import org.gradle.groovy.scripts.*;
import org.gradle.initialization.*;
import org.gradle.invocation.DefaultGradle;
import org.gradle.listener.DefaultListenerManager;
import org.gradle.listener.ListenerManager;
import org.gradle.util.WrapUtil;

import java.util.HashMap;
import java.util.Map;



/**
 * @author Hans Dockter
 */
public class DefaultGradleLauncherFactory implements GradleFactory {
    private CommandLine2StartParameterConverter commandLine2StartParameterConverter;

    public DefaultGradleLauncherFactory(CommandLine2StartParameterConverter commandLine2StartParameterConverter) {
        this.commandLine2StartParameterConverter = commandLine2StartParameterConverter;
    }

    public StartParameter createStartParameter(String[] commandLineArgs) {
        return commandLine2StartParameterConverter.convert(commandLineArgs);
    }

    public GradleLauncher newInstance(String[] commandLineArgs) {
        return newInstance(commandLine2StartParameterConverter.convert(commandLineArgs));
    }

    public GradleLauncher newInstance(StartParameter startParameter) {
        // Switching StandardOutputLogging off is important if the factory is used to
        // run multiple Gradle builds (each one requiring a new instances of GradleLauncher).
        // Switching it off shouldn't be strictly necessary as StandardOutput capturing should
        // always be closed. But as we expose this functionality to the builds, we can't
        // guarantee this.
        StandardOutputLogging.off();
        LoggingConfigurer loggingConfigurer = new DefaultLoggingConfigurer();
        ListenerManager listenerManager = new DefaultListenerManager();
        loggingConfigurer.initialize(listenerManager);
        loggingConfigurer.configure(startParameter.getLogLevel());

        ImportsReader importsReader = new ImportsReader(startParameter.getDefaultImportsFile());

        ISettingsFinder settingsFinder = new EmbeddedScriptSettingsFinder(
                new DefaultSettingsFinder(WrapUtil.<ISettingsFileSearchStrategy>toList(
                        new MasterDirSettingsFinderStrategy(),
                        new ParentDirSettingsFinderStrategy()))
        );
        Map<String, ModuleDescriptor> clientModuleRegistry = new HashMap<String, ModuleDescriptor>();
        ExcludeRuleConverter excludeRuleConverter = new DefaultExcludeRuleConverter();
        ModuleDescriptorConverter moduleDescriptorConverter = new DefaultModuleDescriptorConverter(
                new DefaultModuleDescriptorFactory(),
                new DefaultConfigurationsToModuleDescriptorConverter(),
                new DefaultDependenciesToModuleDescriptorConverter(
                        new DefaultDependencyDescriptorFactory(
                                excludeRuleConverter,
                                new DefaultClientModuleDescriptorFactory(),
                                clientModuleRegistry),
                        excludeRuleConverter),
                new DefaultArtifactsToModuleDescriptorConverter());
        ConfigurationContainerFactory configurationContainerFactory = new DefaultConfigurationContainerFactory(
                clientModuleRegistry,
                new DefaultSettingsConverter(), moduleDescriptorConverter,
                new DefaultIvyFactory(),
                new SelfResolvingDependencyResolver(
                        new DefaultIvyDependencyResolver(new DefaultIvyReportConverter())),
                new DefaultIvyDependencyPublisher(new DefaultModuleDescriptorForUploadConverter(),
                        new DefaultPublishOptionsFactory()));
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
                dependencyFactory, projectEvaluator,
                classGenerator,
                moduleDescriptorConverter);
        InitScriptHandler initScriptHandler = new InitScriptHandler(
                new UserHomeInitScriptFinder(
                        new DefaultInitScriptFinder()),
                new DefaultInitScriptProcessor(scriptCompilerFactory, importsReader));
        DefaultGradle gradle = new DefaultGradle(
                startParameter,
                serviceRegistryFactory,
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
                                new DefaultGradleLauncherFactory(
                                        commandLine2StartParameterConverter),
                                new DefaultCacheInvalidationStrategy()
                        )),
                new DefaultGradlePropertiesLoader(),
                new BuildLoader(
                        new ProjectFactory(
                                startParameter.getBuildScriptSource())),
                new BuildConfigurer(new ProjectDependencies2TaskResolver()),
                loggingConfigurer,
                listenerManager);
    }
}
