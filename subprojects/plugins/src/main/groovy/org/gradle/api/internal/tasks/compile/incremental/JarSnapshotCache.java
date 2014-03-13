/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

//TODO SF this obviously needs to be replaced with a proper cache
public class JarSnapshotCache {

    private File sharedJarSnapshotCache;
    private Map<File, JarSnapshot> snapshots;

    public JarSnapshotCache(File sharedJarSnapshotCache) {
        this.sharedJarSnapshotCache = sharedJarSnapshotCache;
    }

    public JarSnapshot getSnapshot(File jar) {
        init();
        return snapshots.get(jar);
    }

    public void putSnapshots(Map<File, JarSnapshot> newSnapshots) {
        init();
        this.snapshots.putAll(newSnapshots);
        DummySerializer.writeTargetTo(sharedJarSnapshotCache, this.snapshots);
    }

    private void init() {
        if (snapshots == null) {
            if (sharedJarSnapshotCache.isFile()) {
                snapshots = (Map) DummySerializer.readFrom(sharedJarSnapshotCache);
            } else {
                snapshots = new HashMap<File, JarSnapshot>();
            }
        }
    }
}