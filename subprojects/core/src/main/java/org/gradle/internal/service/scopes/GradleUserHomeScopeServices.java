/*
 * Copyright 2016 the original author or authors.
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
import org.gradle.api.internal.ClassPathRegistry;
import org.gradle.api.internal.DefaultClassPathProvider;
import org.gradle.api.internal.DefaultClassPathRegistry;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.CachingFileHasher;
import org.gradle.api.internal.changedetection.state.CrossBuildFileHashCache;
import org.gradle.api.internal.changedetection.state.DefaultFileAccessTimeJournal;
import org.gradle.api.internal.changedetection.state.DefaultResourceSnapshotterCacheService;
import org.gradle.api.internal.changedetection.state.DefaultWellKnownFileLocations;
import org.gradle.api.internal.changedetection.state.GlobalScopeFileTimeStampInspector;
import org.gradle.api.internal.changedetection.state.ResourceFilter;
import org.gradle.api.internal.changedetection.state.ResourceSnapshotterCacheService;
import org.gradle.api.internal.classpath.ModuleRegistry;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache;
import org.gradle.api.internal.initialization.loadercache.DefaultClassLoaderCache;
import org.gradle.api.internal.initialization.loadercache.DefaultClasspathHasher;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.PersistentIndexedCacheParameters;
import org.gradle.cache.internal.CacheRepositoryServices;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.cache.internal.DefaultFileContentCacheFactory;
import org.gradle.cache.internal.DefaultGeneratedGradleJarCache;
import org.gradle.cache.internal.FileContentCacheFactory;
import org.gradle.cache.internal.GradleUserHomeCleanupServices;
import org.gradle.cache.internal.InMemoryCacheDecoratorFactory;
import org.gradle.cache.internal.UsedGradleVersions;
import org.gradle.groovy.scripts.internal.CrossBuildInMemoryCachingScriptClassCache;
import org.gradle.groovy.scripts.internal.DefaultScriptSourceHasher;
import org.gradle.groovy.scripts.internal.RegistryAwareClassLoaderHierarchyHasher;
import org.gradle.groovy.scripts.internal.ScriptSourceHasher;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.internal.classloader.ClasspathHasher;
import org.gradle.internal.classloader.DefaultHashingClassLoaderFactory;
import org.gradle.internal.classloader.HashingClassLoaderFactory;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.classpath.CachedJarFileStore;
import org.gradle.internal.classpath.DefaultCachedClasspathTransformer;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.OutputChangeListener;
import org.gradle.internal.execution.timeout.TimeoutHandler;
import org.gradle.internal.execution.timeout.impl.DefaultTimeoutHandler;
import org.gradle.internal.file.FileAccessTimeJournal;
import org.gradle.internal.file.JarCache;
import org.gradle.internal.file.Stat;
import org.gradle.internal.fingerprint.FileCollectionSnapshotter;
import org.gradle.internal.fingerprint.classpath.ClasspathFingerprinter;
import org.gradle.internal.fingerprint.classpath.impl.DefaultClasspathFingerprinter;
import org.gradle.internal.fingerprint.impl.DefaultFileCollectionSnapshotter;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.hash.DefaultFileHasher;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.jvm.inspection.JvmVersionDetector;
import org.gradle.internal.logging.LoggingManagerInternal;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.remote.MessagingServer;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.snapshot.FileSystemMirror;
import org.gradle.internal.snapshot.FileSystemSnapshotter;
import org.gradle.internal.snapshot.WellKnownFileLocations;
import org.gradle.internal.snapshot.impl.DefaultFileSystemMirror;
import org.gradle.internal.snapshot.impl.DefaultFileSystemSnapshotter;
import org.gradle.internal.snapshot.impl.DefaultValueSnapshotter;
import org.gradle.internal.state.ManagedFactoryRegistry;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.internal.vfs.impl.DefaultVirtualFileSystem;
import org.gradle.internal.vfs.impl.VfsFileSystemSnapshotter;
import org.gradle.process.internal.JavaExecHandleFactory;
import org.gradle.process.internal.health.memory.MemoryManager;
import org.gradle.process.internal.worker.DefaultWorkerProcessFactory;
import org.gradle.process.internal.worker.WorkerProcessFactory;
import org.gradle.process.internal.worker.child.WorkerProcessClassPathProvider;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.util.List;

/**
 * Defines the shared services scoped to a particular Gradle user home directory. These services are reused across multiple builds and operations.
 */
public class GradleUserHomeScopeServices {
    public static final String ENABLE_VFS_SYSTEM_PROPERTY_NAME = "org.gradle.experimental.enable.vfs";
    public static final boolean VFS_ENABLED = true;
    private final ServiceRegistry globalServices;

    public GradleUserHomeScopeServices(ServiceRegistry globalServices) {
        this.globalServices = globalServices;
    }

    public void configure(ServiceRegistration registration, GradleUserHomeDirProvider userHomeDirProvider) {
        File userHomeDir = userHomeDirProvider.getGradleUserHomeDirectory();
        registration.addProvider(new CacheRepositoryServices(userHomeDir, null));
        registration.addProvider(new GradleUserHomeCleanupServices());
        for (PluginServiceRegistry plugin : globalServices.getAll(PluginServiceRegistry.class)) {
            plugin.registerGradleUserHomeServices(registration);
        }
    }

    ListenerManager createListenerManager(ListenerManager parent) {
        return parent.createChild();
    }

    CrossBuildFileHashCache createCrossBuildFileHashCache(CacheRepository cacheRepository, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        return new CrossBuildFileHashCache(null, cacheRepository, inMemoryCacheDecoratorFactory);
    }

    GlobalScopeFileTimeStampInspector createFileTimestampInspector(CacheScopeMapping cacheScopeMapping, ListenerManager listenerManager) {
        GlobalScopeFileTimeStampInspector timeStampInspector = new GlobalScopeFileTimeStampInspector(cacheScopeMapping);
        listenerManager.addListener(timeStampInspector);
        return timeStampInspector;
    }

    FileHasher createCachingFileHasher(StringInterner stringInterner, CrossBuildFileHashCache fileStore, FileSystem fileSystem, GlobalScopeFileTimeStampInspector fileTimeStampInspector, StreamHasher streamHasher) {
        CachingFileHasher fileHasher = new CachingFileHasher(new DefaultFileHasher(streamHasher), fileStore, stringInterner, fileTimeStampInspector, "fileHashes", fileSystem);
        fileTimeStampInspector.attach(fileHasher);
        return fileHasher;
    }

    ScriptSourceHasher createScriptSourceHasher() {
        return new DefaultScriptSourceHasher();
    }

    CrossBuildInMemoryCachingScriptClassCache createCachingScriptCompiler(CrossBuildInMemoryCacheFactory cacheFactory) {
        return new CrossBuildInMemoryCachingScriptClassCache(cacheFactory);
    }

    DefaultValueSnapshotter createValueSnapshotter(ClassLoaderHierarchyHasher classLoaderHierarchyHasher, ManagedFactoryRegistry managedFactoryRegistry) {
        return new DefaultValueSnapshotter(classLoaderHierarchyHasher, managedFactoryRegistry);
    }

    ClassLoaderHierarchyHasher createClassLoaderHierarchyHasher(ClassLoaderRegistry registry, HashingClassLoaderFactory classLoaderFactory) {
        return new RegistryAwareClassLoaderHierarchyHasher(registry, classLoaderFactory);
    }

    WellKnownFileLocations createFileCategorizer(List<CachedJarFileStore> fileStores) {
        return new DefaultWellKnownFileLocations(fileStores);
    }

    FileSystemMirror createFileSystemMirror(ListenerManager listenerManager, WellKnownFileLocations wellKnownFileLocations) {
        final DefaultFileSystemMirror fileSystemMirror = new DefaultFileSystemMirror(wellKnownFileLocations);
        listenerManager.addListener(new OutputChangeListener() {
            @Override
            public void beforeOutputChange() {
                fileSystemMirror.beforeOutputChange();
            }

            @Override
            public void beforeOutputChange(Iterable<String> affectedOutputPaths) {
                fileSystemMirror.beforeOutputChange(affectedOutputPaths);
            }
        });
        listenerManager.addListener(new RootBuildLifecycleListener() {
            @Override
            public void afterStart() {
            }

            @Override
            public void beforeComplete() {
                fileSystemMirror.beforeBuildFinished();
            }
        });
        return fileSystemMirror;
    }

    VirtualFileSystem createVirtualFileSystem(FileHasher hasher, StringInterner stringInterner, Stat stat, ListenerManager listenerManager) {
        DefaultVirtualFileSystem vfs = new DefaultVirtualFileSystem(hasher, stringInterner, stat, DirectoryScanner.getDefaultExcludes());
        listenerManager.addListener(new OutputChangeListener() {
            @Override
            public void beforeOutputChange() {
                vfs.invalidateAll();
            }

            @Override
            public void beforeOutputChange(Iterable<String> affectedOutputPaths) {
                vfs.update(affectedOutputPaths, () -> {});
            }
        });
        listenerManager.addListener(new RootBuildLifecycleListener() {
            @Override
            public void afterStart() {
            }

            @Override
            public void beforeComplete() {
                vfs.invalidateAll();
            }
        });
        return vfs;
    }

    FileSystemSnapshotter createFileSystemSnapshotter(FileHasher hasher, StringInterner stringInterner, Stat stat, FileSystemMirror fileSystemMirror, VirtualFileSystem vfs) {
        return VFS_ENABLED
            ? new VfsFileSystemSnapshotter(vfs, stringInterner, hasher)
            : new DefaultFileSystemSnapshotter(hasher, stringInterner, stat, fileSystemMirror, DirectoryScanner.getDefaultExcludes());
    }

    FileCollectionSnapshotter createFileCollectionSnapshotter(FileSystemSnapshotter fileSystemSnapshotter, Stat stat) {
        return new DefaultFileCollectionSnapshotter(fileSystemSnapshotter, stat);
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

    HashingClassLoaderFactory createClassLoaderFactory(ClasspathHasher classpathHasher) {
        return new DefaultHashingClassLoaderFactory(classpathHasher);
    }

    ClassLoaderCache createClassLoaderCache(HashingClassLoaderFactory classLoaderFactory, ClasspathHasher classpathHasher, ListenerManager listenerManager) {
        DefaultClassLoaderCache cache = new DefaultClassLoaderCache(classLoaderFactory, classpathHasher);
        listenerManager.addListener(cache);
        return cache;
    }

    CachedClasspathTransformer createCachedClasspathTransformer(CacheRepository cacheRepository, FileHasher fileHasher, FileAccessTimeJournal fileAccessTimeJournal,
                                                                List<CachedJarFileStore> fileStores, UsedGradleVersions usedGradleVersions) {
        return new DefaultCachedClasspathTransformer(cacheRepository, new JarCache(fileHasher), fileAccessTimeJournal, fileStores, usedGradleVersions);
    }

    WorkerProcessFactory createWorkerProcessFactory(LoggingManagerInternal loggingManagerInternal, MessagingServer messagingServer, ClassPathRegistry classPathRegistry,
                                                    TemporaryFileProvider temporaryFileProvider, JavaExecHandleFactory execHandleFactory, JvmVersionDetector jvmVersionDetector,
                                                    MemoryManager memoryManager, GradleUserHomeDirProvider gradleUserHomeDirProvider, OutputEventListener outputEventListener) {
        return new DefaultWorkerProcessFactory(
            loggingManagerInternal,
            messagingServer,
            classPathRegistry,
            new LongIdGenerator(),
            gradleUserHomeDirProvider.getGradleUserHomeDirectory(),
            temporaryFileProvider,
            execHandleFactory,
            jvmVersionDetector,
            outputEventListener,
            memoryManager
        );
    }

    ClassPathRegistry createClassPathRegistry(ModuleRegistry moduleRegistry, WorkerProcessClassPathProvider workerProcessClassPathProvider) {
        return new DefaultClassPathRegistry(
            new DefaultClassPathProvider(moduleRegistry),
            workerProcessClassPathProvider
        );
    }

    WorkerProcessClassPathProvider createWorkerProcessClassPathProvider(CacheRepository cacheRepository, ModuleRegistry moduleRegistry) {
        return new WorkerProcessClassPathProvider(cacheRepository, moduleRegistry);
    }

    DefaultGeneratedGradleJarCache createGeneratedGradleJarCache(CacheRepository cacheRepository) {
        String gradleVersion = GradleVersion.current().getVersion();
        return new DefaultGeneratedGradleJarCache(cacheRepository, gradleVersion);
    }

    FileContentCacheFactory createFileContentCacheFactory(ListenerManager listenerManager, VirtualFileSystem virtualFileSystem, CacheRepository cacheRepository, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        return new DefaultFileContentCacheFactory(listenerManager, virtualFileSystem, cacheRepository, inMemoryCacheDecoratorFactory, null);
    }

    FileAccessTimeJournal createFileAccessTimeJournal(CacheRepository cacheRepository, InMemoryCacheDecoratorFactory cacheDecoratorFactory) {
        return new DefaultFileAccessTimeJournal(cacheRepository, cacheDecoratorFactory);
    }

    TimeoutHandler createTimeoutHandler(ExecutorFactory executorFactory) {
        return new DefaultTimeoutHandler(executorFactory.createScheduled("execution timeouts", 1));
    }
}
