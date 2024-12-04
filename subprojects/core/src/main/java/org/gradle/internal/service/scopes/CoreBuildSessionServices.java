/*
 * Copyright 2024 the original author or authors.
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
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.BuildSessionScopeFileTimeStampInspector;
import org.gradle.api.internal.changedetection.state.CrossBuildFileHashCache;
import org.gradle.api.internal.changedetection.state.FileHasherStatistics;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.archive.DecompressionCoordinator;
import org.gradle.api.internal.file.archive.DefaultDecompressionCoordinator;
import org.gradle.api.internal.project.BuildOperationCrossProjectConfigurator;
import org.gradle.api.internal.project.CrossProjectConfigurator;
import org.gradle.api.model.ObjectFactory;
import org.gradle.cache.UnscopedCacheBuilderFactory;
import org.gradle.cache.internal.BuildScopeCacheDir;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.scopes.DefaultBuildTreeScopedCacheBuilderFactory;
import org.gradle.cache.scopes.BuildTreeScopedCacheBuilderFactory;
import org.gradle.deployment.internal.DefaultDeploymentRegistry;
import org.gradle.deployment.internal.PendingChangesManager;
import org.gradle.groovy.scripts.internal.DefaultScriptSourceHasher;
import org.gradle.groovy.scripts.internal.ScriptSourceHasher;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.initialization.layout.BuildLayoutConfiguration;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.initialization.layout.ProjectCacheDir;
import org.gradle.internal.build.BuildLayoutValidator;
import org.gradle.internal.buildevents.BuildStartedTime;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.hash.DefaultChecksumService;
import org.gradle.internal.jvm.JavaModuleDetector;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.InMemoryCacheFactory;
import org.gradle.internal.model.StateTransitionControllerFactory;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.scopeids.PersistentScopeIdLoader;
import org.gradle.internal.scopeids.ScopeIdsServices;
import org.gradle.internal.scopeids.id.UserScopeId;
import org.gradle.internal.scopeids.id.WorkspaceScopeId;
import org.gradle.internal.service.PrivateService;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.time.Clock;
import org.gradle.internal.work.DefaultAsyncWorkTracker;
import org.gradle.process.internal.ClientExecHandleBuilderFactory;
import org.gradle.process.internal.DefaultClientExecHandleBuilderFactory;
import org.gradle.process.internal.ExecFactory;

import java.io.File;

public class CoreBuildSessionServices implements ServiceRegistrationProvider {
    void configure(ServiceRegistration registration) {
        registration.add(CalculatedValueContainerFactory.class);
        registration.add(InMemoryCacheFactory.class);
        registration.add(StateTransitionControllerFactory.class);
        registration.add(BuildLayoutValidator.class);
        registration.add(DefaultAsyncWorkTracker.class);

        // Must be no higher than this scope as needs cache repository services.
        registration.addProvider(new ScopeIdsServices());
    }

    @Provides
    PendingChangesManager createPendingChangesManager(ListenerManager listenerManager) {
        return new PendingChangesManager(listenerManager);
    }

    @Provides
    DefaultDeploymentRegistry createDeploymentRegistry(PendingChangesManager pendingChangesManager, BuildOperationRunner buildOperationRunner, ObjectFactory objectFactory) {
        return new DefaultDeploymentRegistry(pendingChangesManager, buildOperationRunner, objectFactory);
    }

    @Provides
    CrossProjectConfigurator createCrossProjectConfigurator(BuildOperationRunner buildOperationRunner) {
        return new BuildOperationCrossProjectConfigurator(buildOperationRunner);
    }

    @Provides
    BuildLayout createBuildLocations(BuildLayoutFactory buildLayoutFactory, StartParameter startParameter) {
        return buildLayoutFactory.getLayoutFor(new BuildLayoutConfiguration(startParameter));
    }

    @Provides
    FileResolver createFileResolver(FileLookup fileLookup, BuildLayout buildLayout) {
        return fileLookup.getFileResolver(buildLayout.getRootDirectory());
    }

    @Provides
    ProjectCacheDir createProjectCacheDir(
        GradleUserHomeDirProvider userHomeDirProvider,
        BuildLayout buildLayout,
        Deleter deleter,
        BuildOperationRunner buildOperationRunner,
        StartParameter startParameter
    ) {
        BuildScopeCacheDir cacheDir = new BuildScopeCacheDir(userHomeDirProvider, buildLayout, startParameter);
        return new ProjectCacheDir(cacheDir.getDir(), buildOperationRunner, deleter);
    }

    @Provides
    BuildTreeScopedCacheBuilderFactory createBuildTreeScopedCache(ProjectCacheDir projectCacheDir, UnscopedCacheBuilderFactory unscopedCacheBuilderFactory) {
        return new DefaultBuildTreeScopedCacheBuilderFactory(projectCacheDir.getDir(), unscopedCacheBuilderFactory);
    }

    @Provides
    DecompressionCoordinator createDecompressionCoordinator(BuildTreeScopedCacheBuilderFactory cacheBuilderFactory) {
        return new DefaultDecompressionCoordinator(cacheBuilderFactory);
    }

    @Provides
    BuildSessionScopeFileTimeStampInspector createFileTimeStampInspector(BuildTreeScopedCacheBuilderFactory cacheBuilderFactory) {
        File workDir = cacheBuilderFactory.baseDirForCache("fileChanges");
        return new BuildSessionScopeFileTimeStampInspector(workDir);
    }

    @Provides
    ScriptSourceHasher createScriptSourceHasher() {
        return new DefaultScriptSourceHasher();
    }

    @Provides
    UserScopeId createUserScopeId(PersistentScopeIdLoader persistentScopeIdLoader) {
        return persistentScopeIdLoader.getUser();
    }

    @Provides
    protected WorkspaceScopeId createWorkspaceScopeId(PersistentScopeIdLoader persistentScopeIdLoader) {
        return persistentScopeIdLoader.getWorkspace();
    }

    @Provides
    BuildStartedTime createBuildStartedTime(Clock clock, BuildRequestMetaData buildRequestMetaData) {
        long currentTime = clock.getCurrentTime();
        return BuildStartedTime.startingAt(Math.min(currentTime, buildRequestMetaData.getStartTime()));
    }

    /**
     * ClientExecHandleFactory is available in global scope, but we need to provide it here to use a build dir based file resolver.
     */
    @Provides
    ClientExecHandleBuilderFactory createExecHandleFactory(
        FileResolver fileResolver,
        ExecutorFactory executorFactory,
        BuildCancellationToken buildCancellationToken
    ) {
        return DefaultClientExecHandleBuilderFactory.of(fileResolver, executorFactory, buildCancellationToken);
    }

    @Provides
    protected ExecFactory decorateExecFactory(
        ExecFactory execFactory,
        FileResolver fileResolver,
        FileCollectionFactory fileCollectionFactory,
        Instantiator instantiator,
        BuildCancellationToken buildCancellationToken,
        ObjectFactory objectFactory,
        JavaModuleDetector javaModuleDetector,
        ClientExecHandleBuilderFactory clientExecHandleBuilderFactory
    ) {
        return execFactory.forContext()
            .withFileResolver(fileResolver)
            .withFileCollectionFactory(fileCollectionFactory)
            .withInstantiator(instantiator)
            .withBuildCancellationToken(buildCancellationToken)
            .withObjectFactory(objectFactory)
            .withJavaModuleDetector(javaModuleDetector)
            .withExecHandleFactory(clientExecHandleBuilderFactory)
            .build();
    }

    @Provides
    @PrivateService
    CrossBuildFileHashCache createCrossBuildChecksumCache(BuildTreeScopedCacheBuilderFactory cacheBuilderFactory, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        return new CrossBuildFileHashCache(cacheBuilderFactory, inMemoryCacheDecoratorFactory, CrossBuildFileHashCache.Kind.CHECKSUMS);
    }

    @Provides
    ChecksumService createChecksumService(
        StringInterner stringInterner,
        FileSystem fileSystem,
        CrossBuildFileHashCache crossBuildCache,
        BuildSessionScopeFileTimeStampInspector inspector,
        FileHasherStatistics.Collector statisticsCollector
    ) {
        return new DefaultChecksumService(stringInterner, crossBuildCache, fileSystem, inspector, statisticsCollector);
    }
}
