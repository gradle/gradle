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
import org.gradle.internal.deprecation.DeprecationLogger;
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
import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.vfs.FileSystemAccess;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.internal.vfs.impl.DefaultFileSystemAccess;
import org.gradle.internal.vfs.impl.DefaultSnapshotHierarchy;
import org.gradle.internal.vfs.impl.VfsRootReference;
import org.gradle.internal.watch.registry.FileWatcherRegistryFactory;
import org.gradle.internal.watch.registry.impl.DarwinFileWatcherRegistryFactory;
import org.gradle.internal.watch.registry.impl.LinuxFileWatcherRegistryFactory;
import org.gradle.internal.watch.registry.impl.WindowsFileWatcherRegistryFactory;
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem;
import org.gradle.internal.watch.vfs.impl.LocationsWrittenByCurrentBuild;
import org.gradle.internal.watch.vfs.impl.WatchingNotSupportedVirtualFileSystem;
import org.gradle.internal.watch.vfs.impl.WatchingVirtualFileSystem;
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
     * Deprecated system property used to enable watching the file system.
     *
     * Using this property causes Gradle to emit a deprecation warning.
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    public static final String DEPRECATED_VFS_RETENTION_ENABLED_PROPERTY = "org.gradle.unsafe.vfs.retention";

    /**
     * When file system watching is enabled, this system property can be used to invalidate the entire VFS.
     *
     * @see org.gradle.initialization.StartParameterBuildOptions.WatchFileSystemOption
     */
    public static final String VFS_DROP_PROPERTY = "org.gradle.vfs.drop";

    /**
     * Previous name for {@link #VFS_DROP_PROPERTY}.
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    @VisibleForTesting
    public static final String DEPRECATED_VFS_DROP_PROPERTY = "org.gradle.unsafe.vfs.drop";

    private static final int DEFAULT_MAX_HIERARCHIES_TO_WATCH = 50;
    public static final String MAX_HIERARCHIES_TO_WATCH_PROPERTY = "org.gradle.vfs.watch.hierarchies.max";

    public static boolean isDropVfs(StartParameter startParameter) {
        if (getSystemProperty(DEPRECATED_VFS_DROP_PROPERTY, startParameter.getSystemPropertiesArgs()) != null) {
            DeprecationLogger
                .deprecateSystemProperty(DEPRECATED_VFS_DROP_PROPERTY)
                .replaceWith(VFS_DROP_PROPERTY)
                .willBeRemovedInGradle7()
                .undocumented()
                .nagUser();
            return isSystemPropertyEnabled(DEPRECATED_VFS_DROP_PROPERTY, startParameter.getSystemPropertiesArgs());
        }
        return isSystemPropertyEnabled(VFS_DROP_PROPERTY, startParameter.getSystemPropertiesArgs());
    }

    public static int getMaximumNumberOfWatchedHierarchies(StartParameter startParameter) {
        String fromProperty = getSystemProperty(MAX_HIERARCHIES_TO_WATCH_PROPERTY, startParameter.getSystemPropertiesArgs());
        return fromProperty != null && !fromProperty.isEmpty()
            ? Integer.parseInt(fromProperty, 10)
            : DEFAULT_MAX_HIERARCHIES_TO_WATCH;
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

        LocationsWrittenByCurrentBuild createLocationsUpdatedByCurrentBuild(ListenerManager listenerManager) {
            LocationsWrittenByCurrentBuild locationsWrittenByCurrentBuild = new LocationsWrittenByCurrentBuild();
            listenerManager.addListener(new RootBuildLifecycleListener() {
                @Override
                public void afterStart(GradleInternal gradle) {
                    locationsWrittenByCurrentBuild.buildStarted();
                }

                @Override
                public void beforeComplete(GradleInternal gradle) {
                    locationsWrittenByCurrentBuild.buildFinished();
                }
            });
            return locationsWrittenByCurrentBuild;
        }

        BuildLifecycleAwareVirtualFileSystem createVirtualFileSystem(
            LocationsWrittenByCurrentBuild locationsWrittenByCurrentBuild,
            DocumentationRegistry documentationRegistry,
            NativeCapabilities nativeCapabilities,
            ListenerManager listenerManager,
            FileSystem fileSystem,
            GlobalCacheLocations globalCacheLocations
        ) {
            CaseSensitivity caseSensitivity = fileSystem.isCaseSensitive() ? CASE_SENSITIVE : CASE_INSENSITIVE;
            VfsRootReference rootReference = new VfsRootReference(DefaultSnapshotHierarchy.empty(caseSensitivity));
            // All the changes in global caches should be done by Gradle itself, so in order
            // to minimize the number of watches we don't watch anything within the global caches.
            Predicate<String> watchFilter = path -> !globalCacheLocations.isInsideGlobalCache(path);

            BuildLifecycleAwareVirtualFileSystem virtualFileSystem = determineWatcherRegistryFactory(OperatingSystem.current(), nativeCapabilities, watchFilter)
                .<BuildLifecycleAwareVirtualFileSystem>map(watcherRegistryFactory -> new WatchingVirtualFileSystem(
                    watcherRegistryFactory,
                    rootReference,
                    sectionId -> documentationRegistry.getDocumentationFor("gradle_daemon", sectionId),
                    locationsWrittenByCurrentBuild
                ))
                .orElse(new WatchingNotSupportedVirtualFileSystem(rootReference));
            listenerManager.addListener((BuildAddedListener) buildState ->
                virtualFileSystem.registerWatchableHierarchy(buildState.getBuildRootDir())
            );
            return virtualFileSystem;
        }

        FileSystemAccess createFileSystemAccess(
            FileHasher hasher,
            VirtualFileSystem virtualFileSystem,
            Stat stat,
            StringInterner stringInterner,
            ListenerManager listenerManager,
            PatternSpecFactory patternSpecFactory,
            FileSystemAccess.WriteListener writeListener
        ) {
            DefaultFileSystemAccess fileSystemAccess = new DefaultFileSystemAccess(
                hasher,
                stringInterner,
                stat,
                virtualFileSystem,
                writeListener,
                DirectoryScanner.getDefaultExcludes()
            );
            listenerManager.addListener(new DefaultExcludesBuildListener(fileSystemAccess) {
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
            return fileSystemAccess;
        }

        private Optional<FileWatcherRegistryFactory> determineWatcherRegistryFactory(OperatingSystem operatingSystem, NativeCapabilities nativeCapabilities, Predicate<String> watchFilter) {
            if (nativeCapabilities.useFileSystemWatching()) {
                try {
                    if (operatingSystem.isMacOsX()) {
                        return Optional.of(new DarwinFileWatcherRegistryFactory(watchFilter));
                    } else if (operatingSystem.isWindows()) {
                        return Optional.of(new WindowsFileWatcherRegistryFactory(watchFilter));
                    } else if (operatingSystem.isLinux()) {
                        return Optional.of(new LinuxFileWatcherRegistryFactory(watchFilter));
                    }
                } catch (NativeIntegrationUnavailableException e) {
                    LOGGER.debug("Native file system watching is not available for this operating system.", e);
                }
            }
            return Optional.empty();
        }

        GenericFileTreeSnapshotter createGenericFileTreeSnapshotter(FileHasher hasher, StringInterner stringInterner) {
            return new DefaultGenericFileTreeSnapshotter(hasher, stringInterner);
        }

        FileCollectionSnapshotter createFileCollectionSnapshotter(FileSystemAccess fileSystemAccess, GenericFileTreeSnapshotter genericFileTreeSnapshotter, Stat stat) {
            return new DefaultFileCollectionSnapshotter(fileSystemAccess, genericFileTreeSnapshotter, stat);
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

        FileSystemAccess createFileSystemAccess(
            FileHasher hasher,
            ListenerManager listenerManager,
            Stat stat,
            StringInterner stringInterner,
            VirtualFileSystem root,
            FileSystemAccess.WriteListener writeListener
        ) {
            DefaultFileSystemAccess buildSessionsScopedVirtualFileSystem = new DefaultFileSystemAccess(
                hasher,
                stringInterner,
                stat,
                root,
                writeListener,
                DirectoryScanner.getDefaultExcludes()
            );

            listenerManager.addListener(new DefaultExcludesBuildListener(buildSessionsScopedVirtualFileSystem));
            listenerManager.addListener((OutputChangeListener) affectedOutputPaths -> buildSessionsScopedVirtualFileSystem.write(affectedOutputPaths, () -> {}));

            return buildSessionsScopedVirtualFileSystem;
        }

        GenericFileTreeSnapshotter createGenericFileTreeSnapshotter(FileHasher hasher, StringInterner stringInterner) {
            return new DefaultGenericFileTreeSnapshotter(hasher, stringInterner);
        }

        FileCollectionSnapshotter createFileCollectionSnapshotter(FileSystemAccess fileSystemAccess, GenericFileTreeSnapshotter genericFileTreeSnapshotter, Stat stat) {
            return new DefaultFileCollectionSnapshotter(fileSystemAccess, genericFileTreeSnapshotter, stat);
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
        private final DefaultFileSystemAccess fileSystemAccess;

        public DefaultExcludesBuildListener(DefaultFileSystemAccess fileSystemAccess) {
            this.fileSystemAccess = fileSystemAccess;
        }

        @Override
        public void settingsEvaluated(Settings settings) {
            fileSystemAccess.updateDefaultExcludes(DirectoryScanner.getDefaultExcludes());
        }
    }

    interface WatchFilter extends Predicate<String> {}
}
