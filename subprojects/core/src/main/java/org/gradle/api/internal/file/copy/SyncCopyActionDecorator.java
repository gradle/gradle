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

import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.CopyActionProcessingStreamAction;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.MinimalFileTree;
import org.gradle.api.internal.tasks.SimpleWorkResult;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class SyncCopyActionDecorator implements CopyAction {
    private final File baseDestDir;
    private final CopyAction delegate;
    private final PatternFilterable preserveSpec;

    public SyncCopyActionDecorator(File baseDestDir, CopyAction delegate) {
        this(baseDestDir, delegate, null);
    }

    public SyncCopyActionDecorator(File baseDestDir, CopyAction delegate, PatternFilterable preserveSpec) {
        this.baseDestDir = baseDestDir;
        this.delegate = delegate;
        this.preserveSpec = preserveSpec;
    }

    public WorkResult execute(final CopyActionProcessingStream stream) {
        final Set<RelativePath> visited = new HashSet<RelativePath>();

        WorkResult didWork = delegate.execute(new CopyActionProcessingStream() {
            public void process(final CopyActionProcessingStreamAction action) {
                stream.process(new CopyActionProcessingStreamAction() {
                    public void processFile(FileCopyDetailsInternal details) {
                        visited.add(details.getRelativePath());
                        action.processFile(details);
                    }
                });
            }
        });

        SyncCopyActionDecoratorFileVisitor fileVisitor = new SyncCopyActionDecoratorFileVisitor(visited, preserveSpec);

        MinimalFileTree walker = new DirectoryFileTree(baseDestDir).postfix();
        walker.visit(fileVisitor);
        visited.clear();

        return new SimpleWorkResult(didWork.getDidWork() || fileVisitor.didWork);
    }

    private static class SyncCopyActionDecoratorFileVisitor implements FileVisitor {
        private final Set<RelativePath> visited;
        private final Spec<FileTreeElement> preserveSpec;
        private final PatternSet preserveSet;
        private boolean didWork;

        private SyncCopyActionDecoratorFileVisitor(Set<RelativePath> visited, PatternFilterable preserveSpec) {
            this.visited = visited;
            PatternSet preserveSet = new PatternSet();
            if (preserveSpec != null) {
                preserveSet.include(preserveSpec.getIncludes());
                preserveSet.exclude(preserveSpec.getExcludes());
            }
            this.preserveSet = preserveSet;
            this.preserveSpec = preserveSet.getAsSpec();
        }

        public void visitDir(FileVisitDetails dirDetails) {
            maybeDelete(dirDetails, true);
        }

        public void visitFile(FileVisitDetails fileDetails) {
            maybeDelete(fileDetails, false);
        }

        private void maybeDelete(FileVisitDetails fileDetails, boolean isDir) {
            RelativePath path = fileDetails.getRelativePath();
            if (!visited.contains(path)) {
                if (preserveSet.isEmpty() || !preserveSpec.isSatisfiedBy(fileDetails)) {
                    if (isDir) {
                        GFileUtils.deleteDirectory(fileDetails.getFile());
                    } else {
                        GFileUtils.deleteQuietly(fileDetails.getFile());
                    }
                    didWork = true;
                }
            }
        }
    }
}
