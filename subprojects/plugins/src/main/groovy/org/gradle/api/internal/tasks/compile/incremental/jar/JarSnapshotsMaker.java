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

package org.gradle.api.internal.tasks.compile.incremental.jar;

import org.apache.commons.collections.CollectionUtils;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.util.Clock;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class JarSnapshotsMaker {

    private static final Logger LOG = Logging.getLogger(JarSnapshotsMaker.class);

    private final LocalJarClasspathSnapshot localJarClasspathSnapshot;
    private final JarSnapshotter jarSnapshotter;
    private final ClasspathJarFinder classpathJarFinder;

    private JarClasspathSnapshot jarClasspathSnapshot;

    public JarSnapshotsMaker(LocalJarClasspathSnapshot localJarClasspathSnapshot, JarSnapshotter jarSnapshotter, ClasspathJarFinder classpathJarFinder) {
        this.localJarClasspathSnapshot = localJarClasspathSnapshot;
        this.jarSnapshotter = jarSnapshotter;
        this.classpathJarFinder = classpathJarFinder;
    }

    public void storeJarSnapshots(Iterable<File> classpath) {
        init(classpath);
        Clock clock = new Clock();
        localJarClasspathSnapshot.putClasspathSnapshot(jarClasspathSnapshot.getData());
        LOG.info("Written jar snapshots for incremental compilation in {}.", clock.getTime());
    }

    public JarClasspathSnapshot createJarClasspathSnapshot(Iterable<File> classpath) {
        init(classpath);
        return jarClasspathSnapshot;
    }

    private void init(Iterable<File> classpath) {
        if (jarClasspathSnapshot != null) {
            return;
        }
        Clock clock = new Clock();
        Iterable<JarArchive> jarArchives = classpathJarFinder.findJarArchives(classpath);

        Map<File, JarSnapshot> jarSnapshots = new HashMap<File, JarSnapshot>();
        Map<File, byte[]> jarHashes = new HashMap<File, byte[]>();
        Set<String> allClasses = new HashSet<String>();
        Set<String> duplicateClasses = new HashSet<String>();
        for (JarArchive jar : jarArchives) {
            JarSnapshot snapshot = jarSnapshotter.createSnapshot(jar);
            jarSnapshots.put(jar.file, snapshot);
            jarHashes.put(jar.file, snapshot.getHash());
            duplicateClasses.addAll(CollectionUtils.intersection(allClasses, snapshot.hashes.keySet()));
            allClasses.addAll(snapshot.hashes.keySet());
        }
        String creationTime = clock.getTime();
        JarClasspathSnapshotData jarClasspathSnapshotData = new JarClasspathSnapshotData(jarHashes, duplicateClasses);
        jarClasspathSnapshot = new JarClasspathSnapshot(jarSnapshots, jarClasspathSnapshotData);
        LOG.info("Created jar snapshots for incremental compilation in {}.", clock.getTime(), creationTime);
    }
}