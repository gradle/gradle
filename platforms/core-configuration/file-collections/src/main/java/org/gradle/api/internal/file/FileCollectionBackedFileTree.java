/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.api.internal.file;

import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.internal.nativeintegration.services.FileSystems;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class FileCollectionBackedFileTree extends AbstractFileTree {
    private final AbstractFileCollection collection;

    public FileCollectionBackedFileTree(TaskDependencyFactory taskDependencyFactory, Factory<PatternSet> patternSetFactory, AbstractFileCollection collection) {
        super(taskDependencyFactory, patternSetFactory);
        this.collection = collection;
    }

    @Override
    protected void appendContents(TreeFormatter formatter) {
        formatter.node("backing collection");
        formatter.startChildren();
        collection.describeContents(formatter);
        formatter.endChildren();
    }

    public AbstractFileCollection getCollection() {
        return collection;
    }

    @Override
    public FileTreeInternal matching(PatternFilterable patterns) {
        return new FilteredFileTree(this, taskDependencyFactory, patternSetFactory, () -> {
            PatternSet patternSet = patternSetFactory.create();
            patternSet.copyFrom(patterns);
            return patternSet;
        });
    }

    @Override
    public FileTree visit(FileVisitor visitor) {
        visitContentsAsFileTrees(child -> child.visit(visitor));
        return this;
    }

    @Override
    public void visitContentsAsFileTrees(Consumer<FileTreeInternal> visitor) {
        visitContents(new FileCollectionStructureVisitor() {
            @Override
            public void visitCollection(Source source, Iterable<File> contents) {
                throw new UnsupportedOperationException("Should not be called");
            }

            @Override
            public void visitFileTree(File root, PatternSet patterns, FileTreeInternal fileTree) {
                visitor.accept(fileTree);
            }

            @Override
            public void visitFileTreeBackedByFile(File file, FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
                visitor.accept(fileTree);
            }
        });
    }

    @Override
    protected void visitContents(FileCollectionStructureVisitor visitor) {
        collection.visitStructure(new FileCollectionStructureVisitor() {
            final Set<File> seen = new HashSet<>();

            @Override
            public void visitCollection(Source source, Iterable<File> contents) {
                PatternSet patterns = patternSetFactory.create();
                for (File file : contents) {
                    if (seen.add(file)) {
                        new FileTreeAdapter(new DirectoryFileTree(file, patterns, FileSystems.getDefault()), taskDependencyFactory, patternSetFactory).visitStructure(visitor);
                    }
                }
            }

            @Override
            public void visitFileTree(File root, PatternSet patterns, FileTreeInternal fileTree) {
                visitor.visitFileTree(root, patterns, fileTree);
            }

            @Override
            public void visitFileTreeBackedByFile(File file, FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
                visitor.visitFileTreeBackedByFile(file, fileTree, sourceTree);
            }
        });
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        context.add(collection);
    }

    @Override
    public String getDisplayName() {
        return collection.getDisplayName();
    }
}
