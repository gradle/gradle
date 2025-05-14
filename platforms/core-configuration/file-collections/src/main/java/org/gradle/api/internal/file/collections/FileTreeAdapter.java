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

import org.gradle.api.Buildable;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.AbstractFileTree;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.api.tasks.util.internal.PatternSetFactory;
import org.gradle.internal.logging.text.TreeFormatter;

import java.io.File;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Adapts a {@link MinimalFileTree} into a full {@link FileTree} implementation.
 */
public final class FileTreeAdapter extends AbstractFileTree {
    private final MinimalFileTree tree;
    private final FileCollectionObservationListener listener;

    public FileTreeAdapter(MinimalFileTree tree, FileCollectionObservationListener listener, TaskDependencyFactory taskDependencyFactory, PatternSetFactory patternSetFactory) {
        super(taskDependencyFactory, patternSetFactory);
        this.tree = tree;
        this.listener = listener;
    }

    public FileTreeAdapter(MinimalFileTree tree, TaskDependencyFactory taskDependencyFactory, PatternSetFactory patternSetFactory) {
        this(tree, fileCollection -> {}, taskDependencyFactory, patternSetFactory);
    }

    @Override
    public Set<File> getFiles() {
        listener.fileCollectionObserved(this);
        return super.getFiles();
    }

    public MinimalFileTree getTree() {
        return tree;
    }

    @Override
    public String getDisplayName() {
        return tree.getDisplayName();
    }

    @Override
    protected void appendContents(TreeFormatter formatter) {
        formatter.node("tree: " + tree);
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        if (tree instanceof Buildable) {
            context.add(tree);
        } else if (tree instanceof TaskDependencyContainer) {
            ((TaskDependencyContainer) tree).visitDependencies(context);
        }
    }

    @Override
    public boolean contains(File file) {
        if (tree instanceof RandomAccessFileCollection) {
            RandomAccessFileCollection randomAccess = (RandomAccessFileCollection) tree;
            return randomAccess.contains(file);
        }
        if (tree instanceof GeneratedSingletonFileTree) {
            return ((GeneratedSingletonFileTree) tree).getFileWithoutCreating().equals(file);
        }
        if (tree instanceof FileSystemMirroringFileTree) {
            return ((FileSystemMirroringFileTree) tree).getMirror().contains(file);
        }
        return super.contains(file);
    }

    @Override
    public FileTreeInternal matching(PatternFilterable patterns) {
        if (tree instanceof PatternFilterableFileTree) {
            PatternFilterableFileTree filterableTree = (PatternFilterableFileTree) tree;
            return new FileTreeAdapter(filterableTree.filter(patterns), listener, taskDependencyFactory, patternSetFactory);
        } else if (tree instanceof FileSystemMirroringFileTree) {
            return new FileTreeAdapter(new FilteredMinimalFileTree((PatternSet) patterns, (FileSystemMirroringFileTree) tree), listener, taskDependencyFactory, patternSetFactory);
        }
        throw new UnsupportedOperationException(String.format("Do not know how to filter %s.", tree));
    }

    @Override
    public FileTree visit(FileVisitor visitor) {
        tree.visit(visitor);
        return this;
    }

    @Override
    public void visitContentsAsFileTrees(Consumer<FileTreeInternal> visitor) {
        visitor.accept(this);
    }

    @Override
    protected void visitContents(FileCollectionStructureVisitor visitor) {
        tree.visitStructure(new MinimalFileTree.MinimalFileTreeStructureVisitor() {

            @Override
            public void visitFileTree(File root, PatternSet patterns, FileTreeInternal fileTree) {
                visitor.visitFileTree(root, patterns, fileTree);
            }

            @Override
            public void visitFileTreeBackedByFile(File file, FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
                visitor.visitFileTreeBackedByFile(file, fileTree, sourceTree);
            }
        }, this);
    }
}
