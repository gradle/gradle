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

import com.google.common.collect.ImmutableList;
import org.apache.tools.ant.DirectoryScanner;
import org.gradle.StartParameter;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.BuildScopeFileTimeStampInspector;
import org.gradle.api.internal.changedetection.state.CachingFileHasher;
import org.gradle.api.internal.changedetection.state.CrossBuildFileHashCache;
import org.gradle.api.internal.changedetection.state.DefaultResourceSnapshotterCacheService;
import org.gradle.api.internal.changedetection.state.GlobalScopeFileTimeStampInspector;
import org.gradle.api.internal.changedetection.state.ResourceFilter;
import org.gradle.api.internal.changedetection.state.ResourceSnapshotterCacheService;
import org.gradle.api.internal.changedetection.state.SplitFileHasher;
import org.gradle.api.internal.changedetection.state.SplitResourceSnapshotterCacheService;
import org.gradle.api.internal.file.BaseDirFileResolver;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.initialization.loadercache.DefaultClasspathHasher;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.VersionStrategy;
import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.initialization.layout.ProjectCacheDir;
import org.gradle.internal.classloader.ClasspathHasher;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.file.PathToFileResolver;
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
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.vfs.AdditiveCacheLocations;
import org.gradle.internal.vfs.DarwinFileWatcherRegistry;
import org.gradle.internal.vfs.RoutingVirtualFileSystem;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.internal.vfs.WatchingVirtualFileSystem;
import org.gradle.internal.vfs.WindowsFileWatcherRegistry;
import org.gradle.internal.vfs.impl.DefaultVirtualFileSystem;
import org.gradle.internal.vfs.impl.DefaultWatchingVirtualFileSystem;
import org.gradle.internal.vfs.watch.FileWatcherRegistryFactory;
import org.gradle.internal.vfs.watch.impl.JdkFileWatcherRegistry;
import org.gradle.internal.vfs.watch.impl.NoopFileWatcherRegistry;
import org.gradle.util.SingleMessageLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gradle.internal.snapshot.CaseSensitivity.CASE_INSENSITIVE;
import static org.gradle.internal.snapshot.CaseSensitivity.CASE_SENSITIVE;

public class VirtualFileSystemServices extends AbstractPluginServiceRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(VirtualFileSystemServices.class);

    /**
     * System property to enable partial invalidation.
     */
    public static final String VFS_PARTIAL_INVALIDATION_ENABLED_PROPERTY = "org.gradle.unsafe.vfs.partial-invalidation";

    /**
     * System property to enable retaining VFS state between builds.
     *
     * Also enables partial VFS invalidation.
     *
     * @see #VFS_PARTIAL_INVALIDATION_ENABLED_PROPERTY
     */
    public static final String VFS_RETENTION_ENABLED_PROPERTY = "org.gradle.unsafe.vfs.retention";

    /**
     * When retention is enabled, this system property can be used to pass a comma-separated
     * list of file paths that have changed since the last build.
     *
     * @see #VFS_RETENTION_ENABLED_PROPERTY
     */
    public static final String VFS_CHANGES_SINCE_LAST_BUILD_PROPERTY = "org.gradle.unsafe.vfs.changes";

    /**
     * When retention is enabled, this system property can be used to invalidate the entire VFS.
     *
     * @see #VFS_RETENTION_ENABLED_PROPERTY
     */
    public static final String VFS_DROP_PROPERTY = "org.gradle.unsafe.vfs.drop";

    public static boolean isPartialInvalidationEnabled(Map<String, String> systemPropertiesArgs) {
        return getSystemProperty(VFS_PARTIAL_INVALIDATION_ENABLED_PROPERTY, systemPropertiesArgs) != null
            || isRetentionEnabled(systemPropertiesArgs);
    }

    public static boolean isRetentionEnabled(Map<String, String> systemPropertiesArgs) {
        return getSystemProperty(VFS_RETENTION_ENABLED_PROPERTY, systemPropertiesArgs) != null;
    }

    public static List<File> getChangedPathsSinceLastBuild(PathToFileResolver resolver, Map<String, String> systemPropertiesArgs) {
        String changeList = getSystemProperty(VFS_CHANGES_SINCE_LAST_BUILD_PROPERTY, systemPropertiesArgs);
        if (changeList == null) {
            return ImmutableList.of();
        }
        return Stream.of(changeList.split(","))
            .filter(path -> !path.isEmpty())
            .map(resolver::resolve)
            .collect(Collectors.toList());
    }

    @Nullable
    private static String getSystemProperty(String systemProperty, Map<String, String> systemPropertiesArgs) {
        return systemPropertiesArgs.getOrDefault(systemProperty, System.getProperty(systemProperty));
    }

    @Override
    public void registerGradleUserHomeServices(ServiceRegistration registration) {
        registration.addProvider(new Object() {

            CrossBuildFileHashCache createCrossBuildFileHashCache(CacheRepository cacheRepository, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
                return new CrossBuildFileHashCache(null, cacheRepository, inMemoryCacheDecoratorFactory, CrossBuildFileHashCache.Kind.FILE_HASHES);
            }

            FileHasher createCachingFileHasher(StringInterner stringInterner, CrossBuildFileHashCache fileStore, FileSystem fileSystem, GlobalScopeFileTimeStampInspector fileTimeStampInspector, StreamHasher streamHasher) {
                CachingFileHasher fileHasher = new CachingFileHasher(new DefaultFileHasher(streamHasher), fileStore, stringInterner, fileTimeStampInspector, "fileHashes", fileSystem);
                fileTimeStampInspector.attach(fileHasher);
                return fileHasher;
            }

            FileWatcherRegistryFactory createFileWatcherRegistryFactory() {
                OperatingSystem operatingSystem = OperatingSystem.current();
                if (operatingSystem.isMacOsX()) {
                    return new DarwinFileWatcherRegistry.Factory();
                } else if (operatingSystem.isWindows()) {
                    return new WindowsFileWatcherRegistry.Factory();
                } else if (operatingSystem.isLinux()) {
                    // The Linux watcher in the JDK works quite well
                    return new JdkFileWatcherRegistry.Factory();
                } else {
                    return new NoopFileWatcherRegistry.Factory();
                }
            }

            VirtualFileSystem createVirtualFileSystem(
                AdditiveCacheLocations additiveCacheLocations,
                FileHasher hasher,
                FileSystem fileSystem,
                FileWatcherRegistryFactory watcherRegistryFactory,
                ListenerManager listenerManager,
                Stat stat,
                StringInterner stringInterner
            ) {
                WatchingVirtualFileSystem virtualFileSystem = new DefaultWatchingVirtualFileSystem(
                    watcherRegistryFactory,
                    new DefaultVirtualFileSystem(
                        hasher,
                        stringInterner,
                        stat,
                        fileSystem.isCaseSensitive() ? CASE_SENSITIVE : CASE_INSENSITIVE,
                        DirectoryScanner.getDefaultExcludes()
                    ),
                    path -> !additiveCacheLocations.isInsideAdditiveCache(path.toString())
                );
                listenerManager.addListener(new RootBuildLifecycleListener() {
                    @Override
                    public void afterStart(GradleInternal gradle) {
                        StartParameter startParameter = gradle.getStartParameter();
                        Map<String, String> systemPropertiesArgs = startParameter.getSystemPropertiesArgs();
                        if (isRetentionEnabled(systemPropertiesArgs)) {
                            SingleMessageLogger.incubatingFeatureUsed("Virtual file system retention");
                            FileResolver fileResolver = new BaseDirFileResolver(startParameter.getCurrentDir(), () -> {
                                throw new UnsupportedOperationException();
                            });
                            if (getSystemProperty(VFS_DROP_PROPERTY, systemPropertiesArgs) != null) {
                                virtualFileSystem.invalidateAll();
                            } else {
                                List<File> changedPathsSinceLastBuild = getChangedPathsSinceLastBuild(fileResolver, systemPropertiesArgs);
                                for (File changedPathSinceLastBuild : changedPathsSinceLastBuild) {
                                    LOGGER.warn("Marking as changed since last build: {}", changedPathSinceLastBuild);
                                }
                                virtualFileSystem.update(
                                    changedPathsSinceLastBuild
                                        .stream()
                                        .map(File::getAbsolutePath)
                                        .collect(Collectors.toList()),
                                    () -> {
                                    }
                                );
                            }
                        } else {
                            virtualFileSystem.invalidateAll();
                        }
                        virtualFileSystem.stopWatching();
                    }

                    @Override
                    public void beforeComplete(GradleInternal gradle) {
                        if (isRetentionEnabled(gradle.getStartParameter().getSystemPropertiesArgs())) {
                            virtualFileSystem.startWatching();
                        } else {
                            virtualFileSystem.invalidateAll();
                        }
                    }
                });
                return virtualFileSystem;
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
                return new DefaultClasspathFingerprinter(resourceSnapshotterCacheService, fileCollectionSnapshotter, ResourceFilter.FILTER_NOTHING, stringInterner);
            }

            ClasspathHasher createClasspathHasher(ClasspathFingerprinter fingerprinter, FileCollectionFactory fileCollectionFactory) {
                return new DefaultClasspathHasher(fingerprinter, fileCollectionFactory);
            }
        });
    }

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
        registration.addProvider(new Object() {
            CrossBuildFileHashCache createCrossBuildFileHashCache(ProjectCacheDir projectCacheDir, CacheScopeMapping cacheScopeMapping, CacheRepository cacheRepository, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
                File cacheDir = cacheScopeMapping.getBaseDirectory(projectCacheDir.getDir(), "fileHashes", VersionStrategy.CachePerVersion);
                return new CrossBuildFileHashCache(cacheDir, cacheRepository, inMemoryCacheDecoratorFactory, CrossBuildFileHashCache.Kind.FILE_HASHES);
            }

            FileHasher createFileHasher(
                AdditiveCacheLocations additiveCacheLocations,
                BuildScopeFileTimeStampInspector fileTimeStampInspector,
                CrossBuildFileHashCache cacheAccess,
                FileHasher globalHasher,
                FileSystem fileSystem,
                StreamHasher streamHasher,
                StringInterner stringInterner
            ) {
                CachingFileHasher localHasher = new CachingFileHasher(new DefaultFileHasher(streamHasher), cacheAccess, stringInterner, fileTimeStampInspector, "fileHashes", fileSystem);
                return new SplitFileHasher(globalHasher, localHasher, additiveCacheLocations);
            }

            VirtualFileSystem createVirtualFileSystem(
                AdditiveCacheLocations additiveCacheLocations,
                FileHasher hasher,
                FileSystem fileSystem,
                ListenerManager listenerManager,
                StartParameter startParameter,
                Stat stat,
                StringInterner stringInterner,
                VirtualFileSystem gradleUserHomeVirtualFileSystem
            ) {
                VirtualFileSystem buildSessionsScopedVirtualFileSystem = new DefaultVirtualFileSystem(
                    hasher,
                    stringInterner,
                    stat,
                    fileSystem.isCaseSensitive() ? CASE_SENSITIVE : CASE_INSENSITIVE,
                    DirectoryScanner.getDefaultExcludes()
                );
                RoutingVirtualFileSystem routingVirtualFileSystem = new RoutingVirtualFileSystem(
                    additiveCacheLocations,
                    gradleUserHomeVirtualFileSystem,
                    buildSessionsScopedVirtualFileSystem,
                    () -> isRetentionEnabled(startParameter.getSystemPropertiesArgs())
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
                listenerManager.addListener(new OutputChangeListener() {
                    @Override
                    public void beforeOutputChange() {
                        buildSessionsScopedVirtualFileSystem.invalidateAll();
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
                AdditiveCacheLocations additiveCacheLocations,
                CrossBuildFileHashCache store,
                ResourceSnapshotterCacheService globalCache
            ) {
                PersistentIndexedCache<HashCode, HashCode> resourceHashesCache = store.createCache(PersistentIndexedCacheParameters.of("resourceHashesCache", HashCode.class, new HashCodeSerializer()), 800000, true);
                DefaultResourceSnapshotterCacheService localCache = new DefaultResourceSnapshotterCacheService(resourceHashesCache);
                return new SplitResourceSnapshotterCacheService(globalCache, localCache, additiveCacheLocations);
            }

            CompileClasspathFingerprinter createCompileClasspathFingerprinter(ResourceSnapshotterCacheService resourceSnapshotterCacheService, FileCollectionSnapshotter fileCollectionSnapshotter, StringInterner stringInterner) {
                return new DefaultCompileClasspathFingerprinter(resourceSnapshotterCacheService, fileCollectionSnapshotter, stringInterner);
            }
        });
    }
}
