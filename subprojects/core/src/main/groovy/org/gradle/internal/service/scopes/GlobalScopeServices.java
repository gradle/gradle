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
import org.gradle.api.internal.*;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.CachingFileSnapshotter;
import org.gradle.api.internal.changedetection.state.InMemoryTaskArtifactCache;
import org.gradle.api.internal.classpath.*;
import org.gradle.api.internal.file.*;
import org.gradle.api.internal.hash.DefaultHasher;
import org.gradle.api.internal.initialization.loadercache.*;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.internal.*;
import org.gradle.cache.internal.locklistener.DefaultFileLockContentionHandler;
import org.gradle.cache.internal.locklistener.FileLockContentionHandler;
import org.gradle.cli.CommandLineConverter;
import org.gradle.configuration.DefaultImportsReader;
import org.gradle.configuration.ImportsReader;
import org.gradle.initialization.*;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.classloader.DefaultClassLoaderFactory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.environment.GradleBuildEnvironment;
import org.gradle.internal.event.DefaultListenerManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.filewatch.DefaultFileWatcherFactory;
import org.gradle.internal.filewatch.FileWatcherFactory;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceLocator;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.messaging.remote.MessagingServer;
import org.gradle.messaging.remote.internal.MessagingServices;
import org.gradle.messaging.remote.internal.inet.InetAddressFactory;
import org.gradle.model.internal.inspect.MethodModelRuleExtractor;
import org.gradle.model.internal.inspect.MethodModelRuleExtractors;
import org.gradle.model.internal.inspect.ModelRuleExtractor;
import org.gradle.model.internal.inspect.ModelRuleSourceDetector;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.extract.*;
import org.gradle.model.internal.persist.DefaultModelRegistryStore;
import org.gradle.model.internal.persist.ModelRegistryStore;

import java.util.List;

/**
 * Defines the global services shared by all services in a given process. This includes the Gradle CLI, daemon and tooling API provider.
 */
public class GlobalScopeServices {

    private static final Logger LOGGER = Logging.getLogger(GlobalScopeServices.class);
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
        final List<PluginServiceRegistry> pluginServiceFactories = new ServiceLocator(classLoaderRegistry.getRuntimeClassLoader(), classLoaderRegistry.getPluginsClassLoader()).getAll(PluginServiceRegistry.class);
        for (PluginServiceRegistry pluginServiceRegistry : pluginServiceFactories) {
            registration.add(PluginServiceRegistry.class, pluginServiceRegistry);
            pluginServiceRegistry.registerGlobalServices(registration);
        }
    }

    GradleLauncherFactory createGradleLauncherFactory(ServiceRegistry services) {
        return new DefaultGradleLauncherFactory(services);
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

    ModuleRegistry createModuleRegistry() {
        return new DefaultModuleRegistry(additionalModuleClassPath);
    }

    GradleDistributionLocator createGradleDistributionLocator() {
        return new DefaultGradleDistributionLocator();
    }

    DocumentationRegistry createDocumentationRegistry() {
        return new DocumentationRegistry();
    }

    PluginModuleRegistry createPluginModuleRegistry(ModuleRegistry moduleRegistry) {
        return new DefaultPluginModuleRegistry(moduleRegistry);
    }

    protected CacheFactory createCacheFactory(FileLockManager fileLockManager) {
        return new DefaultCacheFactory(fileLockManager);
    }

    DefaultClassLoaderRegistry createClassLoaderRegistry(ClassPathRegistry classPathRegistry, ClassLoaderFactory classLoaderFactory) {
        return new DefaultClassLoaderRegistry(classPathRegistry, classLoaderFactory);
    }

    ListenerManager createListenerManager() {
        return new DefaultListenerManager();
    }

    ClassLoaderFactory createClassLoaderFactory() {
        return new DefaultClassLoaderFactory();
    }

    MessagingServices createMessagingServices(ClassLoaderRegistry classLoaderRegistry) {
        return new MessagingServices(getClass().getClassLoader());
    }

    MessagingServer createMessagingServer(MessagingServices messagingServices) {
        return messagingServices.get(MessagingServer.class);
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
        return new InMemoryTaskArtifactCache();
    }

    DefaultFileLockContentionHandler createFileLockContentionHandler(ExecutorFactory executorFactory, MessagingServices messagingServices) {
        return new DefaultFileLockContentionHandler(
            executorFactory,
            messagingServices.get(InetAddressFactory.class)
        );
    }

    FileResolver createFileResolver(FileSystem fileSystem) {
        return new IdentityFileResolver(fileSystem);
    }

    FileLookup createFileLookup(FileSystem fileSystem) {
        return new DefaultFileLookup(fileSystem);
    }

    ModelRuleExtractor createModelRuleInspector(ServiceRegistry services, ModelSchemaStore modelSchemaStore) {
        List<MethodModelRuleExtractor> extractors = services.getAll(MethodModelRuleExtractor.class);
        List<MethodModelRuleExtractor> coreExtractors = MethodModelRuleExtractors.coreExtractors(modelSchemaStore);
        return new ModelRuleExtractor(Iterables.concat(coreExtractors, extractors));
    }

    ClassPathSnapshotter createClassPathSnapshotter(GradleBuildEnvironment environment, StringInterner stringInterner) {
        if (environment.isLongLivingProcess()) {
            CachingFileSnapshotter fileSnapshotter = new CachingFileSnapshotter(new DefaultHasher(), new NonThreadsafeInMemoryStore(), stringInterner);
            return new HashClassPathSnapshotter(fileSnapshotter);
        } else {
            return new FileClassPathSnapshotter();
        }
    }

    ClassLoaderCache createClassLoaderCache(ClassPathSnapshotter classPathSnapshotter) {
        return new DefaultClassLoaderCache(classPathSnapshotter);
    }

    protected ModelSchemaAspectExtractor createModelSchemaAspectExtractor(ServiceRegistry serviceRegistry) {
        List<ModelSchemaAspectExtractionStrategy> strategies = serviceRegistry.getAll(ModelSchemaAspectExtractionStrategy.class);
        return new ModelSchemaAspectExtractor(strategies);
    }

    protected ManagedProxyFactory createManagedProxyFactory() {
        return new ManagedProxyFactory();
    }

    protected ModelSchemaExtractor createModelSchemaExtractor(ModelSchemaAspectExtractor aspectExtractor, ServiceRegistry serviceRegistry) {
        List<ModelSchemaExtractionStrategy> strategies = serviceRegistry.getAll(ModelSchemaExtractionStrategy.class);
        return new ModelSchemaExtractor(strategies, aspectExtractor);
    }

    protected ModelSchemaStore createModelSchemaStore(ModelSchemaExtractor modelSchemaExtractor) {
        return new DefaultModelSchemaStore(modelSchemaExtractor);
    }

    protected ModelRuleSourceDetector createModelRuleSourceDetector() {
        return new ModelRuleSourceDetector();
    }

    protected ModelRegistryStore createModelRegistryStore(GradleBuildEnvironment buildEnvironment, ModelRuleExtractor ruleExtractor) {
        return new DefaultModelRegistryStore(ruleExtractor);
    }

    protected ImportsReader createImportsReader() {
        return new DefaultImportsReader();
    }

    FileWatcherFactory createFileWatcherFactory(ExecutorFactory executorFactory) {
        return new DefaultFileWatcherFactory(executorFactory);
    }

    StringInterner createStringInterner() {
        return new StringInterner();
    }
}
