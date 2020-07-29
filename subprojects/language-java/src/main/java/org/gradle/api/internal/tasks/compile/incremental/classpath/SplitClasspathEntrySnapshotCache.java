/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.classpath;

import org.gradle.cache.GlobalCacheLocations;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.hash.HashCode;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * A {@link ClasspathEntrySnapshotCache} that delegates to the global cache for files that are known to be immutable.
 * All other files are cached in the local cache. Closing this cache only closes the local delegate, not the global one.
 */
public class SplitClasspathEntrySnapshotCache implements ClasspathEntrySnapshotCache, Closeable {
    private final GlobalCacheLocations globalCacheLocations;
    private final ClasspathEntrySnapshotCache globalCache;
    private final ClasspathEntrySnapshotCache localCache;

    public SplitClasspathEntrySnapshotCache(GlobalCacheLocations globalCacheLocations, ClasspathEntrySnapshotCache globalCache, ClasspathEntrySnapshotCache localCache) {
        this.globalCacheLocations = globalCacheLocations;
        this.globalCache = globalCache;
        this.localCache = localCache;
    }

    @Override
    public ClasspathEntrySnapshot get(File file, HashCode hash) {
        if (globalCacheLocations.isInsideGlobalCache(file.getPath())) {
            return globalCache.get(file, hash);
        } else {
            return localCache.get(file, hash);
        }
    }

    @Override
    public ClasspathEntrySnapshot get(File entry, Factory<ClasspathEntrySnapshot> factory) {
        if (globalCacheLocations.isInsideGlobalCache(entry.getPath())) {
            return globalCache.get(entry, factory);
        } else {
            return localCache.get(entry, factory);
        }
    }

    @Override
    public void close() throws IOException {
        CompositeStoppable.stoppable(localCache).stop();
    }
}
