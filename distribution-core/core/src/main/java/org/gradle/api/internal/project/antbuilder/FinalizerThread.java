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

import com.google.common.collect.Maps;
import org.gradle.internal.classpath.ClassPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.ReferenceQueue;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

import static org.gradle.api.internal.project.antbuilder.Cleanup.Mode.CLOSE_CLASSLOADER;
import static org.gradle.api.internal.project.antbuilder.Cleanup.Mode.DONT_CLOSE_CLASSLOADER;

class FinalizerThread extends Thread {
    private final static Logger LOG = LoggerFactory.getLogger(FinalizerThread.class);

    private final ReferenceQueue<CachedClassLoader> referenceQueue;
    private final AtomicBoolean stopped = new AtomicBoolean();

    // Protects the following fields
    private final Lock lock;
    private final Map<ClassPath, Cleanup> cleanups;
    private final Map<ClassPath, CacheEntry> cacheEntries;

    public FinalizerThread(Map<ClassPath, CacheEntry> cacheEntries, Lock lock) {
        this.setName("Classloader cache reference queue poller");
        this.setDaemon(true);
        this.referenceQueue = new ReferenceQueue<CachedClassLoader>();
        this.cacheEntries = cacheEntries;
        this.cleanups = Maps.newConcurrentMap();
        this.lock = lock;
    }

    @Override
    public void run() {

        try {
            while (!stopped.get()) {
                Cleanup entry = (Cleanup) referenceQueue.remove();
                ClassPath key = entry.getKey();
                removeCacheEntry(key, entry, DONT_CLOSE_CLASSLOADER);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void removeCacheEntry(ClassPath key, Cleanup entry, Cleanup.Mode mode) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Removing classloader from cache, classpath = {}", key.getAsURIs());
        }
        lock.lock();
        try {
            cacheEntries.remove(key);
            cleanups.remove(key);
        } finally {
            lock.unlock();
        }
        try {
            entry.clear();
            entry.cleanup(mode);
        } catch (Exception ex) {
            LOG.error("Unable to perform cleanup of classloader for classpath: "+key, ex);
        }
    }

    public ReferenceQueue<CachedClassLoader> getReferenceQueue() {
        return referenceQueue;
    }

    public void exit() {
        stopped.set(true);
        interrupt();
        lock.lock();
        try {
            while (!cleanups.isEmpty()) {
                Map.Entry<ClassPath, Cleanup> entry = cleanups.entrySet().iterator().next();
                removeCacheEntry(entry.getKey(), entry.getValue(), CLOSE_CLASSLOADER);
            }
            LOG.debug("Completed shutdown");
        } finally {
            lock.unlock();
        }
    }

    public void putCleanup(ClassPath key, Cleanup cleanup) {
        cleanups.put(key, cleanup);
    }
}
