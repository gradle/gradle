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
import org.gradle.internal.snapshot.AbstractFileSystemNode;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemNode;
import org.gradle.internal.snapshot.SnapshotFileSystemNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.gradle.internal.snapshot.AbstractFileSystemNode.invalidateSingleChild;
import static org.gradle.internal.snapshot.AbstractFileSystemNode.updateSingleChild;

public class DefaultFileHierarchySet implements FileHierarchySet {
    private final FileSystemNode rootNode;

    DefaultFileHierarchySet(String path, FileSystemLocationSnapshot snapshot) {
        this.rootNode = new SnapshotFileSystemNode(normalizeFileSystemRoot(path), snapshot);
    }

    DefaultFileHierarchySet(FileSystemNode rootNode) {
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
        if (!AbstractFileSystemNode.isChildOfOrThis(path, 0, rootNode.getPrefix())) {
            return Optional.empty();
        }
        return rootNode.getSnapshot(normalizeFileSystemRoot(path), rootNode.getPrefix().length() + 1);
    }

    @Override
    public FileHierarchySet update(FileSystemLocationSnapshot snapshot) {
        String normalizedPath = normalizeFileSystemRoot(snapshot.getAbsolutePath());
        return new DefaultFileHierarchySet(updateSingleChild(rootNode, normalizedPath, snapshot));
    }

    @Override
    public FileHierarchySet invalidate(String path) {
        String normalizedPath = normalizeFileSystemRoot(path);
        return invalidateSingleChild(rootNode, normalizedPath)
            .<FileHierarchySet>map(DefaultFileHierarchySet::new)
            .orElse(FileHierarchySet.EMPTY);
    }

    private String normalizeFileSystemRoot(String absolutePath) {
        return absolutePath.equals("/") ? "" : absolutePath;
    }
}
