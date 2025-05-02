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

import org.gradle.internal.RelativePathSupplier;

public interface RelativePathTrackingFileSystemSnapshotHierarchyVisitor {
    /**
     * Called before visiting the contents of a directory.
     */
    default void enterDirectory(DirectorySnapshot directorySnapshot, RelativePathSupplier relativePath) {}

    /**
     * Called for each regular file/directory/missing/unavailable file.
     *
     * @return how to continue visiting the rest of the snapshot hierarchy.
     */
    SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot, RelativePathSupplier relativePath);

    /**
     * Called after all entries in the directory has been visited.
     */
    default void leaveDirectory(DirectorySnapshot directorySnapshot, RelativePathSupplier relativePath) {}
}
