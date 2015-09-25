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
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.internal.classloading.MemoryLeakPrevention;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Factory;
import org.gradle.internal.classpath.ClassPath;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A cache which caches classloaders based on their classpath. This cache provides bridging with the classloader cleanup mechanism which makes it more complex than it should: - class loaders can be
 * reused, so they *must not* be cleared as long as a cached loader is in cache - once a classloader is discarded from the cache, it must be cleared using a cleanup strategy
 *
 * It is important that the cleanup only occurs when nobody uses the classloader anymore, which means when no consumer retains a strong reference onto a {@link CachedClassLoader}. If we directly put
 * the cached classloader as a value of the map, then it cannot be reclaimed, and will never be cleaned up. If we just use a SoftReference to the cached class loader, then the reference will be
 * cleared before we have a chance to clean it up. So we use a PhantomReference to the cached class loader, in addition to the soft reference, to finalize the class loader before it gets kicked off
 * the cache.
 */
public class ClassPathToClassLoaderCache {
    private final static Logger LOG = Logging.getLogger(ClassPathToClassLoaderCache.class);

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<ClassPath, CacheEntry> cacheEntries = Maps.newConcurrentMap();
    private final FinalizerThread finalizerThread;
    private final Set<CachedClassLoader> inUseClassLoaders = Sets.newHashSet();

    public ClassPathToClassLoaderCache() {
        this.finalizerThread = new FinalizerThread(cacheEntries, lock);
        this.finalizerThread.start();
    }

    static String toCacheKey(ClassPath classPath) {
        return Joiner.on(":").join(classPath.getAsURIs());
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

    /**
     * Provides execution of arbitrary code that consumes a cached class loader in a memory safe manner,
     * that is to say making sure that concurrent calls reuse the same classloader, or that the class loader
     * is retrived from cache if available.
     *
     * It will also make sure that once a cached class loader is unused and removed from cache, memory cleanup
     * is done.
     *
     * The action MUST be done on a CachedClassLoader, and not directly with the ClassLoader, in order for
     * a strong reference to be kept on the cached class loader while in use. If we don't do so there are risks
     * that the cached entry gets released by the GC before we've finished working with the classloader it
     * wraps!
     *
     * @param libClasspath the classpath for this classloader
     * @param gradleToIsolatedLeakPrevention memory leak strategy for Gradle classes leaking into the isolated loader
     * @param antToGradleLeakPrevention memory leak strategy for Ant classes leaking into the Gradle core loader
     * @param factory the factory to create a new class loader on cache miss
     * @param action the action to execute with the cached class loader
     */
    public void withCachedClassLoader(ClassPath libClasspath,
                                      MemoryLeakPrevention gradleToIsolatedLeakPrevention,
                                      MemoryLeakPrevention antToGradleLeakPrevention,
                                      Factory<? extends ClassLoader> factory,
                                      Action<? super CachedClassLoader> action) {
        lock.readLock().lock();
        CacheEntry cacheEntry = cacheEntries.get(libClasspath);
        CachedClassLoader cachedClassLoader = maybeGet(cacheEntry);
        if (cachedClassLoader == null) {
            lock.readLock().unlock();
            lock.writeLock().lock();
            try {
                cacheEntry = cacheEntries.get(libClasspath);
                cachedClassLoader = maybeGet(cacheEntry);
                if (cachedClassLoader == null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(String.format("Classloader cache miss for classpath : %s. Creating classloader.", libClasspath.getAsURIs()));
                    }
                    ClassLoader classLoader = factory.create();
                    cachedClassLoader = new CachedClassLoader(libClasspath, classLoader);
                    cacheEntry = new CacheEntry(libClasspath, cachedClassLoader);
                    Cleanup cleanup = new Cleanup(libClasspath, cachedClassLoader, finalizerThread.getReferenceQueue(), classLoader, gradleToIsolatedLeakPrevention, antToGradleLeakPrevention);
                    finalizerThread.putCleanup(libClasspath, cleanup);
                    cacheEntries.put(libClasspath, cacheEntry);
                }
                lock.readLock().lock();
            } finally {
                lock.writeLock().unlock();
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format("Classloader found in cache: %s", libClasspath.getAsURIs()));
            }
        }

        lock.readLock().unlock();

        // action can safely be done outside the locking section
        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("Consumer %s uses cached classloader: %s", action.toString(), libClasspath.getAsURIs()));
        }

        // in order to make sure that the CacheEntry is not collected
        // while the cached class loader is still in use, we need to keep a strong reference onto
        // the cached class loader as long as the action is executed
        lock.writeLock().lock();
        inUseClassLoaders.add(cachedClassLoader);
        lock.writeLock().unlock();

        try {
            action.execute(cachedClassLoader);
        } finally {
            lock.writeLock().lock();
            inUseClassLoaders.remove(cachedClassLoader);
            lock.writeLock().unlock();
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("End of usage of cached classloader: %s by consumer %s", libClasspath.getAsURIs(), action.toString()));
        }
    }

    private CachedClassLoader maybeGet(CacheEntry cacheEntry) {
        return cacheEntry != null ? cacheEntry.get() : null;
    }

}
