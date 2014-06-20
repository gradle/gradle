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
    private ClasspathJarFinder classpathJarFinder;

    public JarSnapshotsMaker(LocalJarSnapshots localJarSnapshots, JarSnapshotter jarSnapshotter, ClasspathJarFinder classpathJarFinder) {
        this.localJarSnapshots = localJarSnapshots;
        this.jarSnapshotter = jarSnapshotter;
        this.classpathJarFinder = classpathJarFinder;
    }

    public void storeJarSnapshots(Iterable<File> classpath) {
        Clock clock = new Clock();
        Iterable<JarArchive> jarArchives = classpathJarFinder.findJarArchives(classpath);

        Map<File, byte[]> jarHashes = new HashMap<File, byte[]>();
        for (JarArchive jar : jarArchives) {
            //it's important that we create a snapshot for every jar in the classpath.
            // The implementation of the snapshotter deals with caching of the jar snapshots globally
            JarSnapshot snapshot = jarSnapshotter.createSnapshot(jar);
            jarHashes.put(jar.file, snapshot.getHash());
        }
        String creationTime = clock.getTime();
        localJarSnapshots.putHashes(jarHashes);
        LOG.lifecycle("Created and written jar snapshots in {} (creation took {}).", clock.getTime(), creationTime); //TODO SF fix this lifecycle message and others, too
    }
}