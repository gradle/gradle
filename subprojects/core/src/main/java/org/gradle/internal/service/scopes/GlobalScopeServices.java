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

package org.gradle.internal.service.scopes;

import com.google.common.collect.Iterables;
import org.gradle.api.execution.internal.DefaultTaskInputsListener;
import org.gradle.api.execution.internal.TaskInputsListener;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.MutationGuards;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.collections.DefaultDomainObjectCollectionFactory;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.DefaultFilePropertyFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FilePropertyFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.file.TmpDirTemporaryFileProvider;
import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.model.DefaultObjectFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.internal.provider.DefaultProviderFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.api.tasks.util.internal.CachingPatternSpecFactory;
import org.gradle.api.tasks.util.internal.PatternSpecFactory;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cli.CommandLineConverter;
import org.gradle.configuration.DefaultImportsReader;
import org.gradle.configuration.ImportsReader;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.initialization.DefaultJdkToolsInitializer;
import org.gradle.initialization.DefaultParallelismConfigurationManager;
import org.gradle.initialization.JdkToolsInitializer;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.Factory;
import org.gradle.internal.classloader.DefaultClassLoaderFactory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ParallelismConfigurationManager;
import org.gradle.internal.environment.GradleBuildEnvironment;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.history.changes.DefaultExecutionStateChangeDetector;
import org.gradle.internal.execution.history.changes.ExecutionStateChangeDetector;
import org.gradle.internal.filewatch.DefaultFileWatcherFactory;
import org.gradle.internal.filewatch.FileWatcherFactory;
import org.gradle.internal.hash.DefaultStreamHasher;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.instantiation.DefaultInstantiatorFactory;
import org.gradle.internal.instantiation.InjectAnnotationHandler;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.DefaultBuildOperationListenerManager;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.remote.MessagingServer;
import org.gradle.internal.remote.services.MessagingServices;
import org.gradle.internal.resources.DefaultResourceLockCoordinationService;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.service.CachingServiceLocator;
import org.gradle.internal.service.DefaultServiceLocator;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.model.internal.inspect.MethodModelRuleExtractor;
import org.gradle.model.internal.inspect.MethodModelRuleExtractors;
import org.gradle.model.internal.inspect.ModelRuleExtractor;
import org.gradle.model.internal.inspect.ModelRuleSourceDetector;
import org.gradle.model.internal.manage.binding.DefaultStructBindingsStore;
import org.gradle.model.internal.manage.binding.StructBindingsStore;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaExtractor;
import org.gradle.model.internal.manage.schema.extract.DefaultModelSchemaStore;
import org.gradle.model.internal.manage.schema.extract.ModelSchemaAspectExtractionStrategy;
import org.gradle.model.internal.manage.schema.extract.ModelSchemaAspectExtractor;
import org.gradle.model.internal.manage.schema.extract.ModelSchemaExtractionStrategy;
import org.gradle.model.internal.manage.schema.extract.ModelSchemaExtractor;
import org.gradle.process.internal.health.memory.DefaultJvmMemoryInfo;
import org.gradle.process.internal.health.memory.DefaultMemoryManager;
import org.gradle.process.internal.health.memory.DefaultOsMemoryInfo;
import org.gradle.process.internal.health.memory.JvmMemoryInfo;
import org.gradle.process.internal.health.memory.MemoryManager;
import org.gradle.process.internal.health.memory.OsMemoryInfo;

import java.util.List;

/**
 * Defines the extended global services of a given process. This includes the CLI, daemon and tooling API provider. The CLI
 * only needs these advances services if it is running in --no-daemon mode.
 */
public class GlobalScopeServices extends WorkerSharedGlobalScopeServices {

    private GradleBuildEnvironment environment;

    public GlobalScopeServices(final boolean longLiving) {
        this(longLiving, ClassPath.EMPTY);
    }

    public GlobalScopeServices(final boolean longLiving, ClassPath additionalModuleClassPath) {
        super(additionalModuleClassPath);
        this.environment = new GradleBuildEnvironment() {
            @Override
            public boolean isLongLivingProcess() {
                return longLiving;
            }
        };
    }

    void configure(ServiceRegistration registration, ClassLoaderRegistry classLoaderRegistry) {
        final List<PluginServiceRegistry> pluginServiceFactories = new DefaultServiceLocator(classLoaderRegistry.getRuntimeClassLoader(), classLoaderRegistry.getPluginsClassLoader()).getAll(PluginServiceRegistry.class);
        for (PluginServiceRegistry pluginServiceRegistry : pluginServiceFactories) {
            registration.add(PluginServiceRegistry.class, pluginServiceRegistry);
            pluginServiceRegistry.registerGlobalServices(registration);
        }
    }

    ResourceLockCoordinationService createWorkerLeaseCoordinationService() {
        return new DefaultResourceLockCoordinationService();
    }

    CurrentBuildOperationRef createCurrentBuildOperationRef() {
        return CurrentBuildOperationRef.instance();
    }

    BuildOperationListenerManager createBuildOperationService() {
        return new DefaultBuildOperationListenerManager();
    }

    TemporaryFileProvider createTemporaryFileProvider() {
        return new TmpDirTemporaryFileProvider();
    }

    GradleBuildEnvironment createGradleBuildEnvironment() {
        return environment;
    }

    CommandLineConverter<StartParameterInternal> createCommandLine2StartParameterConverter() {
        return new DefaultCommandLineConverter();
    }

    CachingServiceLocator createPluginsServiceLocator(ClassLoaderRegistry registry) {
        return CachingServiceLocator.of(
            new DefaultServiceLocator(registry.getPluginsClassLoader())
        );
    }

    JdkToolsInitializer createJdkToolsInitializer() {
        return new DefaultJdkToolsInitializer(new DefaultClassLoaderFactory());
    }

    MessagingServer createMessagingServer(MessagingServices messagingServices) {
        return messagingServices.get(MessagingServer.class);
    }

    Instantiator createInstantiator(InstantiatorFactory instantiatorFactory) {
        return instantiatorFactory.decorateLenient();
    }

    InMemoryCacheDecoratorFactory createInMemoryTaskArtifactCache(CrossBuildInMemoryCacheFactory cacheFactory) {
        return new InMemoryCacheDecoratorFactory(environment.isLongLivingProcess(), cacheFactory);
    }

    DirectoryFileTreeFactory createDirectoryFileTreeFactory(Factory<PatternSet> patternSetFactory, FileSystem fileSystem) {
        return new DefaultDirectoryFileTreeFactory(patternSetFactory, fileSystem);
    }

    ModelRuleExtractor createModelRuleInspector(List<MethodModelRuleExtractor> extractors, ModelSchemaStore modelSchemaStore, StructBindingsStore structBindingsStore, ManagedProxyFactory managedProxyFactory) {
        List<MethodModelRuleExtractor> coreExtractors = MethodModelRuleExtractors.coreExtractors(modelSchemaStore);
        return new ModelRuleExtractor(Iterables.concat(coreExtractors, extractors), managedProxyFactory, modelSchemaStore, structBindingsStore);
    }

    protected ModelSchemaAspectExtractor createModelSchemaAspectExtractor(List<ModelSchemaAspectExtractionStrategy> strategies) {
        return new ModelSchemaAspectExtractor(strategies);
    }

    protected ManagedProxyFactory createManagedProxyFactory() {
        return new ManagedProxyFactory();
    }

    protected ModelSchemaExtractor createModelSchemaExtractor(ModelSchemaAspectExtractor aspectExtractor, List<ModelSchemaExtractionStrategy> strategies) {
        return DefaultModelSchemaExtractor.withDefaultStrategies(strategies, aspectExtractor);
    }

    protected ModelSchemaStore createModelSchemaStore(ModelSchemaExtractor modelSchemaExtractor) {
        return new DefaultModelSchemaStore(modelSchemaExtractor);
    }

    protected StructBindingsStore createStructBindingsStore(ModelSchemaStore schemaStore) {
        return new DefaultStructBindingsStore(schemaStore);
    }

    protected ModelRuleSourceDetector createModelRuleSourceDetector() {
        return new ModelRuleSourceDetector();
    }

    protected ImportsReader createImportsReader() {
        return new DefaultImportsReader();
    }

    FileWatcherFactory createFileWatcherFactory(ExecutorFactory executorFactory, FileSystem fileSystem) {
        return new DefaultFileWatcherFactory(executorFactory, fileSystem);
    }

    StringInterner createStringInterner() {
        return new StringInterner();
    }

    InstantiatorFactory createInstantiatorFactory(CrossBuildInMemoryCacheFactory cacheFactory, List<InjectAnnotationHandler> annotationHandlers) {
        return new DefaultInstantiatorFactory(cacheFactory, annotationHandlers);
    }

    GradleUserHomeScopeServiceRegistry createGradleUserHomeScopeServiceRegistry(ServiceRegistry globalServices) {
        return new DefaultGradleUserHomeScopeServiceRegistry(globalServices, new GradleUserHomeScopeServices(globalServices));
    }

    OsMemoryInfo createOsMemoryInfo() {
        return new DefaultOsMemoryInfo();
    }

    JvmMemoryInfo createJvmMemoryInfo() {
        return new DefaultJvmMemoryInfo();
    }

    MemoryManager createMemoryManager(OsMemoryInfo osMemoryInfo, JvmMemoryInfo jvmMemoryInfo, ListenerManager listenerManager, ExecutorFactory executorFactory) {
        return new DefaultMemoryManager(osMemoryInfo, jvmMemoryInfo, listenerManager, executorFactory);
    }

    FilePropertyFactory createFilePropertyFactory(FileResolver fileResolver) {
        return new DefaultFilePropertyFactory(fileResolver);
    }

    ObjectFactory createObjectFactory(InstantiatorFactory instantiatorFactory, ServiceRegistry services, FileResolver fileResolver, DirectoryFileTreeFactory directoryFileTreeFactory, FileCollectionFactory fileCollectionFactory, DomainObjectCollectionFactory domainObjectCollectionFactory) {
        return new DefaultObjectFactory(
            instantiatorFactory.injectAndDecorate(services),
            NamedObjectInstantiator.INSTANCE,
            fileResolver,
            directoryFileTreeFactory,
            new DefaultFilePropertyFactory(fileResolver),
            fileCollectionFactory,
            domainObjectCollectionFactory);
    }

    DomainObjectCollectionFactory createDomainObjectCollectionFactory(InstantiatorFactory instantiatorFactory, ServiceRegistry services) {
        return new DefaultDomainObjectCollectionFactory(instantiatorFactory, services, CollectionCallbackActionDecorator.NOOP, MutationGuards.identity());
    }

    ProviderFactory createProviderFactory() {
        return new DefaultProviderFactory();
    }

    BuildLayoutFactory createBuildLayoutFactory() {
        return new BuildLayoutFactory();
    }

    TaskInputsListener createTaskInputsListener() {
        return new DefaultTaskInputsListener();
    }

    ParallelismConfigurationManager createMaxWorkersManager(ListenerManager listenerManager) {
        return new DefaultParallelismConfigurationManager(listenerManager);
    }

    @Override
    PatternSpecFactory createPatternSpecFactory() {
        return new CachingPatternSpecFactory();
    }

    LoggingManagerInternal createLoggingManager(Factory<LoggingManagerInternal> loggingManagerFactory) {
        return loggingManagerFactory.create();
    }

    StreamHasher createStreamHasher() {
        return new DefaultStreamHasher();
    }

    ExecutionStateChangeDetector createExecutionStateChangeDetector() {
        return new DefaultExecutionStateChangeDetector();
    }
}
