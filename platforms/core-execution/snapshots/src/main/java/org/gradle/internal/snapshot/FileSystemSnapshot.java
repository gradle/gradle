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

package org.gradle.internal.snapshot;

import java.util.stream.Stream;

/**
 * A snapshot of a part of the file system.
 */
public interface FileSystemSnapshot {
    /**
     * An empty snapshot.
     */
    FileSystemSnapshot EMPTY = new FileSystemSnapshot() {
        @Override
        public Stream<FileSystemLocationSnapshot> roots() {
            return Stream.empty();
        }

        @Override
        public SnapshotVisitResult accept(FileSystemSnapshotHierarchyVisitor visitor) {
            return SnapshotVisitResult.CONTINUE;
        }

        @Override
        public SnapshotVisitResult accept(RelativePathTracker pathTracker, RelativePathTrackingFileSystemSnapshotHierarchyVisitor visitor) {
            return SnapshotVisitResult.CONTINUE;
        }
    };

    /**
     * Returns a stream of roots.
     */
    Stream<FileSystemLocationSnapshot> roots();

    /**
     * Walks the whole hierarchy represented by this snapshot.
     *
     * The walk is depth first.
     */
    SnapshotVisitResult accept(FileSystemSnapshotHierarchyVisitor visitor);

    /**
     * Walks the whole hierarchy represented by this snapshot.
     *
     * The walk is depth first.
     */
    SnapshotVisitResult accept(RelativePathTracker pathTracker, RelativePathTrackingFileSystemSnapshotHierarchyVisitor visitor);
}
