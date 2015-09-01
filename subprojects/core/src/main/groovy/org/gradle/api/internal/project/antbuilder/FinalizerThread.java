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

import java.lang.ref.ReferenceQueue;
import java.util.Map;

class FinalizerThread extends Thread {
    private final Map<String, Cleanup> cleanups;
    private final ReferenceQueue<CachedClassLoader> referenceQueue;
    private final Map<String, CacheEntry> cacheEntries;

    private boolean stopped;

    public FinalizerThread(Map<String, CacheEntry> cacheEntries) {
        this.setName("Classloader cache reference queue poller");
        this.setDaemon(true);
        this.referenceQueue = new ReferenceQueue<CachedClassLoader>();
        this.cacheEntries = cacheEntries;
        this.cleanups = Maps.newConcurrentMap();
    }

    public void run() {
        try {
            while (!stopped) {
                Cleanup entry = (Cleanup) referenceQueue.remove();
                String key = entry.getKey();
                cacheEntries.remove(key);
                cleanups.remove(key);
                entry.cleanup();
                entry.clear();
            }

        } catch (InterruptedException e) {
            // noop
        }
    }

    public ReferenceQueue<CachedClassLoader> getReferenceQueue() {
        return referenceQueue;
    }

    public void exit() {
       stopped = true;
        interrupt();
        for (Cleanup cleanup : cleanups.values()) {
            cleanup.cleanup();
        }
        cacheEntries.clear();
        cleanups.clear();
    }

    public void putCleanup(String key, Cleanup cleanup) {
        cleanups.put(key, cleanup);
    }
}
