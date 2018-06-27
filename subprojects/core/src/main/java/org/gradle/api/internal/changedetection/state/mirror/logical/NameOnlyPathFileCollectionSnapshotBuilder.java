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
import org.gradle.api.internal.changedetection.state.IgnoredPathFileSnapshot;
import org.gradle.api.internal.changedetection.state.IndexedNormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot;
import org.gradle.internal.Factory;

import javax.annotation.Nullable;
import java.util.HashSet;
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
            final ImmutableMap.Builder<String, NormalizedFileSnapshot> builder = ImmutableMap.builder();
            final HashSet<String> processedEntries = new HashSet<String>();
            for (Map.Entry<String, LogicalSnapshot> entry : roots.entries()) {
                entry.getValue().accept(new LogicalSnapshotVisitor() {
                    private boolean root = true;

                    @Override
                    public void preVisitDirectory(String path, String name) {
                        if (processedEntries.add(path)) {
                            NormalizedFileSnapshot snapshot = isRoot() ? new IgnoredPathFileSnapshot(DirContentSnapshot.INSTANCE) : new IndexedNormalizedFileSnapshot(path, path.length() - name.length(), DirContentSnapshot.INSTANCE);
                            builder.put(path, snapshot);
                        }
                        root = false;
                    }

                    @Override
                    public void visit(String path, String name, FileContentSnapshot content) {
                        if (processedEntries.add(path)) {
                            builder.put(
                                path,
                                new IndexedNormalizedFileSnapshot(path, path.length() - name.length(), content));
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
    }
}
