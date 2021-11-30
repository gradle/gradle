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

import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DefaultClassPathProvider;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.internal.DependencyClassPathProvider;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.artifacts.DefaultModule;
import org.gradle.api.internal.artifacts.DependencyManagementServices;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.classpath.PluginModuleRegistry;
import org.gradle.api.internal.component.ComponentTypeRegistry;
import org.gradle.api.internal.component.DefaultComponentTypeRegistry;
import org.gradle.api.internal.file.DefaultArchiveOperations;
import org.gradle.api.internal.file.DefaultFileOperations;
import org.gradle.api.internal.file.DefaultFileSystemOperations;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.internal.initialization.DefaultScriptClassPathResolver;
import org.gradle.api.internal.initialization.DefaultScriptHandlerFactory;
import org.gradle.api.internal.initialization.ScriptClassPathInitializer;
import org.gradle.api.internal.initialization.ScriptClassPathResolver;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.internal.plugins.DefaultPluginRegistry;
import org.gradle.api.internal.plugins.PluginInspector;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.internal.project.DefaultProjectRegistry;
import org.gradle.api.internal.project.DefaultProjectTaskLister;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.project.ProjectFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.project.ProjectTaskLister;
import org.gradle.api.internal.project.antbuilder.DefaultIsolatedAntBuilder;
import org.gradle.api.internal.project.taskfactory.AnnotationProcessingTaskFactory;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.internal.project.taskfactory.TaskClassInfoStore;
import org.gradle.api.internal.project.taskfactory.TaskFactory;
import org.gradle.api.internal.properties.GradleProperties;
import org.gradle.api.internal.provider.DefaultProviderFactory;
import org.gradle.api.internal.provider.DefaultValueSourceProviderFactory;
import org.gradle.api.internal.provider.ValueSourceProviderFactory;
import org.gradle.api.internal.provider.sources.process.ExecSpecFactory;
import org.gradle.api.internal.provider.sources.process.ProcessOutputProviderFactory;
import org.gradle.api.internal.resources.ApiTextResourceAdapter;
import org.gradle.api.internal.resources.DefaultResourceHandler;
import org.gradle.api.internal.tasks.TaskStatistics;
import org.gradle.api.internal.tasks.userinput.BuildScanUserInputHandler;
import org.gradle.api.internal.tasks.userinput.DefaultBuildScanUserInputHandler;
import org.gradle.api.internal.tasks.userinput.UserInputHandler;
import org.gradle.api.invocation.BuildInvocationDetails;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.internal.BuildScopeCacheDir;
import org.gradle.cache.internal.scopes.DefaultBuildScopedCache;
import org.gradle.cache.scopes.BuildScopedCache;
import org.gradle.cache.scopes.GlobalScopedCache;
import org.gradle.caching.internal.BuildCacheServices;
import org.gradle.configuration.BuildOperationFiringProjectsPreparer;
import org.gradle.configuration.BuildTreePreparingProjectsPreparer;
import org.gradle.configuration.CompileOperationFactory;
import org.gradle.configuration.DefaultInitScriptProcessor;
import org.gradle.configuration.DefaultProjectsPreparer;
import org.gradle.configuration.DefaultScriptPluginFactory;
import org.gradle.configuration.ImportsReader;
import org.gradle.configuration.ProjectsPreparer;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.configuration.ScriptPluginFactorySelector;
import org.gradle.configuration.internal.UserCodeApplicationContext;
import org.gradle.configuration.project.DefaultCompileOperationFactory;
import org.gradle.configuration.project.PluginsProjectConfigureActions;
import org.gradle.execution.CompositeAwareTaskSelector;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.execution.TaskNameResolver;
import org.gradle.execution.TaskPathProjectEvaluator;
import org.gradle.execution.TaskSelector;
import org.gradle.execution.plan.DefaultNodeValidator;
import org.gradle.execution.plan.ExecutionNodeAccessHierarchies;
import org.gradle.execution.plan.ExecutionPlanFactory;
import org.gradle.execution.plan.TaskDependencyResolver;
import org.gradle.execution.plan.TaskNodeDependencyResolver;
import org.gradle.execution.plan.TaskNodeFactory;
import org.gradle.execution.plan.WorkNodeDependencyResolver;
import org.gradle.groovy.scripts.DefaultScriptCompilerFactory;
import org.gradle.groovy.scripts.ScriptCompilerFactory;
import org.gradle.groovy.scripts.internal.BuildOperationBackedScriptCompilationHandler;
import org.gradle.groovy.scripts.internal.BuildScopeInMemoryCachingScriptClassCompiler;
import org.gradle.groovy.scripts.internal.CrossBuildInMemoryCachingScriptClassCache;
import org.gradle.groovy.scripts.internal.DefaultScriptCompilationHandler;
import org.gradle.groovy.scripts.internal.DefaultScriptRunnerFactory;
import org.gradle.groovy.scripts.internal.FileCacheBackedScriptClassCompiler;
import org.gradle.groovy.scripts.internal.ScriptRunnerFactory;
import org.gradle.initialization.BuildLoader;
import org.gradle.initialization.BuildOperationFiringSettingsPreparer;
import org.gradle.initialization.BuildOperationSettingsProcessor;
import org.gradle.initialization.ClassLoaderScopeRegistry;
import org.gradle.initialization.DefaultGradlePropertiesController;
import org.gradle.initialization.DefaultGradlePropertiesLoader;
import org.gradle.initialization.DefaultProjectDescriptorRegistry;
import org.gradle.initialization.DefaultSettingsLoaderFactory;
import org.gradle.initialization.DefaultSettingsPreparer;
import org.gradle.initialization.Environment;
import org.gradle.initialization.GradlePropertiesController;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.initialization.IGradlePropertiesLoader;
import org.gradle.initialization.InitScriptHandler;
import org.gradle.initialization.InstantiatingBuildLoader;
import org.gradle.initialization.ModelConfigurationListener;
import org.gradle.initialization.NotifyingBuildLoader;
import org.gradle.initialization.ProjectDescriptorRegistry;
import org.gradle.initialization.ProjectPropertySettingBuildLoader;
import org.gradle.initialization.RootBuildCacheControllerSettingsProcessor;
import org.gradle.initialization.ScriptEvaluatingSettingsProcessor;
import org.gradle.initialization.SettingsEvaluatedCallbackFiringSettingsProcessor;
import org.gradle.initialization.SettingsFactory;
import org.gradle.initialization.SettingsLoaderFactory;
import org.gradle.initialization.SettingsPreparer;
import org.gradle.initialization.SettingsProcessor;
import org.gradle.initialization.buildsrc.BuildSourceBuilder;
import org.gradle.initialization.buildsrc.BuildSrcBuildListenerFactory;
import org.gradle.initialization.buildsrc.BuildSrcProjectConfigurationAction;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.initialization.layout.BuildLayoutConfiguration;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.initialization.layout.ResolvedBuildLayout;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.actor.internal.DefaultActorFactory;
import org.gradle.internal.authentication.AuthenticationSchemeRegistry;
import org.gradle.internal.authentication.DefaultAuthenticationSchemeRegistry;
import org.gradle.internal.build.BuildModelControllerServices;
import org.gradle.internal.build.BuildOperationFiringBuildWorkPreparer;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.build.BuildStateRegistry;
import org.gradle.internal.build.BuildWorkPreparer;
import org.gradle.internal.build.DefaultBuildWorkGraphController;
import org.gradle.internal.build.DefaultBuildWorkPreparer;
import org.gradle.internal.build.DefaultPublicBuildPath;
import org.gradle.internal.build.PublicBuildPath;
import org.gradle.internal.buildevents.BuildStartedTime;
import org.gradle.internal.buildtree.BuildInclusionCoordinator;
import org.gradle.internal.buildtree.BuildModelParameters;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.composite.DefaultBuildIncluder;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.DefaultListenerManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.RelativeFilePathResolver;
import org.gradle.internal.file.Stat;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.invocation.DefaultBuildInvocationDetails;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.logging.BuildOperationLoggerFactory;
import org.gradle.internal.operations.logging.DefaultBuildOperationLoggerFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.DefaultTextFileResourceLoader;
import org.gradle.internal.resource.TextFileResourceLoader;
import org.gradle.internal.resource.local.FileResourceListener;
import org.gradle.internal.scripts.ScriptExecutionListener;
import org.gradle.internal.service.CachingServiceLocator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.model.internal.inspect.ModelRuleSourceDetector;
import org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginHandler;
import org.gradle.plugin.use.internal.PluginRequestApplicator;
import org.gradle.process.internal.DefaultExecOperations;
import org.gradle.process.internal.DefaultExecSpecFactory;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.process.internal.ExecFactory;
import org.gradle.tooling.provider.model.internal.BuildScopeToolingModelBuilderRegistryAction;
import org.gradle.tooling.provider.model.internal.DefaultToolingModelBuilderRegistry;

import java.util.List;

/**
 * Contains the singleton services for a single build invocation.
 */
public class BuildScopeServices extends DefaultServiceRegistry {

    public BuildScopeServices(ServiceRegistry parent, BuildModelControllerServices.Supplier supplier) {
        super(parent);
        addProvider(new BuildCacheServices());
        register(registration -> {
            registration.add(DefaultExecOperations.class);
            registration.add(DefaultFileOperations.class);
            registration.add(DefaultFileSystemOperations.class);
            registration.add(DefaultArchiveOperations.class);
            registration.add(TaskPathProjectEvaluator.class);
            registration.add(ProjectFactory.class);
            registration.add(DefaultSettingsLoaderFactory.class);
            registration.add(ResolvedBuildLayout.class);
            registration.add(TaskNodeFactory.class);
            registration.add(TaskNodeDependencyResolver.class);
            registration.add(WorkNodeDependencyResolver.class);
            registration.add(TaskDependencyResolver.class);
            registration.add(DefaultBuildWorkGraphController.class);
            registration.add(DefaultBuildIncluder.class);
            supplier.applyServicesTo(registration, this);
            for (PluginServiceRegistry pluginServiceRegistry : parent.getAll(PluginServiceRegistry.class)) {
                pluginServiceRegistry.registerBuildServices(registration);
            }
        });
    }

    ExecutionPlanFactory createExecutionPlanFactory(
        GradleInternal gradleInternal,
        TaskNodeFactory taskNodeFactory,
        TaskDependencyResolver dependencyResolver,
        ExecutionNodeAccessHierarchies executionNodeAccessHierarchies
    ) {
        return new ExecutionPlanFactory(
            gradleInternal.getIdentityPath().toString(),
            taskNodeFactory,
            dependencyResolver,
            new DefaultNodeValidator(),
            executionNodeAccessHierarchies.getOutputHierarchy(),
            executionNodeAccessHierarchies.getDestroyableHierarchy()
        );
    }

    ExecutionNodeAccessHierarchies createExecutionNodeAccessHierarchies(FileSystem fileSystem, Stat stat) {
        return new ExecutionNodeAccessHierarchies(fileSystem.isCaseSensitive() ? CaseSensitivity.CASE_SENSITIVE : CaseSensitivity.CASE_INSENSITIVE, stat);
    }

    protected BuildScopedCache createBuildScopedCache(
        GradleUserHomeDirProvider userHomeDirProvider,
        BuildLayout buildLayout,
        StartParameter startParameter,
        CacheRepository cacheRepository
    ) {
        BuildScopeCacheDir cacheDir = new BuildScopeCacheDir(userHomeDirProvider, buildLayout, startParameter);
        return new DefaultBuildScopedCache(cacheDir.getDir(), cacheRepository);
    }

    protected BuildLayout createBuildLayout(BuildLayoutFactory buildLayoutFactory, BuildDefinition buildDefinition) {
        return buildLayoutFactory.getLayoutFor(new BuildLayoutConfiguration(buildDefinition.getStartParameter()));
    }

    protected DefaultResourceHandler.Factory createResourceHandlerFactory(FileResolver fileResolver, FileSystem fileSystem, TemporaryFileProvider temporaryFileProvider, ApiTextResourceAdapter.Factory textResourceAdapterFactory) {
        return DefaultResourceHandler.Factory.from(
            fileResolver,
            fileSystem,
            temporaryFileProvider,
            textResourceAdapterFactory
        );
    }

    protected FileCollectionFactory decorateFileCollectionFactory(FileCollectionFactory fileCollectionFactory, FileResolver fileResolver) {
        return fileCollectionFactory.withResolver(fileResolver);
    }

    protected ExecFactory decorateExecFactory(ExecFactory parent, FileResolver fileResolver, FileCollectionFactory fileCollectionFactory, Instantiator instantiator, ObjectFactory objectFactory, JavaModuleDetector javaModuleDetector) {
        return parent.forContext(fileResolver, fileCollectionFactory, instantiator, objectFactory, javaModuleDetector);
    }

    protected PublicBuildPath createPublicBuildPath(BuildState buildState) {
        return new DefaultPublicBuildPath(buildState.getIdentityPath());
    }

    protected TaskStatistics createTaskStatistics() {
        return new TaskStatistics();
    }

    protected DefaultProjectRegistry<ProjectInternal> createProjectRegistry() {
        return new DefaultProjectRegistry<ProjectInternal>();
    }

    protected TextFileResourceLoader createTextFileResourceLoader(RelativeFilePathResolver resolver) {
        return new DefaultTextFileResourceLoader(resolver);
    }

    protected ProjectDescriptorRegistry createProjectDescriptorRegistry() {
        return new DefaultProjectDescriptorRegistry();
    }

    protected DefaultListenerManager createListenerManager(DefaultListenerManager listenerManager) {
        return listenerManager.createChild(Scopes.Build.class);
    }

    protected ClassPathRegistry createClassPathRegistry() {
        ModuleRegistry moduleRegistry = get(ModuleRegistry.class);
        return new DefaultClassPathRegistry(
            new DefaultClassPathProvider(moduleRegistry),
            new DependencyClassPathProvider(moduleRegistry, get(PluginModuleRegistry.class)));
    }

    protected IsolatedAntBuilder createIsolatedAntBuilder(ClassPathRegistry classPathRegistry, ClassLoaderFactory classLoaderFactory, ModuleRegistry moduleRegistry) {
        return new DefaultIsolatedAntBuilder(classPathRegistry, classLoaderFactory, moduleRegistry);
    }

    protected GradleProperties createGradleProperties(
        GradlePropertiesController gradlePropertiesController
    ) {
        return gradlePropertiesController.getGradleProperties();
    }

    protected GradlePropertiesController createGradlePropertiesController(
        IGradlePropertiesLoader propertiesLoader
    ) {
        return new DefaultGradlePropertiesController(propertiesLoader);
    }

    protected IGradlePropertiesLoader createGradlePropertiesLoader(
        Environment environment
    ) {
        return new DefaultGradlePropertiesLoader(
            (StartParameterInternal) get(StartParameter.class),
            environment
        );
    }

    protected ValueSourceProviderFactory createValueSourceProviderFactory(
        InstantiatorFactory instantiatorFactory,
        IsolatableFactory isolatableFactory,
        ServiceRegistry services,
        GradleProperties gradleProperties,
        ExecFactory execFactory,
        ListenerManager listenerManager
    ) {
        return new DefaultValueSourceProviderFactory(
            listenerManager,
            instantiatorFactory,
            isolatableFactory,
            gradleProperties,
            new DefaultExecOperations(execFactory),
            services
        );
    }

    protected ExecSpecFactory createExecSpecFactory(ExecActionFactory execActionFactory) {
        return new DefaultExecSpecFactory(execActionFactory);
    }

    protected ProcessOutputProviderFactory createProcessOutputProviderFactory(Instantiator instantiator, ExecSpecFactory execSpecFactory) {
        return new ProcessOutputProviderFactory(instantiator, execSpecFactory);
    }

    protected ProviderFactory createProviderFactory(
        Instantiator instantiator,
        ValueSourceProviderFactory valueSourceProviderFactory,
        ProcessOutputProviderFactory processOutputProviderFactory,
        ListenerManager listenerManager
    ) {
        return instantiator.newInstance(DefaultProviderFactory.class, valueSourceProviderFactory, processOutputProviderFactory, listenerManager);
    }

    protected ActorFactory createActorFactory() {
        return new DefaultActorFactory(get(ExecutorFactory.class));
    }

    protected BuildLoader createBuildLoader(
        GradleProperties gradleProperties,
        BuildOperationExecutor buildOperationExecutor,
        ListenerManager listenerManager
    ) {
        return new NotifyingBuildLoader(
            new ProjectPropertySettingBuildLoader(
                gradleProperties,
                new InstantiatingBuildLoader(),
                listenerManager.getBroadcaster(FileResourceListener.class)
            ),
            buildOperationExecutor
        );
    }

    protected ITaskFactory createITaskFactory(Instantiator instantiator, TaskClassInfoStore taskClassInfoStore) {
        return new AnnotationProcessingTaskFactory(
            instantiator,
            taskClassInfoStore,
            new TaskFactory());
    }

    protected ScriptCompilerFactory createScriptCompileFactory(
        FileCacheBackedScriptClassCompiler scriptCompiler,
        CrossBuildInMemoryCachingScriptClassCache cache,
        ScriptRunnerFactory scriptRunnerFactory
    ) {
        return new DefaultScriptCompilerFactory(
            new BuildScopeInMemoryCachingScriptClassCompiler(cache, scriptCompiler),
            scriptRunnerFactory
        );
    }

    protected FileCacheBackedScriptClassCompiler createFileCacheBackedScriptClassCompiler(
        BuildOperationExecutor buildOperationExecutor,
        GlobalScopedCache cacheRepository,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        DefaultScriptCompilationHandler scriptCompilationHandler,
        CachedClasspathTransformer classpathTransformer,
        ProgressLoggerFactory progressLoggerFactory
    ) {
        return new FileCacheBackedScriptClassCompiler(
            cacheRepository,
            new BuildOperationBackedScriptCompilationHandler(scriptCompilationHandler, buildOperationExecutor),
            progressLoggerFactory,
            classLoaderHierarchyHasher,
            classpathTransformer);
    }

    protected ScriptPluginFactory createScriptPluginFactory(InstantiatorFactory instantiatorFactory, BuildOperationExecutor buildOperationExecutor, UserCodeApplicationContext userCodeApplicationContext) {
        DefaultScriptPluginFactory defaultScriptPluginFactory = defaultScriptPluginFactory();
        ScriptPluginFactorySelector.ProviderInstantiator instantiator = ScriptPluginFactorySelector.defaultProviderInstantiatorFor(instantiatorFactory.inject(this));
        ScriptPluginFactorySelector scriptPluginFactorySelector = new ScriptPluginFactorySelector(defaultScriptPluginFactory, instantiator, buildOperationExecutor, userCodeApplicationContext);
        defaultScriptPluginFactory.setScriptPluginFactory(scriptPluginFactorySelector);
        return scriptPluginFactorySelector;
    }

    private DefaultScriptPluginFactory defaultScriptPluginFactory() {
        return new DefaultScriptPluginFactory(
            this,
            get(ScriptCompilerFactory.class),
            getFactory(LoggingManagerInternal.class),
            get(AutoAppliedPluginHandler.class),
            get(PluginRequestApplicator.class),
            get(CompileOperationFactory.class));
    }

    protected BuildSourceBuilder createBuildSourceBuilder(BuildState currentBuild, FileLockManager fileLockManager, BuildOperationExecutor buildOperationExecutor, CachedClasspathTransformer cachedClasspathTransformer, CachingServiceLocator cachingServiceLocator, BuildStateRegistry buildRegistry, PublicBuildPath publicBuildPath) {
        return new BuildSourceBuilder(
            currentBuild,
            fileLockManager,
            buildOperationExecutor,
            cachedClasspathTransformer,
            new BuildSrcBuildListenerFactory(
                PluginsProjectConfigureActions.of(
                    BuildSrcProjectConfigurationAction.class,
                    cachingServiceLocator)),
            buildRegistry,
            publicBuildPath);
    }

    protected InitScriptHandler createInitScriptHandler(ScriptPluginFactory scriptPluginFactory, ScriptHandlerFactory scriptHandlerFactory, BuildOperationExecutor buildOperationExecutor, TextFileResourceLoader resourceLoader) {
        return new InitScriptHandler(
            new DefaultInitScriptProcessor(
                scriptPluginFactory,
                scriptHandlerFactory
            ),
            buildOperationExecutor,
            resourceLoader
        );
    }

    protected SettingsProcessor createSettingsProcessor(
        ScriptPluginFactory scriptPluginFactory,
        ScriptHandlerFactory scriptHandlerFactory,
        Instantiator instantiator,
        ServiceRegistryFactory serviceRegistryFactory,
        GradleProperties gradleProperties,
        BuildOperationExecutor buildOperationExecutor,
        TextFileResourceLoader textFileResourceLoader
    ) {
        return new BuildOperationSettingsProcessor(
            new RootBuildCacheControllerSettingsProcessor(
                new SettingsEvaluatedCallbackFiringSettingsProcessor(
                    new ScriptEvaluatingSettingsProcessor(
                        scriptPluginFactory,
                        new SettingsFactory(
                            instantiator,
                            serviceRegistryFactory,
                            scriptHandlerFactory
                        ),
                        gradleProperties,
                        textFileResourceLoader
                    )
                )
            ),
            buildOperationExecutor
        );
    }

    protected SettingsPreparer createSettingsPreparer(SettingsLoaderFactory settingsLoaderFactory, BuildOperationExecutor buildOperationExecutor, BuildDefinition buildDefinition) {
        return new BuildOperationFiringSettingsPreparer(
            new DefaultSettingsPreparer(
                settingsLoaderFactory
            ),
            buildOperationExecutor,
            buildDefinition.getFromBuild());
    }

    protected ScriptClassPathResolver createScriptClassPathResolver(List<ScriptClassPathInitializer> initializers) {
        return new DefaultScriptClassPathResolver(initializers);
    }

    protected ScriptHandlerFactory createScriptHandlerFactory(DependencyManagementServices dependencyManagementServices, FileResolver fileResolver, FileCollectionFactory fileCollectionFactory, DependencyMetaDataProvider dependencyMetaDataProvider, ScriptClassPathResolver classPathResolver, NamedObjectInstantiator instantiator) {
        return new DefaultScriptHandlerFactory(
            dependencyManagementServices,
            fileResolver,
            fileCollectionFactory,
            dependencyMetaDataProvider,
            classPathResolver,
            instantiator);
    }

    protected ProjectsPreparer createBuildConfigurer(
        ProjectConfigurer projectConfigurer,
        BuildSourceBuilder buildSourceBuilder,
        BuildStateRegistry buildStateRegistry,
        BuildInclusionCoordinator inclusionCoordinator,
        BuildLoader buildLoader,
        ListenerManager listenerManager,
        BuildOperationExecutor buildOperationExecutor,
        BuildModelParameters buildModelParameters
    ) {
        ModelConfigurationListener modelConfigurationListener = listenerManager.getBroadcaster(ModelConfigurationListener.class);
        return new BuildOperationFiringProjectsPreparer(
            new BuildTreePreparingProjectsPreparer(
                new DefaultProjectsPreparer(
                    projectConfigurer,
                    buildModelParameters,
                    modelConfigurationListener,
                    buildOperationExecutor,
                    buildStateRegistry),
                buildLoader,
                inclusionCoordinator,
                buildSourceBuilder),
            buildOperationExecutor);
    }

    protected BuildWorkPreparer createWorkPreparer(BuildOperationExecutor buildOperationExecutor, ExecutionPlanFactory executionPlanFactory) {
        return new BuildOperationFiringBuildWorkPreparer(
            buildOperationExecutor,
            new DefaultBuildWorkPreparer(
                executionPlanFactory
            ));
    }

    protected TaskSelector createTaskSelector(GradleInternal gradle, BuildStateRegistry buildStateRegistry, ProjectConfigurer projectConfigurer) {
        return new CompositeAwareTaskSelector(gradle, buildStateRegistry, projectConfigurer, new TaskNameResolver());
    }

    protected PluginRegistry createPluginRegistry(ClassLoaderScopeRegistry scopeRegistry, PluginInspector pluginInspector) {
        return new DefaultPluginRegistry(pluginInspector, scopeRegistry.getCoreAndPluginsScope());
    }

    protected BuildScopeServiceRegistryFactory createServiceRegistryFactory(final ServiceRegistry services) {
        return new BuildScopeServiceRegistryFactory(services);
    }

    protected ProjectTaskLister createProjectTaskLister() {
        return new DefaultProjectTaskLister();
    }

    protected DependencyMetaDataProvider createDependencyMetaDataProvider() {
        return new DependencyMetaDataProviderImpl();
    }

    protected ComponentTypeRegistry createComponentTypeRegistry() {
        return new DefaultComponentTypeRegistry();
    }

    protected PluginInspector createPluginInspector(ModelRuleSourceDetector modelRuleSourceDetector) {
        return new PluginInspector(modelRuleSourceDetector);
    }

    private static class DependencyMetaDataProviderImpl implements DependencyMetaDataProvider {
        @Override
        public Module getModule() {
            return new DefaultModule("unspecified", "unspecified", Project.DEFAULT_VERSION, Project.DEFAULT_STATUS);
        }
    }

    protected BuildOperationLoggerFactory createBuildOperationLoggerFactory() {
        return new DefaultBuildOperationLoggerFactory();
    }

    AuthenticationSchemeRegistry createAuthenticationSchemeRegistry() {
        return new DefaultAuthenticationSchemeRegistry();
    }

    protected DefaultToolingModelBuilderRegistry createBuildScopedToolingModelBuilders(
        List<BuildScopeToolingModelBuilderRegistryAction> registryActions,
        BuildOperationExecutor buildOperationExecutor,
        ProjectStateRegistry projectStateRegistry,
        UserCodeApplicationContext userCodeApplicationContext
    ) {
        DefaultToolingModelBuilderRegistry registry = new DefaultToolingModelBuilderRegistry(buildOperationExecutor, projectStateRegistry, userCodeApplicationContext);
        // Services are created on demand, and this may happen while applying a plugin
        userCodeApplicationContext.gradleRuntime(() -> {
            for (BuildScopeToolingModelBuilderRegistryAction registryAction : registryActions) {
                registryAction.execute(registry);
            }
        });
        return registry;
    }

    protected BuildScanUserInputHandler createBuildScanUserInputHandler(UserInputHandler userInputHandler) {
        return new DefaultBuildScanUserInputHandler(userInputHandler);
    }

    protected BuildInvocationDetails createBuildInvocationDetails(BuildStartedTime buildStartedTime) {
        return new DefaultBuildInvocationDetails(buildStartedTime);
    }

    protected CompileOperationFactory createCompileOperationFactory(DocumentationRegistry documentationRegistry) {
        return new DefaultCompileOperationFactory(documentationRegistry);
    }

    protected DefaultScriptCompilationHandler createScriptCompilationHandler(Deleter deleter, ImportsReader importsReader) {
        return new DefaultScriptCompilationHandler(deleter, importsReader);
    }

    protected ScriptRunnerFactory createScriptRunnerFactory(ListenerManager listenerManager, InstantiatorFactory instantiatorFactory) {
        ScriptExecutionListener scriptExecutionListener = listenerManager.getBroadcaster(ScriptExecutionListener.class);
        return new DefaultScriptRunnerFactory(
            scriptExecutionListener,
            instantiatorFactory.inject()
        );
    }
}
