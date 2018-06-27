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
import com.google.common.collect.ListMultimap;
import org.gradle.api.internal.changedetection.state.DirContentSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.internal.Factory;
import org.gradle.internal.file.FileType;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;

public class AbsolutePathFileCollectionSnapshotBuilder extends RootFileCollectionSnapshotBuilder {

    private final boolean includeMissing;

    public AbsolutePathFileCollectionSnapshotBuilder(boolean includeMissing) {
        this.includeMissing = includeMissing;
    }

    @Override
    protected FileCollectionSnapshot build(ListMultimap<String, LogicalSnapshot> roots) {
        return new AbsolutePathFileCollectionSnapshot(new AbsolutePathSnapshotFactory(roots));
    }

    private class AbsolutePathSnapshotFactory implements Factory<Map<String, FileContentSnapshot>> {
        private final ListMultimap<String, LogicalSnapshot> roots;

        public AbsolutePathSnapshotFactory(ListMultimap<String, LogicalSnapshot> roots) {
            this.roots = roots;
        }

        @Nullable
        @Override
        public Map<String, FileContentSnapshot> create() {
            final ImmutableMap.Builder<String, FileContentSnapshot> builder = ImmutableMap.builder();
            final HashSet<String> processedEntries = new HashSet<String>();
            for (Map.Entry<String, LogicalSnapshot> entry : roots.entries()) {
                entry.getValue().accept(new HierarchicalSnapshotVisitor() {

                    @Override
                    public void preVisitDirectory(String path, String name) {
                        if (processedEntries.add(path)) {
                            builder.put(path, DirContentSnapshot.INSTANCE);
                        }
                    }

                    @Override
                    public void visit(String path, String name, FileContentSnapshot content) {
                        if (!includeMissing && content.getType() == FileType.Missing) {
                            return;
                        }
                        if (processedEntries.add(path)) {
                            builder.put(path, content);
                        }
                    }

                    @Override
                    public void postVisitDirectory() {
                    }

                });
            }
            return builder.build();
        }
    }
}
