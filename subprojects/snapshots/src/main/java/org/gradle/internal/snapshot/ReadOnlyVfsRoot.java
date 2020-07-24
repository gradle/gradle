/*
 * Copyright 2020 the original author or authors.
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

import java.util.Optional;

public interface ReadOnlyVfsRoot {
    /**
     * Returns the snapshot stored at the absolute path.
     */
    Optional<MetadataSnapshot> getMetadata(String absolutePath);

    /**
     * Returns the complete snapshot stored at the absolute path.
     */
    default Optional<CompleteFileSystemLocationSnapshot> getSnapshot(String absolutePath) {
        return getMetadata(absolutePath)
            .filter(CompleteFileSystemLocationSnapshot.class::isInstance)
            .map(CompleteFileSystemLocationSnapshot.class::cast);
    }

    void visitSnapshotRoots(SnapshotVisitor snapshotVisitor);

    interface SnapshotVisitor {
        void visitSnapshotRoot(CompleteFileSystemLocationSnapshot snapshot);
    }
}
