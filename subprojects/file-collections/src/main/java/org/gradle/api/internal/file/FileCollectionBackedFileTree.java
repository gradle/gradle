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

import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.internal.nativeintegration.services.FileSystems;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class FileCollectionBackedFileTree extends CompositeFileTree {
    private final AbstractFileCollection collection;

    public FileCollectionBackedFileTree(Factory<PatternSet> patternSetFactory, AbstractFileCollection collection) {
        super(patternSetFactory);
        this.collection = collection;
    }

    public AbstractFileCollection getCollection() {
        return collection;
    }

    @Override
    protected void visitChildren(Consumer<FileCollectionInternal> visitor) {
        collection.visitStructure(new FileCollectionStructureVisitor() {
            final Set<File> seen = new HashSet<>();

            @Override
            public void visitCollection(Source source, Iterable<File> contents) {
                for (File file : contents) {
                    if (seen.add(file)) {
                        visitor.accept(new FileTreeAdapter(new DirectoryFileTree(file, patternSetFactory.create(), FileSystems.getDefault()), patternSetFactory));
                    }
                }
            }

            @Override
            public void visitGenericFileTree(FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
                visitor.accept(fileTree);
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
    public void visitDependencies(TaskDependencyResolveContext context) {
        context.add(collection);
    }

    @Override
    public String getDisplayName() {
        return collection.getDisplayName();
    }
}
