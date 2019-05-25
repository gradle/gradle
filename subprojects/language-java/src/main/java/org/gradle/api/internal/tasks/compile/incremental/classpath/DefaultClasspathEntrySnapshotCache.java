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

import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.MinimalPersistentCache;
import org.gradle.internal.Factory;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.FileSystemSnapshotter;

import java.io.File;

public class DefaultClasspathEntrySnapshotCache implements ClasspathEntrySnapshotCache {
    private final FileSystemSnapshotter fileSystemSnapshotter;
    private final MinimalPersistentCache<HashCode, ClasspathEntrySnapshotData> cache;

    public DefaultClasspathEntrySnapshotCache(FileSystemSnapshotter fileSystemSnapshotter, PersistentIndexedCache<HashCode, ClasspathEntrySnapshotData> persistentCache) {
        this.fileSystemSnapshotter = fileSystemSnapshotter;
        cache = new MinimalPersistentCache<HashCode, ClasspathEntrySnapshotData>(persistentCache);
    }

    @Override
    public ClasspathEntrySnapshot get(File file, HashCode hash) {
        ClasspathEntrySnapshotData data = cache.get(hash);
        return data != null ? new ClasspathEntrySnapshot(data) : null;
    }

    @Override
    public ClasspathEntrySnapshot get(File key, final Factory<ClasspathEntrySnapshot> factory) {
        HashCode fileContentHash = fileSystemSnapshotter.snapshot(key).getHash();
        return new ClasspathEntrySnapshot(cache.get(fileContentHash, new Factory<ClasspathEntrySnapshotData>() {
            @Override
            public ClasspathEntrySnapshotData create() {
                return factory.create().getData();
            }
        }));
    }
}
