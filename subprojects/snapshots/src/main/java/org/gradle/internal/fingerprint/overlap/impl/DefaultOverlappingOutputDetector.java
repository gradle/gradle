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

package org.gradle.internal.fingerprint.overlap.impl;

import com.google.common.collect.ImmutableSortedMap;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.overlap.OverlappingOutputDetector;
import org.gradle.internal.fingerprint.overlap.OverlappingOutputs;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot.FileSystemLocationSnapshotTransformer;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.MissingFileSnapshot;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.RootTrackingFileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotVisitResult;

import javax.annotation.Nullable;
import java.util.Map;

public class DefaultOverlappingOutputDetector implements OverlappingOutputDetector {
    @Override
    @Nullable
    public OverlappingOutputs detect(ImmutableSortedMap<String, FileCollectionFingerprint> previous, ImmutableSortedMap<String, FileSystemSnapshot> current) {
        for (Map.Entry<String, FileSystemSnapshot> entry : current.entrySet()) {
            String propertyName = entry.getKey();
            FileSystemSnapshot beforeExecution = entry.getValue();
            FileCollectionFingerprint afterPreviousExecution = getFingerprintAfterPreviousExecution(previous, propertyName);
            OverlappingOutputs overlappingOutputs = detect(propertyName, afterPreviousExecution, beforeExecution);
            if (overlappingOutputs != null) {
                return overlappingOutputs;
            }
        }
        return null;
    }

    private static FileCollectionFingerprint getFingerprintAfterPreviousExecution(@Nullable ImmutableSortedMap<String, FileCollectionFingerprint> previous, String propertyName) {
        if (previous != null) {
            FileCollectionFingerprint afterPreviousExecution = previous.get(propertyName);
            if (afterPreviousExecution != null) {
                return afterPreviousExecution;
            }
        }
        return FileCollectionFingerprint.EMPTY;
    }

    @Nullable
    private static OverlappingOutputs detect(String propertyName, FileCollectionFingerprint previous, FileSystemSnapshot before) {
        Map<String, FileSystemLocationFingerprint> previousFingerprints = previous.getFingerprints();
        OverlappingOutputsDetectingVisitor outputsDetectingVisitor = new OverlappingOutputsDetectingVisitor(previousFingerprints);
        before.accept(outputsDetectingVisitor);
        String overlappingPath = outputsDetectingVisitor.getOverlappingPath();
        return overlappingPath == null ? null : new OverlappingOutputs(propertyName, overlappingPath);
    }

    private static class OverlappingOutputsDetectingVisitor extends RootTrackingFileSystemSnapshotHierarchyVisitor {
        private final Map<String, FileSystemLocationFingerprint> previousFingerprints;
        private String overlappingPath;

        public OverlappingOutputsDetectingVisitor(Map<String, FileSystemLocationFingerprint> previousFingerprints) {
            this.previousFingerprints = previousFingerprints;
        }

        @Override
        public SnapshotVisitResult visitEntry(CompleteFileSystemLocationSnapshot snapshot, boolean isRoot) {
            boolean newContent = snapshot.accept(new FileSystemLocationSnapshotTransformer<Boolean>() {
                @Override
                public Boolean visitDirectory(CompleteDirectorySnapshot directorySnapshot) {
                    // Check if a new directory appeared. For matching directories don't check content
                    // hash as we should detect individual entries that are different instead)
                    return hasNewContent(directorySnapshot, null);
                }

                @Override
                public Boolean visitRegularFile(RegularFileSnapshot fileSnapshot) {
                    // Check if a new file has appeared, or if an existing file's content has changed
                    return hasNewContent(fileSnapshot, fileSnapshot.getHash());
                }

                @Override
                public Boolean visitMissing(MissingFileSnapshot missingSnapshot) {
                    // If the root has gone missing then we don't have overlaps
                    if (isRoot) {
                        return false;
                    }
                    // Otherwise check for newly added broken symlinks and unreadable files
                    return hasNewContent(missingSnapshot, null);
                }
            });
            if (newContent) {
                overlappingPath = snapshot.getAbsolutePath();
                return SnapshotVisitResult.TERMINATE;
            } else {
                return SnapshotVisitResult.CONTINUE;
            }
        }

        private boolean hasNewContent(CompleteFileSystemLocationSnapshot snapshot, @Nullable HashCode currentContentHash) {
            FileSystemLocationFingerprint previousFingerprint = previousFingerprints.get(snapshot.getAbsolutePath());
            // Created since last execution, possibly by another task
            if (previousFingerprint == null) {
                return true;
            }
            if (snapshot.getType() != previousFingerprint.getType()) {
                return true;
            }
            if (currentContentHash == null) {
                return false;
            }
            // Content changed since last execution
            return !currentContentHash.equals(previousFingerprint.getNormalizedContentHash());
        }

        @Nullable
        public String getOverlappingPath() {
            return overlappingPath;
        }
    }
}
