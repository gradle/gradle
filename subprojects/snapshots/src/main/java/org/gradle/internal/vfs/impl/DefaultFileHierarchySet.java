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
import org.gradle.internal.snapshot.FileSystemNode;
import org.gradle.internal.snapshot.MetadataSnapshot;
import org.gradle.internal.snapshot.PathUtil;

import java.util.Optional;

import static org.gradle.internal.snapshot.PathUtil.isFileSeparator;
import static org.gradle.internal.snapshot.SnapshotUtil.getSnapshotFromChild;
import static org.gradle.internal.snapshot.SnapshotUtil.invalidateSingleChild;
import static org.gradle.internal.snapshot.SnapshotUtil.storeSingleChild;

public class DefaultFileHierarchySet implements FileHierarchySet {
    @VisibleForTesting
    final FileSystemNode rootNode;

    public static FileHierarchySet from(String absolutePath, MetadataSnapshot snapshot) {
        String normalizedPath = normalizeRoot(absolutePath);
        int offset = determineOffset(absolutePath);
        return new DefaultFileHierarchySet(snapshot.asFileSystemNode(offset == 0 ? normalizedPath : normalizedPath.substring(offset)));
    }

    private DefaultFileHierarchySet(FileSystemNode rootNode) {
        this.rootNode = rootNode;
    }

    @Override
    public Optional<MetadataSnapshot> getMetadata(String absolutePath) {
        String normalizedPath = normalizeRoot(absolutePath);
        int offset = determineOffset(absolutePath);
        String pathToParent = rootNode.getPathToParent();
        if (!PathUtil.isChildOfOrThis(normalizedPath, offset, pathToParent)) {
            return Optional.empty();
        }
        return getSnapshotFromChild(normalizedPath, offset, rootNode);
    }

    @Override
    public FileHierarchySet update(String absolutePath, MetadataSnapshot snapshot) {
        String normalizedPath = normalizeRoot(absolutePath);
        return new DefaultFileHierarchySet(storeSingleChild(rootNode, normalizedPath, determineOffset(normalizedPath), snapshot));
    }

    @Override
    public FileHierarchySet invalidate(String absolutePath) {
        String normalizedPath = normalizeRoot(absolutePath);
        return invalidateSingleChild(rootNode, normalizedPath, determineOffset(normalizedPath))
            .<FileHierarchySet>map(DefaultFileHierarchySet::new)
            .orElse(FileHierarchySet.EMPTY);
    }

    private static int determineOffset(String absolutePath) {
        for (int i = 0; i < absolutePath.length() - 1; i++) {
            if (!isFileSeparator(absolutePath.charAt(i))) {
                return i;
            }
        }
        return absolutePath.length();
    }

    private static String normalizeRoot(String absolutePath) {
        if (absolutePath.equals("/")) {
            return absolutePath;
        }
        return isFileSeparator(absolutePath.charAt(absolutePath.length() - 1))
            ? absolutePath.substring(0, absolutePath.length() - 1)
            : absolutePath;
    }
}
