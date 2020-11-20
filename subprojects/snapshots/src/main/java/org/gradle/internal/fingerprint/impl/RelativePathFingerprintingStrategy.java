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
import com.google.common.collect.Interner;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.FingerprintHashingStrategy;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemLeafSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.MissingFileSnapshot;
import org.gradle.internal.snapshot.PathTracker;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.gradle.internal.snapshot.SnapshotVisitResult;
import org.gradle.internal.snapshot.UnreadableSnapshot;

import java.util.HashSet;
import java.util.Map;

/**
 * Fingerprint file system snapshots normalizing the path to the relative path in a hierarchy.
 *
 * File names for root directories are ignored. For root files, the file name is used as normalized path.
 */
public class RelativePathFingerprintingStrategy extends AbstractFingerprintingStrategy {
    public static final String IDENTIFIER = "RELATIVE_PATH";

    private final Interner<String> stringInterner;

    public RelativePathFingerprintingStrategy(Interner<String> stringInterner) {
        super(IDENTIFIER);
        this.stringInterner = stringInterner;
    }

    @Override
    public String normalizePath(CompleteFileSystemLocationSnapshot snapshot) {
        if (snapshot.getType() == FileType.Directory) {
            return "";
        } else {
            return snapshot.getName();
        }
    }

    @Override
    public Map<String, FileSystemLocationFingerprint> collectFingerprints(FileSystemSnapshot roots) {
        ImmutableMap.Builder<String, FileSystemLocationFingerprint> builder = ImmutableMap.builder();
        HashSet<String> processedEntries = new HashSet<>();
        roots.accept(new PathTracker(), (snapshot, relativePath) -> {
            String absolutePath = snapshot.getAbsolutePath();
            if (processedEntries.add(absolutePath)) {
                snapshot.accept(new CompleteFileSystemLocationSnapshot.FileSystemLocationSnapshotVisitor() {
                    @Override
                    public void visitDirectory(CompleteDirectorySnapshot directorySnapshot) {
                        if (relativePath.isRoot()) {
                            recordFingerprint(IgnoredPathFileSystemLocationFingerprint.DIRECTORY);
                        } else {
                            visitNonRootEntry(directorySnapshot);
                        }
                    }

                    @Override
                    public void visitRegularFile(RegularFileSnapshot fileSnapshot) {
                        if (relativePath.isRoot()) {
                            visitRootEntry(fileSnapshot);
                        } else {
                            visitNonRootEntry(fileSnapshot);
                        }
                    }

                    @Override
                    public void visitMissing(MissingFileSnapshot missingSnapshot) {
                        if (relativePath.isRoot()) {
                            visitRootEntry(missingSnapshot);
                        } else {
                            visitNonRootEntry(missingSnapshot);
                        }
                    }

                    @Override
                    public void visitUnreadable(UnreadableSnapshot unreadableSnapshot) {
                        throw unreadableSnapshot.rethrowOnAttemptedRead();
                    }

                    private void visitRootEntry(FileSystemLeafSnapshot snapshot) {
                        recordFingerprint(new DefaultFileSystemLocationFingerprint(snapshot.getName(), snapshot));
                    }

                    private void visitNonRootEntry(CompleteFileSystemLocationSnapshot snapshot) {
                        recordFingerprint(new DefaultFileSystemLocationFingerprint(stringInterner.intern(relativePath.toRelativePath()), snapshot));
                    }

                    private void recordFingerprint(FileSystemLocationFingerprint fingerprint) {
                        builder.put(absolutePath, fingerprint);
                    }
                });
            }
            return SnapshotVisitResult.CONTINUE;
        });
        return builder.build();
    }

    @Override
    public FingerprintHashingStrategy getHashingStrategy() {
        return FingerprintHashingStrategy.SORT;
    }
}
