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
import org.gradle.api.internal.changedetection.state.DefaultNormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.FileSystemSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalDirectorySnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshotVisitor;
import org.gradle.api.internal.changedetection.state.mirror.RelativePathStringTracker;
import org.gradle.internal.fingerprint.FingerprintingStrategy;
import org.gradle.internal.fingerprint.NormalizedFileSnapshot;

import java.util.HashSet;
import java.util.Map;

/**
 * Fingerprint {@link org.gradle.api.file.FileCollection}s normalizing the path to the relative path in a hierarchy.
 *
 * File names for root directories are ignored. For root files, the file name is used as normalized path.
 */
public class RelativePathFingerprintingStrategy implements FingerprintingStrategy {
    private final StringInterner stringInterner;

    public RelativePathFingerprintingStrategy(StringInterner stringInterner) {
        this.stringInterner = stringInterner;
    }

    @Override
    public Map<String, NormalizedFileSnapshot> collectSnapshots(Iterable<FileSystemSnapshot> roots) {
        final ImmutableMap.Builder<String, NormalizedFileSnapshot> builder = ImmutableMap.builder();
        final HashSet<String> processedEntries = new HashSet<String>();
        for (FileSystemSnapshot root : roots) {
            root.accept(new PhysicalSnapshotVisitor() {
                private final RelativePathStringTracker relativePathStringTracker = new RelativePathStringTracker();

                @Override
                public boolean preVisitDirectory(PhysicalDirectorySnapshot directorySnapshot) {
                    boolean isRoot = relativePathStringTracker.isRoot();
                    relativePathStringTracker.enter(directorySnapshot);
                    String absolutePath = directorySnapshot.getAbsolutePath();
                    if (processedEntries.add(absolutePath)) {
                        NormalizedFileSnapshot snapshot = isRoot ? IgnoredPathFingerprint.DIRECTORY : new DefaultNormalizedFileSnapshot(stringInterner.intern(relativePathStringTracker.getRelativePathString()), directorySnapshot);
                        builder.put(absolutePath, snapshot);
                    }
                    return true;
                }

                @Override
                public void visit(PhysicalSnapshot fileSnapshot) {
                    String absolutePath = fileSnapshot.getAbsolutePath();
                    if (processedEntries.add(absolutePath)) {
                        NormalizedFileSnapshot normalizedFileSnapshot = relativePathStringTracker.isRoot() ? new DefaultNormalizedFileSnapshot(fileSnapshot.getName(), fileSnapshot) : createNormalizedFileSnapshot(fileSnapshot);
                        builder.put(absolutePath, normalizedFileSnapshot);
                    }
                }

                private NormalizedFileSnapshot createNormalizedFileSnapshot(PhysicalSnapshot fileSnapshot) {
                    relativePathStringTracker.enter(fileSnapshot);
                    NormalizedFileSnapshot normalizedFileSnapshot = new DefaultNormalizedFileSnapshot(stringInterner.intern(relativePathStringTracker.getRelativePathString()), fileSnapshot);
                    relativePathStringTracker.leave();
                    return normalizedFileSnapshot;
                }

                @Override
                public void postVisitDirectory(PhysicalDirectorySnapshot directorySnapshot) {
                    relativePathStringTracker.leave();
                }
            });
        }
        return builder.build();
    }

    @Override
    public FingerprintCompareStrategy getCompareStrategy() {
        return FingerprintCompareStrategy.NORMALIZED;
    }

    @Override
    public Identifier getIdentifier() {
        return Identifier.RELATIVE_PATH;
    }
}
