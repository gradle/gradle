/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.session;

import org.gradle.StartParameter;
import org.gradle.api.internal.FeaturePreviews;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.attributes.DefaultImmutableAttributesFactory;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.BuildSessionScopeFileTimeStampInspector;
import org.gradle.api.internal.changedetection.state.CrossBuildFileHashCache;
import org.gradle.api.internal.changedetection.state.FileHasherStatistics;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.internal.project.BuildOperationCrossProjectConfigurator;
import org.gradle.api.internal.project.CrossProjectConfigurator;
import org.gradle.api.internal.tasks.userinput.DefaultUserInputHandler;
import org.gradle.api.internal.tasks.userinput.DefaultUserInputReader;
import org.gradle.api.internal.tasks.userinput.NonInteractiveUserInputHandler;
import org.gradle.api.internal.tasks.userinput.UserInputHandler;
import org.gradle.api.model.ObjectFactory;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.BuildScopeCacheDir;
import org.gradle.cache.internal.CleanupActionFactory;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.scopes.DefaultBuildTreeScopedCache;
import org.gradle.cache.scopes.BuildTreeScopedCache;
import org.gradle.deployment.internal.DefaultDeploymentRegistry;
import org.gradle.groovy.scripts.internal.DefaultScriptSourceHasher;
import org.gradle.groovy.scripts.internal.ScriptSourceHasher;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.initialization.layout.BuildLayoutConfiguration;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.initialization.layout.ProjectCacheDir;
import org.gradle.internal.build.BuildLayoutValidator;
import org.gradle.internal.buildevents.BuildStartedTime;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.event.DefaultListenerManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.filewatch.PendingChangesManager;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.hash.DefaultChecksumService;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.logging.sink.OutputEventListenerManager;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resources.ProjectLeaseRegistry;
import org.gradle.internal.scopeids.PersistentScopeIdLoader;
import org.gradle.internal.scopeids.ScopeIdsServices;
import org.gradle.internal.scopeids.id.UserScopeId;
import org.gradle.internal.scopeids.id.WorkspaceScopeId;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.time.Clock;
import org.gradle.internal.work.AsyncWorkTracker;
import org.gradle.internal.work.DefaultAsyncWorkTracker;
import org.gradle.plugin.use.internal.InjectedPluginClasspath;
import org.gradle.process.internal.ExecFactory;

import java.io.Closeable;
import java.io.File;
import java.util.List;

/**
 * Contains the services for a single build session, which could be a single build or multiple builds when in continuous mode.
 */
public class BuildSessionScopeServices {

    private final StartParameterInternal startParameter;
    private final BuildRequestMetaData buildRequestMetaData;
    private final ClassPath injectedPluginClassPath;
    private final BuildCancellationToken buildCancellationToken;
    private final BuildClientMetaData buildClientMetaData;
    private final BuildEventConsumer buildEventConsumer;

    public BuildSessionScopeServices(StartParameterInternal startParameter, BuildRequestMetaData buildRequestMetaData, ClassPath injectedPluginClassPath, BuildCancellationToken buildCancellationToken, BuildClientMetaData buildClientMetaData, BuildEventConsumer buildEventConsumer) {
        this.startParameter = startParameter;
        this.buildRequestMetaData = buildRequestMetaData;
        this.injectedPluginClassPath = injectedPluginClassPath;
        this.buildCancellationToken = buildCancellationToken;
        this.buildClientMetaData = buildClientMetaData;
        this.buildEventConsumer = buildEventConsumer;
    }

    void configure(ServiceRegistration registration, List<PluginServiceRegistry> pluginServiceRegistries) {
        registration.add(StartParameterInternal.class, startParameter);
        for (PluginServiceRegistry pluginServiceRegistry : pluginServiceRegistries) {
            pluginServiceRegistry.registerBuildSessionServices(registration);
        }
        registration.add(InjectedPluginClasspath.class, new InjectedPluginClasspath(injectedPluginClassPath));
        registration.add(BuildCancellationToken.class, buildCancellationToken);
        registration.add(BuildRequestMetaData.class, buildRequestMetaData);
        registration.add(BuildClientMetaData.class, buildClientMetaData);
        registration.add(BuildEventConsumer.class, buildEventConsumer);
        registration.add(CalculatedValueContainerFactory.class);
        registration.add(BuildLayoutValidator.class);

        // Must be no higher than this scope as needs cache repository services.
        registration.addProvider(new ScopeIdsServices());
    }

    PendingChangesManager createPendingChangesManager(ListenerManager listenerManager) {
        return new PendingChangesManager(listenerManager);
    }

    DefaultDeploymentRegistry createDeploymentRegistry(PendingChangesManager pendingChangesManager, BuildOperationExecutor buildOperationExecutor, ObjectFactory objectFactory) {
        return new DefaultDeploymentRegistry(pendingChangesManager, buildOperationExecutor, objectFactory);
    }

    DefaultListenerManager createListenerManager(DefaultListenerManager parent) {
        return parent.createChild(Scopes.BuildSession.class);
    }

    CrossProjectConfigurator createCrossProjectConfigurator(BuildOperationExecutor buildOperationExecutor) {
        return new BuildOperationCrossProjectConfigurator(buildOperationExecutor);
    }

    BuildLayout createBuildLayout(BuildLayoutFactory buildLayoutFactory, StartParameter startParameter) {
        return buildLayoutFactory.getLayoutFor(new BuildLayoutConfiguration(startParameter));
    }

    FileResolver createFileResolver(FileLookup fileLookup, BuildLayout buildLayout) {
        return fileLookup.getFileResolver(buildLayout.getRootDirectory());
    }

    ProjectCacheDir createProjectCacheDir(
        GradleUserHomeDirProvider userHomeDirProvider,
        BuildLayout buildLayout,
        Deleter deleter,
        ProgressLoggerFactory progressLoggerFactory,
        StartParameter startParameter
    ) {
        BuildScopeCacheDir cacheDir = new BuildScopeCacheDir(userHomeDirProvider, buildLayout, startParameter);
        return new ProjectCacheDir(cacheDir.getDir(), progressLoggerFactory, deleter);
    }

    BuildTreeScopedCache createBuildTreeScopedCache(ProjectCacheDir projectCacheDir, CacheRepository cacheRepository) {
        return new DefaultBuildTreeScopedCache(projectCacheDir.getDir(), cacheRepository);
    }

    BuildSessionScopeFileTimeStampInspector createFileTimeStampInspector(BuildTreeScopedCache scopedCache) {
        File workDir = scopedCache.baseDirForCache("fileChanges");
        return new BuildSessionScopeFileTimeStampInspector(workDir);
    }

    ScriptSourceHasher createScriptSourceHasher() {
        return new DefaultScriptSourceHasher();
    }

    DefaultImmutableAttributesFactory createImmutableAttributesFactory(IsolatableFactory isolatableFactory, NamedObjectInstantiator instantiator) {
        return new DefaultImmutableAttributesFactory(isolatableFactory, instantiator);
    }

    AsyncWorkTracker createAsyncWorkTracker(ProjectLeaseRegistry projectLeaseRegistry) {
        return new DefaultAsyncWorkTracker(projectLeaseRegistry);
    }

    UserScopeId createUserScopeId(PersistentScopeIdLoader persistentScopeIdLoader) {
        return persistentScopeIdLoader.getUser();
    }

    protected WorkspaceScopeId createWorkspaceScopeId(PersistentScopeIdLoader persistentScopeIdLoader) {
        return persistentScopeIdLoader.getWorkspace();
    }

    BuildStartedTime createBuildStartedTime(Clock clock, BuildRequestMetaData buildRequestMetaData) {
        long currentTime = clock.getCurrentTime();
        return BuildStartedTime.startingAt(Math.min(currentTime, buildRequestMetaData.getStartTime()));
    }

    FeaturePreviews createExperimentalFeatures() {
        return new FeaturePreviews();
    }

    CleanupActionFactory createCleanupActionFactory(BuildOperationExecutor buildOperationExecutor) {
        return new CleanupActionFactory(buildOperationExecutor);
    }

    protected ExecFactory decorateExecFactory(ExecFactory execFactory, FileResolver fileResolver, FileCollectionFactory fileCollectionFactory, Instantiator instantiator, BuildCancellationToken buildCancellationToken, ObjectFactory objectFactory, JavaModuleDetector javaModuleDetector) {
        return execFactory.forContext(fileResolver, fileCollectionFactory, instantiator, buildCancellationToken, objectFactory, javaModuleDetector);
    }

    CrossBuildFileHashCacheWrapper createCrossBuildChecksumCache(BuildTreeScopedCache scopedCache, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        CrossBuildFileHashCache crossBuildCache = new CrossBuildFileHashCache(scopedCache, inMemoryCacheDecoratorFactory, CrossBuildFileHashCache.Kind.CHECKSUMS);
        return new CrossBuildFileHashCacheWrapper(crossBuildCache);
    }

    ChecksumService createChecksumService(
        StringInterner stringInterner,
        FileSystem fileSystem,
        CrossBuildFileHashCacheWrapper crossBuildCache,
        BuildSessionScopeFileTimeStampInspector inspector,
        FileHasherStatistics.Collector statisticsCollector
    ) {
        return new DefaultChecksumService(stringInterner, crossBuildCache.delegate, fileSystem, inspector, statisticsCollector);
    }

    UserInputHandler createUserInputHandler(BuildRequestMetaData requestMetaData, OutputEventListenerManager outputEventListenerManager, Clock clock) {
        if (!requestMetaData.isInteractive()) {
            return new NonInteractiveUserInputHandler();
        }

        return new DefaultUserInputHandler(outputEventListenerManager.getBroadcaster(), clock, new DefaultUserInputReader());
    }

    // Wraps CrossBuildFileHashCache so that it doesn't conflict
    // with other services in different scopes
    static class CrossBuildFileHashCacheWrapper implements Closeable {
        private final CrossBuildFileHashCache delegate;

        private CrossBuildFileHashCacheWrapper(CrossBuildFileHashCache delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
