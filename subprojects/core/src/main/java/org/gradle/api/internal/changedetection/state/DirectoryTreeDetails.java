/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.Ordering;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.changedetection.state.mirror.HierarchicalFileTreeVisitor;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalFileTreeVisitor;
import org.gradle.internal.file.FileType;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Represents the state of a directory tree.
 */
@SuppressWarnings("Since15")
@NonNullApi
public class DirectoryTreeDetails implements FileTreeSnapshot {
    // Interned path
    private final String path;
    // All elements, not just direct children
    private final Collection<FileSnapshot> descendants;

    public DirectoryTreeDetails(String path, Collection<FileSnapshot> descendants) {
        this.path = path;
        this.descendants = descendants;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public Collection<FileSnapshot> getDescendants() {
        return descendants;
    }

    @Override
    public String toString() {
        return path + " (" + descendants.size() + " descendants)";
    }

    @Override
    public void visit(PhysicalFileTreeVisitor visitor) {
        for (FileSnapshot descendant : descendants) {
            visitor.visit(Paths.get(descendant.getPath()), path, descendant.getName(), descendant.getRelativePath(), descendant.getContent());
        }
    }

    @Override
    public void accept(HierarchicalFileTreeVisitor visitor) {
        List<FileSnapshot> sortedDescendants = new ArrayList<FileSnapshot>(descendants);
        Collections.sort(sortedDescendants, Ordering.usingToString());
        boolean first = true;
        boolean parentDirVisited = false;
        RelativePath currentRelativePath = RelativePath.EMPTY_ROOT;
        for (FileSnapshot descendant : sortedDescendants) {
            if (first) {
                first = false;
                if (!descendant.getPath().equals(path)) {
                    Path parentDir = Paths.get(path).getParent();
                    visitor.preVisitDirectory(parentDir, parentDir.getFileName().toString());
                    parentDirVisited = true;
                }
            }
            while (!currentRelativePath.equals(descendant.getRelativePath().getParent())) {
                visitor.postVisitDirectory();
                currentRelativePath = currentRelativePath.getParent();
            }
            Path currentPath = Paths.get(descendant.getPath());
            if (descendant.getType() == FileType.Directory) {
                visitor.preVisitDirectory(currentPath, descendant.getName());
                if (!descendant.getPath().equals(path)) {
                    currentRelativePath = descendant.getRelativePath();
                }
            } else {
                visitor.visit(currentPath, descendant.getName(), descendant.getContent());
            }
        }
        if (parentDirVisited) {
            visitor.postVisitDirectory();
        }
    }
}
