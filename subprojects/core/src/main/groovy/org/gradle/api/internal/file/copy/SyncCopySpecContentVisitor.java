/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file.copy;

import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.MinimalFileTree;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class SyncCopySpecContentVisitor extends DelegatingCopySpecContentVisitor {
    private final Set<RelativePath> visited = new HashSet<RelativePath>();
    private File baseDestDir;
    private boolean didWork;

    public SyncCopySpecContentVisitor(CopySpecContentVisitor visitor) {
        super(visitor);
    }

    public void startVisit(CopyAction action) {
        baseDestDir = ((FileCopyAction) action).getDestinationDir();
        getVisitor().startVisit(action);
    }

    @Override
    public void visitDir(FileCopyDetails dirDetails) {
        visited.add(dirDetails.getRelativePath());
        getVisitor().visitDir(dirDetails);
    }

    @Override
    public void visitFile(FileCopyDetails fileDetails) {
        visited.add(fileDetails.getRelativePath());
        getVisitor().visitFile(fileDetails);
    }

    @Override
    public void endVisit() {
        FileVisitor visitor = new FileVisitor() {
            public void visitDir(FileVisitDetails dirDetails) {
                maybeDelete(dirDetails, true);
            }

            public void visitFile(FileVisitDetails fileDetails) {
                maybeDelete(fileDetails, false);
            }

            private void maybeDelete(FileVisitDetails fileDetails, boolean isDir) {
                RelativePath path = fileDetails.getRelativePath();
                if (!visited.contains(path)) {
                    if (isDir) {
                        GFileUtils.deleteDirectory(fileDetails.getFile());
                    } else {
                        GFileUtils.deleteQuietly(fileDetails.getFile());
                    }
                    didWork = true;
                }
            }
        };

        MinimalFileTree walker = new DirectoryFileTree(baseDestDir).postfix();
        walker.visit(visitor);
        visited.clear();

        getVisitor().endVisit();
    }

    @Override
    public boolean getDidWork() {
        return didWork || getVisitor().getDidWork();
    }
}
