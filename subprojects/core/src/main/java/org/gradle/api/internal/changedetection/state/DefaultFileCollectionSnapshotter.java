/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.DefaultFileVisitDetails;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.Factory;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;

import java.io.File;
import java.util.List;

public class DefaultFileCollectionSnapshotter extends AbstractFileCollectionSnapshotter {
    private final FileSystem fileSystem;
    private final Factory<PatternSet> patternSetFactory;

    public DefaultFileCollectionSnapshotter(FileSnapshotter snapshotter, TaskArtifactStateCacheAccess cacheAccess, StringInterner stringInterner, FileSystem fileSystem, Factory<PatternSet> patternSetFactory) {
        super(snapshotter, cacheAccess, stringInterner);
        this.patternSetFactory = patternSetFactory;
        this.fileSystem = fileSystem;
    }

    @Override
    protected void visitFiles(FileCollection input, final List<FileTreeElement> fileTreeElements, final List<FileTreeElement> missingFiles) {
        FileCollectionInternal fileCollection = (FileCollectionInternal) input;
        fileCollection.visitRootElements(new FileCollectionVisitor() {
            @Override
            public void visitCollection(FileCollectionInternal fileCollection) {
                for (File file : fileCollection) {
                    if (file.isFile()) {
                        fileTreeElements.add(new DefaultFileVisitDetails(file, fileSystem, fileSystem));
                    } else if (file.isDirectory()) {
                        visitTree(new FileTreeAdapter(new DirectoryFileTree(file, patternSetFactory.create())));
                    } else {
                        missingFiles.add(new MissingFileVisitDetails(file));
                    }
                }
            }

            @Override
            public void visitTree(FileTreeInternal fileTree) {
                fileTree.visitTreeOrBackingFile(new FileVisitor() {
                    @Override
                    public void visitDir(FileVisitDetails dirDetails) {
                        fileTreeElements.add(dirDetails);
                    }

                    @Override
                    public void visitFile(FileVisitDetails fileDetails) {
                        fileTreeElements.add(fileDetails);
                    }
                });
            }
        });
    }
}
