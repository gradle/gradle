/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.fingerprint.impl;

import org.gradle.internal.file.FileType;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.RootTrackingFileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotVisitResult;

import java.util.HashSet;

/**
 * A {@link RootTrackingFileSystemSnapshotHierarchyVisitor} that ignores missing roots and multiple entries for the same path.
 */
public abstract class MissingRootAndDuplicateIgnoringFileSystemSnapshotVisitor extends RootTrackingFileSystemSnapshotHierarchyVisitor {
    private final HashSet<String> processedEntries = new HashSet<>();

    @Override
    public final SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot, boolean isRoot) {
        if (!(snapshot.getType() == FileType.Missing && isRoot) && processedEntries.add(snapshot.getAbsolutePath())) {
            visitAcceptedEntry(snapshot, isRoot);
        }
        return SnapshotVisitResult.CONTINUE;
    }

    /**
     * Called for all entries that are not missing roots and have not been visited before.
     */
    public abstract void visitAcceptedEntry(FileSystemLocationSnapshot snapshot, boolean isRoot);
}
