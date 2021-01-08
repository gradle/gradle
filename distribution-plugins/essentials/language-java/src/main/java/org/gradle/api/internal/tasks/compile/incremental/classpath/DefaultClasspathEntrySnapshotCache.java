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
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.vfs.FileSystemAccess;

import java.io.File;
import java.util.function.Function;

public class DefaultClasspathEntrySnapshotCache implements ClasspathEntrySnapshotCache {
    private final FileSystemAccess fileSystemAccess;
    private final MinimalPersistentCache<HashCode, ClasspathEntrySnapshotData> cache;

    public DefaultClasspathEntrySnapshotCache(FileSystemAccess fileSystemAccess, PersistentIndexedCache<HashCode, ClasspathEntrySnapshotData> persistentCache) {
        this.fileSystemAccess = fileSystemAccess;
        this.cache = new MinimalPersistentCache<>(persistentCache);
    }

    @Override
    public ClasspathEntrySnapshot get(File file, HashCode hash) {
        ClasspathEntrySnapshotData data = cache.getIfPresent(hash);
        return data != null ? new ClasspathEntrySnapshot(data) : null;
    }

    @Override
    public ClasspathEntrySnapshot get(File key, Function<? super File, ? extends ClasspathEntrySnapshot> factory) {
        HashCode fileContentHash = getFileContentHash(key);
        return new ClasspathEntrySnapshot(cache.get(fileContentHash, () -> factory.apply(key).getData()));
    }

    private HashCode getFileContentHash(File key) {
        return fileSystemAccess.read(
            key.getAbsolutePath(),
            FileSystemLocationSnapshot::getHash
        );
    }

    @Override
    public ClasspathEntrySnapshot getIfPresent(File key) {
        HashCode fileContentHash = getFileContentHash(key);
        ClasspathEntrySnapshotData classpathEntrySnapshotData = cache.getIfPresent(fileContentHash);
        return classpathEntrySnapshotData == null ? null : new ClasspathEntrySnapshot(classpathEntrySnapshotData);
    }

    @Override
    public void put(File key, ClasspathEntrySnapshot value) {
        HashCode fileContentHash = getFileContentHash(key);
        cache.put(fileContentHash, value.getData());
    }
}
