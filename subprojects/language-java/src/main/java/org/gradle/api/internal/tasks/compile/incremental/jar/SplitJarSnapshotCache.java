/*
 * Copyright 2018 the original author or authors.
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
import org.gradle.api.internal.changedetection.state.WellKnownFileLocations;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.hash.HashCode;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Map;

/**
 * A {@link JarSnapshotCache} that delegates to the global cache for files that are known to be immutable.
 * All other files are cached in the local cache. Closing this cache only closes the local delegate, not the global one.
 */
public class SplitJarSnapshotCache implements JarSnapshotCache, Closeable {
    private final WellKnownFileLocations fileLocations;
    private final JarSnapshotCache globalCache;
    private final JarSnapshotCache localCache;

    public SplitJarSnapshotCache(WellKnownFileLocations fileLocations, JarSnapshotCache globalCache, JarSnapshotCache localCache) {
        this.fileLocations = fileLocations;
        this.globalCache = globalCache;
        this.localCache = localCache;
    }

    @Override
    public Map<File, JarSnapshot> getJarSnapshots(Map<File, HashCode> jars) {
        Map<File, HashCode> globalJars = Maps.newLinkedHashMap();
        Map<File, HashCode> localJars = Maps.newLinkedHashMap();
        for (Map.Entry<File, HashCode> entry : jars.entrySet()) {
            if (fileLocations.isImmutable(entry.getKey().getPath())) {
                globalJars.put(entry.getKey(), entry.getValue());
            } else {
                localJars.put(entry.getKey(), entry.getValue());
            }
        }
        Map<File, JarSnapshot> globalSnapshots = globalCache.getJarSnapshots(globalJars);
        Map<File, JarSnapshot> localSnapshots = localCache.getJarSnapshots(localJars);

        Map<File, JarSnapshot> snapshots = Maps.newLinkedHashMap();
        for (File jar : jars.keySet()) {
            JarSnapshot snapshot = globalSnapshots.get(jar);
            if (snapshot == null) {
                snapshot = localSnapshots.get(jar);
            }
            snapshots.put(jar, snapshot);
        }
        return snapshots;
    }

    @Override
    public JarSnapshot get(File jar, Factory<JarSnapshot> factory) {
        if (fileLocations.isImmutable(jar.getPath())) {
            return globalCache.get(jar, factory);
        } else {
            return localCache.get(jar, factory);
        }
    }

    @Override
    public void close() throws IOException {
        CompositeStoppable.stoppable(localCache).stop();
    }
}
