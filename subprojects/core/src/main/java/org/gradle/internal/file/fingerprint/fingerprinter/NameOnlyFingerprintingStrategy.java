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

package org.gradle.internal.file.fingerprint.fingerprinter;

import com.google.common.collect.ImmutableMap;
import org.gradle.internal.file.content.DirContentSnapshot;
import org.gradle.internal.file.content.FileContentSnapshot;
import org.gradle.internal.file.content.NormalizedFileSnapshot;
import org.gradle.internal.file.fingerprint.internal.DefaultNormalizedFileSnapshot;
import org.gradle.internal.file.physical.PhysicalSnapshot;
import org.gradle.internal.file.physical.PhysicalSnapshotVisitor;

import java.util.HashSet;
import java.util.Map;

public class NameOnlyFingerprintingStrategy implements FingerprintingStrategy {

    @Override
    public Map<String, NormalizedFileSnapshot> collectSnapshots(Iterable<PhysicalSnapshot> roots) {
        final ImmutableMap.Builder<String, NormalizedFileSnapshot> builder = ImmutableMap.builder();
        final HashSet<String> processedEntries = new HashSet<String>();
        for (PhysicalSnapshot root : roots) {
            root.accept(new PhysicalSnapshotVisitor() {
                private boolean root = true;

                @Override
                public boolean preVisitDirectory(String absolutePath, String name) {
                    if (processedEntries.add(absolutePath)) {
                        NormalizedFileSnapshot snapshot = isRoot() ? DirContentSnapshot.INSTANCE : new DefaultNormalizedFileSnapshot(name, DirContentSnapshot.INSTANCE);
                        builder.put(absolutePath, snapshot);
                    }
                    root = false;
                    return true;
                }

                @Override
                public void visit(String absolutePath, String name, FileContentSnapshot content) {
                    if (processedEntries.add(absolutePath)) {
                        builder.put(
                            absolutePath,
                            new DefaultNormalizedFileSnapshot(name, content));
                    }
                }

                private boolean isRoot() {
                    return root;
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
        return FingerprintCompareStrategy.NORMALIZED;
    }
}
