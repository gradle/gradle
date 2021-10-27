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
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.FingerprintHashingStrategy;
import org.gradle.internal.fingerprint.hashing.FileSystemLocationSnapshotHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.RelativePathTracker;
import org.gradle.internal.snapshot.SnapshotVisitResult;

import javax.annotation.Nullable;
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
    private final FileSystemLocationSnapshotHasher normalizedContentHasher;

    public RelativePathFingerprintingStrategy(Interner<String> stringInterner, DirectorySensitivity directorySensitivity, FileSystemLocationSnapshotHasher normalizedContentHasher) {
        super(IDENTIFIER, directorySensitivity, normalizedContentHasher);
        this.stringInterner = stringInterner;
        this.normalizedContentHasher = normalizedContentHasher;
    }

    public RelativePathFingerprintingStrategy(Interner<String> stringInterner, DirectorySensitivity directorySensitivity) {
        this(stringInterner, directorySensitivity, FileSystemLocationSnapshotHasher.DEFAULT);
    }

    @Override
    public String normalizePath(FileSystemLocationSnapshot snapshot) {
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
        roots.accept(new RelativePathTracker(), (snapshot, relativePath) -> {
            String absolutePath = snapshot.getAbsolutePath();
            if (processedEntries.add(absolutePath) && getDirectorySensitivity().shouldFingerprint(snapshot)) {
                FileSystemLocationFingerprint fingerprint;
                if (relativePath.isRoot()) {
                    if (snapshot.getType() == FileType.Directory) {
                        fingerprint = IgnoredPathFileSystemLocationFingerprint.DIRECTORY;
                    } else {
                        fingerprint = fingerprint(snapshot.getName(), snapshot.getType(), snapshot);
                    }
                } else {
                    fingerprint = fingerprint(stringInterner.intern(relativePath.toRelativePath()), snapshot.getType(), snapshot);
                }

                if (fingerprint != null) {
                    builder.put(absolutePath, fingerprint);
                }
            }
            return SnapshotVisitResult.CONTINUE;
        });
        return builder.build();
    }

    @Nullable
    FileSystemLocationFingerprint fingerprint(String name, FileType type, FileSystemLocationSnapshot snapshot) {
        HashCode normalizedContentHash = getNormalizedContentHash(snapshot, normalizedContentHasher);
        return normalizedContentHash == null ? null : new DefaultFileSystemLocationFingerprint(name, type, normalizedContentHash);
    }

    @Override
    public FingerprintHashingStrategy getHashingStrategy() {
        return FingerprintHashingStrategy.SORT;
    }
}
