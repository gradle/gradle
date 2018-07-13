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

import com.google.common.collect.Maps;
import org.gradle.api.internal.changedetection.state.WellKnownFileLocations;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.hash.HashCode;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * A {@link ClasspathEntrySnapshotCache} that delegates to the global cache for files that are known to be immutable.
 * All other files are cached in the local cache. Closing this cache only closes the local delegate, not the global one.
 */
public class SplitClasspathEntrySnapshotCache implements ClasspathEntrySnapshotCache, Closeable {
    private final WellKnownFileLocations fileLocations;
    private final ClasspathEntrySnapshotCache globalCache;
    private final ClasspathEntrySnapshotCache localCache;

    public SplitClasspathEntrySnapshotCache(WellKnownFileLocations fileLocations, ClasspathEntrySnapshotCache globalCache, ClasspathEntrySnapshotCache localCache) {
        this.fileLocations = fileLocations;
        this.globalCache = globalCache;
        this.localCache = localCache;
    }

    @Override
    public Map<File, ClasspathEntrySnapshot> getClasspathEntrySnapshots(Map<File, HashCode> fileHashes) {
        Map<File, HashCode> globalEntries = Maps.newLinkedHashMap();
        Map<File, HashCode> localEntries = Maps.newLinkedHashMap();
        for (Map.Entry<File, HashCode> entry : fileHashes.entrySet()) {
            if (fileLocations.isImmutable(entry.getKey().getPath())) {
                globalEntries.put(entry.getKey(), entry.getValue());
            } else {
                localEntries.put(entry.getKey(), entry.getValue());
            }
        }
        Map<File, ClasspathEntrySnapshot> globalSnapshots = globalCache.getClasspathEntrySnapshots(globalEntries);
        Map<File, ClasspathEntrySnapshot> localSnapshots = localCache.getClasspathEntrySnapshots(localEntries);

        Map<File, ClasspathEntrySnapshot> snapshots = Maps.newLinkedHashMap();
        for (File entry : fileHashes.keySet()) {
            ClasspathEntrySnapshot snapshot = globalSnapshots.get(entry);
            if (snapshot == null) {
                snapshot = localSnapshots.get(entry);
            }
            snapshots.put(entry, snapshot);
        }
        return snapshots;
    }

    @Override
    public ClasspathEntrySnapshot get(File entry, Factory<ClasspathEntrySnapshot> factory) {
        if (fileLocations.isImmutable(entry.getPath())) {
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
