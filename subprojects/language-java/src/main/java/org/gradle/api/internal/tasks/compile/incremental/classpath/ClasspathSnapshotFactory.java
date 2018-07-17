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

package org.gradle.api.internal.tasks.compile.incremental.classpath;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Set;

public class ClasspathSnapshotFactory {

    private final ClasspathEntrySnapshotter classpathEntrySnapshotter;
    private final BuildOperationExecutor buildOperationExecutor;

    public ClasspathSnapshotFactory(ClasspathEntrySnapshotter classpathEntrySnapshotter, BuildOperationExecutor buildOperationExecutor) {
        this.classpathEntrySnapshotter = classpathEntrySnapshotter;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    ClasspathSnapshot createSnapshot(final Iterable<File> entries) {
        final Set<CreateSnapshot> snapshotOperations = snapshotAll(entries);

        final LinkedHashMap<File, ClasspathEntrySnapshot> snapshots = Maps.newLinkedHashMap();
        final LinkedHashMap<File, HashCode> hashes = Maps.newLinkedHashMap();
        final Set<String> allClasses = Sets.newHashSet();
        final Set<String> duplicateClasses = Sets.newHashSet();

        for (CreateSnapshot operation : snapshotOperations) {
            File entry = operation.entry;
            ClasspathEntrySnapshot snapshot = operation.snapshot;
            if (snapshot != null) {
                snapshots.put(entry, snapshot);
                hashes.put(entry, snapshot.getHash());
                for (String c : snapshot.getClasses()) {
                    if (!allClasses.add(c)) {
                        duplicateClasses.add(c);
                    }
                }
            }
        }

        ClasspathSnapshotData classpathSnapshotData = new ClasspathSnapshotData(hashes, duplicateClasses);
        return new ClasspathSnapshot(snapshots, classpathSnapshotData);
    }

    private Set<CreateSnapshot> snapshotAll(final Iterable<File> entries) {
        final Set<CreateSnapshot> snapshotOperations = Sets.newLinkedHashSet();

        buildOperationExecutor.runAll(new Action<BuildOperationQueue<CreateSnapshot>>() {
            @Override
            public void execute(BuildOperationQueue<CreateSnapshot> buildOperationQueue) {
                for (File entry : entries) {
                    CreateSnapshot operation = new CreateSnapshot(entry);
                    snapshotOperations.add(operation);
                    buildOperationQueue.add(operation);
                }
            }
        });
        return snapshotOperations;
    }

    private class CreateSnapshot implements RunnableBuildOperation {
        private final File entry;
        private ClasspathEntrySnapshot snapshot;

        private CreateSnapshot(File entry) {
            this.entry = entry;
        }

        @Override
        public void run(BuildOperationContext context) {
            if (entry.exists()) {
                snapshot = classpathEntrySnapshotter.createSnapshot(entry);
            }
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName("Create incremental compile snapshot for " + entry);
        }
    }
}
