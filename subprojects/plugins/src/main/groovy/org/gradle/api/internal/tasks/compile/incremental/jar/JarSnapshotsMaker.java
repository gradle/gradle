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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.util.Clock;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class JarSnapshotsMaker {

    private static final Logger LOG = Logging.getLogger(JarSnapshotsMaker.class);

    private final LocalJarSnapshots localJarSnapshots;
    private final JarSnapshotter jarSnapshotter;
    private final ClasspathJarFinder classpathJarFinder;

    private Map<File, JarSnapshot> jarSnapshots;

    public JarSnapshotsMaker(LocalJarSnapshots localJarSnapshots, JarSnapshotter jarSnapshotter, ClasspathJarFinder classpathJarFinder) {
        this.localJarSnapshots = localJarSnapshots;
        this.jarSnapshotter = jarSnapshotter;
        this.classpathJarFinder = classpathJarFinder;
    }

    public void storeJarSnapshots(Iterable<File> classpath) {
        initSnapshots(classpath);
        Clock clock = new Clock();
        Map<File, byte[]> jarHashes = new HashMap<File, byte[]>();
        for (Map.Entry<File, JarSnapshot> e : jarSnapshots.entrySet()) {
            jarHashes.put(e.getKey(), e.getValue().getHash());
        }
        localJarSnapshots.putHashes(jarHashes);
        LOG.info("Written jar snapshots for incremental compilation in {}.", clock.getTime());
    }

    public JarClasspathSnapshot createJarClasspathSnapshot(Iterable<File> classpath) {
        initSnapshots(classpath);
        return new JarClasspathSnapshot(jarSnapshots);
    }

    private void initSnapshots(Iterable<File> classpath) {
        if (jarSnapshots != null) {
            return;
        }
        Clock clock = new Clock();
        Iterable<JarArchive> jarArchives = classpathJarFinder.findJarArchives(classpath);

        jarSnapshots = new HashMap<File, JarSnapshot>();
        for (JarArchive jar : jarArchives) {
            JarSnapshot snapshot = jarSnapshotter.createSnapshot(jar);
            jarSnapshots.put(jar.file, snapshot);
        }
        String creationTime = clock.getTime();
        LOG.info("Created jar snapshots for incremental compilation in {}.", clock.getTime(), creationTime);
    }
}