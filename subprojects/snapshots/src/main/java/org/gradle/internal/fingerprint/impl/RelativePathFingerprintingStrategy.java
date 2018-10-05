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
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.internal.fingerprint.FileSystemLocationFingerprint;
import org.gradle.internal.snapshot.DirectorySnapshot;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.snapshot.RelativePathStringTracker;

import java.util.HashSet;
import java.util.Map;

/**
 * Fingerprint {@link org.gradle.api.file.FileCollection}s normalizing the path to the relative path in a hierarchy.
 *
 * File names for root directories are ignored. For root files, the file name is used as normalized path.
 */
public class RelativePathFingerprintingStrategy extends AbstractFingerprintingStrategy {
    private final StringInterner stringInterner;

    public RelativePathFingerprintingStrategy(StringInterner stringInterner) {
        super("RELATIVE_PATH", NormalizedPathFingerprintCompareStrategy.INSTANCE);
        this.stringInterner = stringInterner;
    }

    @Override
    public Map<String, FileSystemLocationFingerprint> collectFingerprints(Iterable<FileSystemSnapshot> roots) {
        final ImmutableMap.Builder<String, FileSystemLocationFingerprint> builder = ImmutableMap.builder();
        final HashSet<String> processedEntries = new HashSet<String>();
        for (FileSystemSnapshot root : roots) {
            root.accept(new FileSystemSnapshotVisitor() {
                private final RelativePathStringTracker relativePathStringTracker = new RelativePathStringTracker();

                @Override
                public boolean preVisitDirectory(DirectorySnapshot directorySnapshot) {
                    boolean isRoot = relativePathStringTracker.isRoot();
                    relativePathStringTracker.enter(directorySnapshot);
                    String absolutePath = directorySnapshot.getAbsolutePath();
                    if (processedEntries.add(absolutePath)) {
                        FileSystemLocationFingerprint fingerprint = isRoot ? IgnoredPathFileSystemLocationFingerprint.DIRECTORY : new DefaultFileSystemLocationFingerprint(stringInterner.intern(relativePathStringTracker.getRelativePathString()), directorySnapshot);
                        builder.put(absolutePath, fingerprint);
                    }
                    return true;
                }

                @Override
                public void visit(FileSystemLocationSnapshot fileSnapshot) {
                    String absolutePath = fileSnapshot.getAbsolutePath();
                    if (processedEntries.add(absolutePath)) {
                        FileSystemLocationFingerprint fingerprint = relativePathStringTracker.isRoot() ? new DefaultFileSystemLocationFingerprint(fileSnapshot.getName(), fileSnapshot) : createFingerprint(fileSnapshot);
                        builder.put(absolutePath, fingerprint);
                    }
                }

                private FileSystemLocationFingerprint createFingerprint(FileSystemLocationSnapshot snapshot) {
                    relativePathStringTracker.enter(snapshot);
                    FileSystemLocationFingerprint fingerprint = new DefaultFileSystemLocationFingerprint(stringInterner.intern(relativePathStringTracker.getRelativePathString()), snapshot);
                    relativePathStringTracker.leave();
                    return fingerprint;
                }

                @Override
                public void postVisitDirectory(DirectorySnapshot directorySnapshot) {
                    relativePathStringTracker.leave();
                }
            });
        }
        return builder.build();
    }
}
