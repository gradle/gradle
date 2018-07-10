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
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.changedetection.state.DefaultNormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.DirContentSnapshot;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshotVisitor;
import org.gradle.api.internal.changedetection.state.mirror.RelativePathHolder;

import java.util.HashSet;
import java.util.Map;

public class RelativePathFingerprintingStrategy implements FingerprintingStrategy {
    private final StringInterner stringInterner;

    public RelativePathFingerprintingStrategy(StringInterner stringInterner) {
        this.stringInterner = stringInterner;
    }

    @Override
    public Map<String, NormalizedFileSnapshot> collectSnapshots(Iterable<PhysicalSnapshot> roots) {
        final ImmutableMap.Builder<String, NormalizedFileSnapshot> builder = ImmutableMap.builder();
        final HashSet<String> processedEntries = new HashSet<String>();
        for (PhysicalSnapshot root : roots) {
            root.accept(new PhysicalSnapshotVisitor() {
                private final RelativePathHolder relativePathHolder = new RelativePathHolder();

                @Override
                public boolean preVisitDirectory(PhysicalSnapshot directorySnapshot) {
                    boolean isRoot = relativePathHolder.isRoot();
                    relativePathHolder.enter(directorySnapshot);
                    if (processedEntries.add(directorySnapshot.getAbsolutePath())) {
                        NormalizedFileSnapshot snapshot = isRoot ? DirContentSnapshot.INSTANCE : new DefaultNormalizedFileSnapshot(stringInterner.intern(relativePathHolder.getRelativePathString()), directorySnapshot);
                        builder.put(directorySnapshot.getAbsolutePath(), snapshot);
                    }
                    return true;
                }

                @Override
                public void visit(PhysicalSnapshot fileSnapshot) {
                    if (processedEntries.add(fileSnapshot.getAbsolutePath())) {
                        NormalizedFileSnapshot normalizedFileSnapshot = relativePathHolder.isRoot() ? new DefaultNormalizedFileSnapshot(fileSnapshot.getName(), fileSnapshot) : createNormalizedFileSnapshot(fileSnapshot);
                        builder.put(
                            fileSnapshot.getAbsolutePath(),
                            normalizedFileSnapshot
                        );
                    }
                }

                private NormalizedFileSnapshot createNormalizedFileSnapshot(PhysicalSnapshot fileSnapshot) {
                    relativePathHolder.enter(fileSnapshot.getName());
                    NormalizedFileSnapshot normalizedFileSnapshot = new DefaultNormalizedFileSnapshot(stringInterner.intern(relativePathHolder.getRelativePathString()), fileSnapshot);
                    relativePathHolder.leave();
                    return normalizedFileSnapshot;
                }

                @Override
                public void postVisitDirectory() {
                    relativePathHolder.leave();
                }
            });
        }
        return builder.build();
    }

    @Override
    public FingerprintCompareStrategy getCompareStrategy() {
        return FingerprintCompareStrategy.NORMALIZED;
    }

}
