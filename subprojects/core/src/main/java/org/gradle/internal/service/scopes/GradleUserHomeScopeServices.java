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

import com.google.common.hash.HashCode;
import org.gradle.api.internal.cache.CrossBuildInMemoryCacheFactory;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.CachingClasspathEntryHasher;
import org.gradle.api.internal.changedetection.state.CachingFileHasher;
import org.gradle.api.internal.changedetection.state.ClasspathEntryHasher;
import org.gradle.api.internal.changedetection.state.ClasspathSnapshotter;
import org.gradle.api.internal.changedetection.state.CrossBuildFileHashCache;
import org.gradle.api.internal.changedetection.state.DefaultClasspathContentHasher;
import org.gradle.api.internal.changedetection.state.DefaultClasspathEntryHasher;
import org.gradle.api.internal.changedetection.state.DefaultClasspathSnapshotter;
import org.gradle.api.internal.changedetection.state.DefaultFileSystemMirror;
import org.gradle.api.internal.changedetection.state.DefaultFileSystemSnapshotter;
import org.gradle.api.internal.changedetection.state.DefaultGenericFileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.FileSystemMirror;
import org.gradle.api.internal.changedetection.state.FileSystemSnapshotter;
import org.gradle.api.internal.changedetection.state.GenericFileCollectionSnapshotter;
import org.gradle.api.internal.changedetection.state.GlobalScopeFileTimeStampInspector;
import org.gradle.api.internal.changedetection.state.InMemoryCacheDecoratorFactory;
import org.gradle.api.internal.changedetection.state.TaskHistoryStore;
import org.gradle.api.internal.changedetection.state.ValueSnapshotter;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.hash.DefaultFileHasher;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache;
import org.gradle.api.internal.initialization.loadercache.DefaultClassLoaderCache;
import org.gradle.api.internal.initialization.loadercache.DefaultClasspathHasher;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.CacheRepositoryServices;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.groovy.scripts.internal.CrossBuildInMemoryCachingScriptClassCache;
import org.gradle.groovy.scripts.internal.RegistryAwareClassLoaderHierarchyHasher;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.classloader.ClassLoaderHasher;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.classloader.ClasspathHasher;
import org.gradle.internal.classloader.DefaultHashingClassLoaderFactory;
import org.gradle.internal.classloader.HashingClassLoaderFactory;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.classpath.CachedJarFileStore;
import org.gradle.internal.classpath.DefaultCachedClasspathTransformer;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.file.JarCache;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.serialize.HashCodeSerializer;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;

import java.util.List;

/**
 * Defines the shared services scoped to a particular Gradle user home directory. These services are reused across multiple builds and operations.
 */
public class GradleUserHomeScopeServices {
    private final ServiceRegistry globalServices;

    public GradleUserHomeScopeServices(ServiceRegistry globalServices) {
        this.globalServices = globalServices;
    }

    public void configure(ServiceRegistration registration, GradleUserHomeDirProvider userHomeDirProvider) {
        registration.addProvider(new CacheRepositoryServices(userHomeDirProvider.getGradleUserHomeDirectory(), null));
        for (GradleUserHomeScopePluginServices plugin : globalServices.getAll(GradleUserHomeScopePluginServices.class)) {
            plugin.registerGradleUserHomeServices(registration);
        }
    }

    CrossBuildFileHashCache createCrossBuildFileHashCache(CacheRepository cacheRepository, InMemoryCacheDecoratorFactory inMemoryCacheDecoratorFactory) {
        return new CrossBuildFileHashCache(cacheRepository, inMemoryCacheDecoratorFactory);
    }

    GlobalScopeFileTimeStampInspector createFileTimestampInspector(CacheScopeMapping cacheScopeMapping) {
        return new GlobalScopeFileTimeStampInspector(cacheScopeMapping);
    }

    FileHasher createCachingFileHasher(StringInterner stringInterner, CrossBuildFileHashCache fileStore, FileSystem fileSystem, GlobalScopeFileTimeStampInspector fileTimeStampInspector) {
        CachingFileHasher fileHasher = new CachingFileHasher(new DefaultFileHasher(), fileStore, stringInterner, fileTimeStampInspector, "fileHashes", fileSystem);
        fileTimeStampInspector.attach(fileHasher);
        return fileHasher;
    }

    CrossBuildInMemoryCachingScriptClassCache createCachingScriptCompiler(FileHasher hasher, CrossBuildInMemoryCacheFactory cacheFactory) {
        return new CrossBuildInMemoryCachingScriptClassCache(hasher, cacheFactory);
    }

    ValueSnapshotter createValueSnapshotter(ClassLoaderHierarchyHasher classLoaderHierarchyHasher) {
        return new ValueSnapshotter(classLoaderHierarchyHasher);
    }

    ClassLoaderHierarchyHasher createClassLoaderHierarchyHasher(ClassLoaderRegistry registry, ClassLoaderHasher classLoaderHasher) {
        return new RegistryAwareClassLoaderHierarchyHasher(registry, classLoaderHasher);
    }

    ClasspathEntryHasher createClasspathEntryHasher(TaskHistoryStore store) {
        PersistentIndexedCache<HashCode, HashCode> signatureCache = store.createCache("jvmRuntimeClassSignatures", HashCode.class, new HashCodeSerializer(), 400000, true);
        return new CachingClasspathEntryHasher(new DefaultClasspathEntryHasher(new DefaultClasspathContentHasher()), signatureCache);
    }

    FileSystemMirror createFileSystemMirror(ListenerManager listenerManager, List<CachedJarFileStore> fileStores) {
        DefaultFileSystemMirror fileSystemMirror = new DefaultFileSystemMirror(fileStores);
        listenerManager.addListener(fileSystemMirror);
        return fileSystemMirror;
    }

    FileSystemSnapshotter createFileSystemSnapshotter(FileHasher hasher, StringInterner stringInterner, FileSystem fileSystem, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemMirror fileSystemMirror) {
        return new DefaultFileSystemSnapshotter(hasher, stringInterner, fileSystem, directoryFileTreeFactory, fileSystemMirror);
    }

    GenericFileCollectionSnapshotter createGenericFileCollectionSnapshotter(StringInterner stringInterner, DirectoryFileTreeFactory directoryFileTreeFactory, FileSystemSnapshotter fileSystemSnapshotter) {
        return new DefaultGenericFileCollectionSnapshotter(stringInterner, directoryFileTreeFactory, fileSystemSnapshotter);
    }

    ClasspathSnapshotter createClasspathSnapshotter(StringInterner stringInterner, DirectoryFileTreeFactory directoryFileTreeFactory, ClasspathEntryHasher classpathEntryHasher, FileSystemSnapshotter fileSystemSnapshotter) {
        return new DefaultClasspathSnapshotter(stringInterner, directoryFileTreeFactory, fileSystemSnapshotter, classpathEntryHasher);
    }

    ClasspathHasher createClasspathHasher(ClasspathSnapshotter snapshotter) {
        return new DefaultClasspathHasher(snapshotter);
    }

    HashingClassLoaderFactory createClassLoaderFactory(ClasspathHasher classpathHasher) {
        return new DefaultHashingClassLoaderFactory(classpathHasher);
    }

    ClassLoaderCache createClassLoaderCache(HashingClassLoaderFactory classLoaderFactory, ClasspathHasher classpathHasher) {
        return new DefaultClassLoaderCache(classLoaderFactory, classpathHasher);
    }

    CachedClasspathTransformer createCachedClasspathTransformer(CacheRepository cacheRepository, FileHasher fileHasher, List<CachedJarFileStore> fileStores) {
        return new DefaultCachedClasspathTransformer(cacheRepository, new JarCache(fileHasher), fileStores);
    }
}
