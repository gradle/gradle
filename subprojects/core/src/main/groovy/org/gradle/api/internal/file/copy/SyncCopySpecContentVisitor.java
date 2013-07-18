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

import org.gradle.api.Action;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.MinimalFileTree;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.tasks.WorkResult;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class SyncCopySpecContentVisitor implements CopySpecContentVisitor {
    private final File baseDestDir;
    private final CopySpecContentVisitor delegate;

    public SyncCopySpecContentVisitor(File baseDestDir, CopySpecContentVisitor delegate) {
        this.baseDestDir = baseDestDir;
        this.delegate = delegate;
    }

    public WorkResult visit(final Action<Action<? super FileCopyDetailsInternal>> visitor) {
        final Set<RelativePath> visited = new HashSet<RelativePath>();

        WorkResult didWork = delegate.visit(new Action<Action<? super FileCopyDetailsInternal>>() {
            public void execute(final Action<? super FileCopyDetailsInternal> delegateAction) {
                visitor.execute(new Action<FileCopyDetailsInternal>() {
                    public void execute(FileCopyDetailsInternal details) {
                        visited.add(details.getRelativePath());
                        delegateAction.execute(details);
                    }
                });
            }
        });

        final BooleanHolder didDeleteHolder = new BooleanHolder();
        FileVisitor fileVisitor = new FileVisitor() {
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
                    didDeleteHolder.flag = true;
                }
            }
        };

        MinimalFileTree walker = new DirectoryFileTree(baseDestDir).postfix();
        walker.visit(fileVisitor);
        visited.clear();

        return new SimpleWorkResult(didWork.getDidWork() || didDeleteHolder.flag);
    }

    private static class BooleanHolder {
        boolean flag;
    }

}
