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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import org.gradle.api.internal.changedetection.state.DirectoryFileSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.internal.changedetection.state.MissingFileSnapshot;
import org.gradle.api.internal.changedetection.state.RegularFileSnapshot;
import org.gradle.api.internal.changedetection.state.VisitingFileCollectionSnapshotBuilder;
import org.gradle.api.internal.changedetection.state.mirror.HierarchicalFileTreeVisitor;
import org.gradle.api.internal.changedetection.state.mirror.HierarchicalVisitableTree;
import org.gradle.api.internal.changedetection.state.mirror.VisitableDirectoryTree;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicReference;

public abstract class RootFileCollectionSnapshotBuilder implements VisitingFileCollectionSnapshotBuilder {

    private final ListMultimap<String, LogicalSnapshot> roots = LinkedListMultimap.create();

    @Override
    public FileCollectionSnapshot build() {
        return build(roots);
    }

    protected abstract FileCollectionSnapshot build(ListMultimap<String, LogicalSnapshot> roots);

    @Override
    public void visitFileTreeSnapshot(VisitableDirectoryTree tree) {
        visitHierarchicalTree(tree);
    }

    public void visitHierarchicalTree(HierarchicalVisitableTree tree) {
        final Deque<String> relativePathHolder = new ArrayDeque<String>();
        final Deque<ImmutableList.Builder<LogicalSnapshot>> levelHolder = new ArrayDeque<ImmutableList.Builder<LogicalSnapshot>>();
        final AtomicReference<LogicalSnapshot> result = new AtomicReference<LogicalSnapshot>();
        final AtomicReference<String> rootPath = new AtomicReference<String>();
        tree.accept(new HierarchicalFileTreeVisitor() {
            @Override
            public void preVisitDirectory(Path path, String name) {
                if (relativePathHolder.isEmpty()) {
                    rootPath.set(path.toString());
                }
                levelHolder.addLast(ImmutableList.<LogicalSnapshot>builder());
                relativePathHolder.addLast(name);
            }

            @Override
            public void visit(Path path, String name, FileContentSnapshot content) {
                ImmutableList.Builder<LogicalSnapshot> parentBuilder = levelHolder.peekLast();
                LogicalFileSnapshot snapshot = new LogicalFileSnapshot(name, content);
                if (parentBuilder != null) {
                    parentBuilder.add(snapshot);
                } else {
                    result.set(snapshot);
                    rootPath.set(path.toString());
                }
            }

            @Override
            public void postVisitDirectory() {
                String directoryPath = relativePathHolder.removeLast();
                ImmutableList.Builder<LogicalSnapshot> builder = levelHolder.removeLast();
                LogicalDirectorySnapshot directorySnapshot = new LogicalDirectorySnapshot(directoryPath, builder.build());
                ImmutableList.Builder<LogicalSnapshot> parentBuilder = levelHolder.peekLast();
                if (parentBuilder != null) {
                    parentBuilder.add(directorySnapshot);
                } else {
                    result.set(directorySnapshot);
                }
            }
        });
        LogicalSnapshot root = result.get();
        if (root != null) {
            roots.put(rootPath.get(), root);
        }
    }

    @Override
    public void visitDirectorySnapshot(DirectoryFileSnapshot directory) {
        roots.put(directory.getPath(), new LogicalDirectorySnapshot(directory.getName(), ImmutableList.<LogicalSnapshot>of()));
    }

    @Override
    public void visitFileSnapshot(RegularFileSnapshot file) {
        roots.put(file.getPath(), new LogicalFileSnapshot(file.getName(), file.getContent()));
    }

    @Override
    public void visitMissingFileSnapshot(MissingFileSnapshot missingFile) {
        roots.put(missingFile.getPath(), new LogicalFileSnapshot(missingFile.getName(), missingFile.getContent()));
    }
}
