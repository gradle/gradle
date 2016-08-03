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

import com.google.common.collect.ImmutableList;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DefaultFileCollectionResolveContext;

import java.io.File;
import java.util.Collection;
import java.util.List;

public class DefaultFileCollectionSnapshotter extends AbstractFileCollectionSnapshotter {
    public DefaultFileCollectionSnapshotter(FileSnapshotter snapshotter, TaskArtifactStateCacheAccess cacheAccess, StringInterner stringInterner, FileResolver fileResolver) {
        super(snapshotter, cacheAccess, stringInterner, fileResolver);
    }

    @Override
    protected void visitFiles(FileCollection input, final List<FileTreeElement> fileTreeElements, final List<File> missingFiles) {
        DefaultFileCollectionResolveContext context = new DefaultFileCollectionResolveContext(fileResolver);
        context.add(input);
        List<FileTreeInternal> fileTrees = context.resolveAsFileTrees();

        for (FileTreeInternal fileTree : fileTrees) {
            fileTreeElements.addAll(visitTreeForSnapshotting(fileTree));
        }
    }

    private Collection<? extends FileTreeElement> visitTreeForSnapshotting(FileTreeInternal fileTree) {
        final ImmutableList.Builder<FileTreeElement> fileTreeElements = ImmutableList.builder();
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
        return fileTreeElements.build();
    }
}
