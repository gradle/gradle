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

import org.gradle.api.internal.changedetection.state.MissingFileContentSnapshot;
import org.gradle.internal.file.FileType;

import java.nio.file.Path;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("Since15")
public class PhysicalMissingFileSnapshot implements PhysicalSnapshot {
    private final ConcurrentMap<String, PhysicalSnapshot> children = new ConcurrentHashMap<String, PhysicalSnapshot>();
    private final String name;
    private final Path path;

    public PhysicalMissingFileSnapshot(Path path, String name) {
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
    public PhysicalSnapshot add(String[] segments, int offset, PhysicalSnapshot snapshot) {
        if (segments.length == offset) {
            return this;
        }
        if (!(snapshot instanceof PhysicalMissingFileSnapshot)) {
            throw new UnsupportedOperationException("Cannot add children of missing file");
        }
        String currentSegment = segments[offset];
        PhysicalSnapshot child = children.get(currentSegment);
        if (child == null) {
            PhysicalSnapshot newChild;
            if (segments.length == offset + 1) {
                newChild = snapshot;
            } else {
                newChild = new PhysicalMissingFileSnapshot(path.resolve(currentSegment), currentSegment);
            }
            child = children.putIfAbsent(currentSegment, newChild);
            if (child == null) {
                child = newChild;
            }
        }
        return child.add(segments, offset + 1, snapshot);
    }

    @Override
    public FileType getType() {
        return FileType.Missing;
    }

    @Override
    public void visitTree(PhysicalFileVisitor visitor, Deque<String> relativePath) {
        throw new UnsupportedOperationException("Cannot visit missing file");
    }

    @Override
    public void visitSelf(PhysicalFileVisitor visitor, Deque<String> relativePath) {
        visitor.visit(path, name, relativePath, MissingFileContentSnapshot.INSTANCE);
    }

    @Override
    public void accept(HierarchicalFileTreeVisitor visitor) {
        visitor.visit(getPath(), getName(), MissingFileContentSnapshot.INSTANCE);
    }
}
