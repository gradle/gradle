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

package org.gradle.api.internal.changedetection.state.mirror;

import org.gradle.api.internal.changedetection.state.DirContentSnapshot;
import org.gradle.internal.file.FileType;

import java.nio.file.Path;
import java.util.Deque;

public abstract class AbstractPhysicalDirectorySnapshot implements PhysicalSnapshot {
    private final String name;
    private final Path path;

    public AbstractPhysicalDirectorySnapshot(Path path, String name) {
        this.path = path;
        this.name = name;
    }

    @Override
    public Path getPath() {
        return path;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public FileType getType() {
        return FileType.Directory;
    }

    @Override
    public void visitTree(PhysicalFileVisitor visitor, Deque<String> relativePath) {
        for (PhysicalSnapshot snapshot : getChildren()) {
            relativePath.addLast(snapshot.getName());
            snapshot.visitSelf(visitor, relativePath);
            snapshot.visitTree(visitor, relativePath);
            relativePath.removeLast();
        }
    }

    protected abstract Iterable<PhysicalSnapshot> getChildren();

    @Override
    public void visitSelf(PhysicalFileVisitor visitor, Deque<String> relativePath) {
        visitor.visit(getPath(), getName(), relativePath, DirContentSnapshot.INSTANCE);
    }

    @Override
    public void accept(HierarchicalFileTreeVisitor visitor) {
        visitor.preVisitDirectory(path, name);
        for (PhysicalSnapshot child : getChildren()) {
            child.accept(visitor);
        }
        visitor.postVisitDirectory();
    }
}
