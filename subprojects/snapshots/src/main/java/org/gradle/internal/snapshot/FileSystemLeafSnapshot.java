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

/**
 * The snapshot of a leaf element in the file system that can have no children of its own.
 */
public interface FileSystemLeafSnapshot extends FileSystemLocationSnapshot {
    @Override
    default SnapshotVisitResult accept(FileSystemSnapshotHierarchyVisitor visitor) {
        return visitor.visitEntry(this);
    }

    @Override
    default SnapshotVisitResult accept(RelativePathTracker pathTracker, RelativePathTrackingFileSystemSnapshotHierarchyVisitor visitor) {
        pathTracker.enter(getName());
        try {
            return visitor.visitEntry(this, pathTracker);
        } finally {
            pathTracker.leave();
        }
    }
}
