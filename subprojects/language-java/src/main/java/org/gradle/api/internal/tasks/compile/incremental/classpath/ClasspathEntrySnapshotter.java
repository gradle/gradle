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

import org.gradle.cache.Cache;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.vfs.FileSystemAccess;

import java.io.File;

public class ClasspathEntrySnapshotter {

    private final ClasspathEntryAnalyzer analyzer;
    private final FileSystemAccess fileSystemAccess;
    private final Cache<HashCode, ClasspathEntrySnapshotData> cache;

    public ClasspathEntrySnapshotter(ClasspathEntryAnalyzer analyzer,
                                     FileSystemAccess fileSystemAccess,
                                     Cache<HashCode, ClasspathEntrySnapshotData> cache) {
        this.analyzer = analyzer;
        this.fileSystemAccess = fileSystemAccess;
        this.cache = cache;
    }

    public ClasspathEntrySnapshotData createSnapshot(final File classpathEntry) {
        return fileSystemAccess.read(
            classpathEntry.getAbsolutePath(),
            snapshot -> cache.get(snapshot.getHash(), hash -> analyzer.analyze(hash, classpathEntry))
        );
    }
}
