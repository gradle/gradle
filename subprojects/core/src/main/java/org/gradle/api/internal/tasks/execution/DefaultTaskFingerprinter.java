/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.tasks.execution;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.properties.FilePropertySpec;
import org.gradle.internal.fingerprint.CurrentFileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileCollectionFingerprinter;
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.fingerprint.FileCollectionSnapshotter;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.SortedSet;

public class DefaultTaskFingerprinter implements TaskFingerprinter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultTaskFingerprinter.class);

    private final FileCollectionFingerprinterRegistry fingerprinterRegistry;
    private final FileCollectionSnapshotter fileCollectionSnapshotter;

    public DefaultTaskFingerprinter(FileCollectionFingerprinterRegistry fingerprinterRegistry, FileCollectionSnapshotter fileCollectionSnapshotter) {
        this.fingerprinterRegistry = fingerprinterRegistry;
        this.fileCollectionSnapshotter = fileCollectionSnapshotter;
    }

    @Override
    public ImmutableSortedMap<String, CurrentFileCollectionFingerprint> fingerprintTaskFiles(TaskInternal task, SortedSet<? extends FilePropertySpec> fileProperties) {
        ImmutableSortedMap.Builder<String, CurrentFileCollectionFingerprint> builder = ImmutableSortedMap.naturalOrder();
        for (FilePropertySpec propertySpec : fileProperties) {
            FileCollectionFingerprinter fingerprinter = fingerprinterRegistry.getFingerprinter(propertySpec.getNormalizer());
            LOGGER.debug("Fingerprinting property {} for {}", propertySpec, task);
            CurrentFileCollectionFingerprint result = fingerprinter.fingerprint(propertySpec.getPropertyFiles());
            builder.put(propertySpec.getPropertyName(), result);
        }
        return builder.build();
    }

    @Override
    public ImmutableSortedMap<String, FileSystemSnapshot> snapshotTaskFiles(TaskInternal task, SortedSet<? extends FilePropertySpec> fileProperties) {
        ImmutableSortedMap.Builder<String, FileSystemSnapshot> builder = ImmutableSortedMap.naturalOrder();
        for (FilePropertySpec propertySpec : fileProperties) {
            LOGGER.debug("Snapshotting property {} for {}", propertySpec, task);
            List<FileSystemSnapshot> result = fileCollectionSnapshotter.snapshot(propertySpec.getPropertyFiles());
            builder.put(propertySpec.getPropertyName(), new CompositeFileSystemSnapshot(result));
        }
        return builder.build();
    }

    private static class CompositeFileSystemSnapshot implements FileSystemSnapshot {
        private final List<FileSystemSnapshot> snapshots;

        CompositeFileSystemSnapshot(List<FileSystemSnapshot> snapshots) {
            this.snapshots = snapshots;
        }

        @Override
        public void accept(FileSystemSnapshotVisitor visitor) {
            for (FileSystemSnapshot snapshot : snapshots) {
                snapshot.accept(visitor);
            }
        }
    }
}
