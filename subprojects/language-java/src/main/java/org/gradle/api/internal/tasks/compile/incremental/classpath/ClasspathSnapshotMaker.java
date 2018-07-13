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

package org.gradle.api.internal.tasks.compile.incremental.classpath;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;

import java.io.File;

public class ClasspathSnapshotMaker implements ClasspathSnapshotProvider, ClasspathSnapshotWriter {

    private static final Logger LOG = Logging.getLogger(ClasspathSnapshotMaker.class);

    private final LocalClasspathSnapshotStore classpathSnapshotStore;
    private final ClasspathEntryConverter classpathEntryConverter;
    private final ClasspathSnapshotFactory classpathSnapshotFactory;

    private ClasspathSnapshot classpathSnapshot;

    public ClasspathSnapshotMaker(LocalClasspathSnapshotStore classpathSnapshotStore, ClasspathSnapshotFactory classpathSnapshotFactory, ClasspathEntryConverter classpathEntryConverter) {
        this.classpathSnapshotStore = classpathSnapshotStore;
        this.classpathSnapshotFactory = classpathSnapshotFactory;
        this.classpathEntryConverter = classpathEntryConverter;
    }

    @Override
    public void storeSnapshots(Iterable<File> classpath) {
        maybeInitialize(classpath); //clients may or may not have already created classpath snapshot
        Timer clock = Time.startTimer();
        classpathSnapshotStore.put(classpathSnapshot.getData());
        LOG.info("Written classpath snapshot for incremental compilation in {}.", clock.getElapsed());
    }

    @Override
    public ClasspathSnapshot getClasspathSnapshot(Iterable<File> classpath) {
        maybeInitialize(classpath); //clients may or may not have already created classpath snapshot
        return classpathSnapshot;
    }

    private void maybeInitialize(Iterable<File> classpath) {
        if (classpathSnapshot != null) {
            return;
        }
        Timer clock = Time.startTimer();
        Iterable<ClasspathEntry> entriesArchives = classpathEntryConverter.asClasspathEntries(classpath);

        classpathSnapshot = classpathSnapshotFactory.createSnapshot(entriesArchives);
        int duplicatesCount = classpathSnapshot.getData().getDuplicateClasses().size();
        String duplicateClassesMessage = duplicatesCount == 0 ? "" : ". " + duplicatesCount + " duplicate classes found in classpath (see all with --debug)";
        LOG.info("Created classpath snapshot for incremental compilation in {}{}.", clock.getElapsed(), duplicateClassesMessage);
        LOG.debug("While calculating classpath snapshot {} duplicate classes were found: {}.", duplicatesCount, classpathSnapshot.getData().getDuplicateClasses());
    }
}
