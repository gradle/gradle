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
import org.gradle.api.internal.changedetection.state.DefaultNormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.FileSystemSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalDirectorySnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshotVisitor;
import org.gradle.internal.file.FileType;
import org.gradle.internal.fingerprint.FingerprintingStrategy;
import org.gradle.internal.fingerprint.NormalizedFileSnapshot;

import java.util.HashSet;
import java.util.Map;

/**
 * Fingerprint files without path or content normalization.
 */
public enum AbsolutePathFingerprintingStrategy implements FingerprintingStrategy {
    INCLUDE_MISSING(true),
    IGNORE_MISSING(false);

    private final boolean includeMissing;

    AbsolutePathFingerprintingStrategy(boolean includeMissing) {
        this.includeMissing = includeMissing;
    }

    @Override
    public Map<String, NormalizedFileSnapshot> collectSnapshots(Iterable<FileSystemSnapshot> roots) {
        final ImmutableMap.Builder<String, NormalizedFileSnapshot> builder = ImmutableMap.builder();
        final HashSet<String> processedEntries = new HashSet<String>();
        for (FileSystemSnapshot root : roots) {
            root.accept(new PhysicalSnapshotVisitor() {

                @Override
                public boolean preVisitDirectory(PhysicalDirectorySnapshot directorySnapshot) {
                    String absolutePath = directorySnapshot.getAbsolutePath();
                    if (processedEntries.add(absolutePath)) {
                        builder.put(absolutePath, new DefaultNormalizedFileSnapshot(directorySnapshot.getAbsolutePath(), directorySnapshot));
                    }
                    return true;
                }

                @Override
                public void visit(PhysicalSnapshot fileSnapshot) {
                    if (!includeMissing && fileSnapshot.getType() == FileType.Missing) {
                        return;
                    }
                    String absolutePath = fileSnapshot.getAbsolutePath();
                    if (processedEntries.add(absolutePath)) {
                        builder.put(absolutePath, new DefaultNormalizedFileSnapshot(fileSnapshot.getAbsolutePath(), fileSnapshot));
                    }
                }

                @Override
                public void postVisitDirectory(PhysicalDirectorySnapshot directorySnapshot) {
                }

            });
        }
        return builder.build();
    }

    @Override
    public FingerprintCompareStrategy getCompareStrategy() {
        return FingerprintCompareStrategy.ABSOLUTE;
    }

    @Override
    public Identifier getIdentifier() {
        return Identifier.ABSOLUTE_PATH;
    }

}
