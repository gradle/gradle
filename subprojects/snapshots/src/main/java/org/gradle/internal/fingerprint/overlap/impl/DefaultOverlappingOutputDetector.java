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
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.FileCollectionFingerprint;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.overlap.OverlappingOutputDetector;
import org.gradle.internal.fingerprint.overlap.OverlappingOutputs;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;

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

    private static boolean changedSincePreviousExecution(HashCode contentHash, HashCode previousContentHash) {
        // _changed_ since last execution, possibly by another task
        return !contentHash.equals(previousContentHash);
    }

    private static boolean createdSincePreviousExecution(@Nullable HashCode previousContentHash) {
        // created since last execution, possibly by another task
        return previousContentHash == null;
    }

    private static class OverlappingOutputsDetectingVisitor implements FileSystemSnapshotVisitor {
        private final Map<String, FileSystemLocationFingerprint> previousFingerprints;
        private int treeDepth = 0;
        private String overlappingPath;

        public OverlappingOutputsDetectingVisitor(Map<String, FileSystemLocationFingerprint> previousFingerprints) {
            this.previousFingerprints = previousFingerprints;
        }

        @Override
        public boolean preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
            treeDepth++;
            if (overlappingPath == null) {
                overlappingPath = detectOverlappingPath(directorySnapshot);
            }
            return overlappingPath == null;
        }

        @Override
        public void visitFile(CompleteFileSystemLocationSnapshot fileSnapshot) {
            if (overlappingPath == null) {
                overlappingPath = detectOverlappingPath(fileSnapshot);
            }
        }

        @Override
        public void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
            treeDepth--;
        }

        @Nullable
        private String detectOverlappingPath(CompleteFileSystemLocationSnapshot beforeSnapshot) {
            String path = beforeSnapshot.getAbsolutePath();
            HashCode contentHash = beforeSnapshot.getHash();
            FileSystemLocationFingerprint previousFingerprint = previousFingerprints.get(path);
            HashCode previousContentHash = previousFingerprint == null ? null : previousFingerprint.getNormalizedContentHash();
            // Missing files can be ignored
            if (!isRoot() || beforeSnapshot.getType() != FileType.Missing) {
                if (createdSincePreviousExecution(previousContentHash)
                    || (beforeSnapshot.getType() != previousFingerprint.getType())
                    // The fingerprint hashes for non-regular files are slightly different to the snapshot hashes, we only need to compare them for regular files
                    || (beforeSnapshot.getType() == FileType.RegularFile && changedSincePreviousExecution(contentHash, previousContentHash))) {
                    return path;
                }
            }
            return null;
        }

        private boolean isRoot() {
            return treeDepth == 0;
        }

        @Nullable
        public String getOverlappingPath() {
            return overlappingPath;
        }
    }
}
