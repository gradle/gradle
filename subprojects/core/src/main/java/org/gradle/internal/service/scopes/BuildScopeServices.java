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
import org.gradle.api.flow.FlowScope;
import org.gradle.api.internal.BuildDefinition;
import org.gradle.api.internal.BuildType;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DefaultClassPathProvider;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.internal.DependencyClassPathProvider;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.ExternalProcessStartedListener;
import org.gradle.api.internal.FeaturePreviews;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.classpath.PluginModuleRegistry;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.component.ComponentTypeRegistry;
import org.gradle.api.internal.component.DefaultComponentTypeRegistry;
import org.gradle.api.internal.file.DefaultArchiveOperations;
import org.gradle.api.internal.file.DefaultFileOperations;
import org.gradle.api.internal.file.DefaultFileSystemOperations;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.internal.initialization.ActionBasedModelDefaultsHandler;
import org.gradle.api.internal.initialization.BuildLogicBuildQueue;
import org.gradle.api.internal.initialization.BuildLogicBuilder;
import org.gradle.api.initialization.SharedModelDefaults;
import org.gradle.api.internal.initialization.DefaultBuildLogicBuilder;
import org.gradle.api.internal.initialization.DefaultSharedModelDefaults;
import org.gradle.api.internal.initialization.DefaultScriptClassPathResolver;
import org.gradle.api.internal.initialization.DefaultScriptHandlerFactory;
import org.gradle.api.internal.initialization.ScriptClassPathResolver;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
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
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskStatistics;
import org.gradle.api.invocation.BuildInvocationDetails;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.services.internal.BuildServiceProvider;
import org.gradle.api.services.internal.BuildServiceProviderNagger;
import org.gradle.api.services.internal.DefaultBuildServicesRegistry;
import org.gradle.cache.UnscopedCacheBuilderFactory;
import org.gradle.cache.internal.BuildScopeCacheDir;
import org.gradle.cache.internal.scopes.DefaultBuildScopedCacheBuilderFactory;
import org.gradle.cache.scopes.BuildScopedCacheBuilderFactory;
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
import org.gradle.configuration.project.DefaultCompileOperationFactory;
import org.gradle.configuration.project.PluginsProjectConfigureActions;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.execution.plan.DefaultNodeValidator;
import org.gradle.execution.plan.ExecutionNodeAccessHierarchies;
import org.gradle.execution.plan.ExecutionPlanFactory;
import org.gradle.execution.plan.OrdinalGroupFactory;
import org.gradle.execution.plan.TaskDependencyResolver;
import org.gradle.execution.plan.TaskNodeDependencyResolver;
import org.gradle.execution.plan.TaskNodeFactory;
import org.gradle.execution.plan.ToPlannedNodeConverterRegistry;
import org.gradle.execution.plan.WorkNodeDependencyResolver;
import org.gradle.execution.selection.BuildTaskSelector;
import org.gradle.groovy.scripts.DefaultScriptCompilerFactory;
import org.gradle.groovy.scripts.ScriptCompilerFactory;
import org.gradle.groovy.scripts.internal.BuildOperationBackedScriptCompilationHandler;
import org.gradle.groovy.scripts.internal.BuildScopeInMemoryCachingScriptClassCompiler;
import org.gradle.groovy.scripts.internal.CrossBuildInMemoryCachingScriptClassCache;
import org.gradle.groovy.scripts.internal.DefaultScriptCompilationHandler;
import org.gradle.groovy.scripts.internal.DefaultScriptRunnerFactory;
import org.gradle.groovy.scripts.internal.GroovyDslWorkspaceProvider;
import org.gradle.groovy.scripts.internal.GroovyScriptClassCompiler;
import org.gradle.groovy.scripts.internal.ScriptRunnerFactory;
import org.gradle.groovy.scripts.internal.ScriptSourceListener;
import org.gradle.initialization.BuildLoader;
import org.gradle.initialization.BuildOperationFiringSettingsPreparer;
import org.gradle.initialization.BuildOperationSettingsProcessor;
import org.gradle.initialization.ClassLoaderScopeRegistry;
import org.gradle.initialization.DefaultGradlePropertiesController;
import org.gradle.initialization.DefaultGradlePropertiesLoader;
import org.gradle.initialization.DefaultSettingsLoaderFactory;
import org.gradle.initialization.DefaultSettingsPreparer;
import org.gradle.initialization.DefaultToolchainManagement;
import org.gradle.initialization.Environment;
import org.gradle.initialization.EnvironmentChangeTracker;
import org.gradle.initialization.GradlePropertiesController;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.initialization.IGradlePropertiesLoader;
import org.gradle.initialization.InitScriptHandler;
import org.gradle.initialization.InstantiatingBuildLoader;
import org.gradle.initialization.NotifyingBuildLoader;
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
import org.gradle.initialization.properties.DefaultProjectPropertiesLoader;
import org.gradle.initialization.properties.DefaultSystemPropertiesInstaller;
import org.gradle.initialization.properties.ProjectPropertiesLoader;
import org.gradle.initialization.properties.SystemPropertiesInstaller;
import org.gradle.internal.Factory;
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
import org.gradle.internal.buildoption.FeatureFlags;
import org.gradle.internal.buildtree.BuildInclusionCoordinator;
import org.gradle.internal.buildtree.BuildModelParameters;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.classpath.transforms.ClasspathElementTransformFactoryForLegacy;
import org.gradle.internal.classpath.types.GradleCoreInstrumentationTypeRegistry;
import org.gradle.internal.cleanup.DefaultBuildOutputCleanupRegistry;
import org.gradle.internal.code.UserCodeApplicationContext;
import org.gradle.internal.composite.DefaultBuildIncluder;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.event.ScopedListenerManager;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.WorkExecutionTracker;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.RelativeFilePathResolver;
import org.gradle.internal.file.Stat;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.instrumentation.reporting.PropertyUpgradeReportConfig;
import org.gradle.internal.invocation.DefaultBuildInvocationDetails;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.model.CalculatedValueFactory;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.logging.BuildOperationLoggerFactory;
import org.gradle.internal.operations.logging.DefaultBuildOperationLoggerFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.DefaultTextFileResourceLoader;
import org.gradle.internal.resource.TextFileResourceLoader;
import org.gradle.internal.resource.local.FileResourceListener;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.resources.SharedResourceLeaseRegistry;
import org.gradle.internal.scripts.ScriptExecutionListener;
import org.gradle.internal.service.CachingServiceLocator;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.model.internal.inspect.ModelRuleSourceDetector;
import org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginHandler;
import org.gradle.plugin.software.internal.PluginScheme;
import org.gradle.plugin.software.internal.ModelDefaultsHandler;
import org.gradle.plugin.software.internal.SoftwareTypeRegistry;
import org.gradle.plugin.use.internal.PluginRequestApplicator;
import org.gradle.process.internal.DefaultExecOperations;
import org.gradle.process.internal.DefaultExecSpecFactory;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.process.internal.ExecFactory;
import org.gradle.tooling.provider.model.internal.BuildScopeToolingModelBuilderRegistryAction;
import org.gradle.tooling.provider.model.internal.DefaultToolingModelBuilderRegistry;

import java.util.List;

/**
 * Contains the services for a single build invocation inside a build tree.
 */
public class BuildScopeServices implements ServiceRegistrationProvider {

    private final BuildModelControllerServices.Supplier supplier;

    public BuildScopeServices(BuildModelControllerServices.Supplier supplier) {
        this.supplier = supplier;
    }

    @SuppressWarnings("unused")
    void configure(ServiceRegistration registration, ServiceRegistry buildScopeServices, List<GradleModuleServices> serviceProviders) {
        registration.addProvider(new BuildCacheServices());

        registration.add(DefaultExecOperations.class);
        registration.add(DefaultFileOperations.class);
        registration.add(DefaultFileSystemOperations.class);
        registration.add(DefaultArchiveOperations.class);
        registration.add(ProjectFactory.class);
        registration.add(DefaultSettingsLoaderFactory.class);
        registration.add(ResolvedBuildLayout.class);
        registration.add(DefaultNodeValidator.class);
        registration.add(TaskNodeFactory.class);
        registration.add(TaskNodeDependencyResolver.class);
        registration.add(WorkNodeDependencyResolver.class);
        registration.add(TaskDependencyResolver.class);
        registration.add(DefaultBuildWorkGraphController.class);
        registration.add(DefaultBuildIncluder.class);
        registration.add(DefaultScriptClassPathResolver.class);
        registration.add(DefaultScriptHandlerFactory.class);
        registration.add(DefaultBuildOutputCleanupRegistry.class);

        supplier.applyServicesTo(registration, buildScopeServices);

        for (GradleModuleServices services : serviceProviders) {
            services.registerBuildServices(registration);
        }
    }

    @Provides
    OrdinalGroupFactory createOrdinalGroupFactory() {
        return new OrdinalGroupFactory();
    }

    @Provides
    ExecutionPlanFactory createExecutionPlanFactory(
        BuildState build,
        TaskNodeFactory taskNodeFactory,
        OrdinalGroupFactory ordinalGroupFactory,
        TaskDependencyResolver dependencyResolver,
        ExecutionNodeAccessHierarchies executionNodeAccessHierarchies,
        ResourceLockCoordinationService lockCoordinationService
    ) {
        return new ExecutionPlanFactory(
            build.getDisplayName().getDisplayName(),
            taskNodeFactory,
            ordinalGroupFactory,
            dependencyResolver,
            executionNodeAccessHierarchies.getOutputHierarchy(),
            executionNodeAccessHierarchies.getDestroyableHierarchy(),
            lockCoordinationService
        );
    }

    @Provides
    ExecutionNodeAccessHierarchies createExecutionNodeAccessHierarchies(FileSystem fileSystem, Stat stat) {
        return new ExecutionNodeAccessHierarchies(fileSystem.isCaseSensitive() ? CaseSensitivity.CASE_SENSITIVE : CaseSensitivity.CASE_INSENSITIVE, stat);
    }

    @Provides
    protected BuildScopedCacheBuilderFactory createBuildScopedCacheBuilderFactory(
        GradleUserHomeDirProvider userHomeDirProvider,
        BuildLayout buildLayout,
        StartParameter startParameter,
        UnscopedCacheBuilderFactory unscopedCacheBuilderFactory
    ) {
        BuildScopeCacheDir cacheDir = new BuildScopeCacheDir(userHomeDirProvider, buildLayout, startParameter);
        return new DefaultBuildScopedCacheBuilderFactory(cacheDir.getDir(), unscopedCacheBuilderFactory);
    }

    @Provides
    protected BuildLayout createBuildLocations(BuildLayoutFactory buildLayoutFactory, BuildDefinition buildDefinition) {
        return buildLayoutFactory.getLayoutFor(new BuildLayoutConfiguration(buildDefinition.getStartParameter()));
    }

    @Provides
    protected DefaultResourceHandler.Factory createResourceHandlerFactory(FileResolver fileResolver, TaskDependencyFactory taskDependencyFactory, FileSystem fileSystem, TemporaryFileProvider temporaryFileProvider, ApiTextResourceAdapter.Factory textResourceAdapterFactory) {
        return DefaultResourceHandler.Factory.from(
            fileResolver,
            taskDependencyFactory,
            fileSystem,
            temporaryFileProvider,
            textResourceAdapterFactory
        );
    }

    @Provides
    protected FileCollectionFactory decorateFileCollectionFactory(FileCollectionFactory fileCollectionFactory, FileResolver fileResolver) {
        return fileCollectionFactory.withResolver(fileResolver);
    }

    @Provides
    protected ExecFactory decorateExecFactory(ExecFactory parent, FileResolver fileResolver, FileCollectionFactory fileCollectionFactory, Instantiator instantiator, ObjectFactory objectFactory, JavaModuleDetector javaModuleDetector, ListenerManager listenerManager) {
        return parent.forContext()
            .withFileResolver(fileResolver)
            .withFileCollectionFactory(fileCollectionFactory)
            .withInstantiator(instantiator)
            .withObjectFactory(objectFactory)
            .withJavaModuleDetector(javaModuleDetector)
            .withExternalProcessStartedListener(listenerManager.getBroadcaster(ExternalProcessStartedListener.class))
            .build();
    }

    @Provides
    protected PublicBuildPath createPublicBuildPath(BuildState buildState) {
        return new DefaultPublicBuildPath(buildState.getIdentityPath());
    }

    @Provides
    protected TaskStatistics createTaskStatistics() {
        return new TaskStatistics();
    }

    @Provides
    protected DefaultProjectRegistry<ProjectInternal> createProjectRegistry() {
        return new DefaultProjectRegistry<ProjectInternal>();
    }

    @Provides
    protected TextFileResourceLoader createTextFileResourceLoader(RelativeFilePathResolver resolver) {
        return new DefaultTextFileResourceLoader(resolver);
    }

    @Provides
    protected ScopedListenerManager createListenerManager(ScopedListenerManager listenerManager) {
        return listenerManager.createChild(Scope.Build.class);
    }

    @Provides
    protected ClassPathRegistry createClassPathRegistry(ModuleRegistry moduleRegistry, PluginModuleRegistry pluginModuleRegistry) {
        return new DefaultClassPathRegistry(
            new DefaultClassPathProvider(moduleRegistry),
            new DependencyClassPathProvider(moduleRegistry, pluginModuleRegistry));
    }

    @Provides
    protected IsolatedAntBuilder createIsolatedAntBuilder(ClassPathRegistry classPathRegistry, ClassLoaderFactory classLoaderFactory, ModuleRegistry moduleRegistry) {
        return new DefaultIsolatedAntBuilder(classPathRegistry, classLoaderFactory, moduleRegistry);
    }

    @Provides
    protected GradleProperties createGradleProperties(
        GradlePropertiesController gradlePropertiesController
    ) {
        return gradlePropertiesController.getGradleProperties();
    }

    @Provides
    protected GradlePropertiesController createGradlePropertiesController(
        IGradlePropertiesLoader propertiesLoader,
        SystemPropertiesInstaller systemPropertiesInstaller,
        ProjectPropertiesLoader projectPropertiesLoader
    ) {
        return new DefaultGradlePropertiesController(propertiesLoader, systemPropertiesInstaller, projectPropertiesLoader);
    }

    @Provides
    protected ProjectPropertiesLoader createProjectPropertiesLoader(StartParameterInternal startParameter, Environment environment) {
        return new DefaultProjectPropertiesLoader(startParameter, environment);
    }

    @Provides
    protected IGradlePropertiesLoader createGradlePropertiesLoader(StartParameterInternal startParameter, Environment environment) {
        return new DefaultGradlePropertiesLoader(startParameter, environment);
    }

    @Provides
    protected SystemPropertiesInstaller createSystemPropertiesInstaller(
        EnvironmentChangeTracker environmentChangeTracker,
        StartParameterInternal startParameter,
        GradleInternal gradleInternal
    ) {
        return new DefaultSystemPropertiesInstaller(environmentChangeTracker, startParameter, gradleInternal);
    }

    @Provides
    protected ValueSourceProviderFactory createValueSourceProviderFactory(
        InstantiatorFactory instantiatorFactory,
        IsolatableFactory isolatableFactory,
        ServiceRegistry services,
        GradleProperties gradleProperties,
        ExecFactory execFactory,
        ListenerManager listenerManager,
        CalculatedValueFactory calculatedValueFactory
    ) {
        return new DefaultValueSourceProviderFactory(
            listenerManager,
            instantiatorFactory,
            isolatableFactory,
            gradleProperties,
            calculatedValueFactory,
            new DefaultExecOperations(execFactory.forContext().withoutExternalProcessStartedListener().build()),
            services
        );
    }

    @Provides
    protected ExecSpecFactory createExecSpecFactory(ExecActionFactory execActionFactory) {
        return new DefaultExecSpecFactory(execActionFactory);
    }

    @Provides
    protected ProcessOutputProviderFactory createProcessOutputProviderFactory(Instantiator instantiator, ExecSpecFactory execSpecFactory) {
        return new ProcessOutputProviderFactory(instantiator, execSpecFactory);
    }

    @Provides
    protected ProviderFactory createProviderFactory(
        Instantiator instantiator,
        ValueSourceProviderFactory valueSourceProviderFactory,
        ProcessOutputProviderFactory processOutputProviderFactory,
        ListenerManager listenerManager
    ) {
        return instantiator.newInstance(DefaultProviderFactory.class, valueSourceProviderFactory, processOutputProviderFactory, listenerManager);
    }

    @Provides
    protected ActorFactory createActorFactory(ExecutorFactory executorFactory) {
        return new DefaultActorFactory(executorFactory);
    }

    @Provides
    protected BuildLoader createBuildLoader(
        GradleProperties gradleProperties,
        BuildOperationRunner buildOperationRunner,
        BuildOperationProgressEventEmitter emitter,
        ListenerManager listenerManager
    ) {
        return new NotifyingBuildLoader(
            new ProjectPropertySettingBuildLoader(
                gradleProperties,
                new InstantiatingBuildLoader(),
                listenerManager.getBroadcaster(FileResourceListener.class)
            ),
            buildOperationRunner,
            emitter
        );
    }

    @Provides
    protected ITaskFactory createITaskFactory(Instantiator instantiator, TaskClassInfoStore taskClassInfoStore) {
        return new AnnotationProcessingTaskFactory(
            instantiator,
            taskClassInfoStore,
            new TaskFactory());
    }

    @Provides
    protected ScriptCompilerFactory createScriptCompileFactory(
        GroovyScriptClassCompiler scriptCompiler,
        CrossBuildInMemoryCachingScriptClassCache cache,
        ScriptRunnerFactory scriptRunnerFactory
    ) {
        return new DefaultScriptCompilerFactory(
            new BuildScopeInMemoryCachingScriptClassCompiler(cache, scriptCompiler),
            scriptRunnerFactory
        );
    }

    @Provides
    protected GroovyScriptClassCompiler createFileCacheBackedScriptClassCompiler(
        BuildOperationRunner buildOperationRunner,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher,
        DefaultScriptCompilationHandler scriptCompilationHandler,
        CachedClasspathTransformer classpathTransformer,
        ExecutionEngine executionEngine,
        FileCollectionFactory fileCollectionFactory,
        InputFingerprinter inputFingerprinter,
        GroovyDslWorkspaceProvider groovyDslWorkspaceProvider,
        ClasspathElementTransformFactoryForLegacy transformFactoryForLegacy,
        GradleCoreInstrumentationTypeRegistry gradleCoreTypeRegistry,
        PropertyUpgradeReportConfig propertyUpgradeReportConfig
    ) {
        return new GroovyScriptClassCompiler(
            new BuildOperationBackedScriptCompilationHandler(scriptCompilationHandler, buildOperationRunner),
            classLoaderHierarchyHasher,
            classpathTransformer,
            executionEngine,
            fileCollectionFactory,
            inputFingerprinter,
            groovyDslWorkspaceProvider.getWorkspace(),
            transformFactoryForLegacy,
            gradleCoreTypeRegistry,
            propertyUpgradeReportConfig
        );
    }

    @Provides
    protected ScriptPluginFactory createScriptPluginFactory(
        InstantiatorFactory instantiatorFactory,
        ServiceRegistry buildScopedServices,
        ScriptCompilerFactory scriptCompilerFactory,
        Factory<LoggingManagerInternal> loggingManagerFactory,
        AutoAppliedPluginHandler autoAppliedPluginHandler,
        PluginRequestApplicator pluginRequestApplicator,
        CompileOperationFactory compileOperationFactory,
        BuildOperationRunner buildOperationRunner,
        UserCodeApplicationContext userCodeApplicationContext,
        ListenerManager listenerManager
    ) {
        ScriptPluginFactorySelector.ProviderInstantiator instantiator = ScriptPluginFactorySelector.defaultProviderInstantiatorFor(instantiatorFactory.inject(buildScopedServices));
        DefaultScriptPluginFactory defaultScriptPluginFactory = new DefaultScriptPluginFactory(
            buildScopedServices,
            scriptCompilerFactory,
            loggingManagerFactory,
            autoAppliedPluginHandler,
            pluginRequestApplicator,
            compileOperationFactory
        );
        ScriptPluginFactorySelector scriptPluginFactorySelector = new ScriptPluginFactorySelector(defaultScriptPluginFactory, instantiator, buildOperationRunner, userCodeApplicationContext, listenerManager.getBroadcaster(ScriptSourceListener.class));
        defaultScriptPluginFactory.setScriptPluginFactory(scriptPluginFactorySelector);
        return scriptPluginFactorySelector;
    }

    @Provides
    protected BuildSourceBuilder createBuildSourceBuilder(
        BuildState currentBuild,
        BuildOperationRunner buildOperationRunner,
        CachingServiceLocator cachingServiceLocator,
        BuildStateRegistry buildRegistry,
        PublicBuildPath publicBuildPath,
        ScriptClassPathResolver scriptClassPathResolver,
        BuildLogicBuildQueue buildQueue
    ) {
        return new BuildSourceBuilder(
            currentBuild,
            buildOperationRunner,
            new BuildSrcBuildListenerFactory(
                PluginsProjectConfigureActions.of(
                    BuildSrcProjectConfigurationAction.class,
                    cachingServiceLocator),
                scriptClassPathResolver),
            buildRegistry,
            publicBuildPath,
            buildQueue);
    }

    @Provides
    protected BuildLogicBuilder createBuildLogicBuilder(
        BuildState currentBuild,
        ScriptClassPathResolver scriptClassPathResolver,
        BuildLogicBuildQueue buildQueue
    ) {
        return new DefaultBuildLogicBuilder(currentBuild, scriptClassPathResolver, buildQueue);
    }

    @Provides
    protected InitScriptHandler createInitScriptHandler(ScriptPluginFactory scriptPluginFactory, ScriptHandlerFactory scriptHandlerFactory, BuildOperationRunner buildOperationRunner, TextFileResourceLoader resourceLoader) {
        return new InitScriptHandler(
            new DefaultInitScriptProcessor(
                scriptPluginFactory,
                scriptHandlerFactory
            ),
            buildOperationRunner,
            resourceLoader
        );
    }

    @Provides
    protected SettingsProcessor createSettingsProcessor(
        ServiceRegistry buildScopeServices,
        ScriptPluginFactory scriptPluginFactory,
        ScriptHandlerFactory scriptHandlerFactory,
        Instantiator instantiator,
        GradleProperties gradleProperties,
        BuildOperationRunner buildOperationRunner,
        TextFileResourceLoader textFileResourceLoader
    ) {
        return new BuildOperationSettingsProcessor(
            new RootBuildCacheControllerSettingsProcessor(
                new SettingsEvaluatedCallbackFiringSettingsProcessor(
                    new ScriptEvaluatingSettingsProcessor(
                        scriptPluginFactory,
                        new SettingsFactory(
                            instantiator,
                            buildScopeServices,
                            scriptHandlerFactory
                        ),
                        gradleProperties,
                        textFileResourceLoader
                    )
                )
            ),
            buildOperationRunner
        );
    }

    @Provides
    protected SettingsPreparer createSettingsPreparer(SettingsLoaderFactory settingsLoaderFactory, BuildOperationRunner buildOperationRunner, BuildOperationProgressEventEmitter emitter, BuildDefinition buildDefinition) {
        return new BuildOperationFiringSettingsPreparer(
            new DefaultSettingsPreparer(
                settingsLoaderFactory
            ),
            buildOperationRunner,
            emitter,
            buildDefinition.getFromBuild());
    }

    @Provides
    protected ProjectsPreparer createBuildConfigurer(
        ProjectConfigurer projectConfigurer,
        BuildSourceBuilder buildSourceBuilder,
        BuildInclusionCoordinator inclusionCoordinator,
        BuildLoader buildLoader,
        BuildOperationRunner buildOperationRunner,
        BuildModelParameters buildModelParameters,
        BuildType buildType
    ) {
        return new BuildOperationFiringProjectsPreparer(
            new BuildTreePreparingProjectsPreparer(
                new DefaultProjectsPreparer(
                    projectConfigurer,
                    buildType,
                    buildModelParameters,
                    buildOperationRunner),
                buildLoader,
                inclusionCoordinator,
                buildSourceBuilder),
            buildOperationRunner);
    }

    @Provides
    protected BuildWorkPreparer createWorkPreparer(BuildOperationRunner buildOperationRunner, ExecutionPlanFactory executionPlanFactory, ToPlannedNodeConverterRegistry converterRegistry) {
        return new BuildOperationFiringBuildWorkPreparer(
            buildOperationRunner,
            new DefaultBuildWorkPreparer(
                executionPlanFactory
            ),
            converterRegistry
        );
    }

    @Provides
    protected BuildTaskSelector.BuildSpecificSelector createTaskSelector(BuildTaskSelector selector, BuildState build) {
        return selector.relativeToBuild(build);
    }

    @Provides
    protected PluginRegistry createPluginRegistry(ClassLoaderScopeRegistry scopeRegistry, PluginInspector pluginInspector) {
        return new DefaultPluginRegistry(pluginInspector, scopeRegistry.getCoreAndPluginsScope());
    }

    @Provides
    protected BuildScopeServiceRegistryFactory createServiceRegistryFactory(final ServiceRegistry services) {
        return new BuildScopeServiceRegistryFactory(services);
    }

    @Provides
    protected ProjectTaskLister createProjectTaskLister() {
        return new DefaultProjectTaskLister();
    }

    @Provides
    protected ComponentTypeRegistry createComponentTypeRegistry() {
        return new DefaultComponentTypeRegistry();
    }

    @Provides
    protected PluginInspector createPluginInspector(ModelRuleSourceDetector modelRuleSourceDetector) {
        return new PluginInspector(modelRuleSourceDetector);
    }

    @Provides
    protected BuildOperationLoggerFactory createBuildOperationLoggerFactory() {
        return new DefaultBuildOperationLoggerFactory();
    }

    @Provides
    AuthenticationSchemeRegistry createAuthenticationSchemeRegistry() {
        return new DefaultAuthenticationSchemeRegistry();
    }

    @Provides
    protected DefaultToolingModelBuilderRegistry createBuildScopedToolingModelBuilders(
        List<BuildScopeToolingModelBuilderRegistryAction> registryActions,
        BuildOperationRunner buildOperationRunner,
        ProjectStateRegistry projectStateRegistry,
        UserCodeApplicationContext userCodeApplicationContext
    ) {
        DefaultToolingModelBuilderRegistry registry = new DefaultToolingModelBuilderRegistry(buildOperationRunner, projectStateRegistry, userCodeApplicationContext);
        // Services are created on demand, and this may happen while applying a plugin
        userCodeApplicationContext.gradleRuntime(() -> {
            for (BuildScopeToolingModelBuilderRegistryAction registryAction : registryActions) {
                registryAction.execute(registry);
            }
        });
        return registry;
    }

    @Provides
    protected BuildInvocationDetails createBuildInvocationDetails(BuildStartedTime buildStartedTime) {
        return new DefaultBuildInvocationDetails(buildStartedTime);
    }

    @Provides
    protected CompileOperationFactory createCompileOperationFactory(DocumentationRegistry documentationRegistry) {
        return new DefaultCompileOperationFactory(documentationRegistry);
    }

    @Provides
    protected DefaultScriptCompilationHandler createScriptCompilationHandler(Deleter deleter, ImportsReader importsReader, ObjectFactory objectFactory) {
        return objectFactory.newInstance(DefaultScriptCompilationHandler.class, deleter, importsReader);
    }

    @Provides
    protected ScriptRunnerFactory createScriptRunnerFactory(ListenerManager listenerManager, InstantiatorFactory instantiatorFactory) {
        ScriptExecutionListener scriptExecutionListener = listenerManager.getBroadcaster(ScriptExecutionListener.class);
        return new DefaultScriptRunnerFactory(
            scriptExecutionListener,
            instantiatorFactory.inject()
        );
    }

    @Provides
    protected DefaultToolchainManagement createToolchainManagement(ObjectFactory objectFactory) {
        return objectFactory.newInstance(DefaultToolchainManagement.class);
    }

    @Provides
    protected SharedResourceLeaseRegistry createSharedResourceLeaseRegistry(ResourceLockCoordinationService coordinationService) {
        return new SharedResourceLeaseRegistry(coordinationService);
    }

    @Provides
    protected DefaultBuildServicesRegistry createSharedServiceRegistry(
        BuildState buildState,
        Instantiator instantiator,
        DomainObjectCollectionFactory factory,
        InstantiatorFactory instantiatorFactory,
        ServiceRegistry services,
        ListenerManager listenerManager,
        IsolatableFactory isolatableFactory,
        SharedResourceLeaseRegistry sharedResourceLeaseRegistry,
        FeatureFlags featureFlags
    ) {
        // TODO:configuration-cache remove this hack
        // HACK: force the instantiation of FlowScope so its listeners are registered before DefaultBuildServicesRegistry's
        services.find(FlowScope.class);

        // Instantiate via `instantiator` for the DSL decorations to the `BuildServiceRegistry` API
        return instantiator.newInstance(
            DefaultBuildServicesRegistry.class,
            buildState.getBuildIdentifier(),
            factory,
            instantiatorFactory,
            services,
            listenerManager,
            isolatableFactory,
            sharedResourceLeaseRegistry,
            featureFlags.isEnabled(FeaturePreviews.Feature.STABLE_CONFIGURATION_CACHE)
                ? new BuildServiceProviderNagger(services.get(WorkExecutionTracker.class))
                : BuildServiceProvider.Listener.EMPTY
        );
    }

    @Provides
    protected SharedModelDefaults createSharedModelDefaults(Instantiator instantiator, SoftwareTypeRegistry softwareTypeRegistry) {
        return instantiator.newInstance(DefaultSharedModelDefaults.class, softwareTypeRegistry);
    }

    @Provides
    protected ModelDefaultsHandler createActionDefaultsHandler(SoftwareTypeRegistry softwareTypeRegistry, PluginScheme pluginScheme) {
        return new ActionBasedModelDefaultsHandler(softwareTypeRegistry, pluginScheme.getInspectionScheme());
    }
}
