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
import org.apache.tools.ant.DirectoryScanner;
import org.gradle.BuildAdapter;
import org.gradle.StartParameter;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.BuildSessionScopeFileTimeStampInspector;
import org.gradle.api.internal.changedetection.state.CachingFileHasher;
import org.gradle.api.internal.changedetection.state.CrossBuildFileHashCache;
import org.gradle.api.internal.changedetection.state.DefaultResourceSnapshotterCacheService;
import org.gradle.api.internal.changedetection.state.GradleUserHomeScopeFileTimeStampInspector;
import org.gradle.api.internal.changedetection.state.ResourceEntryFilter;
import org.gradle.api.internal.changedetection.state.ResourceFilter;
import org.gradle.api.internal.changedetection.state.ResourceSnapshotterCacheService;
import org.gradle.api.internal.changedetection.state.SplitFileHasher;
import org.gradle.api.internal.changedetection.state.SplitResourceSnapshotterCacheService;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.initialization.loadercache.DefaultClasspathHasher;
import org.gradle.api.tasks.util.internal.PatternSpecFactory;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.GlobalCacheLocations;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.VersionStrategy;
import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.initialization.layout.ProjectCacheDir;
import org.gradle.internal.build.BuildAddedListener;
import org.gradle.internal.classloader.ClasspathHasher;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.file.Stat;
import org.gradle.internal.fingerprint.FileCollectionFingerprinter;
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.fingerprint.FileCollectionSnapshotter;
import org.gradle.internal.fingerprint.GenericFileTreeSnapshotter;
import org.gradle.internal.fingerprint.classpath.ClasspathFingerprinter;
import org.gradle.internal.fingerprint.classpath.CompileClasspathFingerprinter;
import org.gradle.internal.fingerprint.classpath.impl.DefaultClasspathFingerprinter;
import org.gradle.internal.fingerprint.classpath.impl.DefaultCompileClasspathFingerprinter;
import org.gradle.internal.fingerprint.impl.AbsolutePathFileCollectionFingerprinter;
import org.gradle.internal.fingerprint.impl.DefaultFileCollectionFingerprinterRegistry;
import org.gradle.internal.fingerprint.impl.DefaultFileCollectionSnapshotter;
import org.gradle.internal.fingerprint.impl.DefaultGenericFileTreeSnapshotter;
import org.gradle.internal.fingerprint.impl.IgnoredPathFileCollectionFingerprinter;
import org.gradle.internal.fingerprint.impl.NameOnlyFileCollectionFingerprinter;
import org.gradle.internal.fingerprint.impl.OutputFileCollectionFingerprinter;
import org.gradle.internal.fingerprint.impl.RelativePathFileCollectionFingerprinter;
import org.gradle.internal.hash.DefaultFileHasher;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.nativeintegration.NativeCapabilities;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.snapshot.AtomicSnapshotHierarchyReference;
import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.vfs.RoutingVirtualFileSystem;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.internal.vfs.impl.DefaultSnapshotHierarchy;
import org.gradle.internal.vfs.impl.DefaultVirtualFileSystem;
import org.gradle.internal.watch.registry.FileWatcherRegistryFactory;
import org.gradle.internal.watch.registry.impl.DarwinFileWatcherRegistryFactory;
import org.gradle.internal.watch.registry.impl.LinuxFileWatcherRegistryFactory;
import org.gradle.internal.watch.registry.impl.WindowsFileWatcherRegistryFactory;
import org.gradle.internal.watch.vfs.FileSystemWatchingHandler;
import org.gradle.internal.watch.vfs.impl.DefaultFileSystemWatchingHandler;
import org.gradle.internal.watch.vfs.impl.DelegatingDiffCapturingUpdateFunctionDecorator;
import org.gradle.internal.watch.vfs.impl.RecentlyCapturedSnapshots;
import org.gradle.internal.watch.vfs.impl.WatchingNotSupportedFileSystemWatchingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_INSENSITIVE;
import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE;

public class VirtualFileSystemServices extends AbstractPluginServiceRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(VirtualFileSystemServices.class);

    /**
     * Boolean system property to enable partial invalidation.
     */
    public static final String VFS_PARTIAL_INVALIDATION_ENABLED_PROPERTY = "org.gradle.unsafe.vfs.partial-invalidation";

    /**
     * Deprecated system property used to enable watching the file system.
     *
     * Using this property causes Gradle to emit a deprecation warning.
     */
    @Deprecated
    public static final String DEPRECATED_VFS_RETENTION_ENABLED_PROPERTY = "org.gradle.unsafe.vfs.retention";

    /**
     * When retention is enabled, this system property can be used to invalidate the entire VFS.
     *
     * @see org.gradle.initialization.StartParameterBuildOptions.WatchFileSystemOption
     */
    public static final String VFS_DROP_PROPERTY = "org.gradle.unsafe.vfs.drop";

    public static boolean isPartialInvalidationEnabled(StartParameterInternal startParameter) {
        return startParameter.isWatchFileSystem()
            || isSystemPropertyEnabled(VFS_PARTIAL_INVALIDATION_ENABLED_PROPERTY, startParameter.getSystemPropertiesArgs());
    }

    public static boolean isDropVfs(StartParameter startParameter) {
        return isSystemPropertyEnabled(VFS_DROP_PROPERTY, startParameter.getSystemPropertiesArgs());
    }

    public static boolean isDeprecatedVfsRetentionPropertyPresent(StartParameter startParameter) {
        return getSystemProperty(DEPRECATED_VFS_RETENTION_ENABLED_PROPERTY, startParameter.getSystemPropertiesArgs()) != null;
    }

    private static boolean isSystemPropertyEnabled(String systemProperty, Map<String, String> systemPropertiesArgs) {
        String value = getSystemProperty(systemProperty, systemPropertiesArgs);
        return value != null && !"false".equalsIgnoreCase(value);
    }

    @Nullable
    private static String getSystemProperty(String systemProperty, Map<String, String> systemPropertiesArgs) {
        return systemPropertiesArgs.getOrDefault(systemProperty, System.getProperty(systemProperty));
    }

    @Override
    public void registerGradleUserHomeServices(ServiceRegistration registration) {
        registration.addProvider(new GradleUserHomeServices());
    }

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
        registration.addProvider(new BuildSessionServices());
    }

    @VisibleForTesting
    static class GradleUserHomeServices {

        CrossBuildFileHashCache createCrossBuildFileHashCache(CacheRepository cacheRepository, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
            return new CrossBuildFileHashCache(null, cacheRepository, inMemoryCacheDecoratorFactory, CrossBuildFileHashCache.Kind.FILE_HASHES);
        }

        FileHasher createCachingFileHasher(StringInterner stringInterner, CrossBuildFileHashCache fileStore, FileSystem fileSystem, GradleUserHomeScopeFileTimeStampInspector fileTimeStampInspector, StreamHasher streamHasher) {
            CachingFileHasher fileHasher = new CachingFileHasher(new DefaultFileHasher(streamHasher), fileStore, stringInterner, fileTimeStampInspector, "fileHashes", fileSystem);
            fileTimeStampInspector.attach(fileHasher);
            return fileHasher;
        }

        RecentlyCapturedSnapshots createRecentlyCapturedSnapshots(ListenerManager listenerManager) {
            RecentlyCapturedSnapshots recentlyCapturedSnapshots = new RecentlyCapturedSnapshots();
            listenerManager.addListener(new RootBuildLifecycleListener() {
                @Override
                public void afterStart(GradleInternal gradle) {
                    recentlyCapturedSnapshots.buildStarted();
                }

                @Override
                public void beforeComplete(GradleInternal gradle) {
                    recentlyCapturedSnapshots.buildFinished();
                }
            });
            return recentlyCapturedSnapshots;
        }

        WatchFilter createWatchFilter(GlobalCacheLocations globalCacheLocations) {
            // All the changes in global caches should be done by Gradle itself, so in order
            // to minimize the number of watches we don't watch anything within the global caches.
            return path -> !globalCacheLocations.isInsideGlobalCache(path);
        }

        DelegatingDiffCapturingUpdateFunctionDecorator createUpdateFunctionDecorator(WatchFilter watchFilter) {
            return new DelegatingDiffCapturingUpdateFunctionDecorator(watchFilter);
        }

        AtomicSnapshotHierarchyReference createSnapshotHierarchyReference(FileSystem fileSystem) {
            CaseSensitivity caseSensitivity = fileSystem.isCaseSensitive() ? CASE_SENSITIVE : CASE_INSENSITIVE;
            return new AtomicSnapshotHierarchyReference(DefaultSnapshotHierarchy.empty(caseSensitivity));
        }

        VirtualFileSystem createVirtualFileSystem(
            FileHasher hasher,
            AtomicSnapshotHierarchyReference snapshotHierarchyReference,
            Stat stat,
            SnapshotHierarchy.DiffCapturingUpdateFunctionDecorator diffCapturingUpdateFunctionDecorator,
            StringInterner stringInterner,
            ListenerManager listenerManager,
            PatternSpecFactory patternSpecFactory,
            VirtualFileSystem.RecentlyCreatedSnapshotsListener recentlyCreatedSnapshotsListener
        ) {
            DefaultVirtualFileSystem virtualFileSystem = new DefaultVirtualFileSystem(
                hasher,
                stringInterner,
                stat,
                snapshotHierarchyReference,
                diffCapturingUpdateFunctionDecorator,
                recentlyCreatedSnapshotsListener,
                DirectoryScanner.getDefaultExcludes()
            );
            listenerManager.addListener(new DefaultExcludesBuildListener(virtualFileSystem) {
                @Override
                public void settingsEvaluated(Settings settings) {
                    super.settingsEvaluated(settings);
                    String[] defaultExcludes = DirectoryScanner.getDefaultExcludes();
                    patternSpecFactory.setDefaultExcludesFromSettings(defaultExcludes);
                    PatternSpecFactory.INSTANCE.setDefaultExcludesFromSettings(defaultExcludes);
                }
            });
            listenerManager.addListener(new RootBuildLifecycleListener() {
                @Override
                public void afterStart(GradleInternal gradle) {
                    // Reset default excludes for each build
                    DirectoryScanner.resetDefaultExcludes();
                    String[] defaultExcludes = DirectoryScanner.getDefaultExcludes();
                    patternSpecFactory.setDefaultExcludesFromSettings(defaultExcludes);
                    PatternSpecFactory.INSTANCE.setDefaultExcludesFromSettings(defaultExcludes);
                }

                @Override
                public void beforeComplete(GradleInternal gradle) {
                }
            });
            return virtualFileSystem;
        }

        FileSystemWatchingHandler createFileSystemWatchingHandler(
            WatchFilter watchFilter,
            AtomicSnapshotHierarchyReference snapshotHierarchyReference,
            RecentlyCapturedSnapshots recentlyCapturedSnapshots,
            DocumentationRegistry documentationRegistry,
            NativeCapabilities nativeCapabilities,
            DelegatingDiffCapturingUpdateFunctionDecorator updateFunctionDecorator,
            ListenerManager listenerManager
        ) {
            FileSystemWatchingHandler watchingHandler = determineWatcherRegistryFactory(OperatingSystem.current(), nativeCapabilities)
                .<FileSystemWatchingHandler>map(watcherRegistryFactory -> new DefaultFileSystemWatchingHandler(
                    watcherRegistryFactory,
                    snapshotHierarchyReference,
                    updateFunctionDecorator,
                    watchFilter,
                    sectionId -> documentationRegistry.getDocumentationFor("gradle_daemon", sectionId),
                    recentlyCapturedSnapshots
                ))
                .orElse(new WatchingNotSupportedFileSystemWatchingHandler(snapshotHierarchyReference));
            listenerManager.addListener((BuildAddedListener) buildState ->
                watchingHandler.buildRootDirectoryAdded(buildState.getBuildRootDir())
            );
            return watchingHandler;
        }

        private Optional<FileWatcherRegistryFactory> determineWatcherRegistryFactory(OperatingSystem operatingSystem, NativeCapabilities nativeCapabilities) {
            if (nativeCapabilities.isNativeIntegrationAvailable()) {
                try {
                    if (operatingSystem.isMacOsX()) {
                        return Optional.of(new DarwinFileWatcherRegistryFactory());
                    } else if (operatingSystem.isWindows()) {
                        return Optional.of(new WindowsFileWatcherRegistryFactory());
                    } else if (operatingSystem.isLinux()) {
                        return Optional.of(new LinuxFileWatcherRegistryFactory());
                    }
                } catch (NativeIntegrationUnavailableException e) {
                    LOGGER.info("Native file system watching is not available for this operating system.", e);
                }
            }
            return Optional.empty();
        }

        GenericFileTreeSnapshotter createGenericFileTreeSnapshotter(FileHasher hasher, StringInterner stringInterner) {
            return new DefaultGenericFileTreeSnapshotter(hasher, stringInterner);
        }

        FileCollectionSnapshotter createFileCollectionSnapshotter(VirtualFileSystem virtualFileSystem, GenericFileTreeSnapshotter genericFileTreeSnapshotter, Stat stat) {
            return new DefaultFileCollectionSnapshotter(virtualFileSystem, genericFileTreeSnapshotter, stat);
        }

        ResourceSnapshotterCacheService createResourceSnapshotterCacheService(CrossBuildFileHashCache store) {
            PersistentIndexedCache<HashCode, HashCode> resourceHashesCache = store.createCache(
                PersistentIndexedCacheParameters.of("resourceHashesCache", HashCode.class, new HashCodeSerializer()),
                400000,
                true);
            return new DefaultResourceSnapshotterCacheService(resourceHashesCache);
        }

        ClasspathFingerprinter createClasspathFingerprinter(ResourceSnapshotterCacheService resourceSnapshotterCacheService, FileCollectionSnapshotter fileCollectionSnapshotter, StringInterner stringInterner) {
            return new DefaultClasspathFingerprinter(resourceSnapshotterCacheService, fileCollectionSnapshotter, ResourceFilter.FILTER_NOTHING, ResourceEntryFilter.FILTER_NOTHING, ResourceEntryFilter.FILTER_NOTHING, stringInterner);
        }

        ClasspathHasher createClasspathHasher(ClasspathFingerprinter fingerprinter, FileCollectionFactory fileCollectionFactory) {
            return new DefaultClasspathHasher(fingerprinter, fileCollectionFactory);
        }
    }

    @VisibleForTesting
    static class BuildSessionServices {
        CrossBuildFileHashCache createCrossBuildFileHashCache(ProjectCacheDir projectCacheDir, CacheScopeMapping cacheScopeMapping, CacheRepository cacheRepository, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
            File cacheDir = cacheScopeMapping.getBaseDirectory(projectCacheDir.getDir(), "fileHashes", VersionStrategy.CachePerVersion);
            return new CrossBuildFileHashCache(cacheDir, cacheRepository, inMemoryCacheDecoratorFactory, CrossBuildFileHashCache.Kind.FILE_HASHES);
        }

        FileHasher createFileHasher(
            GlobalCacheLocations globalCacheLocations,
            BuildSessionScopeFileTimeStampInspector fileTimeStampInspector,
            CrossBuildFileHashCache cacheAccess,
            FileHasher globalHasher,
            FileSystem fileSystem,
            StreamHasher streamHasher,
            StringInterner stringInterner
        ) {
            CachingFileHasher localHasher = new CachingFileHasher(new DefaultFileHasher(streamHasher), cacheAccess, stringInterner, fileTimeStampInspector, "fileHashes", fileSystem);
            return new SplitFileHasher(globalHasher, localHasher, globalCacheLocations);
        }

        VirtualFileSystem createVirtualFileSystem(
            GlobalCacheLocations globalCacheLocations,
            FileHasher hasher,
            FileSystem fileSystem,
            ListenerManager listenerManager,
            StartParameter startParameter,
            Stat stat,
            StringInterner stringInterner,
            VirtualFileSystem gradleUserHomeVirtualFileSystem
        ) {
            StartParameterInternal startParameterInternal = (StartParameterInternal) startParameter;
            AtomicSnapshotHierarchyReference snapshotHierarchyReference = new AtomicSnapshotHierarchyReference(DefaultSnapshotHierarchy.empty(fileSystem.isCaseSensitive() ? CASE_SENSITIVE : CASE_INSENSITIVE));
            DefaultVirtualFileSystem buildSessionsScopedVirtualFileSystem = new DefaultVirtualFileSystem(
                hasher,
                stringInterner,
                stat,
                snapshotHierarchyReference,
                SnapshotHierarchy.DiffCapturingUpdateFunctionDecorator.NOOP,
                locations -> {},
                DirectoryScanner.getDefaultExcludes()
            );
            RoutingVirtualFileSystem routingVirtualFileSystem = new RoutingVirtualFileSystem(
                globalCacheLocations,
                gradleUserHomeVirtualFileSystem,
                buildSessionsScopedVirtualFileSystem,
                startParameterInternal::isWatchFileSystem
            );

            listenerManager.addListener(new RootBuildLifecycleListener() {
                @Override
                public void afterStart(GradleInternal gradle) {
                    // Note: this never fires as we are registering it too late
                }

                @Override
                public void beforeComplete(GradleInternal gradle) {
                    buildSessionsScopedVirtualFileSystem.invalidateAll();
                }
            });
            listenerManager.addListener(new DefaultExcludesBuildListener(buildSessionsScopedVirtualFileSystem));
            listenerManager.addListener(new OutputChangeListener() {
                @Override
                public void beforeOutputChange() {
                    routingVirtualFileSystem.invalidateAll();
                }

                @Override
                public void beforeOutputChange(Iterable<String> affectedOutputPaths) {
                    routingVirtualFileSystem.update(affectedOutputPaths, () -> {});
                }
            });

            return routingVirtualFileSystem;
        }

        GenericFileTreeSnapshotter createGenericFileTreeSnapshotter(FileHasher hasher, StringInterner stringInterner) {
            return new DefaultGenericFileTreeSnapshotter(hasher, stringInterner);
        }

        FileCollectionSnapshotter createFileCollectionSnapshotter(VirtualFileSystem virtualFileSystem, GenericFileTreeSnapshotter genericFileTreeSnapshotter, Stat stat) {
            return new DefaultFileCollectionSnapshotter(virtualFileSystem, genericFileTreeSnapshotter, stat);
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

        ResourceSnapshotterCacheService createResourceSnapshotterCacheService(
            GlobalCacheLocations globalCacheLocations,
            CrossBuildFileHashCache store,
            ResourceSnapshotterCacheService globalCache
        ) {
            PersistentIndexedCache<HashCode, HashCode> resourceHashesCache = store.createCache(PersistentIndexedCacheParameters.of("resourceHashesCache", HashCode.class, new HashCodeSerializer()), 800000, true);
            DefaultResourceSnapshotterCacheService localCache = new DefaultResourceSnapshotterCacheService(resourceHashesCache);
            return new SplitResourceSnapshotterCacheService(globalCache, localCache, globalCacheLocations);
        }

        CompileClasspathFingerprinter createCompileClasspathFingerprinter(ResourceSnapshotterCacheService resourceSnapshotterCacheService, FileCollectionSnapshotter fileCollectionSnapshotter, StringInterner stringInterner) {
            return new DefaultCompileClasspathFingerprinter(resourceSnapshotterCacheService, fileCollectionSnapshotter, stringInterner);
        }
    }

    private static class DefaultExcludesBuildListener extends BuildAdapter {
        private final DefaultVirtualFileSystem virtualFileSystem;

        public DefaultExcludesBuildListener(DefaultVirtualFileSystem virtualFileSystem) {
            this.virtualFileSystem = virtualFileSystem;
        }

        @Override
        public void settingsEvaluated(Settings settings) {
            virtualFileSystem.updateDefaultExcludes(DirectoryScanner.getDefaultExcludes());
        }
    }

    interface WatchFilter extends Predicate<String> {}
}
