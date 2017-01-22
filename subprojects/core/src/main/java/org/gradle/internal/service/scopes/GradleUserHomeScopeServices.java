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

import org.gradle.api.internal.cache.CrossBuildInMemoryCacheFactory;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.CachingFileHasher;
import org.gradle.api.internal.changedetection.state.CrossBuildFileHashCache;
import org.gradle.api.internal.changedetection.state.InMemoryTaskArtifactCache;
import org.gradle.api.internal.changedetection.state.GlobalScopeFileTimeStampInspector;
import org.gradle.api.internal.hash.DefaultFileHasher;
import org.gradle.api.internal.hash.FileHasher;
import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache;
import org.gradle.api.internal.initialization.loadercache.DefaultClassLoaderCache;
import org.gradle.api.internal.initialization.loadercache.HashClassPathSnapshotter;
import org.gradle.cache.CacheRepository;
import org.gradle.cache.internal.CacheRepositoryServices;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.groovy.scripts.internal.CrossBuildInMemoryCachingScriptClassCache;
import org.gradle.groovy.scripts.internal.RegistryAwareClassLoaderHierarchyHasher;
import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.classloader.ClassLoaderHasher;
import org.gradle.internal.classloader.ClassLoaderHierarchyHasher;
import org.gradle.internal.classloader.ClassPathSnapshotter;
import org.gradle.internal.classloader.DefaultHashingClassLoaderFactory;
import org.gradle.internal.classloader.HashingClassLoaderFactory;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.classpath.CachedJarFileStore;
import org.gradle.internal.classpath.DefaultCachedClasspathTransformer;
import org.gradle.internal.file.JarCache;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistry;

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

    CrossBuildFileHashCache createCrossBuildFileHashCache(CacheRepository cacheRepository, InMemoryTaskArtifactCache inMemoryTaskArtifactCache) {
        return new CrossBuildFileHashCache(cacheRepository, inMemoryTaskArtifactCache);
    }

    GlobalScopeFileTimeStampInspector createFileTimestampInspector(CacheScopeMapping cacheScopeMapping) {
        return new GlobalScopeFileTimeStampInspector(cacheScopeMapping);
    }

    FileHasher createCachingFileHasher(StringInterner stringInterner, CrossBuildFileHashCache fileStore, GlobalScopeFileTimeStampInspector fileTimeStampInspector) {
        CachingFileHasher fileHasher = new CachingFileHasher(new DefaultFileHasher(), fileStore, stringInterner, fileTimeStampInspector, "fileHashes");
        fileTimeStampInspector.attach(fileHasher);
        return fileHasher;
    }

    CrossBuildInMemoryCachingScriptClassCache createCachingScriptCompiler(FileHasher hasher, CrossBuildInMemoryCacheFactory cacheFactory) {
        return new CrossBuildInMemoryCachingScriptClassCache(hasher, cacheFactory);
    }

    ClassLoaderHierarchyHasher createClassLoaderHierarchyHasher(ClassLoaderRegistry registry, ClassLoaderHasher classLoaderHasher) {
        return new RegistryAwareClassLoaderHierarchyHasher(registry, classLoaderHasher);
    }

    ClassLoaderCache createClassLoaderCache(HashingClassLoaderFactory classLoaderFactory, ClassPathSnapshotter classPathSnapshotter) {
        return new DefaultClassLoaderCache(classLoaderFactory, classPathSnapshotter);
    }

    HashingClassLoaderFactory createClassLoaderFactory(ClassPathSnapshotter snapshotter) {
        return new DefaultHashingClassLoaderFactory(snapshotter);
    }

    ClassPathSnapshotter createClassPathSnapshotter(FileHasher hasher, FileSystem fileSystem) {
        return new HashClassPathSnapshotter(hasher, fileSystem);
    }

    CachedClasspathTransformer createCachedClasspathTransformer(CacheRepository cacheRepository, ServiceRegistry serviceRegistry) {
        return new DefaultCachedClasspathTransformer(cacheRepository, new JarCache(), serviceRegistry.getAll(CachedJarFileStore.class));
    }
}
