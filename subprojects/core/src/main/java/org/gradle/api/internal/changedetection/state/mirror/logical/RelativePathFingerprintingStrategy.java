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
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
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
                public boolean preVisitDirectory(String absolutePath, String name) {
                    boolean isRoot = relativePathHolder.isRoot();
                    relativePathHolder.enter(name);
                    if (processedEntries.add(absolutePath)) {
                        NormalizedFileSnapshot snapshot = isRoot ? DirContentSnapshot.INSTANCE : new DefaultNormalizedFileSnapshot(stringInterner.intern(relativePathHolder.getRelativePathString()), DirContentSnapshot.INSTANCE);
                        builder.put(absolutePath, snapshot);
                    }
                    return true;
                }

                @Override
                public void visit(String absolutePath, String name, FileContentSnapshot content) {
                    if (processedEntries.add(absolutePath)) {
                        NormalizedFileSnapshot normalizedFileSnapshot = relativePathHolder.isRoot() ? new DefaultNormalizedFileSnapshot(name, content) : createNormalizedFileSnapshot(name, content);
                        builder.put(
                            absolutePath,
                            normalizedFileSnapshot
                        );
                    }
                }

                private NormalizedFileSnapshot createNormalizedFileSnapshot(String name, FileContentSnapshot content) {
                    relativePathHolder.enter(name);
                    NormalizedFileSnapshot normalizedFileSnapshot = new DefaultNormalizedFileSnapshot(stringInterner.intern(relativePathHolder.getRelativePathString()), content);
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
