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

import com.google.common.collect.ImmutableList;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.tasks.util.PatternSet;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TreeSnapshotter {
    private ConcurrentMap<String, Collection<? extends FileVisitDetails>> cachedTrees = new ConcurrentHashMap<String, Collection<? extends FileVisitDetails>>();

    public Collection<? extends FileVisitDetails> visitTree(FileTreeInternal fileTree, boolean allowReuse) {
        if (isDirectoryFileTree(fileTree)) {
            DirectoryFileTree directoryFileTree = DirectoryFileTree.class.cast(((FileTreeAdapter) fileTree).getTree());
            if (isEmptyPattern(directoryFileTree.getPatterns())) {
                final String absolutePath = directoryFileTree.getDir().getAbsolutePath();
                Collection<? extends FileVisitDetails> cachedTree = cachedTrees.get(absolutePath);
                if (cachedTree != null) {
                    return cachedTree;
                } else {
                    cachedTree = doVisitTree(fileTree);
                    Collection<? extends FileVisitDetails> previous = cachedTrees.putIfAbsent(absolutePath, cachedTree);
                    return previous != null ? previous : cachedTree;
                }
            }
        }
        return doVisitTree(fileTree);
    }

    private boolean isEmptyPattern(PatternSet patterns) {
        return patterns.getExcludes().isEmpty() && patterns.getIncludes().isEmpty() && patterns.getExcludeSpecs().isEmpty() && patterns.getIncludeSpecs().isEmpty();
    }

    private boolean isDirectoryFileTree(FileTreeInternal fileTree) {
        return fileTree instanceof FileTreeAdapter && ((FileTreeAdapter) fileTree).getTree() instanceof DirectoryFileTree;
    }

    private Collection<? extends FileVisitDetails> doVisitTree(FileTreeInternal fileTree) {
        final ImmutableList.Builder<FileVisitDetails> fileVisitDetails = ImmutableList.builder();
        fileTree.visitTreeOrBackingFile(new FileVisitor() {
            @Override
            public void visitDir(FileVisitDetails dirDetails) {
                fileVisitDetails.add(dirDetails);
            }

            @Override
            public void visitFile(FileVisitDetails fileDetails) {
                fileVisitDetails.add(fileDetails);
            }
        });
        return fileVisitDetails.build();
    }

    public void clearCache() {
        cachedTrees.clear();
    }
}
