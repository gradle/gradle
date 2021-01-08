/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.execution.history.impl;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.execution.history.OverlappingOutputDetector;
import org.gradle.internal.execution.history.OverlappingOutputs;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot.FileSystemLocationSnapshotTransformer;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.MissingFileSnapshot;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.RootTrackingFileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotUtil;
import org.gradle.internal.snapshot.SnapshotVisitResult;

import javax.annotation.Nullable;
import java.util.Map;

import static org.gradle.internal.snapshot.SnapshotUtil.getRootHashes;

public class DefaultOverlappingOutputDetector implements OverlappingOutputDetector {
    @Override
    @Nullable
    public OverlappingOutputs detect(ImmutableSortedMap<String, FileSystemSnapshot> previous, ImmutableSortedMap<String, FileSystemSnapshot> current) {
        for (Map.Entry<String, FileSystemSnapshot> entry : current.entrySet()) {
            String propertyName = entry.getKey();
            FileSystemSnapshot currentSnapshot = entry.getValue();
            FileSystemSnapshot previousSnapshot = previous.getOrDefault(propertyName, FileSystemSnapshot.EMPTY);
            // If the root hashes are the same there are no overlapping outputs
            if (getRootHashes(previousSnapshot).equals(getRootHashes(currentSnapshot))) {
                continue;
            }
            OverlappingOutputs overlappingOutputs = detect(propertyName, previousSnapshot, currentSnapshot);
            if (overlappingOutputs != null) {
                return overlappingOutputs;
            }
        }
        return null;
    }

    @Nullable
    private static OverlappingOutputs detect(String propertyName, FileSystemSnapshot previous, FileSystemSnapshot before) {
        Map<String, FileSystemLocationSnapshot> previousIndex = SnapshotUtil.index(previous);
        OverlappingOutputsDetectingVisitor outputsDetectingVisitor = new OverlappingOutputsDetectingVisitor(previousIndex);
        before.accept(outputsDetectingVisitor);
        String overlappingPath = outputsDetectingVisitor.getOverlappingPath();
        return overlappingPath == null ? null : new OverlappingOutputs(propertyName, overlappingPath);
    }

    private static class OverlappingOutputsDetectingVisitor extends RootTrackingFileSystemSnapshotHierarchyVisitor {
        private final Map<String, FileSystemLocationSnapshot> previousSnapshots;
        private String overlappingPath;

        public OverlappingOutputsDetectingVisitor(Map<String, FileSystemLocationSnapshot> previousSnapshots) {
            this.previousSnapshots = previousSnapshots;
        }

        @Override
        public SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot, boolean isRoot) {
            boolean newContent = snapshot.accept(new FileSystemLocationSnapshotTransformer<Boolean>() {
                @Override
                public Boolean visitDirectory(DirectorySnapshot directorySnapshot) {
                    // Check if a new directory appeared. For matching directories don't check content
                    // hash as we should detect individual entries that are different instead)
                    return hasNewContent(directorySnapshot);
                }

                @Override
                public Boolean visitRegularFile(RegularFileSnapshot fileSnapshot) {
                    // Check if a new file has appeared, or if an existing file's content has changed
                    return hasNewContent(fileSnapshot);
                }

                @Override
                public Boolean visitMissing(MissingFileSnapshot missingSnapshot) {
                    // If the root has gone missing then we don't have overlaps
                    if (isRoot) {
                        return false;
                    }
                    // Otherwise check for newly added broken symlinks and unreadable files
                    return hasNewContent(missingSnapshot);
                }
            });
            if (newContent) {
                overlappingPath = snapshot.getAbsolutePath();
                return SnapshotVisitResult.TERMINATE;
            } else {
                return SnapshotVisitResult.CONTINUE;
            }
        }

        private boolean hasNewContent(FileSystemLocationSnapshot snapshot) {
            FileSystemLocationSnapshot previousSnapshot = previousSnapshots.get(snapshot.getAbsolutePath());
            // Created since last execution, possibly by another task
            if (previousSnapshot == null) {
                return true;
            }
            return !snapshot.isContentUpToDate(previousSnapshot);
        }

        @Nullable
        public String getOverlappingPath() {
            return overlappingPath;
        }
    }
}
