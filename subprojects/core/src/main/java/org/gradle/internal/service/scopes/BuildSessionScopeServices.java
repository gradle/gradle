/*
 * Copyright 2015 the original author or authors.
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

import org.apache.tools.ant.DirectoryScanner;
import org.gradle.StartParameter;
import org.gradle.api.Action;
import org.gradle.api.internal.FeaturePreviews;
import org.gradle.api.internal.attributes.DefaultImmutableAttributesFactory;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.BuildScopeFileTimeStampInspector;
import org.gradle.api.internal.changedetection.state.CachingFileHasher;
import org.gradle.api.internal.changedetection.state.CrossBuildFileHashCache;
import org.gradle.api.internal.changedetection.state.DefaultResourceSnapshotterCacheService;
import org.gradle.api.internal.changedetection.state.ResourceSnapshotterCacheService;
import org.gradle.api.internal.changedetection.state.SplitFileHasher;
import org.gradle.api.internal.changedetection.state.SplitResourceSnapshotterCacheService;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.internal.project.BuildOperationCrossProjectConfigurator;
import org.gradle.api.internal.project.CrossProjectConfigurator;
import org.gradle.api.model.ObjectFactory;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.CacheRepositoryServices;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.cache.internal.CleanupActionFactory;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.VersionStrategy;
import org.gradle.deployment.internal.DefaultDeploymentRegistry;
import org.gradle.groovy.scripts.internal.DefaultScriptSourceHasher;
import org.gradle.groovy.scripts.internal.ScriptSourceHasher;
import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.initialization.BuildEventConsumer;
import org.gradle.initialization.BuildRequestMetaData;
import org.gradle.initialization.layout.BuildLayout;
import org.gradle.initialization.layout.BuildLayoutConfiguration;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.initialization.layout.ProjectCacheDir;
import org.gradle.internal.buildevents.BuildStartedTime;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.featurelifecycle.DeprecatedUsageBuildOperationProgressBroadaster;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.Stat;
import org.gradle.internal.filewatch.PendingChangesManager;
import org.gradle.internal.fingerprint.FileCollectionFingerprinter;
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.fingerprint.FileCollectionSnapshotter;
import org.gradle.internal.fingerprint.classpath.CompileClasspathFingerprinter;
import org.gradle.internal.fingerprint.classpath.impl.DefaultCompileClasspathFingerprinter;
import org.gradle.internal.fingerprint.impl.AbsolutePathFileCollectionFingerprinter;
import org.gradle.internal.fingerprint.impl.DefaultFileCollectionFingerprinterRegistry;
import org.gradle.internal.fingerprint.impl.DefaultFileCollectionSnapshotter;
import org.gradle.internal.fingerprint.impl.IgnoredPathFileCollectionFingerprinter;
import org.gradle.internal.fingerprint.impl.NameOnlyFileCollectionFingerprinter;
import org.gradle.internal.fingerprint.impl.OutputFileCollectionFingerprinter;
import org.gradle.internal.fingerprint.impl.RelativePathFileCollectionFingerprinter;
import org.gradle.internal.hash.DefaultFileHasher;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationListenerManager;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resources.ProjectLeaseRegistry;
import org.gradle.internal.scopeids.PersistentScopeIdLoader;
import org.gradle.internal.scopeids.ScopeIdsServices;
import org.gradle.internal.scopeids.id.UserScopeId;
import org.gradle.internal.scopeids.id.WorkspaceScopeId;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.snapshot.FileSystemMirror;
import org.gradle.internal.snapshot.FileSystemSnapshotter;
import org.gradle.internal.snapshot.WellKnownFileLocations;
import org.gradle.internal.snapshot.impl.DefaultFileSystemSnapshotter;
import org.gradle.internal.time.Clock;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.internal.vfs.impl.VfsFileSystemSnapshotter;
import org.gradle.internal.work.AsyncWorkTracker;
import org.gradle.internal.work.DefaultAsyncWorkTracker;
import org.gradle.plugin.use.internal.InjectedPluginClasspath;
import org.gradle.process.internal.ExecFactory;

import java.io.File;
import java.util.List;

/**
 * Contains the services for a single build session, which could be a single build or multiple builds when in continuous mode.
 */
public class BuildSessionScopeServices extends DefaultServiceRegistry {

    public BuildSessionScopeServices(final ServiceRegistry parent, CrossBuildSessionScopeServices crossBuildSessionScopeServices, final StartParameter startParameter, BuildRequestMetaData buildRequestMetaData, ClassPath injectedPluginClassPath, BuildCancellationToken buildCancellationToken, BuildClientMetaData buildClientMetaData, BuildEventConsumer buildEventConsumer) {
        super(parent);
        addProvider(crossBuildSessionScopeServices);
        register(new Action<ServiceRegistration>() {
            @Override
            public void execute(ServiceRegistration registration) {
                add(StartParameter.class, startParameter);
                for (PluginServiceRegistry pluginServiceRegistry : parent.getAll(PluginServiceRegistry.class)) {
                    pluginServiceRegistry.registerBuildSessionServices(registration);
                }
            }
        });
        add(InjectedPluginClasspath.class, new InjectedPluginClasspath(injectedPluginClassPath));
        add(BuildCancellationToken.class, buildCancellationToken);
        add(BuildRequestMetaData.class, buildRequestMetaData);
        add(BuildClientMetaData.class, buildClientMetaData);
        add(BuildEventConsumer.class, buildEventConsumer);
        addProvider(new CacheRepositoryServices(startParameter.getGradleUserHomeDir(), startParameter.getProjectCacheDir()));

        // Must be no higher than this scope as needs cache repository services.
        addProvider(new ScopeIdsServices());
    }

    PendingChangesManager createPendingChangesManager(ListenerManager listenerManager) {
        return new PendingChangesManager(listenerManager);
    }

    DefaultDeploymentRegistry createDeploymentRegistry(PendingChangesManager pendingChangesManager, BuildOperationExecutor buildOperationExecutor, ObjectFactory objectFactory) {
        return new DefaultDeploymentRegistry(pendingChangesManager, buildOperationExecutor, objectFactory);
    }

    ListenerManager createListenerManager(ListenerManager parent) {
        return parent.createChild();
    }

    CrossProjectConfigurator createCrossProjectConfigurator(BuildOperationExecutor buildOperationExecutor) {
        return new BuildOperationCrossProjectConfigurator(buildOperationExecutor);
    }

    ProjectCacheDir createCacheLayout(
        BuildLayoutFactory buildLayoutFactory,
        Deleter deleter,
        ProgressLoggerFactory progressLoggerFactory,
        StartParameter startParameter
    ) {
        BuildLayout buildLayout = buildLayoutFactory.getLayoutFor(new BuildLayoutConfiguration(startParameter));
        File cacheDir = startParameter.getProjectCacheDir() != null ? startParameter.getProjectCacheDir() : new File(buildLayout.getRootDirectory(), ".gradle");
        return new ProjectCacheDir(cacheDir, progressLoggerFactory, deleter);
    }

    BuildScopeFileTimeStampInspector createFileTimeStampInspector(ProjectCacheDir projectCacheDir, CacheScopeMapping cacheScopeMapping, ListenerManager listenerManager) {
        File workDir = cacheScopeMapping.getBaseDirectory(projectCacheDir.getDir(), "fileChanges", VersionStrategy.CachePerVersion);
        BuildScopeFileTimeStampInspector timeStampInspector = new BuildScopeFileTimeStampInspector(workDir);
        listenerManager.addListener(timeStampInspector);
        return timeStampInspector;
    }

    CrossBuildFileHashCache createCrossBuildFileHashCache(ProjectCacheDir projectCacheDir, CacheScopeMapping cacheScopeMapping, CacheRepository cacheRepository, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        File cacheDir = cacheScopeMapping.getBaseDirectory(projectCacheDir.getDir(), "fileHashes", VersionStrategy.CachePerVersion);
        return new CrossBuildFileHashCache(cacheDir, cacheRepository, inMemoryCacheDecoratorFactory);
    }

    FileHasher createFileSnapshotter(FileHasher globalHasher, CrossBuildFileHashCache cacheAccess, StringInterner stringInterner, FileSystem fileSystem, BuildScopeFileTimeStampInspector fileTimeStampInspector, StreamHasher streamHasher, WellKnownFileLocations wellKnownFileLocations) {
        CachingFileHasher localHasher = new CachingFileHasher(new DefaultFileHasher(streamHasher), cacheAccess, stringInterner, fileTimeStampInspector, "fileHashes", fileSystem);
        return new SplitFileHasher(globalHasher, localHasher, wellKnownFileLocations);
    }

    ScriptSourceHasher createScriptSourceHasher() {
        return new DefaultScriptSourceHasher();
    }

    FileSystemSnapshotter createFileSystemSnapshotter(FileHasher hasher, StringInterner stringInterner, Stat stat, FileSystemMirror fileSystemMirror, VirtualFileSystem vfs) {
        return GradleUserHomeScopeServices.VFS_ENABLED
            ? new VfsFileSystemSnapshotter(vfs, stringInterner, hasher)
            : new DefaultFileSystemSnapshotter(hasher, stringInterner, stat, fileSystemMirror, DirectoryScanner.getDefaultExcludes());
    }

    FileCollectionSnapshotter createFileCollectionSnapshotter(FileSystemSnapshotter fileSystemSnapshotter, Stat stat) {
        return new DefaultFileCollectionSnapshotter(fileSystemSnapshotter, stat);
    }

    AbsolutePathFileCollectionFingerprinter createAbsolutePathFileCollectionFingerprinter(FileCollectionSnapshotter fileCollectionSnapshotter) {
        return new AbsolutePathFileCollectionFingerprinter(fileCollectionSnapshotter);
    }

    RelativePathFileCollectionFingerprinter createRelativePathFileCollectionFingerprinter(StringInterner stringInterner, FileCollectionSnapshotter fileCollectionSnapshotter) {
        return new RelativePathFileCollectionFingerprinter(stringInterner, fileCollectionSnapshotter);
    }

    NameOnlyFileCollectionFingerprinter createNameOnlyFileCollectionFingerprinter(FileCollectionSnapshotter fileCollectionSnapshotter) {
        return new NameOnlyFileCollectionFingerprinter(fileCollectionSnapshotter);
    }

    IgnoredPathFileCollectionFingerprinter createIgnoredPathFileCollectionFingerprinter(FileCollectionSnapshotter fileCollectionSnapshotter) {
        return new IgnoredPathFileCollectionFingerprinter(fileCollectionSnapshotter);
    }

    OutputFileCollectionFingerprinter createOutputFileCollectionFingerprinter(FileCollectionSnapshotter fileCollectionSnapshotter) {
        return new OutputFileCollectionFingerprinter(fileCollectionSnapshotter);
    }

    FileCollectionFingerprinterRegistry createFileCollectionFingerprinterRegistry(List<FileCollectionFingerprinter> fingerprinters) {
        return new DefaultFileCollectionFingerprinterRegistry(fingerprinters);
    }

    ResourceSnapshotterCacheService createResourceSnapshotterCacheService(ResourceSnapshotterCacheService globalCache, CrossBuildFileHashCache store, WellKnownFileLocations wellKnownFileLocations) {
        PersistentIndexedCache<HashCode, HashCode> resourceHashesCache = store.createCache(PersistentIndexedCacheParameters.of("resourceHashesCache", HashCode.class, new HashCodeSerializer()), 800000, true);
        DefaultResourceSnapshotterCacheService localCache = new DefaultResourceSnapshotterCacheService(resourceHashesCache);
        return new SplitResourceSnapshotterCacheService(globalCache, localCache, wellKnownFileLocations);
    }

    CompileClasspathFingerprinter createCompileClasspathFingerprinter(ResourceSnapshotterCacheService resourceSnapshotterCacheService, FileCollectionSnapshotter fileCollectionSnapshotter, StringInterner stringInterner) {
        return new DefaultCompileClasspathFingerprinter(resourceSnapshotterCacheService, fileCollectionSnapshotter, stringInterner);
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

    protected ExecFactory decorateExecFactory(ExecFactory execFactory, FileResolver fileResolver, FileCollectionFactory fileCollectionFactory, Instantiator instantiator, BuildCancellationToken buildCancellationToken, ObjectFactory objectFactory) {
        return execFactory.forContext(fileResolver, fileCollectionFactory, instantiator, buildCancellationToken, objectFactory);
    }

    DeprecatedUsageBuildOperationProgressBroadaster createDeprecatedUsageBuildOperationProgressBroadaster(
        Clock clock,
        BuildOperationListenerManager buildOperationListenerManager,
        CurrentBuildOperationRef currentBuildOperationRef
    ) {
        return new DeprecatedUsageBuildOperationProgressBroadaster(
            clock,
            buildOperationListenerManager.getBroadcaster(),
            currentBuildOperationRef
        );
    }
}
