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
import com.google.common.collect.Lists;
import org.gradle.api.internal.changedetection.state.DirectoryFileSnapshot;
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot;
import org.gradle.api.internal.changedetection.state.FileContentSnapshot;
import org.gradle.api.internal.changedetection.state.MissingFileSnapshot;
import org.gradle.api.internal.changedetection.state.RegularFileSnapshot;
import org.gradle.api.internal.changedetection.state.VisitingFileCollectionSnapshotBuilder;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshotVisitor;
import org.gradle.api.internal.changedetection.state.mirror.RelativePathTracker;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public abstract class RootFileCollectionSnapshotBuilder implements VisitingFileCollectionSnapshotBuilder {

    private final List<LogicalSnapshot> roots = Lists.newArrayList();

    @Override
    public FileCollectionSnapshot build() {
        return build(roots);
    }

    protected abstract FileCollectionSnapshot build(List<LogicalSnapshot> roots);

    @Override
    public void visitFileTreeSnapshot(PhysicalSnapshot tree) {
        final AtomicReference<LogicalSnapshot> result = new AtomicReference<LogicalSnapshot>();
        tree.accept(new PhysicalSnapshotVisitor() {
            private final RelativePathTracker relativePath = new RelativePathTracker();
            private final Deque<List<LogicalSnapshot>> levelHolder = new ArrayDeque<List<LogicalSnapshot>>();
            private final Deque<String> absolutePathHolder = new ArrayDeque<String>();

            @Override
            public boolean preVisitDirectory(String path, String name) {
                relativePath.enter(name);
                absolutePathHolder.addLast(path);
                levelHolder.addLast(new ArrayList<LogicalSnapshot>());
                return true;
            }

            @Override
            public void visit(String path, String name, FileContentSnapshot content) {
                List<LogicalSnapshot> parentBuilder = levelHolder.peekLast();
                relativePath.enter(name);
                FileContentSnapshot newContent = snapshotFileContents(path, relativePath.get(), content);
                relativePath.leave();
                if (newContent != null) {
                    LogicalFileSnapshot snapshot = new LogicalFileSnapshot(path, name, newContent);
                    if (parentBuilder != null) {
                        parentBuilder.add(snapshot);
                    } else {
                        result.set(snapshot);
                    }
                }
            }

            @Override
            public void postVisitDirectory() {
                String directoryPath = relativePath.leave();
                List<LogicalSnapshot> children = levelHolder.removeLast();
                LogicalDirectorySnapshot directorySnapshot = new LogicalDirectorySnapshot(absolutePathHolder.removeLast(), directoryPath, children);
                List<LogicalSnapshot> siblings = levelHolder.peekLast();
                if (siblings != null) {
                    siblings.add(directorySnapshot);
                } else {
                    result.set(directorySnapshot);
                }
            }
        });
        LogicalSnapshot root = result.get();
        if (root != null) {
            roots.add(root);
        }
    }

    @Override
    public void visitDirectorySnapshot(DirectoryFileSnapshot directory) {
        roots.add(new LogicalDirectorySnapshot(directory.getPath(), directory.getName(), ImmutableList.<LogicalSnapshot>of()));
    }

    @Override
    public void visitFileSnapshot(RegularFileSnapshot file) {
        addRoot(file.getPath(), file.getName(), file.getContent());
    }

    protected boolean addRoot(String path, String name, FileContentSnapshot content) {
        return roots.add(new LogicalFileSnapshot(path, name, content));
    }

    @Override
    public void visitMissingFileSnapshot(MissingFileSnapshot missingFile) {
        addRoot(missingFile.getPath(), missingFile.getName(), missingFile.getContent());
    }

    @Nullable
    public FileContentSnapshot snapshotFileContents(String path, Deque<String> relativePath, FileContentSnapshot contentSnapshot) {
        return contentSnapshot;
    }
}
