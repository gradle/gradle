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

import javax.annotation.Nullable;
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
    public void visitFileTreeSnapshot(HierarchicalVisitableTree tree) {
        final Deque<String> relativePathHolder = new ArrayDeque<String>();
        final Deque<ImmutableList.Builder<LogicalSnapshot>> levelHolder = new ArrayDeque<ImmutableList.Builder<LogicalSnapshot>>();
        final AtomicReference<LogicalSnapshot> result = new AtomicReference<LogicalSnapshot>();
        final AtomicReference<String> rootPath = new AtomicReference<String>();
        final AtomicReference<String> rootName = new AtomicReference<String>();
        tree.accept(new HierarchicalFileTreeVisitor() {
            @Override
            public boolean preVisitDirectory(Path path, String name) {
                if (levelHolder.isEmpty()) {
                    rootPath.set(path.toString());
                    rootName.set(path.getFileName().toString());
                } else {
                    relativePathHolder.addLast(name);
                }
                levelHolder.addLast(ImmutableList.<LogicalSnapshot>builder());
                return true;
            }

            @Override
            public void visit(Path path, String name, FileContentSnapshot content) {
                ImmutableList.Builder<LogicalSnapshot> parentBuilder = levelHolder.peekLast();
                relativePathHolder.addLast(name);
                FileContentSnapshot newContent = snapshotFileContents(path, relativePathHolder, content);
                relativePathHolder.removeLast();
                if (newContent != null) {
                    LogicalFileSnapshot snapshot = new LogicalFileSnapshot(name, newContent);
                    if (parentBuilder != null) {
                        parentBuilder.add(snapshot);
                    } else {
                        result.set(snapshot);
                        rootPath.set(path.toString());
                    }
                }
            }

            @Override
            public void postVisitDirectory() {
                ImmutableList.Builder<LogicalSnapshot> builder = levelHolder.removeLast();
                String directoryPath = relativePathHolder.isEmpty() ? rootName.get() : relativePathHolder.removeLast();
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
        addRoot(file.getPath(), file.getName(), file.getContent());
    }

    protected boolean addRoot(String path, String name, FileContentSnapshot content) {
        return roots.put(path, new LogicalFileSnapshot(name, content));
    }

    @Override
    public void visitMissingFileSnapshot(MissingFileSnapshot missingFile) {
        addRoot(missingFile.getPath(), missingFile.getName(), missingFile.getContent());
    }

    @Nullable
    public FileContentSnapshot snapshotFileContents(Path path, Deque<String> relativePath, FileContentSnapshot contentSnapshot) {
        return contentSnapshot;
    }
}
