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
import org.gradle.api.internal.AsmBackedClassGenerator;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.ConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.DefaultConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.dsl.DefaultPublishArtifactFactory;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandlerFactory;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.*;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.*;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ResolveModuleDescriptorConverter;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.*;
import org.gradle.api.internal.changedetection.CachingHasher;
import org.gradle.api.internal.changedetection.DefaultHasher;
import org.gradle.api.internal.changedetection.DefaultTaskArtifactStateRepository;
import org.gradle.api.internal.changedetection.TaskArtifactStateRepository;
import org.gradle.api.internal.tasks.DefaultTaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.SkipTaskExecuter;
import org.gradle.api.execution.TaskActionListener;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.DefaultCacheRepository;
import org.gradle.configuration.BuildScriptProcessor;
import org.gradle.configuration.DefaultProjectEvaluator;
import org.gradle.configuration.ProjectEvaluator;
import org.gradle.groovy.scripts.*;
import org.gradle.listener.ListenerManager;
import org.gradle.listener.DefaultListenerManager;
import org.gradle.util.WrapUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Contains the singleton services which are shared by all builds.
 */
public class DefaultServiceRegistryFactory extends AbstractServiceRegistry implements ServiceRegistryFactory {
    public DefaultServiceRegistryFactory(StartParameter startParameter) {
        this(startParameter, new DefaultListenerManager());
    }

    public DefaultServiceRegistryFactory(final StartParameter startParameter, ListenerManager listenerManager) {
        final Map<String, ModuleDescriptor> clientModuleRegistry = new HashMap<String, ModuleDescriptor>();

        add(ListenerManager.class, listenerManager);
        add(ImportsReader.class, new ImportsReader(startParameter.getDefaultImportsFile()));
        add(ClassGenerator.class, new AsmBackedClassGenerator());
        add(StandardOutputRedirector.class, new DefaultStandardOutputRedirector());
        add(PublishArtifactFactory.class, new DefaultPublishArtifactFactory());

        add(new Service(TaskExecuter.class){
            protected Object create() {
                return new SkipTaskExecuter(
                        new ExecutionShortCircuitTaskExecuter(
                                new DefaultTaskExecuter(
                                        get(ListenerManager.class).getBroadcaster(TaskActionListener.class)),
                                get(TaskArtifactStateRepository.class),
                                startParameter));
            }
        });

        add(new Service(RepositoryHandlerFactory.class) {
            protected Object create() {
                return new DefaultRepositoryHandlerFactory(new DefaultResolverFactory(), get(ClassGenerator.class));
            }
        });

        add(new Service(CacheRepository.class) {
            protected Object create() {
                return new DefaultCacheRepository(startParameter.getGradleUserHomeDir(),
                        startParameter.getCacheUsage());
            }
        });

        add(new Service(ModuleDescriptorFactory.class) {
            protected Object create() {
                return new DefaultModuleDescriptorFactory();
            }
        });

        add(new Service(ExcludeRuleConverter.class) {
            protected Object create() {
                return new DefaultExcludeRuleConverter();
            }
        });

        add(new Service(ExternalModuleDependencyDescriptorFactory.class) {
            protected Object create() {
                return new ExternalModuleDependencyDescriptorFactory(get(ExcludeRuleConverter.class));
            }
        });

        add(new Service(ConfigurationsToModuleDescriptorConverter.class) {
            protected Object create() {
                return new DefaultConfigurationsToModuleDescriptorConverter();
            }
        });


        add(new Service(IvyFileConverter.class) {
            protected Object create() {
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
        });

        add(new Service(ResolveModuleDescriptorConverter.class) {
            protected Object create() {
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
        });

        add(new Service(PublishModuleDescriptorConverter.class) {
            protected Object create() {
                return new PublishModuleDescriptorConverter(
                        get(ResolveModuleDescriptorConverter.class),
                        new DefaultArtifactsToModuleDescriptorConverter(DefaultArtifactsToModuleDescriptorConverter.RESOLVE_STRATEGY));
            }
        });

        add(new Service(ConfigurationContainerFactory.class) {
            protected Object create() {
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
        });

        add(new Service(DependencyFactory.class) {
            protected Object create() {
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
        });

        add(new Service(ProjectEvaluator.class) {
            protected Object create() {
                return new DefaultProjectEvaluator(new BuildScriptProcessor(get(ImportsReader.class), get(
                        ScriptCompilerFactory.class)));
            }
        });

        add(new Service(ITaskFactory.class) {
            protected Object create() {
                return new DependencyAutoWireTaskFactory(
                        new AnnotationProcessingTaskFactory(
                                new TaskFactory(
                                        get(ClassGenerator.class))));
            }
        });

        add(new Service(TaskArtifactStateRepository.class) {
            protected Object create() {
                return new DefaultTaskArtifactStateRepository(
                        get(CacheRepository.class),
                        new CachingHasher(
                                new DefaultHasher(),
                                get(CacheRepository.class)));
            }
        });

        add(new Service(ScriptCompilerFactory.class) {
            protected Object create() {
                return new DefaultScriptCompilerFactory(new DefaultScriptCompilationHandler(),
                        startParameter.getCacheUsage(), new DefaultScriptRunnerFactory(new DefaultScriptMetaData()),
                        get(CacheRepository.class));
            }
        });
    }

    public ServiceRegistryFactory createFor(Object domainObject) {
        if (domainObject instanceof GradleInternal) {
            return new GradleInternalServiceRegistry(this, (GradleInternal) domainObject);
        }
        throw new IllegalArgumentException(String.format("Cannot create services for unknown domain object of type %s.",
                domainObject.getClass().getSimpleName()));
    }
}
