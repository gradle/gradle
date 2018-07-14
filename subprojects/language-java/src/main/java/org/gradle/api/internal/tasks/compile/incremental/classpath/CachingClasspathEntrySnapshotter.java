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

import org.gradle.api.internal.changedetection.state.FileSystemSnapshotter;
import org.gradle.api.internal.changedetection.state.Snapshot;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;
import org.gradle.caching.internal.DefaultBuildCacheHasher;
import org.gradle.internal.Factory;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.StreamHasher;

public class CachingClasspathEntrySnapshotter implements ClasspathEntrySnapshotter {

    private final DefaultClasspathEntrySnapshotter snapshotter;
    private final FileSystemSnapshotter fileSystemSnapshotter;
    private final ClasspathEntrySnapshotCache cache;

    public CachingClasspathEntrySnapshotter(StreamHasher streamHasher, FileSystemSnapshotter fileSystemSnapshotter, ClassDependenciesAnalyzer analyzer, ClasspathEntrySnapshotCache cache) {
        this.snapshotter = new DefaultClasspathEntrySnapshotter(streamHasher, analyzer);
        this.fileSystemSnapshotter = fileSystemSnapshotter;
        this.cache = cache;
    }

    @Override
    public ClasspathEntrySnapshot createSnapshot(final ClasspathEntry classpathEntry) {
        final HashCode hash = getHash(classpathEntry);
        return cache.get(classpathEntry.file, new Factory<ClasspathEntrySnapshot>() {
            public ClasspathEntrySnapshot create() {
                return snapshotter.createSnapshot(hash, classpathEntry);
            }
        });
    }

    private HashCode getHash(ClasspathEntry classpathEntry) {
        Snapshot fileSnapshot = fileSystemSnapshotter.snapshotAll(classpathEntry.file);
        DefaultBuildCacheHasher hasher = new DefaultBuildCacheHasher();
        fileSnapshot.appendToHasher(hasher);
        return hasher.hash();
    }
}
