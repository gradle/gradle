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

package org.gradle.api.internal.file.collections;

import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;

import java.io.File;

public class FilteredMinimalFileTree implements MinimalFileTree, FileSystemMirroringFileTree, PatternFilterableFileTree {
    private final PatternSet patterns;
    private final FileSystemMirroringFileTree tree;

    public FilteredMinimalFileTree(PatternSet patterns, FileSystemMirroringFileTree tree) {
        this.patterns = patterns;
        this.tree = tree;
    }

    @Override
    public String getDisplayName() {
        return tree.getDisplayName();
    }

    public FileSystemMirroringFileTree getTree() {
        return tree;
    }

    @Override
    public DirectoryFileTree getMirror() {
        DirectoryFileTree mirror = tree.getMirror();
        return mirror.filter(this.patterns);
    }

    @Override
    public MinimalFileTree filter(PatternFilterable patterns) {
        PatternSet filter = this.patterns.intersect();
        filter.copyFrom(patterns);
        return new FilteredMinimalFileTree(filter, tree);
    }

    @Override
    public void visitStructure(FileCollectionStructureVisitor visitor, FileTreeInternal owner) {
        tree.visitStructure(new FileCollectionStructureVisitor() {
            @Override
            public boolean startVisit(FileCollectionInternal.Source source, FileCollectionInternal fileCollection) {
                throw new IllegalStateException();
            }

            @Override
            public VisitType prepareForVisit(FileCollectionInternal.Source source) {
                return visitor.prepareForVisit(source);
            }

            @Override
            public void visitCollection(FileCollectionInternal.Source source, Iterable<File> contents) {
                visitor.visitGenericFileTree(owner, FilteredMinimalFileTree.this);
            }

            @Override
            public void visitGenericFileTree(FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
                visitor.visitGenericFileTree(owner, FilteredMinimalFileTree.this);
            }

            @Override
            public void visitFileTree(File root, PatternSet patterns, FileTreeInternal fileTree) {
                PatternSet intersect = patterns.intersect();
                intersect.copyFrom(FilteredMinimalFileTree.this.patterns);
                visitor.visitFileTree(root, intersect, owner);
            }

            @Override
            public void visitFileTreeBackedByFile(File file, FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
                visitor.visitFileTreeBackedByFile(file, owner, FilteredMinimalFileTree.this);
            }
        }, owner);
    }

    @Override
    public void visit(FileVisitor visitor) {
        Spec<FileTreeElement> spec = patterns.getAsSpec();
        tree.visit(new FileVisitor() {
            @Override
            public void visitDir(FileVisitDetails dirDetails) {
                if (spec.isSatisfiedBy(dirDetails)) {
                    visitor.visitDir(dirDetails);
                }
            }

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                if (spec.isSatisfiedBy(fileDetails)) {
                    visitor.visitFile(fileDetails);
                }
            }
        });
    }
}
