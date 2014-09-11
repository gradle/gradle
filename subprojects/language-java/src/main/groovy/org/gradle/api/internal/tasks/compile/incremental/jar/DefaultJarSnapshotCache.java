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

import org.gradle.api.internal.cache.MinimalPersistentCache;
import org.gradle.cache.CacheRepository;
import org.gradle.internal.Factory;
import org.gradle.messaging.serialize.BaseSerializerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Cross-process, global cache of jar snapshots. Required to make incremental java compilation fast.
 * Jar snapshots are cached globally, so if one project caches the groovy jar, it can be used by some other project.
 */
public class DefaultJarSnapshotCache implements JarSnapshotCache {

    private final MinimalPersistentCache<byte[], JarSnapshotData> cache;

    public DefaultJarSnapshotCache(CacheRepository cacheRepository) {
        cache = new MinimalPersistentCache<byte[], JarSnapshotData>(cacheRepository, "jar snapshots", BaseSerializerFactory.BYTE_ARRAY_SERIALIZER, new JarSnapshotDataSerializer());
    }

    public Map<File, JarSnapshot> getJarSnapshots(final Map<File, byte[]> jarHashes) {
        return cache.getCacheAccess().useCache("loading jar snapshots", new Factory<Map<File, JarSnapshot>>() {
            public Map<File, JarSnapshot> create() {
                final Map<File, JarSnapshot> out = new HashMap<File, JarSnapshot>();
                for (Map.Entry<File, byte[]> entry : jarHashes.entrySet()) {
                    JarSnapshot snapshot = new JarSnapshot(cache.getCache().get(entry.getValue()));
                    out.put(entry.getKey(), snapshot);
                }
                return out;
            }
        });
    }

    public JarSnapshot get(byte[] key, final Factory<JarSnapshot> factory) {
        return new JarSnapshot(cache.get(key, new Factory<JarSnapshotData>() {
            public JarSnapshotData create() {
                return factory.create().getData();
            }
        }));
    }

    public void stop() {
        cache.stop();
    }
}