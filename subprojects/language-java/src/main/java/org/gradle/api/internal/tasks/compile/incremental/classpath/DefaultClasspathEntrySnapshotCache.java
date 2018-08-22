/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.api.internal.changedetection.state.FileSystemSnapshotter;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.MinimalPersistentCache;
import org.gradle.internal.Factory;
import org.gradle.internal.hash.HashCode;

import java.io.File;
import java.util.Map;

public class DefaultClasspathEntrySnapshotCache implements ClasspathEntrySnapshotCache {
    private final FileSystemSnapshotter fileSystemSnapshotter;
    private final MinimalPersistentCache<HashCode, ClasspathEntrySnapshotData> cache;

    public DefaultClasspathEntrySnapshotCache(FileSystemSnapshotter fileSystemSnapshotter, PersistentIndexedCache<HashCode, ClasspathEntrySnapshotData> persistentCache) {
        this.fileSystemSnapshotter = fileSystemSnapshotter;
        cache = new MinimalPersistentCache<HashCode, ClasspathEntrySnapshotData>(persistentCache);
    }

    @Override
    public Map<File, ClasspathEntrySnapshot> getClasspathEntrySnapshots(final Map<File, HashCode> fileHashes) {
        Map<File, ClasspathEntrySnapshot> out = Maps.newLinkedHashMap();
        for (Map.Entry<File, HashCode> entry : fileHashes.entrySet()) {
            ClasspathEntrySnapshotData snapshotData = cache.get(entry.getValue());
            if (snapshotData != null) {
                ClasspathEntrySnapshot snapshot = new ClasspathEntrySnapshot(snapshotData);
                out.put(entry.getKey(), snapshot);
            }
        }
        return out;
    }

    @Override
    public ClasspathEntrySnapshot get(File key, final Factory<ClasspathEntrySnapshot> factory) {
        HashCode fileContentHash = fileSystemSnapshotter.snapshot(key).getHash();
        return new ClasspathEntrySnapshot(cache.get(fileContentHash, new Factory<ClasspathEntrySnapshotData>() {
            public ClasspathEntrySnapshotData create() {
                return factory.create().getData();
            }
        }));
    }
}
