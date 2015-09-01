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
package org.gradle.api.internal.project.antbuilder;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import org.gradle.internal.classpath.ClassPath;

import java.util.Map;

/**
 * A cache which caches classloaders based on their classpath. This cache provides bridging
 * with the classloader cleanup mechanism which makes it more complex than it should:
 *    - class loaders can be reused, so they *must not* be cleared as long as a cached loader is in cache
 *    - once a classloader is discarded from the cache, it must be cleared using a cleanup strategy
 *
 * It is important that the cleanup only occurs when nobody uses the classloader anymore, which means
 * when no consumer retains a strong reference onto a {@link CachedClassLoader}. If we directly put
 * the cached classloader as a value of the map, then it cannot be reclaimed, and will never be cleaned
 * up. If we just use a SoftReference to the cached class loader, then the reference will be cleared
 * before we have a chance to clean it up. So we use a PhantomReference to the cached class loader, in
 * addition to the soft reference, to finalize the class loader before it gets kicked off the cache.
 */
public class ClassPathToClassLoaderCache {
    private final Map<String, CacheEntry> cacheEntries = Maps.newConcurrentMap();
    private final FinalizerThread finalizerThread;

    public ClassPathToClassLoaderCache() {
        this.finalizerThread = new FinalizerThread(cacheEntries);
        this.finalizerThread.start();
    }

    static String toCacheKey(ClassPath classPath) {
        return Joiner.on(":").join(classPath.getAsURIs());
    }

    public CachedClassLoader get(final ClassPath classPath) {
        CacheEntry entry = cacheEntries.get(toCacheKey(classPath));
        return entry == null ? null : entry.get();
    }

    public void shutdown() {
        finalizerThread.exit();
    }

    public int size() {
        return cacheEntries.size();
    }

    public boolean isEmpty() {
        return cacheEntries.isEmpty();
    }

    public CachedClassLoader cache(ClassPath libClasspath,
                                   ClassLoader classLoader,
                                   MemoryLeakPrevention gradleToIsolatedLeakPrevention,
                                   MemoryLeakPrevention antToGradleLeakPrevention) {
        String key = toCacheKey(libClasspath);
        CachedClassLoader loader = new CachedClassLoader(key, classLoader);
        CacheEntry cacheEntry = new CacheEntry(key, loader);
        Cleanup cleanup = new Cleanup(key, libClasspath, loader, finalizerThread.getReferenceQueue(), classLoader, gradleToIsolatedLeakPrevention, antToGradleLeakPrevention);
        finalizerThread.putCleanup(key, cleanup);
        cacheEntries.put(key, cacheEntry);
        return loader;
    }

}
