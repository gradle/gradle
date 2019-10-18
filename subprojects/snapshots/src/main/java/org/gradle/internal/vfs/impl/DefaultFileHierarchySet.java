/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.vfs.impl;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DefaultFileHierarchySet implements FileHierarchySet {
    private final Node rootNode;

    DefaultFileHierarchySet(String path, FileSystemLocationSnapshot snapshot) {
        this.rootNode = new SnapshotNode(normalizeFileSystemRoot(path), snapshot);
    }

    DefaultFileHierarchySet(Node rootNode) {
        this.rootNode = rootNode;
    }

    @VisibleForTesting
    List<String> flatten() {
        List<String> prefixes = new ArrayList<>();
        rootNode.collect(0, prefixes);
        return prefixes;
    }

    @Override
    public Optional<FileSystemLocationSnapshot> getSnapshot(String path) {
        if (!AbstractNode.isChildOfOrThis(path, 0, rootNode.getPrefix())) {
            return Optional.empty();
        }
        return rootNode.getSnapshot(normalizeFileSystemRoot(path), 0);
    }

    @Override
    public FileHierarchySet update(FileSystemLocationSnapshot snapshot) {
        return new DefaultFileHierarchySet(rootNode.update(normalizeFileSystemRoot(snapshot.getAbsolutePath()), snapshot));
    }

    @Override
    public FileHierarchySet invalidate(String path) {
        return rootNode.invalidate(normalizeFileSystemRoot(path))
            .<FileHierarchySet>map(DefaultFileHierarchySet::new)
            .orElse(FileHierarchySet.EMPTY);
    }

    private String normalizeFileSystemRoot(String absolutePath) {
        return absolutePath.equals("/") ? "" : absolutePath;
    }
}
