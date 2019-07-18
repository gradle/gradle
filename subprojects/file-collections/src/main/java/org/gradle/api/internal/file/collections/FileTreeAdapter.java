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
import org.gradle.api.internal.file.FileCollectionLeafVisitor;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.api.internal.tasks.TaskDependencyInternal;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * Adapts a {@link MinimalFileTree} into a full {@link FileTree} implementation.
 */
public class FileTreeAdapter extends AbstractFileTree implements FileCollectionContainer {
    private final MinimalFileTree tree;

    public FileTreeAdapter(MinimalFileTree tree) {
        this.tree = tree;
    }

    public FileTreeAdapter(MinimalFileTree tree, Factory<PatternSet> patternSetFactory) {
        super(patternSetFactory);
        this.tree = tree;
    }

    public MinimalFileTree getTree() {
        return tree;
    }

    @Override
    public String getDisplayName() {
        return tree.getDisplayName();
    }

    @Override
    public void visitContents(FileCollectionResolveContext context) {
        context.add(tree);
    }

    @Override
    public void registerWatchPoints(FileSystemSubset.Builder builder) {
        tree.registerWatchPoints(builder);
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
        } else if (tree instanceof LocalFileTree) {
            LocalFileTree fileTree = (LocalFileTree) tree;
            return fileTree.getLocalContents();
        }
        throw new UnsupportedOperationException(String.format("Cannot convert %s to local file system directories.", tree));
    }

    @Override
    public TaskDependency getBuildDependencies() {
        if (tree instanceof Buildable) {
            Buildable buildable = (Buildable) tree;
            return buildable.getBuildDependencies();
        }
        return TaskDependencyInternal.EMPTY;
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
    public FileTree matching(PatternFilterable patterns) {
        if (tree instanceof PatternFilterableFileTree) {
            PatternFilterableFileTree filterableTree = (PatternFilterableFileTree) tree;
            return new FileTreeAdapter(filterableTree.filter(patterns), patternSetFactory);
        }
        return super.matching(patterns);
    }

    @Override
    public FileTree visit(FileVisitor visitor) {
        tree.visit(visitor);
        return this;
    }

    @Override
    public void visitLeafCollections(FileCollectionLeafVisitor visitor) {
        if (tree instanceof DirectoryFileTree) {
            DirectoryFileTree directoryFileTree = (DirectoryFileTree) tree;
            visitor.visitFileTree(directoryFileTree.getDir(), directoryFileTree.getPatterns());
        } else if (tree instanceof SingletonFileTree) {
            SingletonFileTree singletonFileTree = (SingletonFileTree) tree;
            visitor.visitFileTree(singletonFileTree.getFile(), singletonFileTree.getPatterns());
        } else if (tree instanceof ArchiveFileTree) {
            ArchiveFileTree archiveFileTree = (ArchiveFileTree) tree;
            File backingFile = archiveFileTree.getBackingFile();
            if (backingFile != null) {
                visitor.visitCollection(ImmutableFileCollection.of(backingFile));
            } else {
                visitor.visitGenericFileTree(this);
            }
        } else {
            visitor.visitGenericFileTree(this);
        }
    }

    @Override
    public void visitTreeOrBackingFile(FileVisitor visitor) {
        tree.visitTreeOrBackingFile(visitor);
    }
}
