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
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.internal.Factory;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class NonePathFileCollectionSnapshotBuilder extends RootFileCollectionSnapshotBuilder {
    @Override
    protected FileCollectionSnapshot build(List<LogicalSnapshot> roots) {
        return new NonePathFileCollectionSnapshot(new IgnoredPathSnapshotFactory(roots));
    }

    private class IgnoredPathSnapshotFactory implements Factory<Map<String, FileContentSnapshot>> {
        private final List<LogicalSnapshot> roots;

        public IgnoredPathSnapshotFactory(List<LogicalSnapshot> roots) {
            this.roots = roots;
        }

        @Nullable
        @Override
        public Map<String, FileContentSnapshot> create() {
            final ImmutableMap.Builder<String, FileContentSnapshot> builder = ImmutableMap.builder();
            final HashSet<String> processedEntries = new HashSet<String>();
            for (LogicalSnapshot root : roots) {
                root.accept(new LogicalSnapshotVisitor() {

                    @Override
                    public void preVisitDirectory(String path, String name) {
                    }

                    @Override
                    public void visit(String path, String name, FileContentSnapshot content) {
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
