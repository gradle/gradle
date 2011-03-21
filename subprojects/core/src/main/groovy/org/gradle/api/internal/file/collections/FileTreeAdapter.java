/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.file.collections;

import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.AbstractFileTree;

import java.util.Collection;
import java.util.Collections;

/**
 * Adapts a {@link MinimalFileTree} into a full {@link FileTree} implementation.
 */
public class FileTreeAdapter extends AbstractFileTree implements CompositeFileCollection {
    private final MinimalFileTree tree;

    public FileTreeAdapter(MinimalFileTree tree) {
        this.tree = tree;
    }

    @Override
    public String getDisplayName() {
        return tree.getDisplayName();
    }

    public void resolve(FileCollectionResolveContext context) {
        context.add(tree);
    }

    @Override
    protected Collection<DirectoryFileTree> getAsFileTrees() {
        if (tree instanceof FileSystemMirroringFileTree) {
            FileSystemMirroringFileTree mirroringTree = (FileSystemMirroringFileTree) tree;
            if (visitAll()) {
                return Collections.singletonList(mirroringTree.getMirror());
            } else {
                return Collections.emptyList();
            }
        } else if (tree instanceof DirectoryFileTree) {
            DirectoryFileTree fileTree = (DirectoryFileTree) tree;
            return Collections.singletonList(fileTree);
        }
        throw new UnsupportedOperationException(String.format("Cannot convert %s to a file system mirror.", tree));
    }

    public FileTree visit(FileVisitor visitor) {
        tree.visit(visitor);
        return this;
    }
}
