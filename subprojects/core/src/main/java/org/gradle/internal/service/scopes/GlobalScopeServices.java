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
import org.gradle.StartParameter;
import org.gradle.api.internal.AsmBackedClassGenerator;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.ClassGeneratorBackedInstantiator;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DefaultClassPathProvider;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.internal.DependencyInjectingInstantiator;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.DynamicModulesClassPathProvider;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.CachingFileHasher;
import org.gradle.api.internal.changedetection.state.InMemoryTaskArtifactCache;
import org.gradle.api.internal.changedetection.state.ShortLivedProcessInMemoryTaskArtifactCache;
import org.gradle.api.internal.classpath.DefaultModuleRegistry;
import org.gradle.api.internal.classpath.DefaultPluginModuleRegistry;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.classpath.PluginModuleRegistry;
import org.gradle.api.internal.file.DefaultFileCollectionFactory;
import org.gradle.api.internal.file.DefaultFileLookup;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.file.TmpDirTemporaryFileProvider;
import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.hash.DefaultFileHasher;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache;
import org.gradle.api.internal.initialization.loadercache.DefaultClassLoaderCache;
import org.gradle.api.internal.initialization.loadercache.HashClassPathSnapshotter;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.api.tasks.util.internal.CachingPatternSpecFactory;
import org.gradle.api.tasks.util.internal.PatternSets;
import org.gradle.api.tasks.util.internal.PatternSpecFactory;
import org.gradle.cache.internal.CacheFactory;
import org.gradle.cache.internal.DefaultCacheFactory;
import org.gradle.cache.internal.DefaultFileLockManager;
import org.gradle.cache.internal.DefaultProcessMetaDataProvider;
import org.gradle.cache.internal.FileLockManager;
import org.gradle.cache.internal.MapBackedInMemoryStore;
import org.gradle.cache.internal.locklistener.DefaultFileLockContentionHandler;
import org.gradle.cache.internal.locklistener.FileLockContentionHandler;
import org.gradle.cli.CommandLineConverter;
import org.gradle.configuration.DefaultImportsReader;
import org.gradle.configuration.ImportsReader;
import org.gradle.groovy.scripts.internal.CrossBuildInMemoryCachingScriptClassCache;
import org.gradle.groovy.scripts.internal.RegistryAwareClassLoaderHierarchyHasher;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.initialization.DefaultClassLoaderRegistry;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.initialization.DefaultGradleLauncherFactory;
import org.gradle.initialization.DefaultJdkToolsInitializer;
import org.gradle.initialization.DefaultLegacyTypesSupport;
import org.gradle.initialization.FlatClassLoaderRegistry;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.initialization.JdkToolsInitializer;
import org.gradle.initialization.LegacyTypesSupport;
import org.gradle.internal.Factory;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.classloader.ClassLoaderHasher;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.classloader.ClassPathSnapshotter;
import org.gradle.internal.classloader.DefaultHashingClassLoaderFactory;
import org.gradle.internal.classloader.HashingClassLoaderFactory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.environment.GradleBuildEnvironment;
import org.gradle.internal.event.DefaultListenerManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.filewatch.DefaultFileWatcherFactory;
import org.gradle.internal.filewatch.FileWatcherFactory;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.installation.GradleRuntimeShadedJarDetector;
import org.gradle.internal.jvm.inspection.CachingJvmVersionDetector;
import org.gradle.internal.jvm.inspection.DefaultJvmVersionDetector;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.progress.BuildOperationExecutor;
import org.gradle.internal.progress.DefaultBuildOperationExecutor;
import org.gradle.internal.progress.InternalBuildListener;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.remote.MessagingServer;
import org.gradle.internal.remote.internal.inet.InetAddressFactory;
import org.gradle.internal.remote.services.MessagingServices;
import org.gradle.internal.service.CachingServiceLocator;
import org.gradle.internal.service.DefaultServiceLocator;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.time.TimeProvider;
import org.gradle.internal.time.TrueTimeProvider;
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
import org.gradle.process.internal.DefaultExecActionFactory;
import org.gradle.process.internal.ExecHandleFactory;

import java.util.List;

/**
 * Defines the global services shared by all services in a given process. This includes the Gradle CLI, daemon and tooling API provider.
 */
public class GlobalScopeServices {
    private final ClassPath additionalModuleClassPath;

    private GradleBuildEnvironment environment;

    public GlobalScopeServices(final boolean longLiving) {
        this(longLiving, new DefaultClassPath());
    }

    public GlobalScopeServices(final boolean longLiving, ClassPath additionalModuleClassPath) {
        this.additionalModuleClassPath = additionalModuleClassPath;
        this.environment = new GradleBuildEnvironment() {
            public boolean isLongLivingProcess() {
                return longLiving;
            }
        };
    }

    void configure(ServiceRegistration registration, ClassLoaderRegistry classLoaderRegistry) {
        final List<PluginServiceRegistry> pluginServiceFactories = new DefaultServiceLocator(classLoaderRegistry.getRuntimeClassLoader(), classLoaderRegistry.getPluginsClassLoader()).getAll(PluginServiceRegistry.class);
        for (PluginServiceRegistry pluginServiceRegistry : pluginServiceFactories) {
            registration.add(PluginServiceRegistry.class, pluginServiceRegistry);
            if (pluginServiceRegistry instanceof GradleUserHomeScopePluginServices) {
                registration.add(GradleUserHomeScopePluginServices.class, (GradleUserHomeScopePluginServices) pluginServiceRegistry);
            }
            pluginServiceRegistry.registerGlobalServices(registration);
        }
    }

    GradleLauncherFactory createGradleLauncherFactory(ListenerManager listenerManager, ProgressLoggerFactory progressLoggerFactory, GradleUserHomeScopeServiceRegistry userHomeScopeServiceRegistry) {
        return new DefaultGradleLauncherFactory(listenerManager, progressLoggerFactory, userHomeScopeServiceRegistry);
    }

    BuildOperationExecutor createBuildOperationExecutor(ListenerManager listenerManager, TimeProvider timeProvider, ProgressLoggerFactory progressLoggerFactory) {
        return new DefaultBuildOperationExecutor(listenerManager.getBroadcaster(InternalBuildListener.class), timeProvider, progressLoggerFactory);
    }

    TemporaryFileProvider createTemporaryFileProvider() {
        return new TmpDirTemporaryFileProvider();
    }

    GradleBuildEnvironment createGradleBuildEnvironment() {
        return environment;
    }

    CommandLineConverter<StartParameter> createCommandLine2StartParameterConverter() {
        return new DefaultCommandLineConverter();
    }

    ClassPathRegistry createClassPathRegistry(ModuleRegistry moduleRegistry, PluginModuleRegistry pluginModuleRegistry) {
        return new DefaultClassPathRegistry(
            new DefaultClassPathProvider(moduleRegistry),
            new DynamicModulesClassPathProvider(moduleRegistry,
                pluginModuleRegistry));
    }

    ModuleRegistry createModuleRegistry(CurrentGradleInstallation currentGradleInstallation) {
        return new DefaultModuleRegistry(additionalModuleClassPath, currentGradleInstallation.getInstallation());
    }

    CurrentGradleInstallation createCurrentGradleInstallation() {
        return CurrentGradleInstallation.locate();
    }

    DocumentationRegistry createDocumentationRegistry() {
        return new DocumentationRegistry();
    }

    PluginModuleRegistry createPluginModuleRegistry(ModuleRegistry moduleRegistry) {
        return new DefaultPluginModuleRegistry(moduleRegistry);
    }

    JvmVersionDetector createJvmVersionDetector(ExecHandleFactory execHandleFactory) {
        return new CachingJvmVersionDetector(new DefaultJvmVersionDetector(execHandleFactory));
    }

    protected CacheFactory createCacheFactory(FileLockManager fileLockManager, ExecutorFactory executorFactory) {
        return new DefaultCacheFactory(fileLockManager, executorFactory);
    }

    ClassLoaderRegistry createClassLoaderRegistry(ClassPathRegistry classPathRegistry, LegacyTypesSupport legacyTypesSupport) {
        if (GradleRuntimeShadedJarDetector.isLoadedFrom(getClass())) {
            return new FlatClassLoaderRegistry(getClass().getClassLoader());
        }

        return new DefaultClassLoaderRegistry(classPathRegistry, legacyTypesSupport);
    }

    ClassLoaderHierarchyHasher createClassLoaderHierarchyHasher(ClassLoaderRegistry registry, ClassLoaderHasher classLoaderHasher) {
        return new RegistryAwareClassLoaderHierarchyHasher(registry, classLoaderHasher);
    }

    LegacyTypesSupport createLegacyTypesSupport() {
        return new DefaultLegacyTypesSupport();
    }

    CachingServiceLocator createPluginsServiceLocator(ClassLoaderRegistry registry) {
        return CachingServiceLocator.of(
            new DefaultServiceLocator(registry.getPluginsClassLoader())
        );
    }

    JdkToolsInitializer createJdkToolsInitializer(ClassLoaderFactory classLoaderFactory) {
        return new DefaultJdkToolsInitializer(classLoaderFactory);
    }

    ListenerManager createListenerManager() {
        return new DefaultListenerManager();
    }

    HashingClassLoaderFactory createClassLoaderFactory(ClassPathSnapshotter snapshotter) {
        return new DefaultHashingClassLoaderFactory(snapshotter);
    }

    MessagingServices createMessagingServices() {
        return new MessagingServices();
    }

    MessagingServer createMessagingServer(MessagingServices messagingServices) {
        return messagingServices.get(MessagingServer.class);
    }

    InetAddressFactory createInetAddressFactory(MessagingServices messagingServices) {
        return messagingServices.get(InetAddressFactory.class);
    }

    ClassGenerator createClassGenerator() {
        return new AsmBackedClassGenerator();
    }

    Instantiator createInstantiator(ClassGenerator classGenerator) {
        return new ClassGeneratorBackedInstantiator(classGenerator, DirectInstantiator.INSTANCE);
    }

    ExecutorFactory createExecutorFactory() {
        return new DefaultExecutorFactory();
    }

    FileLockManager createFileLockManager(ProcessEnvironment processEnvironment, FileLockContentionHandler fileLockContentionHandler) {
        return new DefaultFileLockManager(
            new DefaultProcessMetaDataProvider(
                processEnvironment),
            fileLockContentionHandler);
    }

    InMemoryTaskArtifactCache createInMemoryTaskArtifactCache() {
        if(environment.isLongLivingProcess()) {
            return new InMemoryTaskArtifactCache();
        } else {
            return new ShortLivedProcessInMemoryTaskArtifactCache();
        }
    }

    DefaultFileLockContentionHandler createFileLockContentionHandler(ExecutorFactory executorFactory, InetAddressFactory inetAddressFactory) {
        return new DefaultFileLockContentionHandler(
            executorFactory,
            inetAddressFactory);
    }

    FileResolver createFileResolver(FileLookup lookup) {
        return lookup.getFileResolver();
    }

    FileLookup createFileLookup(FileSystem fileSystem, Factory<PatternSet> patternSetFactory) {
        return new DefaultFileLookup(fileSystem, patternSetFactory);
    }

    DirectoryFileTreeFactory createDirectoryFileTreeFactory(Factory<PatternSet> patternSetFactory, FileSystem fileSystem) {
        return new DefaultDirectoryFileTreeFactory(patternSetFactory, fileSystem);
    }

    FileCollectionFactory createFileCollectionFactory() {
        return new DefaultFileCollectionFactory();
    }

    DefaultExecActionFactory createExecActionFactory(FileResolver fileResolver) {
        return new DefaultExecActionFactory(fileResolver);
    }

    ModelRuleExtractor createModelRuleInspector(ServiceRegistry services, ModelSchemaStore modelSchemaStore, StructBindingsStore structBindingsStore, ManagedProxyFactory managedProxyFactory) {
        List<MethodModelRuleExtractor> extractors = services.getAll(MethodModelRuleExtractor.class);
        List<MethodModelRuleExtractor> coreExtractors = MethodModelRuleExtractors.coreExtractors(modelSchemaStore);
        return new ModelRuleExtractor(Iterables.concat(coreExtractors, extractors), managedProxyFactory, modelSchemaStore, structBindingsStore);
    }

    ClassPathSnapshotter createClassPathSnapshotter(GradleBuildEnvironment environment, FileHasher hasher) {
        return new HashClassPathSnapshotter(hasher);
    }

    MapBackedInMemoryStore createInMemoryStore() {
        return new MapBackedInMemoryStore();
    }

    FileHasher createCachingFileHasher(StringInterner stringInterner, MapBackedInMemoryStore inMemoryStore) {
        return new CachingFileHasher(new DefaultFileHasher(), inMemoryStore, stringInterner);
    }

    ClassLoaderCache createClassLoaderCache(HashingClassLoaderFactory classLoaderFactory, ClassPathSnapshotter classPathSnapshotter) {
        return new DefaultClassLoaderCache(classLoaderFactory, classPathSnapshotter);
    }

    protected ModelSchemaAspectExtractor createModelSchemaAspectExtractor(ServiceRegistry serviceRegistry) {
        List<ModelSchemaAspectExtractionStrategy> strategies = serviceRegistry.getAll(ModelSchemaAspectExtractionStrategy.class);
        return new ModelSchemaAspectExtractor(strategies);
    }

    protected ManagedProxyFactory createManagedProxyFactory() {
        return new ManagedProxyFactory();
    }

    protected ModelSchemaExtractor createModelSchemaExtractor(ModelSchemaAspectExtractor aspectExtractor, ServiceRegistry serviceRegistry) {
        return DefaultModelSchemaExtractor.withDefaultStrategies(serviceRegistry.getAll(ModelSchemaExtractionStrategy.class), aspectExtractor);
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

    PatternSpecFactory createPatternSpecFactory(GradleBuildEnvironment environment) {
        return new CachingPatternSpecFactory();
    }

    protected Factory<PatternSet> createPatternSetFactory(final PatternSpecFactory patternSpecFactory) {
        return PatternSets.getPatternSetFactory(patternSpecFactory);
    }

    protected CrossBuildInMemoryCachingScriptClassCache createCachingScriptCompiler(FileHasher hasher) {
        return new CrossBuildInMemoryCachingScriptClassCache(hasher);
    }

    DependencyInjectingInstantiator.ConstructorCache createConstructorCache() {
        return new DependencyInjectingInstantiator.ConstructorCache();
    }

    GradleUserHomeScopeServiceRegistry createGradleUserHomeScopeServiceRegistry(ServiceRegistry globalServices) {
        return new DefaultGradleUserHomeScopeServiceRegistry(globalServices, new GradleUserHomeScopeServices(globalServices));
    }

    TimeProvider createTimeProvider() {
        return new TrueTimeProvider();
    }
}
