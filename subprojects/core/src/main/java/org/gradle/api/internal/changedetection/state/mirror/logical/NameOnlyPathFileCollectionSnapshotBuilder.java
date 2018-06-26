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

import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ListMultimap;
import org.gradle.api.internal.changedetection.state.DirContentSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.internal.changedetection.state.IgnoredPathFileSnapshot;
import org.gradle.api.internal.changedetection.state.IndexedNormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot;
import org.gradle.internal.Factory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;

public class NameOnlyPathFileCollectionSnapshotBuilder extends RootFileCollectionSnapshotBuilder {
    @Override
    protected FileCollectionSnapshot build(ListMultimap<String, LogicalSnapshot> roots) {
        return new NormalizedPathFileCollectionSnapshot(new NameOnlySnapshotFactory(roots));
    }

    private static class NameOnlySnapshotFactory implements Factory<Map<String, NormalizedFileSnapshot>> {
        private final ListMultimap<String, LogicalSnapshot> roots;

        public NameOnlySnapshotFactory(ListMultimap<String, LogicalSnapshot> roots) {
            this.roots = roots;
        }

        @Nullable
        @Override
        public Map<String, NormalizedFileSnapshot> create() {
            final ImmutableSortedMap.Builder<String, NormalizedFileSnapshot> builder = ImmutableSortedMap.naturalOrder();
            final HashSet<String> processedEntries = new HashSet<String>();
            for (Map.Entry<String, LogicalSnapshot> entry : roots.entries()) {
                final String basePath = entry.getKey();
                entry.getValue().accept(new HierarchicalSnapshotVisitor() {
                    private Deque<String> absolutePaths = new LinkedList<String>();

                    @Override
                    public void preVisitDirectory(String name) {
                        String absolutePath = getAbsolutePath(name);
                        if (processedEntries.add(absolutePath)) {
                            NormalizedFileSnapshot snapshot = isRoot() ? new IgnoredPathFileSnapshot(DirContentSnapshot.INSTANCE) : new IndexedNormalizedFileSnapshot(absolutePath, absolutePath.length() - name.length(), DirContentSnapshot.INSTANCE);
                            builder.put(absolutePath, snapshot);
                        }
                        absolutePaths.addLast(absolutePath);
                    }

                    @Override
                    public void visit(String name, FileContentSnapshot content) {
                        String absolutePath = getAbsolutePath(name);
                        if (processedEntries.add(absolutePath)) {
                            builder.put(
                                absolutePath,
                                new IndexedNormalizedFileSnapshot(absolutePath, absolutePath.length() - name.length(), content));
                        }
                    }

                    private String getAbsolutePath(String name) {
                        String parent = absolutePaths.peekLast();
                        return parent == null ? basePath : childPath(parent, name);
                    }

                    private boolean isRoot() {
                        return absolutePaths.isEmpty();
                    }

                    @Override
                    public void postVisitDirectory() {
                        absolutePaths.removeLast();
                    }

                    private String childPath(String parent, String name) {
                        return parent + File.separatorChar + name;
                    }
                });
            }
            return builder.build();
        }
    }
}
