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
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultClientModuleDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultDependenciesToModuleDescriptorConverter;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultDependencyDescriptorFactory;
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

        add(new Service(ConfigurationContainerFactory.class) {
            protected Object create() {
                return new DefaultConfigurationContainerFactory(clientModuleRegistry, new DefaultSettingsConverter(),
                        get(ModuleDescriptorConverter.class), new DefaultIvyFactory(),
                        new SelfResolvingDependencyResolver(new DefaultIvyDependencyResolver(
                                new DefaultIvyReportConverter())), new DefaultIvyDependencyPublisher(
                                new DefaultModuleDescriptorForUploadConverter(), new DefaultPublishOptionsFactory()));
            }
        });

        add(new Service(ModuleDescriptorConverter.class) {
            protected Object create() {
                ExcludeRuleConverter excludeRuleConverter = new DefaultExcludeRuleConverter();
                return new DefaultModuleDescriptorConverter(new DefaultModuleDescriptorFactory(),
                        new DefaultConfigurationsToModuleDescriptorConverter(),
                        new DefaultDependenciesToModuleDescriptorConverter(new DefaultDependencyDescriptorFactory(
                                excludeRuleConverter, new DefaultClientModuleDescriptorFactory(), clientModuleRegistry),
                                excludeRuleConverter), new DefaultArtifactsToModuleDescriptorConverter());
            }
        });

        add(new Service(DependencyFactory.class) {
            protected Object create() {
                return new DefaultDependencyFactory(WrapUtil.<IDependencyImplementationFactory>toSet(
                        new ModuleDependencyFactory(), new SelfResolvingDependencyFactory()),
                        new DefaultClientModuleFactory(), new DefaultProjectDependencyFactory(
                                startParameter.getProjectDependenciesBuildInstruction()));
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
