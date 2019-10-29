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
import org.gradle.internal.snapshot.FileSystemNode;
import org.gradle.internal.snapshot.MetadataSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.gradle.internal.snapshot.AbstractFileSystemNode.invalidateSingleChild;
import static org.gradle.internal.snapshot.AbstractFileSystemNode.isFileSeparator;
import static org.gradle.internal.snapshot.AbstractFileSystemNode.updateSingleChild;

public class DefaultFileHierarchySet implements FileHierarchySet {
    private final FileSystemNode rootNode;

    public static FileHierarchySet from(String absolutePath, MetadataSnapshot snapshot) {
        return new DefaultFileHierarchySet(snapshot.withPathToParent(normalizeRoot(absolutePath)));
    }

    private DefaultFileHierarchySet(FileSystemNode rootNode) {
        this.rootNode = rootNode;
    }

    @VisibleForTesting
    List<String> flatten() {
        List<String> prefixes = new ArrayList<>();
        rootNode.collect(0, prefixes);
        return prefixes;
    }

    @Override
    public Optional<MetadataSnapshot> getMetadata(String absolutePath) {
        String normalizedPath = normalizeRoot(absolutePath);
        String pathToParent = rootNode.getPathToParent();
        if (!AbstractFileSystemNode.isChildOfOrThis(normalizedPath, 0, pathToParent)) {
            return Optional.empty();
        }
        return rootNode.getSnapshot(normalizedPath, pathToParent.length() + rootNodeOffset(pathToParent, normalizedPath));
    }

    private static int rootNodeOffset(String prefix, String normalizedPath) {
        if (prefix.isEmpty() && normalizedPath.length() > 0 && !isFileSeparator(normalizedPath.charAt(0))) {
            return 0;
        }
        return 1;
    }

    @Override
    public FileHierarchySet update(String absolutePath, MetadataSnapshot snapshot) {
        String normalizedPath = normalizeRoot(absolutePath);
        return new DefaultFileHierarchySet(updateSingleChild(rootNode, normalizedPath, 0, snapshot));
    }

    @Override
    public FileHierarchySet invalidate(String absolutePath) {
        String normalizedPath = normalizeRoot(absolutePath);
        return invalidateSingleChild(rootNode, normalizedPath, 0)
            .<FileHierarchySet>map(DefaultFileHierarchySet::new)
            .orElse(FileHierarchySet.EMPTY);
    }

    private static String normalizeRoot(String absolutePath) {
        return absolutePath.equals("/") ? "" : absolutePath;
    }
}
