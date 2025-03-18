/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree;
import org.gradle.api.tasks.util.PatternSet;

import java.io.File;

public class DelegatingFileCollectionStructureVisitor implements FileCollectionStructureVisitor {
    private final FileCollectionStructureVisitor delegate;

    public DelegatingFileCollectionStructureVisitor(FileCollectionStructureVisitor delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean startVisit(FileCollectionInternal.Source source, FileCollectionInternal fileCollection) {
        return delegate.startVisit(source, fileCollection);
    }

    @Override
    public VisitType prepareForVisit(FileCollectionInternal.Source source) {
        return delegate.prepareForVisit(source);
    }

    @Override
    public void visitCollection(FileCollectionInternal.Source source, Iterable<File> contents) {
        delegate.visitCollection(source, contents);
    }

    @Override
    public void visitFileTree(File root, PatternSet patterns, FileTreeInternal fileTree) {
        delegate.visitFileTree(root, patterns, fileTree);
    }

    @Override
    public void visitFileTreeBackedByFile(File file, FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
        delegate.visitFileTreeBackedByFile(file, fileTree, sourceTree);
    }
}
