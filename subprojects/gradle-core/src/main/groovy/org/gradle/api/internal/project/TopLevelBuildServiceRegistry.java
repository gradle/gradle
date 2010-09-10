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

package org.gradle.api.internal.project;

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.plugins.resolver.ChainResolver;
import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.dsl.RepositoryHandlerFactory;
import org.gradle.api.execution.TaskActionListener;
import org.gradle.api.internal.*;
import org.gradle.api.internal.artifacts.ConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.DefaultConfigurationContainerFactory;
import org.gradle.api.internal.artifacts.DefaultModule;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.DefaultPublishArtifactFactory;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandlerFactory;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.*;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.*;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.*;
import org.gradle.api.internal.artifacts.repositories.InternalRepository;
import org.gradle.api.internal.changedetection.*;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.internal.initialization.DefaultScriptHandlerFactory;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.project.taskfactory.*;
import org.gradle.api.internal.tasks.DefaultTaskExecuter;
import org.gradle.api.internal.tasks.ExecuteAtMostOnceTaskExecuter;
import org.gradle.api.internal.tasks.SkipTaskExecuter;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.cache.AutoCloseCacheFactory;
import org.gradle.cache.CacheFactory;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.DefaultCacheRepository;
import org.gradle.configuration.*;
import org.gradle.groovy.scripts.*;
import org.gradle.initialization.*;
import org.gradle.listener.ListenerManager;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.messaging.actor.ActorFactory;
import org.gradle.messaging.actor.internal.DefaultActorFactory;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;
import org.gradle.messaging.concurrent.ExecutorFactory;
import org.gradle.messaging.remote.MessagingServer;
import org.gradle.messaging.remote.internal.TcpMessagingServer;
import org.gradle.process.internal.DefaultWorkerProcessFactory;
import org.gradle.process.internal.WorkerProcessFactory;
import org.gradle.process.internal.child.WorkerProcessClassPathProvider;
import org.gradle.util.*;

import java.io.File;
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
    }

    protected PublishArtifactFactory createPublishArtifactFactory() {
        return new DefaultPublishArtifactFactory();
    }

    protected ImportsReader createImportsReader() {
        return new ImportsReader();
    }
    protected ClassGenerator createClassGenerator() {
        return new AsmBackedClassGenerator();
    }

    protected TimeProvider createTimeProvider() {
        return new TrueTimeProvider();
    }
    
    protected ExecutorFactory createExecutorFactory() {
        return new DefaultExecutorFactory();
    }

    protected IProjectFactory createProjectFactory() {
        return new ProjectFactory(
                startParameter.getBuildScriptSource(),
                get(ClassGenerator.class));
    }

    protected ListenerManager createListenerManager(ListenerManager listenerManager) {
        return listenerManager.createChild();
    }

    protected CacheFactory createCacheFactory(CacheFactory parentFactory) {
        return new AutoCloseCacheFactory(parentFactory);
    }

    protected ClassPathRegistry createClassPathRegistry() {
        return new DefaultClassPathRegistry(new WorkerProcessClassPathProvider(get(CacheRepository.class)));
    }
    
    protected ActorFactory createActorFactory() {
        return new DefaultActorFactory(get(ExecutorFactory.class));
    }

    protected TaskExecuter createTaskExecuter() {
        return new ExecuteAtMostOnceTaskExecuter(
                new SkipTaskExecuter(
                        new ExecutionShortCircuitTaskExecuter(
                                new PostExecutionAnalysisTaskExecuter(
                                        new DefaultTaskExecuter(
                                                get(ListenerManager.class).getBroadcaster(TaskActionListener.class))),
                                        get(TaskArtifactStateRepository.class))));
    }

    protected RepositoryHandlerFactory createRepositoryHandlerFactory() {
        return new DefaultRepositoryHandlerFactory(
                new DefaultResolverFactory(
                        getFactory(LoggingManagerInternal.class)),
                get(ClassGenerator.class));
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
    
    private ResolveModuleDescriptorConverter createResolveModuleDescriptorConverter(ProjectDependencyDescriptorStrategy projectDependencyStrategy) {
        DependencyDescriptorFactory dependencyDescriptorFactoryDelegate = createDependencyDescriptorFactory(projectDependencyStrategy);
        return new ResolveModuleDescriptorConverter(
                get(ModuleDescriptorFactory.class),
                get(ConfigurationsToModuleDescriptorConverter.class),
                new DefaultDependenciesToModuleDescriptorConverter(
                        dependencyDescriptorFactoryDelegate,
                        get(ExcludeRuleConverter.class)));
    }

    private DependencyDescriptorFactory createDependencyDescriptorFactory(ProjectDependencyDescriptorStrategy projectDependencyStrategy) {
        DefaultModuleDescriptorFactoryForClientModule clientModuleDescriptorFactory = new DefaultModuleDescriptorFactoryForClientModule();
        DependencyDescriptorFactory dependencyDescriptorFactoryDelegate = new DependencyDescriptorFactoryDelegate(
                new ClientModuleDependencyDescriptorFactory(
                        get(ExcludeRuleConverter.class), clientModuleDescriptorFactory, clientModuleRegistry),
                new ProjectDependencyDescriptorFactory(
                        get(ExcludeRuleConverter.class),
                        projectDependencyStrategy),
                get(ExternalModuleDependencyDescriptorFactory.class));
        clientModuleDescriptorFactory.setDependencyDescriptorFactory(dependencyDescriptorFactoryDelegate);
        return dependencyDescriptorFactoryDelegate;
    }

    protected PublishModuleDescriptorConverter createPublishModuleDescriptorConverter() {
        return new PublishModuleDescriptorConverter(
                createResolveModuleDescriptorConverter(ProjectDependencyDescriptorFactory.RESOLVE_DESCRIPTOR_STRATEGY),
                new DefaultArtifactsToModuleDescriptorConverter(DefaultArtifactsToModuleDescriptorConverter.RESOLVE_STRATEGY));
    }

    protected ConfigurationContainerFactory createConfigurationContainerFactory() {
        DependencyDescriptorFactory dependencyDescriptorFactoryDelegate = createDependencyDescriptorFactory(ProjectDependencyDescriptorFactory.RESOLVE_DESCRIPTOR_STRATEGY);
        PublishModuleDescriptorConverter fileModuleDescriptorConverter = new PublishModuleDescriptorConverter(
                createResolveModuleDescriptorConverter(ProjectDependencyDescriptorFactory.IVY_FILE_DESCRIPTOR_STRATEGY),
                new DefaultArtifactsToModuleDescriptorConverter(DefaultArtifactsToModuleDescriptorConverter.IVY_FILE_STRATEGY));

        return new DefaultConfigurationContainerFactory(clientModuleRegistry,
                new DefaultSettingsConverter(
                        get(ProgressLoggerFactory.class)
                ),
                get(PublishModuleDescriptorConverter.class),
                get(PublishModuleDescriptorConverter.class),
                fileModuleDescriptorConverter,
                new DefaultIvyFactory(),
                new SelfResolvingDependencyResolver(
                        new DefaultIvyDependencyResolver(
                                new DefaultIvyReportConverter(dependencyDescriptorFactoryDelegate))),
                new DefaultIvyDependencyPublisher(new DefaultPublishOptionsFactory()),
                get(ClassGenerator.class));
    }

    protected DependencyFactory createDependencyFactory() {
        ClassGenerator classGenerator = get(ClassGenerator.class);
        DefaultProjectDependencyFactory projectDependencyFactory = new DefaultProjectDependencyFactory(
                startParameter.getProjectDependenciesBuildInstruction(),
                classGenerator);
        return new DefaultDependencyFactory(
                WrapUtil.<IDependencyImplementationFactory>toSet(
                        new ModuleDependencyFactory(
                                classGenerator),
                        new SelfResolvingDependencyFactory(
                                classGenerator),
                        new ClassPathDependencyFactory(
                                classGenerator,
                                get(ClassPathRegistry.class),
                                new IdentityFileResolver()),
                        projectDependencyFactory),
                new DefaultClientModuleFactory(
                        classGenerator),
                projectDependencyFactory);
    }

    protected ProjectEvaluator createProjectEvaluator() {
        return new DefaultProjectEvaluator(
                new BuildScriptProcessor(
                        get(ScriptPluginFactory.class)));
    }

    protected ITaskFactory createITaskFactory() {
        return new DependencyAutoWireTaskFactory(
                new AnnotationProcessingTaskFactory(
                        new TaskFactory(
                                get(ClassGenerator.class))));
    }

    protected TaskArtifactStateRepository createTaskArtifactStateRepository() {
        CacheRepository cacheRepository = get(CacheRepository.class);
        FileSnapshotter fileSnapshotter = new DefaultFileSnapshotter(
                new CachingHasher(
                        new DefaultHasher(),
                        cacheRepository));

        FileSnapshotter outputFilesSnapshotter = new OutputFilesSnapshotter(fileSnapshotter, new RandomLongIdGenerator(), cacheRepository);
        return new ShortCircuitTaskArtifactStateRepository(
                startParameter,
                new DefaultTaskArtifactStateRepository(cacheRepository,
                        fileSnapshotter,
                        outputFilesSnapshotter));
    }

    protected ScriptCompilerFactory createScriptCompileFactory() {
        ScriptExecutionListener scriptExecutionListener = get(ListenerManager.class).getBroadcaster(ScriptExecutionListener.class);
        return new DefaultScriptCompilerFactory(
                new CachingScriptCompilationHandler(
                        new DefaultScriptCompilationHandler()),
                new DefaultScriptRunnerFactory(
                        scriptExecutionListener),
                get(CacheRepository.class));
    }

    protected ScriptPluginFactory createScriptObjectConfigurerFactory() {
        return new DefaultScriptPluginFactory(
                get(ScriptCompilerFactory.class),
                get(ImportsReader.class),
                get(ScriptHandlerFactory.class),
                get(ClassLoader.class),
                getFactory(LoggingManagerInternal.class));
    }

    protected MultiParentClassLoader createRootClassLoader() {
        return get(ClassLoaderFactory.class).createScriptClassLoader();
    }
    
    protected InitScriptHandler createInitScriptHandler() {
        return new InitScriptHandler(
                new UserHomeInitScriptFinder(
                        new DefaultInitScriptFinder()),
                new DefaultInitScriptProcessor(
                        get(ScriptPluginFactory.class)));

    }

    protected SettingsProcessor createSettingsProcessor() {
        return new PropertiesLoadingSettingsProcessor(new
                ScriptEvaluatingSettingsProcessor(
                    get(ScriptPluginFactory.class),
                    new SettingsFactory(
                        new DefaultProjectDescriptorRegistry())));
    }

    protected ExceptionAnalyser createExceptionAnalyser() {
        return new DefaultExceptionAnalyser(get(ListenerManager.class));
    }

    protected ScriptHandlerFactory createScriptHandlerFactory() {
        return new DefaultScriptHandlerFactory(
                get(RepositoryHandlerFactory.class),
                get(ConfigurationContainerFactory.class),
                new DependencyMetaDataProviderImpl(), 
                get(DependencyFactory.class));
    }

    protected WorkerProcessFactory createWorkerProcessFactory() {
        ClassPathRegistry classPathRegistry = get(ClassPathRegistry.class);
        return new DefaultWorkerProcessFactory(startParameter.getLogLevel(), get(MessagingServer.class), classPathRegistry,
                new IdentityFileResolver(), new LongIdGenerator());
    }
    
    protected MessagingServer createMessagingServer() {
        return new TcpMessagingServer(get(ClassLoaderFactory.class).getRootClassLoader());
    }
    
    public ServiceRegistryFactory createFor(Object domainObject) {
        if (domainObject instanceof GradleInternal) {
            return new GradleInternalServiceRegistry(this, (GradleInternal) domainObject);
        }
        throw new IllegalArgumentException(String.format("Cannot create services for unknown domain object of type %s.",
                domainObject.getClass().getSimpleName()));
    }

    private class DependencyMetaDataProviderImpl implements DependencyMetaDataProvider {
        public InternalRepository getInternalRepository() {
            return new EmptyInternalRepository();
        }

        public File getGradleUserHomeDir() {
            return startParameter.getGradleUserHomeDir();
        }

        public Module getModule() {
            return new DefaultModule("unspecified", "unspecified", Project.DEFAULT_VERSION, Project.DEFAULT_STATUS);
        }
    }

    private static class EmptyInternalRepository extends ChainResolver implements InternalRepository {
    }
}
