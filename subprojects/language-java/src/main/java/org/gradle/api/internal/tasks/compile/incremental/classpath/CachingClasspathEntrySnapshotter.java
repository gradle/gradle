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

import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.StreamHasher;
import org.gradle.internal.vfs.FileSystemAccess;

import java.io.File;

public class CachingClasspathEntrySnapshotter implements ClasspathEntrySnapshotter {

    private final DefaultClasspathEntrySnapshotter snapshotter;
    private final FileSystemAccess fileSystemAccess;
    private final ClasspathEntrySnapshotCache cache;

    public CachingClasspathEntrySnapshotter(FileHasher fileHasher, StreamHasher streamHasher, FileSystemAccess fileSystemAccess, ClassDependenciesAnalyzer analyzer, ClasspathEntrySnapshotCache cache, FileOperations fileOperations) {
        this.snapshotter = new DefaultClasspathEntrySnapshotter(fileHasher, streamHasher, analyzer, fileOperations);
        this.fileSystemAccess = fileSystemAccess;
        this.cache = cache;
    }

    @Override
    public ClasspathEntrySnapshot createSnapshot(final File classpathEntry) {
        return cache.get(
            classpathEntry,
            () -> fileSystemAccess.read(
                classpathEntry.getAbsolutePath(),
                snapshot -> snapshotter.createSnapshot(snapshot.getHash(), classpathEntry)
            )
        );
    }
}
