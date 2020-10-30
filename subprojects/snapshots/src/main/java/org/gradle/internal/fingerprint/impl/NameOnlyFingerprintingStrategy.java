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
import org.gradle.internal.snapshot.FileSystemSnapshotHierarchyVisitor;

import java.util.HashSet;
import java.util.Map;

/**
 * Fingerprint files normalizing the path to the file name.
 *
 * File names for root directories are ignored.
 */
public class NameOnlyFingerprintingStrategy extends AbstractFingerprintingStrategy {

    public static final NameOnlyFingerprintingStrategy INSTANCE = new NameOnlyFingerprintingStrategy();
    public static final String IDENTIFIER = "NAME_ONLY";

    private NameOnlyFingerprintingStrategy() {
        super(IDENTIFIER);
    }

    @Override
    public String normalizePath(CompleteFileSystemLocationSnapshot snapshot) {
        return snapshot.getName();
    }

    @Override
    public Map<String, FileSystemLocationFingerprint> collectFingerprints(Iterable<? extends FileSystemSnapshot> roots) {
        final ImmutableMap.Builder<String, FileSystemLocationFingerprint> builder = ImmutableMap.builder();
        final HashSet<String> processedEntries = new HashSet<String>();
        for (FileSystemSnapshot root : roots) {
            // TODO Use a SnapshotVisitor
            root.accept(new FileSystemSnapshotHierarchyVisitor() {
                private boolean root = true;

                @Override
                public boolean preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
                    return true;
                }

                @Override
                public void visitEntry(CompleteFileSystemLocationSnapshot snapshot) {
                    String absolutePath = snapshot.getAbsolutePath();
                    if (processedEntries.add(absolutePath)) {
                        FileSystemLocationFingerprint fingerprint;
                        if (root && snapshot.getType() == FileType.Directory) {
                            fingerprint = IgnoredPathFileSystemLocationFingerprint.DIRECTORY;
                            root = false;
                        } else {
                            fingerprint = new DefaultFileSystemLocationFingerprint(snapshot.getName(), snapshot);
                        }
                        builder.put(absolutePath, fingerprint);
                    }
                }

                @Override
                public void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
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
