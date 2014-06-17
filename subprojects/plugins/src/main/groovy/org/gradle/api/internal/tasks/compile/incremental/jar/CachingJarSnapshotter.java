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

import org.gradle.api.internal.hash.Hasher;
import org.gradle.api.internal.tasks.compile.incremental.cache.JarSnapshotCache;

public class CachingJarSnapshotter implements JarSnapshotter {

    private final JarSnapshotter snapshotter;
    private final Hasher hasher;
    private final JarSnapshotCache cache;

    public CachingJarSnapshotter(JarSnapshotter snapshotter, Hasher hasher, JarSnapshotCache cache) {
        this.snapshotter = snapshotter;
        this.hasher = hasher;
        this.cache = cache;
    }

    public JarSnapshot createSnapshot(JarArchive jarArchive) {
        byte[] hash = hasher.hash(jarArchive.file);
        JarSnapshot cached = cache.loadSnapshot(hash);
        if (cached != null) {
            return cached;
        }
        JarSnapshot snapshot = snapshotter.createSnapshot(jarArchive);
        cache.storeSnapshot(hash, snapshot);
        return snapshot;
    }
}