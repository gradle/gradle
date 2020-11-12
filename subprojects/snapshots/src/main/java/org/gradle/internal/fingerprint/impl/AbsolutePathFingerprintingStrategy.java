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

package org.gradle.internal.fingerprint.impl;

import com.google.common.collect.ImmutableMap;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.FingerprintHashingStrategy;
import org.gradle.internal.fingerprint.FingerprintingStrategy;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot.FileSystemLocationSnapshotVisitor;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.MissingFileSnapshot;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.RootTrackingFileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotVisitResult;

import java.util.HashSet;
import java.util.Map;

/**
 * Fingerprint files without path or content normalization.
 */
public class AbsolutePathFingerprintingStrategy extends AbstractFingerprintingStrategy {
    public static final FingerprintingStrategy INCLUDE_MISSING = new AbsolutePathFingerprintingStrategy(true);
    public static final FingerprintingStrategy IGNORE_MISSING = new AbsolutePathFingerprintingStrategy(false);
    public static final String IDENTIFIER = "ABSOLUTE_PATH";

    private final boolean includeMissingRoots;

    private AbsolutePathFingerprintingStrategy(boolean includeMissingRoots) {
        super(IDENTIFIER);
        this.includeMissingRoots = includeMissingRoots;
    }

    @Override
    public String normalizePath(CompleteFileSystemLocationSnapshot snapshot) {
        return snapshot.getAbsolutePath();
    }

    @Override
    public Map<String, FileSystemLocationFingerprint> collectFingerprints(Iterable<? extends FileSystemSnapshot> roots) {
        ImmutableMap.Builder<String, FileSystemLocationFingerprint> builder = ImmutableMap.builder();
        HashSet<String> processedEntries = new HashSet<>();
        for (FileSystemSnapshot root : roots) {
            root.accept(new RootTrackingFileSystemSnapshotHierarchyVisitor() {
                @Override
                public SnapshotVisitResult visitEntry(CompleteFileSystemLocationSnapshot snapshot, boolean isRoot) {
                    snapshot.accept(new FileSystemLocationSnapshotVisitor() {
                        @Override
                        public void visitDirectory(CompleteDirectorySnapshot directorySnapshot) {
                            doVisitEntry(directorySnapshot);
                        }

                        @Override
                        public void visitRegularFile(RegularFileSnapshot fileSnapshot) {
                            doVisitEntry(fileSnapshot);
                        }

                        @Override
                        public void visitMissing(MissingFileSnapshot missingSnapshot) {
                            if (includeMissingRoots || !isRoot) {
                                doVisitEntry(missingSnapshot);
                            }
                        }
                    });
                    return SnapshotVisitResult.CONTINUE;
                }

                private void doVisitEntry(CompleteFileSystemLocationSnapshot snapshot) {
                    String absolutePath = snapshot.getAbsolutePath();
                    if (processedEntries.add(absolutePath)) {
                        builder.put(absolutePath, new DefaultFileSystemLocationFingerprint(snapshot.getAbsolutePath(), snapshot));
                    }
                }
            });
        }
        return builder.build();
    }

    @Override
    public FingerprintHashingStrategy getHashingStrategy() {
        return FingerprintHashingStrategy.SORT;
    }
}
