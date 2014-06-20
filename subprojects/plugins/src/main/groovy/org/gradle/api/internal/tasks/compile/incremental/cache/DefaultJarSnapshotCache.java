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

package org.gradle.api.internal.tasks.compile.incremental.cache;

import org.gradle.api.internal.cache.MinimalPersistentCache;
import org.gradle.api.internal.tasks.compile.incremental.jar.JarSnapshot;
import org.gradle.cache.CacheRepository;
import org.gradle.internal.Factory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class DefaultJarSnapshotCache extends MinimalPersistentCache<byte[], JarSnapshot> implements JarSnapshotCache {
    public DefaultJarSnapshotCache(CacheRepository cacheRepository) {
        super(cacheRepository, "jar snapshots", byte[].class, JarSnapshot.class);
    }

    public Map<File, JarSnapshot> getJarSnapshots(final Map<File, byte[]> jarHashes) {
        //TODO SF avoid reaching out to the parent class, compose
        return getCacheAccess().useCache("loading jar snapshots", new Factory<Map<File, JarSnapshot>>() {
            public Map<File, JarSnapshot> create() {
                final Map<File, JarSnapshot> out = new HashMap<File, JarSnapshot>();
                for (Map.Entry<File, byte[]> entry : jarHashes.entrySet()) {
                    JarSnapshot snapshot = getCache().get(entry.getValue());
                    out.put(entry.getKey(), snapshot);
                }
                return out;
            }
        });
    }
}