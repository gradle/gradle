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
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.FingerprintHashingStrategy;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.RootTrackingFileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotVisitResult;

import java.util.HashSet;
import java.util.Map;

/**
 * Fingerprint files normalizing the path to the file name.
 *
 * File names for root directories are ignored.
 */
public class NameOnlyFingerprintingStrategy extends AbstractFingerprintingStrategy {

    public static final NameOnlyFingerprintingStrategy FINGERPRINT_DIRECTORIES = new NameOnlyFingerprintingStrategy(EmptyDirectorySensitivity.FINGERPRINT_EMPTY);
    public static final NameOnlyFingerprintingStrategy IGNORE_DIRECTORIES = new NameOnlyFingerprintingStrategy(EmptyDirectorySensitivity.IGNORE_EMPTY);
    public static final String IDENTIFIER = "NAME_ONLY";
    private final EmptyDirectorySensitivity emptyDirectorySensitivity;

    private NameOnlyFingerprintingStrategy(EmptyDirectorySensitivity emptyDirectorySensitivity) {
        super(IDENTIFIER);
        this.emptyDirectorySensitivity = emptyDirectorySensitivity;
    }

    @Override
    public String normalizePath(CompleteFileSystemLocationSnapshot snapshot) {
        return snapshot.getName();
    }

    private boolean shouldFingerprint(CompleteDirectorySnapshot directorySnapshot) {
        return !(directorySnapshot.getChildren().isEmpty() && emptyDirectorySensitivity == EmptyDirectorySensitivity.IGNORE_EMPTY);
    }

    @Override
    public Map<String, FileSystemLocationFingerprint> collectFingerprints(FileSystemSnapshot roots) {
        ImmutableMap.Builder<String, FileSystemLocationFingerprint> builder = ImmutableMap.builder();
        HashSet<String> processedEntries = new HashSet<>();
        roots.accept(new RootTrackingFileSystemSnapshotHierarchyVisitor() {
            @Override
            public SnapshotVisitResult visitEntry(CompleteFileSystemLocationSnapshot snapshot, boolean isRoot) {
                String absolutePath = snapshot.getAbsolutePath();
                if (processedEntries.add(absolutePath)) {
                    if (snapshot.getType() == FileType.Directory && !shouldFingerprint((CompleteDirectorySnapshot)snapshot)) {
                        return SnapshotVisitResult.CONTINUE;
                    }
                    FileSystemLocationFingerprint fingerprint = isRoot && snapshot.getType() == FileType.Directory
                        ? IgnoredPathFileSystemLocationFingerprint.DIRECTORY
                        : new DefaultFileSystemLocationFingerprint(snapshot.getName(), snapshot);
                    builder.put(absolutePath, fingerprint);
                }
                return SnapshotVisitResult.CONTINUE;
            }
        });
        return builder.build();
    }

    @Override
    public FingerprintHashingStrategy getHashingStrategy() {
        return FingerprintHashingStrategy.SORT;
    }
}
