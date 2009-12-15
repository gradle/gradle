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
package org.gradle.api.internal.project;

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.StartParameter;
import org.gradle.api.artifacts.dsl.RepositoryHandlerFactory;
import org.gradle.api.execution.TaskActionListener;
import org.gradle.api.internal.AsmBackedClassGenerator;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.ExceptionAnalyser;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.ConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.DefaultConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.dsl.DefaultPublishArtifactFactory;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandlerFactory;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.*;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.*;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.*;
import org.gradle.api.internal.changedetection.CachingHasher;
import org.gradle.api.internal.changedetection.DefaultHasher;
import org.gradle.api.internal.changedetection.DefaultTaskArtifactStateRepository;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.tasks.DefaultTaskExecuter;
import org.gradle.api.internal.tasks.SkipTaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.cache.AutoCloseCacheFactory;
import org.gradle.cache.CacheFactory;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.DefaultCacheRepository;
import org.gradle.configuration.*;
import org.gradle.groovy.scripts.*;
import org.gradle.initialization.*;
import org.gradle.listener.DefaultListenerManager;
import org.gradle.listener.ListenerManager;
import org.gradle.util.WrapUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Contains the singleton services which are shared by all builds executed by a single {@link org.gradle.GradleLauncher}
 * invocation.
 */
public class TopLevelBuildServiceRegistry extends DefaultServiceRegistry implements ServiceRegistryFactory {
    private final StartParameter startParameter;
    private final Map<String, ModuleDescriptor> clientModuleRegistry = new HashMap<String, ModuleDescriptor>();

    public TopLevelBuildServiceRegistry(final ServiceRegistry parent, final StartParameter startParameter) {
        super(parent);
        this.startParameter = startParameter;

        add(ListenerManager.class, new DefaultListenerManager());
        add(ImportsReader.class, new ImportsReader(startParameter.getDefaultImportsFile()));
        add(ClassGenerator.class, new AsmBackedClassGenerator());
        add(StandardOutputRedirector.class, new DefaultStandardOutputRedirector());
        add(PublishArtifactFactory.class, new DefaultPublishArtifactFactory());
        add(new Service(CacheFactory.class) {
            @Override
            protected Object create() {
                return new AutoCloseCacheFactory(parent.get(CacheFactory.class));
            }
        });
    }

    protected TaskExecuter createTaskExecuter() {
        return new SkipTaskExecuter(
                new ExecutionShortCircuitTaskExecuter(
                        new DefaultTaskExecuter(
                                get(ListenerManager.class).getBroadcaster(TaskActionListener.class)),
                        get(TaskArtifactStateRepository.class),
                        startParameter));
    }

    protected RepositoryHandlerFactory createRepositoryHandlerFactory() {
        return new DefaultRepositoryHandlerFactory(new DefaultResolverFactory(), get(ClassGenerator.class));
    }

    protected CacheRepository createCacheRepository() {
        return new DefaultCacheRepository(startParameter.getGradleUserHomeDir(),
                startParameter.getCacheUsage(), get(CacheFactory.class));
    }

    protected ModuleDescriptorFactory createModuleDescriptorFactory() {
        return new DefaultModuleDescriptorFactory();
    }

    protected ExcludeRuleConverter createExcludeRuleConverter() {
        return new DefaultExcludeRuleConverter();
    }

    protected ExternalModuleDependencyDescriptorFactory createExternalModuleDependencyDescriptorFactory() {
        return new ExternalModuleDependencyDescriptorFactory(get(ExcludeRuleConverter.class));
    }

    protected ConfigurationsToModuleDescriptorConverter createConfigurationsToModuleDescriptorConverter() {
        return new DefaultConfigurationsToModuleDescriptorConverter();
    }

    protected IvyFileConverter createIvyFileConverter() {
        DefaultModuleDescriptorFactoryForClientModule clientModuleDescriptorFactory = new DefaultModuleDescriptorFactoryForClientModule();
        DependencyDescriptorFactoryDelegate dependencyDescriptorFactoryDelegate = new DependencyDescriptorFactoryDelegate(
                new ClientModuleDependencyDescriptorFactory(
                        get(ExcludeRuleConverter.class), clientModuleDescriptorFactory, clientModuleRegistry),
                new ProjectDependencyDescriptorFactory(
                        get(ExcludeRuleConverter.class),
                        ProjectDependencyDescriptorFactory.IVY_FILE_MODULE_REVISION_ID_STRATEGY),
                get(ExternalModuleDependencyDescriptorFactory.class));
        clientModuleDescriptorFactory.setDependencyDescriptorFactory(dependencyDescriptorFactoryDelegate);
        return new DefaultIvyFileConverter(
                get(ModuleDescriptorFactory.class),
                get(ConfigurationsToModuleDescriptorConverter.class),
                new DefaultDependenciesToModuleDescriptorConverter(
                        dependencyDescriptorFactoryDelegate,
                        get(ExcludeRuleConverter.class)),
                new DefaultArtifactsToModuleDescriptorConverter(DefaultArtifactsToModuleDescriptorConverter.IVY_FILE_STRATEGY));
    }

    protected ResolveModuleDescriptorConverter createResolveModuleDescriptorConverter() {
        DefaultModuleDescriptorFactoryForClientModule clientModuleDescriptorFactory = new DefaultModuleDescriptorFactoryForClientModule();
        DependencyDescriptorFactoryDelegate dependencyDescriptorFactoryDelegate = new DependencyDescriptorFactoryDelegate(
                new ClientModuleDependencyDescriptorFactory(
                        get(ExcludeRuleConverter.class), clientModuleDescriptorFactory, clientModuleRegistry),
                new ProjectDependencyDescriptorFactory(
                        get(ExcludeRuleConverter.class),
                        ProjectDependencyDescriptorFactory.RESOLVE_MODULE_REVISION_ID_STRATEGY),
                get(ExternalModuleDependencyDescriptorFactory.class));
        clientModuleDescriptorFactory.setDependencyDescriptorFactory(dependencyDescriptorFactoryDelegate);
        return new ResolveModuleDescriptorConverter(
                get(ModuleDescriptorFactory.class),
                get(ConfigurationsToModuleDescriptorConverter.class),
                new DefaultDependenciesToModuleDescriptorConverter(
                        dependencyDescriptorFactoryDelegate,
                        get(ExcludeRuleConverter.class)));
    }

    protected PublishModuleDescriptorConverter createPublishModuleDescriptorConverter() {
        return new PublishModuleDescriptorConverter(
                get(ResolveModuleDescriptorConverter.class),
                new DefaultArtifactsToModuleDescriptorConverter(DefaultArtifactsToModuleDescriptorConverter.RESOLVE_STRATEGY));
    }

    protected ConfigurationContainerFactory createConfigurationContainerFactory() {
        // todo this creation is duplicate. When we improve our service registry to allow multiple instances for same type
        // we should consolidate.
        DefaultModuleDescriptorFactoryForClientModule clientModuleDescriptorFactory = new DefaultModuleDescriptorFactoryForClientModule();
        DependencyDescriptorFactoryDelegate dependencyDescriptorFactoryDelegate = new DependencyDescriptorFactoryDelegate(
                new ClientModuleDependencyDescriptorFactory(
                        get(ExcludeRuleConverter.class), clientModuleDescriptorFactory, clientModuleRegistry),
                new ProjectDependencyDescriptorFactory(
                        get(ExcludeRuleConverter.class),
                        ProjectDependencyDescriptorFactory.RESOLVE_MODULE_REVISION_ID_STRATEGY),
                get(ExternalModuleDependencyDescriptorFactory.class));
        clientModuleDescriptorFactory.setDependencyDescriptorFactory(dependencyDescriptorFactoryDelegate);

        return new DefaultConfigurationContainerFactory(clientModuleRegistry,
                new DefaultSettingsConverter(),
                get(ResolveModuleDescriptorConverter.class),
                get(PublishModuleDescriptorConverter.class),
                get(IvyFileConverter.class),
                new DefaultIvyFactory(),
                new SelfResolvingDependencyResolver(
                        new DefaultIvyDependencyResolver(
                                new DefaultIvyReportConverter(dependencyDescriptorFactoryDelegate))),
                new DefaultIvyDependencyPublisher(new DefaultPublishOptionsFactory()),
                get(ClassGenerator.class));
    }

    protected DependencyFactory createDependencyFactory() {
        ClassGenerator classGenerator = get(ClassGenerator.class);
        return new DefaultDependencyFactory(
                WrapUtil.<IDependencyImplementationFactory>toSet(
                        new ModuleDependencyFactory(
                                classGenerator),
                        new SelfResolvingDependencyFactory(
                                classGenerator)),
                new DefaultClientModuleFactory(
                        classGenerator),
                new DefaultProjectDependencyFactory(
                        startParameter.getProjectDependenciesBuildInstruction(),
                        classGenerator));
    }
    
    protected ProjectEvaluator createProjectEvaluator() {
        return new DefaultProjectEvaluator(
                new BuildScriptProcessor(
                        get(ScriptObjectConfigurerFactory.class)));
    }

    protected ITaskFactory createITaskFactory() {
        return new DependencyAutoWireTaskFactory(
                new AnnotationProcessingTaskFactory(
                        new TaskFactory(
                                get(ClassGenerator.class))));
    }

    protected TaskArtifactStateRepository createTaskArtifactStateRepository() {
        return new DefaultTaskArtifactStateRepository(
                get(CacheRepository.class),
                new CachingHasher(
                        new DefaultHasher(),
                        get(CacheRepository.class)));
    }

    protected ScriptCompilerFactory createScriptCompileFactory() {
        ScriptExecutionListener scriptExecutionListener = get(ListenerManager.class).getBroadcaster(ScriptExecutionListener.class);
        return new DefaultScriptCompilerFactory(
                new DefaultScriptCompilationHandler(),
                startParameter.getCacheUsage(),
                new DefaultScriptRunnerFactory(
                        scriptExecutionListener),
                get(CacheRepository.class));
    }

    protected ScriptObjectConfigurerFactory createScriptObjectConfigurerFactory() {
        return new DefaultScriptObjectConfigurerFactory(
                get(ScriptCompilerFactory.class),
                get(ImportsReader.class));
    }
    
    protected InitScriptHandler createInitScriptHandler() {
        return new InitScriptHandler(
                new UserHomeInitScriptFinder(
                        new DefaultInitScriptFinder()),
                new DefaultInitScriptProcessor(
                        get(ScriptObjectConfigurerFactory.class)));

    }

    protected SettingsProcessor createSettingsProcessor() {
        return new PropertiesLoadingSettingsProcessor(new
                ScriptEvaluatingSettingsProcessor(
                    get(ScriptObjectConfigurerFactory.class),
                    new SettingsFactory(
                        new DefaultProjectDescriptorRegistry())));
    }

    protected ExceptionAnalyser createExceptionAnalyser() {
        return new DefaultExceptionAnalyser(get(ListenerManager.class));
    }
    
    public ServiceRegistryFactory createFor(Object domainObject) {
        if (domainObject instanceof GradleInternal) {
            return new GradleInternalServiceRegistry(this, (GradleInternal) domainObject);
        }
        throw new IllegalArgumentException(String.format("Cannot create services for unknown domain object of type %s.",
                domainObject.getClass().getSimpleName()));
    }
}
