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

import com.google.common.hash.HashCode;
import org.gradle.api.internal.cache.MinimalPersistentCache;
import org.gradle.cache.CacheRepository;
import org.gradle.internal.Factory;
import org.gradle.internal.serialize.HashCodeSerializer;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Cross-process, global cache of jar snapshots. Required to make incremental java compilation fast.
 * Jar snapshots are cached globally, so if one project caches the groovy jar, it can be used by some other project.
 */
public class DefaultJarSnapshotCache implements JarSnapshotCache {

    private final MinimalPersistentCache<HashCode, JarSnapshotData> cache;

    public DefaultJarSnapshotCache(CacheRepository cacheRepository) {
        cache = new MinimalPersistentCache<HashCode, JarSnapshotData>(cacheRepository, "jar snapshots", new HashCodeSerializer(), new JarSnapshotDataSerializer());
    }

    @Override
    public Map<File, JarSnapshot> getJarSnapshots(final Map<File, HashCode> jarHashes) {
        return cache.getCacheAccess().useCache(new Factory<Map<File, JarSnapshot>>() {
            public Map<File, JarSnapshot> create() {
                final Map<File, JarSnapshot> out = new HashMap<File, JarSnapshot>();
                for (Map.Entry<File, HashCode> entry : jarHashes.entrySet()) {
                    JarSnapshotData snapshotData = cache.getCache().get(entry.getValue());
                    if (snapshotData == null) {
                        throw new IllegalStateException("No Jar snapshot data available for " + entry.getKey() + " with hash " + entry.getValue() + ".");
                    }
                    JarSnapshot snapshot = new JarSnapshot(snapshotData);
                    out.put(entry.getKey(), snapshot);
                }
                return out;
            }
        });
    }

    @Override
    public JarSnapshot get(HashCode key, final Factory<JarSnapshot> factory) {
        return new JarSnapshot(cache.get(key, new Factory<JarSnapshotData>() {
            public JarSnapshotData create() {
                return factory.create().getData();
            }
        }));
    }

    @Override
    public void stop() {
        cache.stop();
    }
}
