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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class JarSnapshotFeeder {

    private final JarSnapshotCache jarSnapshotCache;
    private final Set<File> changedJars = new HashSet<File>();
    private final JarSnapshotter jarSnapshotter;

    public JarSnapshotFeeder(JarSnapshotCache jarSnapshotCache, JarSnapshotter jarSnapshotter) {
        this.jarSnapshotCache = jarSnapshotCache;
        this.jarSnapshotter = jarSnapshotter;
    }

    public JarSnapshot changedJar(File jarFile) {
        JarSnapshot snapshot = jarSnapshotCache.getSnapshot(jarFile);
        changedJars.add(jarFile);
        return snapshot;
    }

    public void storeJarSnapshots(Iterable<JarArchive> jars) {
        Map<File, JarSnapshot> newSnapshots = new HashMap<File, JarSnapshot>();
        for (JarArchive jar : jars) {
            if (!changedJars.contains(jar.file) && jarSnapshotCache.getSnapshot(jar.file) != null) {
                //if jar was not changed and the the snapshot already exists, skip
                continue;
            }
            newSnapshots.put(jar.file, jarSnapshotter.createSnapshot(jar.contents));
        }
        jarSnapshotCache.putSnapshots(newSnapshots);
    }

    public JarSnapshot createSnapshot(JarArchive jarArchive) {
        return jarSnapshotter.createSnapshot(jarArchive.contents);
    }
}