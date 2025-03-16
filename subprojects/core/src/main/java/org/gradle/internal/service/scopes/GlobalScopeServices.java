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
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.DefaultClassPathProvider;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.internal.DynamicModulesClassPathProvider;
import org.gradle.api.internal.MutationGuards;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.classpath.DefaultPluginModuleRegistry;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.classpath.PluginModuleRegistry;
import org.gradle.api.internal.collections.DefaultDomainObjectCollectionFactory;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FilePropertyFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.internal.model.DefaultObjectFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.properties.annotations.AbstractOutputPropertyAnnotationHandler;
import org.gradle.api.internal.tasks.properties.annotations.OutputPropertyRoleAnnotationHandler;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.api.tasks.util.internal.CachingPatternSpecFactory;
import org.gradle.api.tasks.util.internal.PatternSpecFactory;
import org.gradle.cache.CacheCleanupStrategyFactory;
import org.gradle.cache.internal.CleaningInMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.cache.internal.DefaultCacheCleanupStrategyFactory;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.configuration.DefaultImportsReader;
import org.gradle.configuration.ImportsReader;
import org.gradle.execution.DefaultWorkValidationWarningRecorder;
import org.gradle.execution.WorkValidationWarningReporter;
import org.gradle.groovy.scripts.internal.DefaultScriptSourceHasher;
import org.gradle.groovy.scripts.internal.ScriptSourceHasher;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.initialization.DefaultClassLoaderRegistry;
import org.gradle.initialization.DefaultJdkToolsInitializer;
import org.gradle.initialization.FlatClassLoaderRegistry;
import org.gradle.initialization.JdkToolsInitializer;
import org.gradle.initialization.LegacyTypesSupport;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.Factory;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.environment.GradleBuildEnvironment;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.history.OverlappingOutputDetector;
import org.gradle.internal.execution.history.changes.DefaultExecutionStateChangeDetector;
import org.gradle.internal.execution.history.changes.ExecutionStateChangeDetector;
import org.gradle.internal.execution.history.impl.DefaultOverlappingOutputDetector;
import org.gradle.internal.execution.steps.ValidateStep;
import org.gradle.internal.installation.GradleRuntimeShadedJarDetector;
import org.gradle.internal.instantiation.InjectAnnotationHandler;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.instantiation.generator.DefaultInstantiatorFactory;
import org.gradle.internal.instrumentation.agent.AgentInitializer;
import org.gradle.internal.instrumentation.agent.AgentStatus;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.operations.DefaultBuildOperationProgressEventEmitter;
import org.gradle.internal.problems.failure.DefaultFailureFactory;
import org.gradle.internal.problems.failure.FailureFactory;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.scripts.DefaultScriptFileResolver;
import org.gradle.internal.scripts.DefaultScriptFileResolverListeners;
import org.gradle.internal.scripts.ScriptFileResolvedListener;
import org.gradle.internal.scripts.ScriptFileResolver;
import org.gradle.internal.scripts.ScriptFileResolverListeners;
import org.gradle.internal.service.CachingServiceLocator;
import org.gradle.internal.service.DefaultServiceLocator;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.time.Clock;
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
import org.gradle.process.internal.ExecFactory;
import org.gradle.process.internal.health.memory.DefaultJvmMemoryInfo;
import org.gradle.process.internal.health.memory.DefaultMemoryManager;
import org.gradle.process.internal.health.memory.DefaultOsMemoryInfo;
import org.gradle.process.internal.health.memory.JvmMemoryInfo;
import org.gradle.process.internal.health.memory.MemoryManager;
import org.gradle.process.internal.health.memory.OsMemoryInfo;

import java.util.List;

/**
 * Defines the extended global services of a given process. This includes the CLI, daemon and tooling API provider. The CLI
 * only needs these services if it is running in --no-daemon mode.
 *
 * <p>Do not use this type directly, but instead use it via {@link BuildProcessState}.</p>
 */
public class GlobalScopeServices extends WorkerSharedGlobalScopeServices {

    private final GradleBuildEnvironment environment;
    private final AgentStatus agentStatus;

    public GlobalScopeServices(final boolean longLiving, AgentStatus agentStatus) {
        this(longLiving, agentStatus, ClassPath.EMPTY);
    }

    public GlobalScopeServices(final boolean longLiving, AgentStatus agentStatus, ClassPath additionalModuleClassPath) {
        super(additionalModuleClassPath);
        this.agentStatus = agentStatus;
        this.environment = () -> longLiving;
    }

    @Override
    void configure(ServiceRegistration registration) {
        super.configure(registration);
        registration.add(ScriptFileResolvedListener.class, ScriptFileResolverListeners.class, DefaultScriptFileResolverListeners.class);
        registration.add(BuildLayoutFactory.class);
        registration.add(ValidateStep.ValidationWarningRecorder.class, WorkValidationWarningReporter.class, DefaultWorkValidationWarningRecorder.class);
    }

    @Provides
    ScriptFileResolver createScriptFileResolver(ScriptFileResolvedListener listener) {
        return new DefaultScriptFileResolver(listener);
    }

    @Provides
    protected BuildOperationProgressEventEmitter createBuildOperationProgressEventEmitter(
        Clock clock,
        CurrentBuildOperationRef currentBuildOperationRef,
        BuildOperationListenerManager listenerManager
    ) {
        return new DefaultBuildOperationProgressEventEmitter(
            clock,
            currentBuildOperationRef,
            listenerManager.getBroadcaster()
        );
    }

    @Provides
    GradleBuildEnvironment createGradleBuildEnvironment() {
        return environment;
    }

    @Provides
    CachingServiceLocator createPluginsServiceLocator(ClassLoaderRegistry registry) {
        return CachingServiceLocator.of(
            new DefaultServiceLocator(registry.getPluginsClassLoader())
        );
    }

    @Provides
    JdkToolsInitializer createJdkToolsInitializer(ClassLoaderFactory classLoaderFactory) {
        return new DefaultJdkToolsInitializer(classLoaderFactory);
    }

    @Provides
    InstanceGenerator createInstantiator(InstantiatorFactory instantiatorFactory) {
        return instantiatorFactory.decorateLenient();
    }

    @Provides
    InMemoryCacheDecoratorFactory createInMemoryTaskArtifactCache(CrossBuildInMemoryCacheFactory cacheFactory) {
        return new CleaningInMemoryCacheDecoratorFactory(environment.isLongLivingProcess(), cacheFactory);
    }

    @Provides
    CacheCleanupStrategyFactory createCacheCleanupStrategyFactory(BuildOperationRunner buildOperationRunner) {
        return new DefaultCacheCleanupStrategyFactory(buildOperationRunner);
    }

    @Provides
    ModelRuleExtractor createModelRuleInspector(List<MethodModelRuleExtractor> extractors, ModelSchemaStore modelSchemaStore, StructBindingsStore structBindingsStore, ManagedProxyFactory managedProxyFactory) {
        List<MethodModelRuleExtractor> coreExtractors = MethodModelRuleExtractors.coreExtractors(modelSchemaStore);
        return new ModelRuleExtractor(Iterables.concat(coreExtractors, extractors), managedProxyFactory, modelSchemaStore, structBindingsStore);
    }

    @Provides
    protected ModelSchemaAspectExtractor createModelSchemaAspectExtractor(List<ModelSchemaAspectExtractionStrategy> strategies) {
        return new ModelSchemaAspectExtractor(strategies);
    }

    @Provides
    protected ManagedProxyFactory createManagedProxyFactory() {
        return new ManagedProxyFactory();
    }

    @Provides
    protected ModelSchemaExtractor createModelSchemaExtractor(ModelSchemaAspectExtractor aspectExtractor, List<ModelSchemaExtractionStrategy> strategies) {
        return DefaultModelSchemaExtractor.withDefaultStrategies(strategies, aspectExtractor);
    }

    @Provides
    protected ModelSchemaStore createModelSchemaStore(ModelSchemaExtractor modelSchemaExtractor) {
        return new DefaultModelSchemaStore(modelSchemaExtractor);
    }

    @Provides
    protected StructBindingsStore createStructBindingsStore(ModelSchemaStore schemaStore) {
        return new DefaultStructBindingsStore(schemaStore);
    }

    @Provides
    protected ModelRuleSourceDetector createModelRuleSourceDetector() {
        return new ModelRuleSourceDetector();
    }

    @Provides
    protected ImportsReader createImportsReader() {
        return new DefaultImportsReader();
    }

    @Provides
    StringInterner createStringInterner() {
        return new StringInterner();
    }

    @Provides
    InstantiatorFactory createInstantiatorFactory(CrossBuildInMemoryCacheFactory cacheFactory, List<InjectAnnotationHandler> injectHandlers, List<AbstractOutputPropertyAnnotationHandler> outputHandlers) {
        return new DefaultInstantiatorFactory(cacheFactory, injectHandlers, new OutputPropertyRoleAnnotationHandler(outputHandlers));
    }

    @Provides
    GradleUserHomeScopeServiceRegistry createGradleUserHomeScopeServiceRegistry(ServiceRegistry globalServices) {
        return new DefaultGradleUserHomeScopeServiceRegistry(globalServices, new GradleUserHomeScopeServices(globalServices));
    }

    @Provides
    OsMemoryInfo createOsMemoryInfo() {
        return new DefaultOsMemoryInfo();
    }

    @Provides
    JvmMemoryInfo createJvmMemoryInfo() {
        return new DefaultJvmMemoryInfo();
    }

    @Provides
    MemoryManager createMemoryManager(OsMemoryInfo osMemoryInfo, JvmMemoryInfo jvmMemoryInfo, ListenerManager listenerManager, ExecutorFactory executorFactory) {
        return new DefaultMemoryManager(osMemoryInfo, jvmMemoryInfo, listenerManager, executorFactory);
    }

    @Provides
    ObjectFactory createObjectFactory(
        InstantiatorFactory instantiatorFactory, ServiceRegistry services, DirectoryFileTreeFactory directoryFileTreeFactory, Factory<PatternSet> patternSetFactory,
        PropertyFactory propertyFactory, FilePropertyFactory filePropertyFactory, TaskDependencyFactory taskDependencyFactory, FileCollectionFactory fileCollectionFactory,
        DomainObjectCollectionFactory domainObjectCollectionFactory, NamedObjectInstantiator instantiator
    ) {
        return new DefaultObjectFactory(
            instantiatorFactory.decorate(services),
            instantiator,
            directoryFileTreeFactory,
            patternSetFactory,
            propertyFactory,
            filePropertyFactory,
            taskDependencyFactory,
            fileCollectionFactory,
            domainObjectCollectionFactory);
    }

    @Provides
    ExecFactory createExecFactory(
        FileResolver fileResolver,
        FileCollectionFactory fileCollectionFactory,
        Instantiator instantiator,
        ObjectFactory objectFactory,
        ExecutorFactory executorFactory,
        TemporaryFileProvider temporaryFileProvider,
        BuildCancellationToken buildCancellationToken
    ) {
        return DefaultExecActionFactory.of(
            fileResolver,
            fileCollectionFactory,
            instantiator,
            executorFactory,
            temporaryFileProvider,
            buildCancellationToken,
            objectFactory
        );
    }

    @Provides
    DomainObjectCollectionFactory createDomainObjectCollectionFactory(InstantiatorFactory instantiatorFactory, ServiceRegistry services) {
        return new DefaultDomainObjectCollectionFactory(instantiatorFactory, services, CollectionCallbackActionDecorator.NOOP, MutationGuards.identity());
    }

    @Provides
    @Override
    PatternSpecFactory createPatternSpecFactory(ListenerManager listenerManager) {
        PatternSpecFactory patternSpecFactory = new CachingPatternSpecFactory();
        listenerManager.addListener(patternSpecFactory);
        return patternSpecFactory;
    }

    @Provides
    LoggingManagerInternal createLoggingManager(Factory<LoggingManagerInternal> loggingManagerFactory) {
        return loggingManagerFactory.create();
    }

    @Provides
    ExecutionStateChangeDetector createExecutionStateChangeDetector() {
        return new DefaultExecutionStateChangeDetector();
    }

    @Provides
    ClassPathRegistry createClassPathRegistry(ModuleRegistry moduleRegistry, PluginModuleRegistry pluginModuleRegistry) {
        return new DefaultClassPathRegistry(
            new DefaultClassPathProvider(moduleRegistry),
            new DynamicModulesClassPathProvider(moduleRegistry,
                pluginModuleRegistry));
    }

    @Provides
    PluginModuleRegistry createPluginModuleRegistry(ModuleRegistry moduleRegistry) {
        return new DefaultPluginModuleRegistry(moduleRegistry);
    }

    @Provides
    ClassLoaderRegistry createClassLoaderRegistry(ClassPathRegistry classPathRegistry, LegacyTypesSupport legacyTypesSupport) {
        if (GradleRuntimeShadedJarDetector.isLoadedFrom(getClass())) {
            return new FlatClassLoaderRegistry(getClass().getClassLoader());
        }

        // Use DirectInstantiator here to avoid setting up the instantiation infrastructure early
        return new DefaultClassLoaderRegistry(classPathRegistry, legacyTypesSupport, DirectInstantiator.INSTANCE);
    }

    @Provides
    OverlappingOutputDetector createOverlappingOutputDetector() {
        return new DefaultOverlappingOutputDetector();
    }

    @Provides
    AgentStatus createAgentStatus() {
        return agentStatus;
    }

    @Provides
    AgentInitializer createAgentInitializer() {
        return new AgentInitializer(agentStatus);
    }

    @Provides
    FailureFactory createFailureFactory() {
        return DefaultFailureFactory.withDefaultClassifier();
    }

    @Provides
    ScriptSourceHasher createScriptSourceHasher() {
        return new DefaultScriptSourceHasher();
    }
}
