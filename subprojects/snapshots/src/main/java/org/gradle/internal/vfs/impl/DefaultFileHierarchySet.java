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

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DefaultFileHierarchySet implements FileHierarchySet {
    private final Node rootNode;

    DefaultFileHierarchySet(String rootDir, FileSystemLocationSnapshot snapshot) {
        String path = toPath(rootDir);
        this.rootNode = new SnapshotNode(path, snapshot);
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

    @Nullable
    @Override
    public FileSystemLocationSnapshot getSnapshot(String path) {
        return rootNode.getSnapshot(path, 0);
    }

    @Override
    public FileHierarchySet update(FileSystemLocationSnapshot snapshot) {
        return new DefaultFileHierarchySet(rootNode.update(snapshot.getAbsolutePath(), snapshot));
    }

    @Override
    public FileHierarchySet invalidate(String path) {
        return rootNode.invalidate(path)
            .<FileHierarchySet>map(DefaultFileHierarchySet::new)
            .orElse(FileHierarchySet.EMPTY);
    }

    private String toPath(String absolutePath) {
        if (absolutePath.equals("/")) {
            absolutePath = "";
        } else if (absolutePath.endsWith(File.separator)) {
            absolutePath = absolutePath.substring(0, absolutePath.length() - 1);
        }
        return absolutePath;
    }
}
