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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.internal.ClassGenerator;
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DefaultClassPathProvider;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.internal.DependencyClassPathProvider;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.ExceptionAnalyser;
import org.gradle.api.internal.artifacts.DefaultModule;
import org.gradle.api.internal.artifacts.DependencyManagementServices;
import org.gradle.api.internal.artifacts.ModuleInternal;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.changedetection.state.CacheAccessingFileSnapshotter;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.classpath.PluginModuleRegistry;
import org.gradle.api.internal.component.ComponentTypeRegistry;
import org.gradle.api.internal.component.DefaultComponentTypeRegistry;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.initialization.DefaultScriptHandlerFactory;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache;
import org.gradle.api.internal.plugins.DefaultPluginRegistry;
import org.gradle.api.internal.plugins.PluginInspector;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.api.internal.project.DefaultProjectAccessListener;
import org.gradle.api.internal.project.DefaultProjectRegistry;
import org.gradle.api.internal.project.DefaultProjectTaskLister;
import org.gradle.api.internal.project.IProjectFactory;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.internal.project.ProjectFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.internal.project.ProjectTaskLister;
import org.gradle.api.internal.project.antbuilder.DefaultIsolatedAntBuilder;
import org.gradle.api.internal.project.taskfactory.AnnotationProcessingTaskFactory;
import org.gradle.api.internal.project.taskfactory.DependencyAutoWireTaskFactory;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.internal.project.taskfactory.TaskFactory;
import org.gradle.api.logging.configuration.LoggingConfiguration;
import org.gradle.api.logging.configuration.ShowStacktrace;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.CacheValidator;
import org.gradle.configuration.BuildConfigurer;
import org.gradle.configuration.DefaultBuildConfigurer;
import org.gradle.configuration.DefaultInitScriptProcessor;
import org.gradle.configuration.DefaultScriptPluginFactory;
import org.gradle.configuration.ImportsReader;
import org.gradle.configuration.ScriptPluginFactory;
import org.gradle.configuration.ScriptPluginFactorySelector;
import org.gradle.configuration.project.BuildScriptProcessor;
import org.gradle.configuration.project.ConfigureActionsProjectEvaluator;
import org.gradle.configuration.project.DelayedConfigurationActions;
import org.gradle.configuration.project.LifecycleProjectEvaluator;
import org.gradle.configuration.project.PluginsProjectConfigureActions;
import org.gradle.configuration.project.ProjectEvaluator;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.execution.TaskPathProjectEvaluator;
import org.gradle.groovy.scripts.DefaultScriptCompilerFactory;
import org.gradle.groovy.scripts.ScriptCompilerFactory;
import org.gradle.groovy.scripts.ScriptExecutionListener;
import org.gradle.groovy.scripts.internal.BuildScopeInMemoryCachingScriptClassCompiler;
import org.gradle.groovy.scripts.internal.CrossBuildInMemoryCachingScriptClassCache;
import org.gradle.groovy.scripts.internal.DefaultScriptCompilationHandler;
import org.gradle.groovy.scripts.internal.DefaultScriptRunnerFactory;
import org.gradle.groovy.scripts.internal.FileCacheBackedScriptClassCompiler;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildLoader;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.initialization.ClassLoaderScopeRegistry;
import org.gradle.initialization.DefaultClassLoaderScopeRegistry;
import org.gradle.initialization.DefaultExceptionAnalyser;
import org.gradle.initialization.DefaultGradlePropertiesLoader;
import org.gradle.initialization.DefaultSettingsFinder;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.initialization.IGradlePropertiesLoader;
import org.gradle.initialization.InitScriptHandler;
import org.gradle.initialization.InstantiatingBuildLoader;
import org.gradle.initialization.MultipleBuildFailuresExceptionAnalyser;
import org.gradle.initialization.NotifyingSettingsLoader;
import org.gradle.initialization.NotifyingSettingsProcessor;
import org.gradle.initialization.ProjectAccessListener;
import org.gradle.initialization.ProjectPropertySettingBuildLoader;
import org.gradle.initialization.PropertiesLoadingSettingsProcessor;
import org.gradle.initialization.ScriptEvaluatingSettingsProcessor;
import org.gradle.initialization.SettingsFactory;
import org.gradle.initialization.SettingsHandler;
import org.gradle.initialization.SettingsLoader;
import org.gradle.initialization.SettingsProcessor;
import org.gradle.initialization.StackTraceSanitizingExceptionAnalyser;
import org.gradle.initialization.buildsrc.BuildSourceBuilder;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.TimeProvider;
import org.gradle.internal.TrueTimeProvider;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.actor.internal.DefaultActorFactory;
import org.gradle.internal.authentication.AuthenticationSchemeRegistry;
import org.gradle.internal.authentication.DefaultAuthenticationSchemeRegistry;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.operations.logging.BuildOperationLoggerFactory;
import org.gradle.internal.operations.logging.DefaultBuildOperationLoggerFactory;
import org.gradle.internal.progress.BuildOperationExecutor;
import org.gradle.internal.progress.DefaultBuildOperationExecutor;
import org.gradle.internal.progress.InternalBuildListener;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.model.internal.inspect.ModelRuleSourceDetector;
import org.gradle.plugin.repository.internal.PluginRepositoryFactory;
import org.gradle.plugin.repository.internal.PluginRepositoryRegistry;
import org.gradle.plugin.use.internal.PluginRequestApplicator;
import org.gradle.profile.ProfileEventAdapter;
import org.gradle.profile.ProfileListener;

/**
 * Contains the singleton services for a single build invocation.
 */
public class BuildScopeServices extends DefaultServiceRegistry {

    private final BuildSessionScopeServices sessionServices;
    private final boolean singleUseSession;

    public static BuildScopeServices forSession(BuildSessionScopeServices sessionServices) {
        return new BuildScopeServices(sessionServices, false);
    }

    public static BuildScopeServices singleSession(ServiceRegistry parent, StartParameter startParameter) {
        return new BuildScopeServices(parent, startParameter);
    }

    protected BuildScopeServices(ServiceRegistry parent, StartParameter startParameter) {
        this(new BuildSessionScopeServices(parent, startParameter, ClassPath.EMPTY), true);
    }

    @VisibleForTesting
    BuildScopeServices(final BuildSessionScopeServices sessionServices, boolean singleUseSession) {
        super(sessionServices);
        this.sessionServices = sessionServices;
        this.singleUseSession = singleUseSession;
        register(new Action<ServiceRegistration>() {
            public void execute(ServiceRegistration registration) {
                for (PluginServiceRegistry pluginServiceRegistry : sessionServices.getAll(PluginServiceRegistry.class)) {
                    pluginServiceRegistry.registerBuildServices(registration);
                }
            }
        });
    }

    protected TimeProvider createTimeProvider() {
        return new TrueTimeProvider();
    }

    protected ProjectRegistry<ProjectInternal> createProjectRegistry() {
        return new DefaultProjectRegistry<ProjectInternal>();
    }

    protected IProjectFactory createProjectFactory(Instantiator instantiator, ProjectRegistry<ProjectInternal> projectRegistry) {
        return new ProjectFactory(instantiator, projectRegistry);
    }

    protected ListenerManager createListenerManager(ListenerManager listenerManager) {
        return listenerManager.createChild();
    }

    protected BuildOperationExecutor createBuildOperationExecutor(ListenerManager listenerManager, TimeProvider timeProvider, ProgressLoggerFactory progressLoggerFactory) {
        return new DefaultBuildOperationExecutor(listenerManager.getBroadcaster(InternalBuildListener.class), timeProvider, progressLoggerFactory);
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

    protected ActorFactory createActorFactory() {
        return new DefaultActorFactory(get(ExecutorFactory.class));
    }

    protected IGradlePropertiesLoader createGradlePropertiesLoader() {
        return new DefaultGradlePropertiesLoader(get(StartParameter.class));
    }

    protected BuildLoader createBuildLoader() {
        return new ProjectPropertySettingBuildLoader(
            get(IGradlePropertiesLoader.class),
            new InstantiatingBuildLoader(get(IProjectFactory.class)));
    }

    protected ProjectEvaluator createProjectEvaluator() {
        ConfigureActionsProjectEvaluator withActionsEvaluator = new ConfigureActionsProjectEvaluator(
            new PluginsProjectConfigureActions(get(ClassLoaderRegistry.class).getPluginsClassLoader()),
            new BuildScriptProcessor(get(ScriptPluginFactory.class)),
            new DelayedConfigurationActions()
        );
        return new LifecycleProjectEvaluator(withActionsEvaluator);
    }

    protected ITaskFactory createITaskFactory() {
        return new DependencyAutoWireTaskFactory(
            new AnnotationProcessingTaskFactory(
                new TaskFactory(
                    get(ClassGenerator.class))
            )
        );
    }

    protected ScriptCompilerFactory createScriptCompileFactory(ListenerManager listenerManager, FileCacheBackedScriptClassCompiler scriptCompiler,
                                                               CrossBuildInMemoryCachingScriptClassCache cache) {
        ScriptExecutionListener scriptExecutionListener = listenerManager.getBroadcaster(ScriptExecutionListener.class);
        return new DefaultScriptCompilerFactory(
            new BuildScopeInMemoryCachingScriptClassCompiler(cache, scriptCompiler),
            new DefaultScriptRunnerFactory(
                scriptExecutionListener,
                DirectInstantiator.INSTANCE
            )
        );
    }

    protected FileCacheBackedScriptClassCompiler createFileCacheBackedScriptClassCompiler(
        CacheRepository cacheRepository, final StartParameter startParameter,
        ProgressLoggerFactory progressLoggerFactory, ClassLoaderCache classLoaderCache, ImportsReader importsReader,
        CacheAccessingFileSnapshotter snapshotter, ClassLoaderRegistry registry) {
        CacheValidator scriptCacheInvalidator = new CacheValidator() {
            public boolean isValid() {
                return !startParameter.isRecompileScripts();
            }
        };
        return new FileCacheBackedScriptClassCompiler(
            cacheRepository,
            scriptCacheInvalidator,
            new DefaultScriptCompilationHandler(classLoaderCache, importsReader),
            progressLoggerFactory,
            snapshotter,
            classLoaderCache,
            registry);
    }

    protected ScriptPluginFactory createScriptPluginFactory() {
        return new ScriptPluginFactorySelector(defaultScriptPluginFactory(), this);
    }

    private DefaultScriptPluginFactory defaultScriptPluginFactory() {
        return new DefaultScriptPluginFactory(
            get(ScriptCompilerFactory.class),
            getFactory(LoggingManagerInternal.class),
            get(Instantiator.class),
            get(ScriptHandlerFactory.class),
            get(PluginRequestApplicator.class),
            get(FileLookup.class),
            get(DirectoryFileTreeFactory.class),
            get(DocumentationRegistry.class),
            get(ModelRuleSourceDetector.class),
            get(PluginRepositoryRegistry.class),
            get(PluginRepositoryFactory.class));
    }

    protected SettingsLoader createSettingsLoader(SettingsProcessor settingsProcessor, GradleLauncherFactory gradleLauncherFactory,
                                                  ClassLoaderScopeRegistry classLoaderScopeRegistry, CacheRepository cacheRepository,
                                                  BuildLoader buildLoader, BuildOperationExecutor buildOperationExecutor) {
        return new NotifyingSettingsLoader(
            new SettingsHandler(
                new DefaultSettingsFinder(
                    new BuildLayoutFactory()),
                settingsProcessor,
                new BuildSourceBuilder(
                    gradleLauncherFactory,
                    classLoaderScopeRegistry.getCoreAndPluginsScope(),
                    cacheRepository,
                    buildOperationExecutor)),
            buildLoader);
    }

    protected InitScriptHandler createInitScriptHandler(ScriptPluginFactory scriptPluginFactory, ScriptHandlerFactory scriptHandlerFactory, BuildOperationExecutor buildOperationExecutor) {
        return new InitScriptHandler(
            new DefaultInitScriptProcessor(
                scriptPluginFactory,
                scriptHandlerFactory
            ),
            buildOperationExecutor
        );
    }

    protected SettingsProcessor createSettingsProcessor(ScriptPluginFactory scriptPluginFactory, ScriptHandlerFactory scriptHandlerFactory, Instantiator instantiator,
                                                        ServiceRegistryFactory serviceRegistryFactory, IGradlePropertiesLoader propertiesLoader, BuildOperationExecutor buildOperationExecutor) {
        return new NotifyingSettingsProcessor(
            new PropertiesLoadingSettingsProcessor(
                new ScriptEvaluatingSettingsProcessor(
                    scriptPluginFactory,
                    scriptHandlerFactory,
                    new SettingsFactory(
                        instantiator,
                        serviceRegistryFactory
                    ),
                    propertiesLoader
                ),
                propertiesLoader
            ),
            buildOperationExecutor);
    }

    protected ExceptionAnalyser createExceptionAnalyser(ListenerManager listenerManager, LoggingConfiguration loggingConfiguration) {
        ExceptionAnalyser exceptionAnalyser = new MultipleBuildFailuresExceptionAnalyser(new DefaultExceptionAnalyser(listenerManager));
        if (loggingConfiguration.getShowStacktrace() != ShowStacktrace.ALWAYS_FULL) {
            exceptionAnalyser = new StackTraceSanitizingExceptionAnalyser(exceptionAnalyser);
        }
        return exceptionAnalyser;
    }

    protected ScriptHandlerFactory createScriptHandlerFactory() {
        return new DefaultScriptHandlerFactory(
            get(DependencyManagementServices.class),
            get(FileResolver.class),
            get(DependencyMetaDataProvider.class)
        );
    }

    protected ProjectConfigurer createProjectConfigurer(BuildCancellationToken cancellationToken) {
        return new TaskPathProjectEvaluator(cancellationToken);
    }

    protected BuildConfigurer createBuildConfigurer(ProjectConfigurer projectConfigurer) {
        return new DefaultBuildConfigurer(projectConfigurer);
    }

    protected ProjectAccessListener createProjectAccessListener() {
        return new DefaultProjectAccessListener();
    }

    protected ProfileEventAdapter createProfileEventAdapter() {
        return new ProfileEventAdapter(get(BuildRequestMetaData.class), get(TimeProvider.class), get(ListenerManager.class).getBroadcaster(ProfileListener.class));
    }

    protected PluginRegistry createPluginRegistry(ClassLoaderScopeRegistry scopeRegistry, PluginInspector pluginInspector) {
        return new DefaultPluginRegistry(pluginInspector, scopeRegistry.getCoreAndPluginsScope());
    }

    protected ServiceRegistryFactory createServiceRegistryFactory(final ServiceRegistry services) {
        return new BuildScopeServiceRegistryFactory(services);
    }

    protected ClassLoaderScopeRegistry createClassLoaderScopeRegistry(ClassLoaderRegistry classLoaderRegistry, ClassLoaderCache classLoaderCache) {
        return new DefaultClassLoaderScopeRegistry(classLoaderRegistry, classLoaderCache);
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

    private class DependencyMetaDataProviderImpl implements DependencyMetaDataProvider {
        public ModuleInternal getModule() {
            return new DefaultModule("unspecified", "unspecified", Project.DEFAULT_VERSION, Project.DEFAULT_STATUS);
        }
    }

    protected BuildOperationLoggerFactory createBuildOperationLoggerFactory() {
        return new DefaultBuildOperationLoggerFactory();
    }

    AuthenticationSchemeRegistry createAuthenticationSchemeRegistry() {
        return new DefaultAuthenticationSchemeRegistry();
    }

    @Override
    public void close() {
        if (singleUseSession) {
            // This song and dance is to let CompositeStoppable deal with making sure everything
            // is closed when something errors while stopping
            CompositeStoppable.stoppable(new Stoppable() {
                @Override
                public void stop() {
                    BuildScopeServices.super.close();
                }
            }, sessionServices).stop();
        } else {
            super.close();
        }
    }
}
