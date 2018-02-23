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

import com.google.common.collect.Maps;
import org.gradle.cache.PersistentIndexedCache;
import org.gradle.cache.internal.MinimalPersistentCache;
import org.gradle.internal.Factory;
import org.gradle.internal.hash.HashCode;

import java.io.File;
import java.util.Map;

public class DefaultJarSnapshotCache implements JarSnapshotCache {
    private final MinimalPersistentCache<HashCode, JarSnapshotData> cache;

    public DefaultJarSnapshotCache(PersistentIndexedCache<HashCode, JarSnapshotData> persistentCache) {
        cache = new MinimalPersistentCache<HashCode, JarSnapshotData>(persistentCache);
    }

    @Override
    public Map<File, JarSnapshot> getJarSnapshots(final Map<File, HashCode> jarHashes) {
        Map<File, JarSnapshot> out = Maps.newLinkedHashMap();
        for (Map.Entry<File, HashCode> entry : jarHashes.entrySet()) {
            JarSnapshotData snapshotData = cache.get(entry.getValue());
            if (snapshotData == null) {
                throw new IllegalStateException("No Jar snapshot data available for " + entry.getKey() + " with hash " + entry.getValue() + ".");
            }
            JarSnapshot snapshot = new JarSnapshot(snapshotData);
            out.put(entry.getKey(), snapshot);
        }
        return out;
    }

    @Override
    public JarSnapshot get(HashCode key, final Factory<JarSnapshot> factory) {
        return new JarSnapshot(cache.get(key, new Factory<JarSnapshotData>() {
            public JarSnapshotData create() {
                return factory.create().getData();
            }
        }));
    }
}
