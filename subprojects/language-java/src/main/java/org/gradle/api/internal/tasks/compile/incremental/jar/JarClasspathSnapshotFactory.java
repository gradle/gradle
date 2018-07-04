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

public class JarClasspathSnapshotFactory {

    private final JarSnapshotter jarSnapshotter;
    private final BuildOperationExecutor buildOperationExecutor;

    public JarClasspathSnapshotFactory(JarSnapshotter jarSnapshotter, BuildOperationExecutor buildOperationExecutor) {
        this.jarSnapshotter = jarSnapshotter;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    JarClasspathSnapshot createSnapshot(final Iterable<JarArchive> jarArchives) {
        final Set<CreateJarSnapshot> snapshotOperations = snapshotAll(jarArchives);

        final LinkedHashMap<File, JarSnapshot> jarSnapshots = Maps.newLinkedHashMap();
        final LinkedHashMap<File, HashCode> jarHashes = Maps.newLinkedHashMap();
        final Set<String> allClasses = Sets.newHashSet();
        final Set<String> duplicateClasses = Sets.newHashSet();

        for (CreateJarSnapshot operation : snapshotOperations) {
            JarArchive jar = operation.jar;
            JarSnapshot snapshot = operation.snapshot;
            if (snapshot != null) {
                jarSnapshots.put(jar.file, snapshot);
                jarHashes.put(jar.file, snapshot.getHash());
                for (String c : snapshot.getClasses()) {
                    if (!allClasses.add(c)) {
                        duplicateClasses.add(c);
                    }
                }
            }
        }

        JarClasspathSnapshotData jarClasspathSnapshotData = new JarClasspathSnapshotData(jarHashes, duplicateClasses);
        return new JarClasspathSnapshot(jarSnapshots, jarClasspathSnapshotData);
    }

    private Set<CreateJarSnapshot> snapshotAll(final Iterable<JarArchive> jarArchives) {
        final Set<CreateJarSnapshot> snapshotOperations = Sets.newLinkedHashSet();

        buildOperationExecutor.runAll(new Action<BuildOperationQueue<CreateJarSnapshot>>() {
            @Override
            public void execute(BuildOperationQueue<CreateJarSnapshot> buildOperationQueue) {
                for (JarArchive jar : jarArchives) {
                    CreateJarSnapshot operation = new CreateJarSnapshot(jar);
                    snapshotOperations.add(operation);
                    buildOperationQueue.add(operation);
                }
            }
        });
        return snapshotOperations;
    }

    private class CreateJarSnapshot implements RunnableBuildOperation {
        private final JarArchive jar;
        private JarSnapshot snapshot;

        private CreateJarSnapshot(JarArchive jar) {
            this.jar = jar;
        }

        @Override
        public void run(BuildOperationContext context) {
            if (jar.file.exists()) {
                snapshot = jarSnapshotter.createSnapshot(jar);
            }
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor.displayName("Create JAR snapshot for " + jar);
        }
    }
}
