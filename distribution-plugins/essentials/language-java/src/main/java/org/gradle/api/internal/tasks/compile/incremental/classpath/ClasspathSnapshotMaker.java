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

import org.gradle.internal.time.Time;
import org.gradle.internal.time.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class ClasspathSnapshotMaker implements ClasspathSnapshotProvider {

    private static final Logger LOG = LoggerFactory.getLogger(ClasspathSnapshotMaker.class);

    private final ClasspathSnapshotFactory classpathSnapshotFactory;

    private ClasspathSnapshot classpathSnapshot;

    public ClasspathSnapshotMaker(ClasspathSnapshotFactory classpathSnapshotFactory) {
        this.classpathSnapshotFactory = classpathSnapshotFactory;
    }

    @Override
    public ClasspathSnapshot getClasspathSnapshot(Iterable<File> classpath) {
        maybeInitialize(classpath);
        return classpathSnapshot;
    }

    private void maybeInitialize(Iterable<File> classpath) {
        if (classpathSnapshot != null) {
            return;
        }
        Timer clock = Time.startTimer();

        classpathSnapshot = classpathSnapshotFactory.createSnapshot(classpath);
        int duplicatesCount = classpathSnapshot.getData().getDuplicateClasses().size();
        String duplicateClassesMessage = duplicatesCount == 0 ? "" : ". " + duplicatesCount + " duplicate classes found in classpath (see all with --debug)";
        LOG.info("Created classpath snapshot for incremental compilation in {}{}.", clock.getElapsed(), duplicateClassesMessage);
        LOG.debug("While calculating classpath snapshot {} duplicate classes were found: {}.", duplicatesCount, classpathSnapshot.getData().getDuplicateClasses());
    }
}
