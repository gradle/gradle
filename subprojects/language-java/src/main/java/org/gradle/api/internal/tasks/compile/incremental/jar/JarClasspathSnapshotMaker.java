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
import org.gradle.internal.time.Timer;
import org.gradle.internal.time.Timers;

import java.io.File;

public class JarClasspathSnapshotMaker implements JarClasspathSnapshotProvider, JarClasspathSnapshotWriter {

    private static final Logger LOG = Logging.getLogger(JarClasspathSnapshotMaker.class);

    private final LocalJarClasspathSnapshotStore classpathSnapshotStore;
    private final ClasspathJarFinder classpathJarFinder;
    private final JarClasspathSnapshotFactory classpathSnapshotFactory;

    private JarClasspathSnapshot jarClasspathSnapshot;

    public JarClasspathSnapshotMaker(LocalJarClasspathSnapshotStore classpathSnapshotStore, JarClasspathSnapshotFactory classpathSnapshotFactory, ClasspathJarFinder classpathJarFinder) {
        this.classpathSnapshotStore = classpathSnapshotStore;
        this.classpathSnapshotFactory = classpathSnapshotFactory;
        this.classpathJarFinder = classpathJarFinder;
    }

    @Override
    public void storeJarSnapshots(Iterable<File> classpath) {
        maybeInitialize(classpath); //clients may or may not have already created jar classpath snapshot
        Timer clock = Timers.startTimer();
        classpathSnapshotStore.put(jarClasspathSnapshot.getData());
        LOG.info("Written jar classpath snapshot for incremental compilation in {}.", clock.getElapsed());
    }

    @Override
    public JarClasspathSnapshot getJarClasspathSnapshot(Iterable<File> classpath) {
        maybeInitialize(classpath); //clients may or may not have already created jar classpath snapshot
        return jarClasspathSnapshot;
    }

    private void maybeInitialize(Iterable<File> classpath) {
        if (jarClasspathSnapshot != null) {
            return;
        }
        Timer clock = Timers.startTimer();
        Iterable<JarArchive> jarArchives = classpathJarFinder.findJarArchives(classpath);

        jarClasspathSnapshot = classpathSnapshotFactory.createSnapshot(jarArchives);
        int duplicatesCount = jarClasspathSnapshot.getData().getDuplicateClasses().size();
        String duplicateClassesMessage = duplicatesCount == 0? "" : ". " + duplicatesCount + " duplicate classes found in classpath (see all with --debug)";
        LOG.info("Created jar classpath snapshot for incremental compilation in {}{}.", clock.getElapsed(), duplicateClassesMessage);
        LOG.debug("While calculating jar classpath snapshot {} duplicate classes were found: {}.", duplicatesCount, jarClasspathSnapshot.getData().getDuplicateClasses());
    }
}
