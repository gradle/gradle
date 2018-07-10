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

package org.gradle.api.internal.changedetection.state.mirror.logical;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.internal.changedetection.state.DirContentSnapshot;
import org.gradle.api.internal.changedetection.state.NonNormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshotVisitor;
import org.gradle.internal.file.FileType;

import java.util.HashSet;
import java.util.Map;

public class AbsolutePathFingerprintingStrategy implements FingerprintingStrategy {

    private final boolean includeMissing;

    public AbsolutePathFingerprintingStrategy(boolean includeMissing) {
        this.includeMissing = includeMissing;
    }

    @Override
    public Map<String, NormalizedFileSnapshot> collectSnapshots(Iterable<PhysicalSnapshot> roots) {
        final ImmutableMap.Builder<String, NormalizedFileSnapshot> builder = ImmutableMap.builder();
        final HashSet<String> processedEntries = new HashSet<String>();
        for (PhysicalSnapshot root : roots) {
            root.accept(new PhysicalSnapshotVisitor() {

                @Override
                public boolean preVisitDirectory(PhysicalSnapshot directorySnapshot) {
                    if (processedEntries.add(directorySnapshot.getAbsolutePath())) {
                        builder.put(directorySnapshot.getAbsolutePath(), new NonNormalizedFileSnapshot(directorySnapshot.getAbsolutePath(), DirContentSnapshot.INSTANCE));
                    }
                    return true;
                }

                @Override
                public void visit(PhysicalSnapshot fileSnapshot) {
                    if (!includeMissing && fileSnapshot.getType() == FileType.Missing) {
                        return;
                    }
                    if (processedEntries.add(fileSnapshot.getAbsolutePath())) {
                        builder.put(fileSnapshot.getAbsolutePath(), new NonNormalizedFileSnapshot(fileSnapshot.getAbsolutePath(), fileSnapshot.getContent()));
                    }
                }

                @Override
                public void postVisitDirectory() {
                }

            });
        }
        return builder.build();
    }

    @Override
    public FingerprintCompareStrategy getCompareStrategy() {
        return FingerprintCompareStrategy.ABSOLUTE;
    }

}
