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

import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.fingerprint.FingerprintHashingStrategy;
import org.gradle.internal.fingerprint.FingerprintingStrategy;
import org.gradle.internal.fingerprint.LineEndingNormalization;
import org.gradle.internal.hash.DefaultFileHasher;
import org.gradle.internal.hash.DefaultStreamHasher;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.LineEndingNormalizingInputStream;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.RootTrackingFileSystemSnapshotHierarchyVisitor;
import org.gradle.internal.snapshot.SnapshotVisitResult;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Map;

/**
 * Fingerprint files without path or content normalization.
 */
public class AbsolutePathFingerprintingStrategy extends AbstractFingerprintingStrategy {
    public static final String IDENTIFIER = "ABSOLUTE_PATH";

    private final DirectorySensitivity directorySensitivity;
    private final LineEndingNormalization lineEndingNormalization;
    private final FileHasher lineEndingNormalizedHasher = new DefaultFileHasher(new DefaultStreamHasher(), file -> new LineEndingNormalizingInputStream(new FileInputStream(file)));

    public AbsolutePathFingerprintingStrategy(DirectorySensitivity directorySensitivity, LineEndingNormalization lineEndingNormalization) {
        super(IDENTIFIER);
        this.directorySensitivity = directorySensitivity;
        this.lineEndingNormalization = lineEndingNormalization;
    }

    @Override
    public String normalizePath(FileSystemLocationSnapshot snapshot) {
        return snapshot.getAbsolutePath();
    }

    @Override
    public Map<String, FileSystemLocationFingerprint> collectFingerprints(FileSystemSnapshot roots) {
        ImmutableMap.Builder<String, FileSystemLocationFingerprint> builder = ImmutableMap.builder();
        HashSet<String> processedEntries = new HashSet<>();
        roots.accept(new RootTrackingFileSystemSnapshotHierarchyVisitor() {
            @Override
            public SnapshotVisitResult visitEntry(FileSystemLocationSnapshot snapshot, boolean isRoot) {
                String absolutePath = snapshot.getAbsolutePath();
                if (processedEntries.add(absolutePath) && directorySensitivity.shouldFingerprint(snapshot)) {
                    builder.put(absolutePath, getFingerprint(snapshot));
                }
                return SnapshotVisitResult.CONTINUE;
            }
        });
        return builder.build();
    }

    private DefaultFileSystemLocationFingerprint getFingerprint(FileSystemLocationSnapshot snapshot) {
        File file = new File(snapshot.getAbsolutePath());
        if (lineEndingNormalization.shouldNormalize(file)) {
            return new DefaultFileSystemLocationFingerprint(snapshot.getAbsolutePath(), snapshot.getType(), lineEndingNormalizedHasher.hash(file));
        } else {
            return new DefaultFileSystemLocationFingerprint(snapshot.getAbsolutePath(), snapshot);
        }
    }

    @Override
    public FingerprintHashingStrategy getHashingStrategy() {
        return FingerprintHashingStrategy.SORT;
    }

    @Override
    public DirectorySensitivity getDirectorySensitivity() {
        return directorySensitivity;
    }

    @Override
    public LineEndingNormalization getLineEndingNormalization() {
        return lineEndingNormalization;
    }
}
