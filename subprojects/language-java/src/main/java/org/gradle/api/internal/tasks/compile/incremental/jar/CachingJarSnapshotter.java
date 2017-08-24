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

package org.gradle.api.internal.tasks.compile.incremental.jar;

import org.gradle.api.internal.tasks.compile.incremental.analyzer.ClassDependenciesAnalyzer;
import org.gradle.internal.Factory;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.StreamHasher;

public class CachingJarSnapshotter implements JarSnapshotter {

    private final DefaultJarSnapshotter snapshotter;
    private final FileHasher fileHasher;
    private final JarSnapshotCache cache;

    public CachingJarSnapshotter(StreamHasher streamHasher, FileHasher fileHasher, ClassDependenciesAnalyzer analyzer, JarSnapshotCache cache) {
        this.snapshotter = new DefaultJarSnapshotter(streamHasher, analyzer);
        this.fileHasher = fileHasher;
        this.cache = cache;
    }

    @Override
    public JarSnapshot createSnapshot(final JarArchive jarArchive) {
        final HashCode hash = getHash(jarArchive);
        return cache.get(hash, new Factory<JarSnapshot>() {
            public JarSnapshot create() {
                return snapshotter.createSnapshot(hash, jarArchive);
            }
        });
    }

    private HashCode getHash(JarArchive jarArchive) {
        return fileHasher.hash(jarArchive.file);
    }
}
