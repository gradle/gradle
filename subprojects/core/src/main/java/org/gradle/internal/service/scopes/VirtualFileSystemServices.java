/*
 * Copyright 2019 the original author or authors.
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
import net.rubygrapefruit.platform.NativeIntegrationUnavailableException;
import net.rubygrapefruit.platform.file.FileSystems;
import org.apache.tools.ant.DirectoryScanner;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.BuildSessionScopeFileTimeStampInspector;
import org.gradle.api.internal.changedetection.state.CachingFileHasher;
import org.gradle.api.internal.changedetection.state.CrossBuildFileHashCache;
import org.gradle.api.internal.changedetection.state.DefaultResourceSnapshotterCacheService;
import org.gradle.api.internal.changedetection.state.FileHasherStatistics;
import org.gradle.api.internal.changedetection.state.GradleUserHomeScopeFileTimeStampInspector;
import org.gradle.api.internal.changedetection.state.PropertiesFileFilter;
import org.gradle.api.internal.changedetection.state.ResourceEntryFilter;
import org.gradle.api.internal.changedetection.state.ResourceFilter;
import org.gradle.api.internal.changedetection.state.ResourceSnapshotterCacheService;
import org.gradle.api.internal.changedetection.state.SplitFileHasher;
import org.gradle.api.internal.changedetection.state.SplitResourceSnapshotterCacheService;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.initialization.loadercache.DefaultClasspathHasher;
import org.gradle.cache.GlobalCacheLocations;
import org.gradle.cache.IndexedCache;
import org.gradle.cache.IndexedCacheParameters;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.scopes.BuildTreeScopedCacheBuilderFactory;
import org.gradle.cache.scopes.GlobalScopedCacheBuilderFactory;
import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.internal.build.BuildAddedListener;
import org.gradle.internal.buildoption.IntegerInternalOption;
import org.gradle.internal.buildoption.InternalFlag;
import org.gradle.internal.buildoption.InternalOptions;
import org.gradle.internal.classloader.ClasspathHasher;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.FileCollectionFingerprinterRegistry;
import org.gradle.internal.execution.FileCollectionSnapshotter;
import org.gradle.internal.execution.InputFingerprinter;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.OutputSnapshotter;
import org.gradle.internal.execution.impl.DefaultFileCollectionFingerprinterRegistry;
import org.gradle.internal.execution.impl.DefaultInputFingerprinter;
import org.gradle.internal.execution.impl.DefaultOutputSnapshotter;
import org.gradle.internal.file.DefaultFileSystemDefaultExcludesProvider;
import org.gradle.internal.file.FileMetadataAccessor;
import org.gradle.internal.file.FileSystemDefaultExcludesProvider;
import org.gradle.internal.file.Stat;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.fingerprint.classpath.ClasspathFingerprinter;
import org.gradle.internal.fingerprint.classpath.impl.DefaultClasspathFingerprinter;
import org.gradle.internal.fingerprint.impl.DefaultFileCollectionSnapshotter;
import org.gradle.internal.fingerprint.impl.FileCollectionFingerprinterRegistrations;
import org.gradle.internal.hash.DefaultFileHasher;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.nativeintegration.NativeCapabilities;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.nativeintegration.services.NativeServices;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.service.PrivateService;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.snapshot.ValueSnapshotter;
import org.gradle.internal.snapshot.impl.DirectorySnapshotterStatistics;
import org.gradle.internal.vfs.FileSystemAccess;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.internal.vfs.impl.DefaultFileSystemAccess;
import org.gradle.internal.vfs.impl.DefaultSnapshotHierarchy;
import org.gradle.internal.watch.registry.FileWatcherRegistryFactory;
import org.gradle.internal.watch.registry.impl.DarwinFileWatcherRegistryFactory;
import org.gradle.internal.watch.registry.impl.LinuxFileWatcherRegistryFactory;
import org.gradle.internal.watch.registry.impl.WindowsFileWatcherRegistryFactory;
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem;
import org.gradle.internal.watch.vfs.FileChangeListeners;
import org.gradle.internal.watch.vfs.WatchableFileSystemDetector;
import org.gradle.internal.watch.vfs.impl.DefaultWatchableFileSystemDetector;
import org.gradle.internal.watch.vfs.impl.FileWatchingFilter;
import org.gradle.internal.watch.vfs.impl.WatchingNotSupportedVirtualFileSystem;
import org.gradle.internal.watch.vfs.impl.WatchingVirtualFileSystem;

import java.io.File;
import java.util.Optional;
import java.util.function.Predicate;

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_INSENSITIVE;
import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE;

public class VirtualFileSystemServices extends AbstractGradleModuleServices {

    /**
     * When file system watching is enabled, this system property can be used to invalidate the entire VFS.
     *
     * @see org.gradle.initialization.StartParameterBuildOptions.WatchFileSystemOption
     */
    public static final InternalFlag VFS_DROP_PROPERTY = new InternalFlag("org.gradle.vfs.drop");
    private static final int DEFAULT_MAX_HIERARCHIES_TO_WATCH = 50;
    public static final IntegerInternalOption MAX_HIERARCHIES_TO_WATCH_PROPERTY = new IntegerInternalOption("org.gradle.vfs.watch.hierarchies.max", DEFAULT_MAX_HIERARCHIES_TO_WATCH);
    private static final int FILE_HASHER_MEMORY_CACHE_SIZE = 400000;

    public static boolean isDropVfs(InternalOptions options) {
        return options.getOption(VFS_DROP_PROPERTY).get();
    }

    public static int getMaximumNumberOfWatchedHierarchies(InternalOptions options) {
        return options.getOption(MAX_HIERARCHIES_TO_WATCH_PROPERTY).get();
    }

    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.addProvider(new GlobalScopeServices());
    }

    @Override
    public void registerGradleUserHomeServices(ServiceRegistration registration) {
        registration.addProvider(new GradleUserHomeServices());
    }

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
        registration.addProvider(new BuildSessionServices());
    }

    private static class GlobalScopeServices implements ServiceRegistrationProvider {
        @Provides
        FileHasherStatistics.Collector createCachingFileHasherStatisticsCollector() {
            return new FileHasherStatistics.Collector();
        }

        @Provides
        DirectorySnapshotterStatistics.Collector createDirectorySnapshotterStatisticsCollector() {
            return new DirectorySnapshotterStatistics.Collector();
        }
    }

    @VisibleForTesting
    static class GradleUserHomeServices implements ServiceRegistrationProvider {

        @Provides
        @PrivateService
        CrossBuildFileHashCache createCrossBuildFileHashCache(GlobalScopedCacheBuilderFactory cacheBuilderFactory, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
            return new CrossBuildFileHashCache(cacheBuilderFactory, inMemoryCacheDecoratorFactory, CrossBuildFileHashCache.Kind.FILE_HASHES);
        }

        @Provides
        FileHasher createCachingFileHasher(
            FileHasherStatistics.Collector statisticsCollector,
            CrossBuildFileHashCache fileStore,
            FileSystem fileSystem,
            GradleUserHomeScopeFileTimeStampInspector fileTimeStampInspector,
            StreamHasher streamHasher,
            StringInterner stringInterner
        ) {
            CachingFileHasher fileHasher = new CachingFileHasher(new DefaultFileHasher(streamHasher), fileStore, stringInterner, fileTimeStampInspector, "fileHashes", fileSystem, FILE_HASHER_MEMORY_CACHE_SIZE, statisticsCollector);
            fileTimeStampInspector.attach(fileHasher);
            return fileHasher;
        }

        @Provides
        FileWatchingFilter createFileWatchingFilter(GlobalCacheLocations globalCacheLocations, ListenerManager listenerManager) {
            // All the changes in global caches should be done by Gradle itself, so in order
            // to minimize the number of watches we don't watch anything within the global caches.
            FileWatchingFilter fileWatchingFilter = new FileWatchingFilter(globalCacheLocations.getCacheRoots());
            listenerManager.addListener(new RootBuildLifecycleListener() {
                @Override
                public void afterStart() {
                    fileWatchingFilter.buildStarted();
                }

                @Override
                public void beforeComplete() {
                    fileWatchingFilter.buildFinished();
                }
            });
            return fileWatchingFilter;
        }

        @Provides
        WatchableFileSystemDetector createWatchableFileSystemDetector(FileSystems fileSystems) {
            return new DefaultWatchableFileSystemDetector(fileSystems);
        }

        @Provides
        BuildLifecycleAwareVirtualFileSystem createVirtualFileSystem(
            FileWatchingFilter fileWatchingFilter,
            DocumentationRegistry documentationRegistry,
            NativeCapabilities nativeCapabilities,
            ListenerManager listenerManager,
            FileChangeListeners fileChangeListeners,
            NativeServices.FileEventFunctionsProvider fileEvents,
            FileSystem fileSystem,
            WatchableFileSystemDetector watchableFileSystemDetector
        ) {
            CaseSensitivity caseSensitivity = fileSystem.isCaseSensitive() ? CASE_SENSITIVE : CASE_INSENSITIVE;
            SnapshotHierarchy root = DefaultSnapshotHierarchy.empty(caseSensitivity);

            BuildLifecycleAwareVirtualFileSystem virtualFileSystem = determineWatcherRegistryFactory(
                OperatingSystem.current(),
                nativeCapabilities,
                fileEvents,
                fileWatchingFilter.getImmutableLocations()::contains)
                .<BuildLifecycleAwareVirtualFileSystem>map(watcherRegistryFactory -> new WatchingVirtualFileSystem(
                    watcherRegistryFactory,
                    root,
                    sectionId -> documentationRegistry.getDocumentationRecommendationFor("details", "file_system_watching", sectionId),
                    fileWatchingFilter,
                    watchableFileSystemDetector,
                    fileChangeListeners
                ))
                .orElse(new WatchingNotSupportedVirtualFileSystem(root));
            listenerManager.addListener((BuildAddedListener) buildState -> {
                    File buildRootDir = buildState.getBuildRootDir();
                    virtualFileSystem.registerWatchableHierarchy(buildRootDir);
                }
            );
            return virtualFileSystem;
        }

        @Provides
        FileSystemAccess createFileSystemAccess(
            FileHasher hasher,
            VirtualFileSystem virtualFileSystem,
            FileMetadataAccessor stat,
            StringInterner stringInterner,
            FileSystemAccess.WriteListener writeListener,
            DirectorySnapshotterStatistics.Collector statisticsCollector,
            ListenerManager listenerManager
        ) {
            DefaultFileSystemAccess defaultFileSystemAccess = new DefaultFileSystemAccess(
                hasher,
                stringInterner,
                stat,
                virtualFileSystem,
                writeListener,
                statisticsCollector,
                DirectoryScanner.getDefaultExcludes()
            );
            listenerManager.addListener(defaultFileSystemAccess);

            return defaultFileSystemAccess;
        }

        private static Optional<FileWatcherRegistryFactory> determineWatcherRegistryFactory(
            OperatingSystem operatingSystem,
            NativeCapabilities nativeCapabilities,
            NativeServices.FileEventFunctionsProvider fileEvents,
            Predicate<String> immutableLocationsFilter
        ) {
            if (nativeCapabilities.useFileSystemWatching()) {
                try {
                    if (operatingSystem.isMacOsX()) {
                        return Optional.of(new DarwinFileWatcherRegistryFactory(fileEvents::getFunctions, immutableLocationsFilter));
                    } else if (operatingSystem.isWindows()) {
                        return Optional.of(new WindowsFileWatcherRegistryFactory(fileEvents::getFunctions, immutableLocationsFilter));
                    } else if (operatingSystem.isLinux()) {
                        return Optional.of(new LinuxFileWatcherRegistryFactory(fileEvents::getFunctions, immutableLocationsFilter));
                    }
                } catch (NativeIntegrationUnavailableException e) {
                    NativeServices.logFileSystemWatchingUnavailable(e);
                }
            }
            return Optional.empty();
        }

        @Provides
        FileCollectionSnapshotter createFileCollectionSnapshotter(FileSystemAccess fileSystemAccess, Stat stat) {
            return new DefaultFileCollectionSnapshotter(fileSystemAccess, stat);
        }

        @Provides
        ResourceSnapshotterCacheService createResourceSnapshotterCacheService(CrossBuildFileHashCache store) {
            IndexedCache<HashCode, HashCode> resourceHashesCache = store.createIndexedCache(
                IndexedCacheParameters.of("resourceHashesCache", HashCode.class, new HashCodeSerializer()),
                400000,
                true);
            return new DefaultResourceSnapshotterCacheService(resourceHashesCache);
        }

        @Provides
        ClasspathFingerprinter createClasspathFingerprinter(ResourceSnapshotterCacheService resourceSnapshotterCacheService, FileCollectionSnapshotter fileCollectionSnapshotter, StringInterner stringInterner) {
            return new DefaultClasspathFingerprinter(resourceSnapshotterCacheService, fileCollectionSnapshotter, ResourceFilter.FILTER_NOTHING, ResourceEntryFilter.FILTER_NOTHING, PropertiesFileFilter.FILTER_NOTHING, stringInterner, LineEndingSensitivity.DEFAULT);
        }

        @Provides
        ClasspathHasher createClasspathHasher(ClasspathFingerprinter fingerprinter, FileCollectionFactory fileCollectionFactory) {
            return new DefaultClasspathHasher(fingerprinter, fileCollectionFactory);
        }

        @Provides
        FileChangeListeners createFileChangeListeners(ListenerManager listenerManager) {
            return new DefaultFileChangeListeners(listenerManager);
        }
    }

    @VisibleForTesting
    static class BuildSessionServices implements ServiceRegistrationProvider {

        @Provides
        FileSystemDefaultExcludesProvider createFileSystemDefaultExcludesProvider(ListenerManager listenerManager) {
            return new DefaultFileSystemDefaultExcludesProvider(listenerManager);
        }

        @Provides
        @PrivateService
        CrossBuildFileHashCache createCrossBuildFileHashCache(BuildTreeScopedCacheBuilderFactory cacheBuilderFactory, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
            return new CrossBuildFileHashCache(cacheBuilderFactory, inMemoryCacheDecoratorFactory, CrossBuildFileHashCache.Kind.FILE_HASHES);
        }

        @Provides
        FileHasher createFileHasher(
            GlobalCacheLocations globalCacheLocations,
            BuildSessionScopeFileTimeStampInspector fileTimeStampInspector,
            CrossBuildFileHashCache cacheAccess,
            FileHasher globalHasher,
            FileSystem fileSystem,
            StreamHasher streamHasher,
            StringInterner stringInterner,
            FileHasherStatistics.Collector statisticsCollector
        ) {
            CachingFileHasher localHasher = new CachingFileHasher(new DefaultFileHasher(streamHasher), cacheAccess, stringInterner, fileTimeStampInspector, "fileHashes", fileSystem, FILE_HASHER_MEMORY_CACHE_SIZE, statisticsCollector);
            return new SplitFileHasher(globalHasher, localHasher, globalCacheLocations);
        }

        @Provides
        FileSystemAccess createFileSystemAccess(
            FileHasher hasher,
            ListenerManager listenerManager,
            FileMetadataAccessor stat,
            StringInterner stringInterner,
            VirtualFileSystem root,
            FileSystemAccess.WriteListener writeListener,
            DirectorySnapshotterStatistics.Collector statisticsCollector
        ) {
            DefaultFileSystemAccess buildSessionsScopedVirtualFileSystem = new DefaultFileSystemAccess(
                hasher,
                stringInterner,
                stat,
                root,
                writeListener,
                statisticsCollector,
                DirectoryScanner.getDefaultExcludes()
            );

            listenerManager.addListener(buildSessionsScopedVirtualFileSystem);
            listenerManager.addListener((OutputChangeListener) buildSessionsScopedVirtualFileSystem::invalidate);

            return buildSessionsScopedVirtualFileSystem;
        }

        @Provides
        FileCollectionSnapshotter createFileCollectionSnapshotter(FileSystemAccess fileSystemAccess, Stat stat) {
            return new DefaultFileCollectionSnapshotter(fileSystemAccess, stat);
        }

        @Provides
        OutputSnapshotter createOutputSnapshotter(FileCollectionSnapshotter fileCollectionSnapshotter) {
            return new DefaultOutputSnapshotter(fileCollectionSnapshotter);
        }

        @Provides
        FileCollectionFingerprinterRegistrations createFileCollectionFingerprinterRegistrations(
            StringInterner stringInterner,
            FileCollectionSnapshotter fileCollectionSnapshotter,
            ResourceSnapshotterCacheService resourceSnapshotterCacheService
        ) {
            return new FileCollectionFingerprinterRegistrations(
                stringInterner,
                fileCollectionSnapshotter,
                resourceSnapshotterCacheService,
                ResourceFilter.FILTER_NOTHING,
                ResourceEntryFilter.FILTER_NOTHING,
                PropertiesFileFilter.FILTER_NOTHING
            );
        }

        @Provides
        FileCollectionFingerprinterRegistry createFileCollectionFingerprinterRegistry(FileCollectionFingerprinterRegistrations fileCollectionFingerprinterRegistrations) {
            return new DefaultFileCollectionFingerprinterRegistry(fileCollectionFingerprinterRegistrations.getRegistrants());
        }

        @Provides
        InputFingerprinter createInputFingerprinter(
            FileCollectionSnapshotter snapshotter,
            FileCollectionFingerprinterRegistry fingerprinterRegistry,
            ValueSnapshotter valueSnapshotter
        ) {
            return new DefaultInputFingerprinter(snapshotter, fingerprinterRegistry, valueSnapshotter);
        }

        @Provides
        ResourceSnapshotterCacheService createResourceSnapshotterCacheService(
            GlobalCacheLocations globalCacheLocations,
            CrossBuildFileHashCache store,
            ResourceSnapshotterCacheService globalCache
        ) {
            IndexedCache<HashCode, HashCode> resourceHashesCache = store.createIndexedCache(IndexedCacheParameters.of("resourceHashesCache", HashCode.class, new HashCodeSerializer()), 800000, true);
            DefaultResourceSnapshotterCacheService localCache = new DefaultResourceSnapshotterCacheService(resourceHashesCache);
            return new SplitResourceSnapshotterCacheService(globalCache, localCache, globalCacheLocations);
        }
    }
}
