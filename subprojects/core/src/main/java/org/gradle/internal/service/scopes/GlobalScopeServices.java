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
import org.gradle.api.internal.AsmBackedClassGenerator;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DefaultClassPathProvider;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.internal.DefaultInstantiatorFactory;
import org.gradle.api.internal.DynamicModulesClassPathProvider;
import org.gradle.api.internal.InstantiatorFactory;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory;
import org.gradle.api.internal.classpath.DefaultModuleRegistry;
import org.gradle.api.internal.classpath.DefaultPluginModuleRegistry;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.classpath.PluginModuleRegistry;
import org.gradle.api.internal.file.DefaultFileCollectionFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
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
import org.gradle.cache.FileLockManager;
import org.gradle.cache.internal.CacheFactory;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.cache.internal.DefaultCacheFactory;
import org.gradle.cli.CommandLineConverter;
import org.gradle.configuration.DefaultImportsReader;
import org.gradle.configuration.ImportsReader;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.initialization.DefaultClassLoaderRegistry;
import org.gradle.initialization.DefaultCommandLineConverter;
import org.gradle.initialization.DefaultGradleLauncherFactory;
import org.gradle.initialization.DefaultJdkToolsInitializer;
import org.gradle.initialization.DefaultLegacyTypesSupport;
import org.gradle.initialization.DefaultParallelismConfigurationManager;
import org.gradle.initialization.FlatClassLoaderRegistry;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.initialization.JdkToolsInitializer;
import org.gradle.initialization.LegacyTypesSupport;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.Factory;
import org.gradle.internal.classloader.DefaultClassLoaderFactory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.ParallelismConfigurationManager;
import org.gradle.internal.environment.GradleBuildEnvironment;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.filewatch.DefaultFileWatcherFactory;
import org.gradle.internal.filewatch.FileWatcherFactory;
import org.gradle.internal.hash.ContentHasherFactory;
import org.gradle.internal.hash.DefaultContentHasherFactory;
import org.gradle.internal.hash.DefaultStreamHasher;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.installation.CurrentGradleInstallation;
import org.gradle.internal.installation.GradleRuntimeShadedJarDetector;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.operations.DefaultBuildOperationIdFactory;
import org.gradle.internal.progress.BuildOperationListenerManager;
import org.gradle.internal.progress.DefaultBuildOperationListenerManager;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.remote.MessagingServer;
import org.gradle.internal.remote.services.MessagingServices;
import org.gradle.internal.scripts.DefaultScriptFileResolver;
import org.gradle.internal.scripts.DefaultScriptingLanguages;
import org.gradle.internal.scripts.ScriptFileResolver;
import org.gradle.internal.scripts.ScriptingLanguages;
import org.gradle.internal.service.CachingServiceLocator;
import org.gradle.internal.service.DefaultServiceLocator;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.time.Clock;
import org.gradle.internal.time.Time;
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
public class GlobalScopeServices extends BasicGlobalScopeServices {
    private final ClassPath additionalModuleClassPath;

    private GradleBuildEnvironment environment;

    public GlobalScopeServices(final boolean longLiving) {
        this(longLiving, ClassPath.EMPTY);
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
            pluginServiceRegistry.registerGlobalServices(registration);
        }
    }

    GradleLauncherFactory createGradleLauncherFactory(ListenerManager listenerManager, ProgressLoggerFactory progressLoggerFactory, GradleUserHomeScopeServiceRegistry userHomeScopeServiceRegistry) {
        return new DefaultGradleLauncherFactory(listenerManager, progressLoggerFactory, userHomeScopeServiceRegistry);
    }

    BuildOperationListenerManager createBuildOperationService(ListenerManager listenerManager) {
        return new DefaultBuildOperationListenerManager(listenerManager);
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

    PluginModuleRegistry createPluginModuleRegistry(ModuleRegistry moduleRegistry) {
        return new DefaultPluginModuleRegistry(moduleRegistry);
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

    LegacyTypesSupport createLegacyTypesSupport() {
        return new DefaultLegacyTypesSupport();
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

    ClassGenerator createClassGenerator() {
        return new AsmBackedClassGenerator();
    }

    Instantiator createInstantiator(InstantiatorFactory instantiatorFactory) {
        return instantiatorFactory.decorate();
    }

    CrossBuildInMemoryCacheFactory createCrossBuildInMemoryCacheFactory(ListenerManager listenerManager) {
        return new CrossBuildInMemoryCacheFactory(listenerManager);
    }

    InMemoryCacheDecoratorFactory createInMemoryTaskArtifactCache(CrossBuildInMemoryCacheFactory cacheFactory) {
        return new InMemoryCacheDecoratorFactory(environment.isLongLivingProcess(), cacheFactory);
    }


    DirectoryFileTreeFactory createDirectoryFileTreeFactory(Factory<PatternSet> patternSetFactory, FileSystem fileSystem) {
        return new DefaultDirectoryFileTreeFactory(patternSetFactory, fileSystem);
    }

    FileCollectionFactory createFileCollectionFactory() {
        return new DefaultFileCollectionFactory();
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

    InstantiatorFactory createInstantiatorFactory(ClassGenerator classGenerator, CrossBuildInMemoryCacheFactory cacheFactory) {
        return new DefaultInstantiatorFactory(classGenerator, cacheFactory);
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

    ObjectFactory createObjectFactory(InstantiatorFactory instantiatorFactory, ServiceRegistry services) {
        return new DefaultObjectFactory(instantiatorFactory.injectAndDecorate(services), NamedObjectInstantiator.INSTANCE);
    }

    ProviderFactory createProviderFactory() {
        return new DefaultProviderFactory();
    }

    ScriptingLanguages createScriptingLanguages() {
        return DefaultScriptingLanguages.create();
    }

    ScriptFileResolver createScriptFileResolver(ScriptingLanguages scriptingLanguages) {
        return DefaultScriptFileResolver.forScriptingLanguages(scriptingLanguages);
    }

    BuildLayoutFactory createBuildLayoutFactory(ScriptFileResolver scriptFileResolver) {
        return new BuildLayoutFactory(scriptFileResolver);
    }

    TaskInputsListener createTaskInputsListener() {
        return new DefaultTaskInputsListener();
    }

    ParallelismConfigurationManager createMaxWorkersManager(ListenerManager listenerManager) {
        return new DefaultParallelismConfigurationManager(listenerManager);
    }

    PatternSpecFactory createPatternSpecFactory() {
        return new CachingPatternSpecFactory();
    }

    LoggingManagerInternal createLoggingManager(Factory<LoggingManagerInternal> loggingManagerFactory) {
        return loggingManagerFactory.create();
    }

    BuildOperationIdFactory createBuildOperationIdProvider() {
        return new DefaultBuildOperationIdFactory();
    }

    ContentHasherFactory createHasherFactory() {
        return new DefaultContentHasherFactory();
    }

    StreamHasher createStreamHasher(ContentHasherFactory hasherFactory) {
        return new DefaultStreamHasher(hasherFactory);
    }

    Clock createClock() {
        return Time.clock();
    }
}
