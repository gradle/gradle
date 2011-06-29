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
import org.gradle.api.artifacts.maven.MavenFactory;
import org.gradle.api.execution.TaskActionListener;
import org.gradle.api.internal.*;
import org.gradle.api.internal.artifacts.DefaultModule;
import org.gradle.api.internal.artifacts.DependencyManagementServices;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.dsl.DefaultPublishArtifactFactory;
import org.gradle.api.internal.artifacts.dsl.PublishArtifactFactory;
import org.gradle.api.internal.changedetection.*;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.internal.initialization.DefaultScriptHandlerFactory;
import org.gradle.api.internal.initialization.ScriptHandlerFactory;
import org.gradle.api.internal.project.taskfactory.AnnotationProcessingTaskFactory;
import org.gradle.api.internal.project.taskfactory.DependencyAutoWireTaskFactory;
import org.gradle.api.internal.project.taskfactory.ITaskFactory;
import org.gradle.api.internal.project.taskfactory.TaskFactory;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.execution.*;
import org.gradle.cache.AutoCloseCacheFactory;
import org.gradle.cache.CacheFactory;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.DefaultCacheRepository;
import org.gradle.configuration.*;
import org.gradle.groovy.scripts.*;
import org.gradle.initialization.*;
import org.gradle.listener.ListenerManager;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.messaging.actor.ActorFactory;
import org.gradle.messaging.actor.internal.DefaultActorFactory;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;
import org.gradle.messaging.concurrent.ExecutorFactory;
import org.gradle.messaging.remote.MessagingServer;
import org.gradle.process.internal.DefaultWorkerProcessFactory;
import org.gradle.process.internal.WorkerProcessBuilder;
import org.gradle.process.internal.child.WorkerProcessClassPathProvider;
import org.gradle.util.*;

import java.io.File;

/**
 * Contains the singleton services which are shared by all builds executed by a single {@link org.gradle.GradleLauncher}
 * invocation.
 */
public class TopLevelBuildServiceRegistry extends DefaultServiceRegistry implements ServiceRegistryFactory {
    private final StartParameter startParameter;

    public TopLevelBuildServiceRegistry(final ServiceRegistry parent, final StartParameter startParameter) {
        super(parent);
        this.startParameter = startParameter;
        add(StartParameter.class, startParameter);
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
                new SkipOnlyIfTaskExecuter(
                        new SkipTaskWithNoActionsExecuter(
                                new SkipEmptySourceFilesTaskExecuter(
                                        new ValidatingTaskExecuter(
                                                new SkipUpToDateTaskExecuter(
                                                        new PostExecutionAnalysisTaskExecuter(
                                                                new ExecuteActionsTaskExecuter(
                                                                        get(ListenerManager.class).getBroadcaster(TaskActionListener.class))),
                                                        get(TaskArtifactStateRepository.class)))))));
    }

    protected CacheRepository createCacheRepository() {
        return new DefaultCacheRepository(startParameter.getGradleUserHomeDir(), startParameter.getProjectCacheDir(),
                startParameter.getCacheUsage(), get(CacheFactory.class));
    }

    protected ProjectEvaluator createProjectEvaluator() {
        return new LifecycleProjectEvaluator(
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
        return new FileCacheBroadcastTaskArtifactStateRepository(
                new ShortCircuitTaskArtifactStateRepository(
                        startParameter,
                        new DefaultTaskArtifactStateRepository(cacheRepository,
                                fileSnapshotter,
                                outputFilesSnapshotter)),
                new DefaultFileCacheListener());
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
        return get(ClassLoaderRegistry.class).createScriptClassLoader();
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
        return new DefaultBuildConfigurer(
                new ProjectEvaluationConfigurer(),
                new ProjectDependencies2TaskResolver(),
                new ImplicitTasksConfigurer());
    }

    protected MavenFactory createMavenFactory() {
        return get(DependencyManagementServices.class).get(MavenFactory.class);
    }

    protected DependencyManagementServices createDependencyManagementServices() {
        ClassLoader coreImplClassLoader = get(ClassLoaderRegistry.class).getCoreImplClassLoader();
        try {
            Class<?> implClass = coreImplClassLoader.loadClass("org.gradle.api.internal.artifacts.DefaultDependencyManagementServices");
            return (DependencyManagementServices) implClass.getConstructor(ServiceRegistry.class).newInstance(this);
        } catch (Exception e) {
            throw UncheckedException.asUncheckedException(e);
        }
    }

    public ServiceRegistryFactory createFor(Object domainObject) {
        if (domainObject instanceof GradleInternal) {
            return new GradleInternalServiceRegistry(this, (GradleInternal) domainObject);
        }
        throw new IllegalArgumentException(String.format("Cannot create services for unknown domain object of type %s.",
                domainObject.getClass().getSimpleName()));
    }

    private class DependencyMetaDataProviderImpl implements DependencyMetaDataProvider {
        public File getGradleUserHomeDir() {
            return startParameter.getGradleUserHomeDir();
        }

        public Module getModule() {
            return new DefaultModule("unspecified", "unspecified", Project.DEFAULT_VERSION, Project.DEFAULT_STATUS);
        }
    }
}
