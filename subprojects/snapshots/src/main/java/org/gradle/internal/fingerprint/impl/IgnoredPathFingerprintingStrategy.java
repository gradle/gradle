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
import org.gradle.internal.fingerprint.hashing.FileSystemLocationSnapshotHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;

import java.util.Map;

/**
 * Fingerprint files ignoring the path.
 *
 * Ignores directories.
 */
public class IgnoredPathFingerprintingStrategy extends AbstractFingerprintingStrategy {
    public static final IgnoredPathFingerprintingStrategy DEFAULT = new IgnoredPathFingerprintingStrategy();
    public static final String IDENTIFIER = "IGNORED_PATH";
    public static final String IGNORED_PATH = "";

    private final FileSystemLocationSnapshotHasher normalizedContentHasher;

    public IgnoredPathFingerprintingStrategy(FileSystemLocationSnapshotHasher normalizedContentHasher) {
        super(IDENTIFIER, normalizedContentHasher);
        this.normalizedContentHasher = normalizedContentHasher;
    }

    private IgnoredPathFingerprintingStrategy() {
        this(FileSystemLocationSnapshotHasher.DEFAULT);
    }

    @Override
    public Map<String, FileSystemLocationFingerprint> collectFingerprints(FileSystemSnapshot roots) {
        ImmutableMap.Builder<String, FileSystemLocationFingerprint> builder = ImmutableMap.builder();
        roots.accept(new MissingRootAndDuplicateIgnoringFileSystemSnapshotVisitor() {
            @Override
            public void visitAcceptedEntry(FileSystemLocationSnapshot snapshot, boolean isRoot) {
                if (snapshot.getType() != FileType.Directory) {
                    HashCode normalizedContentHash = getNormalizedContentHash(snapshot, normalizedContentHasher);
                    if (normalizedContentHash != null) {
                        builder.put(snapshot.getAbsolutePath(), IgnoredPathFileSystemLocationFingerprint.create(snapshot.getType(), normalizedContentHash));
                    }
                }
            }
        });
        return builder.build();
    }

    @Override
    public FingerprintHashingStrategy getHashingStrategy() {
        return FingerprintHashingStrategy.SORT;
    }
}
