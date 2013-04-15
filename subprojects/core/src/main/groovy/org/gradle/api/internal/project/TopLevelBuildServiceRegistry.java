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

import org.gradle.StartParameter;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Module;
import org.gradle.api.internal.*;
import org.gradle.api.internal.artifacts.DefaultModule;
import org.gradle.api.internal.artifacts.DependencyManagementServices;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.classpath.PluginModuleRegistry;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.internal.initialization.DefaultScriptHandlerFactory;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.plugins.ResolveDeferredConfigurableAction;
import org.gradle.api.internal.project.taskfactory.AnnotationProcessingTaskFactory;
import org.gradle.api.internal.project.taskfactory.DependencyAutoWireTaskFactory;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.internal.project.taskfactory.TaskFactory;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.CacheValidator;
import org.gradle.cache.internal.CacheFactory;
import org.gradle.cache.internal.DefaultCacheRepository;
import org.gradle.configuration.*;
import org.gradle.groovy.scripts.DefaultScriptCompilerFactory;
import org.gradle.groovy.scripts.ScriptCompilerFactory;
import org.gradle.groovy.scripts.ScriptExecutionListener;
import org.gradle.groovy.scripts.internal.*;
import org.gradle.initialization.*;
import org.gradle.internal.Factory;
import org.gradle.internal.TimeProvider;
import org.gradle.internal.TrueTimeProvider;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceLocator;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.listener.ListenerManager;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.messaging.actor.ActorFactory;
import org.gradle.messaging.actor.internal.DefaultActorFactory;
import org.gradle.messaging.remote.MessagingServer;
import org.gradle.process.internal.DefaultWorkerProcessFactory;
import org.gradle.process.internal.WorkerProcessBuilder;
import org.gradle.process.internal.child.WorkerProcessClassPathProvider;
import org.gradle.profile.ProfileEventAdapter;
import org.gradle.profile.ProfileListener;
import org.gradle.util.ClassLoaderFactory;
import org.gradle.util.MultiParentClassLoader;

/**
 * Contains the singleton services which are shared by all builds executed by a single {@link org.gradle.GradleLauncher} invocation.
 */
public class TopLevelBuildServiceRegistry extends DefaultServiceRegistry implements ServiceRegistryFactory {
    private final StartParameter startParameter;

    public TopLevelBuildServiceRegistry(final ServiceRegistry parent, final StartParameter startParameter) {
        super(parent);
        this.startParameter = startParameter;
        add(StartParameter.class, startParameter);
    }

    protected ImportsReader createImportsReader() {
        return new ImportsReader();
    }

    protected TimeProvider createTimeProvider() {
        return new TrueTimeProvider();
    }

    protected ExecutorFactory createExecutorFactory() {
        return new DefaultExecutorFactory();
    }

    protected IProjectFactory createProjectFactory() {
        return new ProjectFactory(get(Instantiator.class));
    }

    protected ListenerManager createListenerManager(ListenerManager listenerManager) {
        return listenerManager.createChild();
    }

    protected ClassPathRegistry createClassPathRegistry() {
        return new DefaultClassPathRegistry(
                new DefaultClassPathProvider(get(ModuleRegistry.class)),
                new DependencyClassPathProvider(get(ModuleRegistry.class), get(PluginModuleRegistry.class)),
                new WorkerProcessClassPathProvider(get(CacheRepository.class), get(ModuleRegistry.class)));
    }

    protected IsolatedAntBuilder createIsolatedAntBuilder() {
        return new DefaultIsolatedAntBuilder(get(ClassPathRegistry.class), get(ClassLoaderFactory.class));
    }

    protected ActorFactory createActorFactory() {
        return new DefaultActorFactory(get(ExecutorFactory.class));
    }

    protected IGradlePropertiesLoader createGradlePropertiesLoader() {
        return new DefaultGradlePropertiesLoader(startParameter);
    }

    protected BuildLoader createBuildLoader() {
        return new ProjectPropertySettingBuildLoader(
                get(IGradlePropertiesLoader.class),
                new InstantiatingBuildLoader(get(IProjectFactory.class)));
    }

    protected CacheFactory createCacheFactory() {
        return getFactory(CacheFactory.class).create();
    }

    protected CacheRepository createCacheRepository() {
        CacheFactory factory = get(CacheFactory.class);
        return new DefaultCacheRepository(startParameter.getGradleUserHomeDir(), startParameter.getProjectCacheDir(),
                startParameter.getCacheUsage(), factory);
    }

    protected ProjectEvaluator createProjectEvaluator() {
        ConfigureActionsProjectEvaluator withActionsEvaluator = new ConfigureActionsProjectEvaluator(
                new BuildScriptProcessor(get(ScriptPluginFactory.class)),
                new PluginsProjectConfigureActions(get(ClassLoaderRegistry.class).getPluginsClassLoader()),
                new ResolveDeferredConfigurableAction()
        );
        return new LifecycleProjectEvaluator(withActionsEvaluator);
    }

    protected ITaskFactory createITaskFactory() {
        return new DependencyAutoWireTaskFactory(
                new AnnotationProcessingTaskFactory(
                        new TaskFactory(
                                get(ClassGenerator.class))));
    }

    protected ScriptCompilerFactory createScriptCompileFactory() {
        ScriptExecutionListener scriptExecutionListener = get(ListenerManager.class).getBroadcaster(ScriptExecutionListener.class);
        EmptyScriptGenerator emptyScriptGenerator = new AsmBackedEmptyScriptGenerator();
        CacheValidator scriptCacheInvalidator = new CacheValidator() {
            public boolean isValid() {
                return !get(StartParameter.class).isRecompileScripts();
            }
        };
        return new DefaultScriptCompilerFactory(
                new CachingScriptClassCompiler(
                        new ShortCircuitEmptyScriptCompiler(
                                new FileCacheBackedScriptClassCompiler(
                                        get(CacheRepository.class),
                                        scriptCacheInvalidator,
                                        new DefaultScriptCompilationHandler(
                                                emptyScriptGenerator)),
                                emptyScriptGenerator)),
                new DefaultScriptRunnerFactory(scriptExecutionListener));
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
        return get(ClassLoaderRegistry.class).createScriptClassLoader();
    }

    protected InitScriptHandler createInitScriptHandler() {
        return new InitScriptHandler(
                new DefaultInitScriptProcessor(get(ScriptPluginFactory.class))
        );
    }

    protected SettingsProcessor createSettingsProcessor() {
        return new PropertiesLoadingSettingsProcessor(
                new ScriptEvaluatingSettingsProcessor(
                        get(ScriptPluginFactory.class),
                        new SettingsFactory(
                                new DefaultProjectDescriptorRegistry(),
                                get(Instantiator.class),
                                this
                        ),
                        get(IGradlePropertiesLoader.class)),
                get(IGradlePropertiesLoader.class));
    }

    protected ExceptionAnalyser createExceptionAnalyser() {
        return new MultipleBuildFailuresExceptionAnalyser(new DefaultExceptionAnalyser(get(ListenerManager.class)));
    }

    protected ScriptHandlerFactory createScriptHandlerFactory() {
        return new DefaultScriptHandlerFactory(
                get(DependencyManagementServices.class),
                get(FileResolver.class),
                new DependencyMetaDataProviderImpl());
    }

    protected FileResolver createFileResolver() {
        return new IdentityFileResolver();
    }

    protected Factory<WorkerProcessBuilder> createWorkerProcessFactory() {
        ClassPathRegistry classPathRegistry = get(ClassPathRegistry.class);
        return new DefaultWorkerProcessFactory(startParameter.getLogLevel(), get(MessagingServer.class), classPathRegistry,
                new IdentityFileResolver(), new LongIdGenerator());
    }

    protected BuildConfigurer createBuildConfigurer() {
        return new DefaultBuildConfigurer();
    }

    protected ProjectAccessListener createProjectAccessListener() {
        return new DefaultProjectAccessListener();
    }

    protected ProfileEventAdapter createProfileEventAdapter() {
        return new ProfileEventAdapter(get(BuildRequestMetaData.class), get(TimeProvider.class), get(ListenerManager.class).getBroadcaster(ProfileListener.class));
    }

    protected DependencyManagementServices createDependencyManagementServices() {
        ClassLoader coreImplClassLoader = get(ClassLoaderRegistry.class).getCoreImplClassLoader();
        ServiceLocator serviceLocator = new ServiceLocator(coreImplClassLoader);
        return serviceLocator.getFactory(DependencyManagementServices.class).newInstance(this);
    }

    public ServiceRegistryFactory createFor(Object domainObject) {
        if (domainObject instanceof GradleInternal) {
            return new GradleInternalServiceRegistry(this, (GradleInternal) domainObject);
        }
        if (domainObject instanceof SettingsInternal) {
            return new SettingsInternalServiceRegistry(this, (SettingsInternal) domainObject);
        }
        throw new IllegalArgumentException(String.format("Cannot create services for unknown domain object of type %s.",
                domainObject.getClass().getSimpleName()));
    }

    private class DependencyMetaDataProviderImpl implements DependencyMetaDataProvider {
        public Module getModule() {
            return new DefaultModule("unspecified", "unspecified", Project.DEFAULT_VERSION, Project.DEFAULT_STATUS);
        }
    }
}
