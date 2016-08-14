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
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;

import java.io.File;
import java.util.Map;
import java.util.Set;

public class JarClasspathSnapshotFactory {

    private final JarSnapshotter jarSnapshotter;

    public JarClasspathSnapshotFactory(JarSnapshotter jarSnapshotter) {
        this.jarSnapshotter = jarSnapshotter;
    }

    JarClasspathSnapshot createSnapshot(Iterable<JarArchive> jarArchives) {
        Map<File, JarSnapshot> jarSnapshots = Maps.newHashMap();
        Map<File, HashCode> jarHashes = Maps.newHashMap();
        Set<String> allClasses = Sets.newHashSet();
        Set<String> duplicateClasses = Sets.newHashSet();

        for (JarArchive jar : jarArchives) {
            JarSnapshot snapshot = jarSnapshotter.createSnapshot(jar);
            jarSnapshots.put(jar.file, snapshot);
            jarHashes.put(jar.file, snapshot.getHash());
            for (String c : snapshot.getClasses()) {
                if (!allClasses.add(c)) {
                    duplicateClasses.add(c);
                }
            }
        }
        JarClasspathSnapshotData jarClasspathSnapshotData = new JarClasspathSnapshotData(jarHashes, duplicateClasses);
        return new JarClasspathSnapshot(jarSnapshots, jarClasspathSnapshotData);
    }
}
