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
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.MinimalFileTree;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.file.Deleter;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.Set;

public class SyncCopyActionDecorator implements CopyAction {
    private final File baseDestDir;
    private final CopyAction delegate;
    private final PatternFilterable preserveSpec;
    private final Deleter deleter;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;

    public SyncCopyActionDecorator(
        File baseDestDir,
        CopyAction delegate,
        Deleter deleter,
        DirectoryFileTreeFactory directoryFileTreeFactory
    ) {
        this(baseDestDir, delegate, null, deleter, directoryFileTreeFactory);
    }

    public SyncCopyActionDecorator(
        File baseDestDir,
        CopyAction delegate,
        @Nullable PatternFilterable preserveSpec,
        Deleter deleter,
        DirectoryFileTreeFactory directoryFileTreeFactory
    ) {
        this.baseDestDir = baseDestDir;
        this.delegate = delegate;
        this.preserveSpec = preserveSpec;
        this.deleter = deleter;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
    }

    @Override
    public WorkResult execute(final CopyActionProcessingStream stream) {
        final Set<RelativePath> visited = new HashSet<>();

        WorkResult didWork = delegate.execute(action -> stream.process(details -> {
            visited.add(details.getRelativePath());
            action.processFile(details);
        }));

        SyncCopyActionDecoratorFileVisitor fileVisitor = new SyncCopyActionDecoratorFileVisitor(visited, preserveSpec, deleter);

        MinimalFileTree walker = directoryFileTreeFactory.create(baseDestDir).postfix();
        walker.visit(fileVisitor);
        visited.clear();

        return WorkResults.didWork(didWork.getDidWork() || fileVisitor.didWork);
    }

    private static class SyncCopyActionDecoratorFileVisitor implements FileVisitor {
        private final Set<RelativePath> visited;
        private final Spec<FileTreeElement> preserveSpec;
        private final PatternSet preserveSet;
        private final Deleter deleter;
        private boolean didWork;

        private SyncCopyActionDecoratorFileVisitor(Set<RelativePath> visited, @Nullable PatternFilterable preserveSpec, Deleter deleter) {
            this.visited = visited;
            this.deleter = deleter;
            PatternSet preserveSet = new PatternSet();
            if (preserveSpec != null) {
                preserveSet.include(preserveSpec.getIncludes());
                preserveSet.exclude(preserveSpec.getExcludes());
            }
            this.preserveSet = preserveSet;
            this.preserveSpec = preserveSet.getAsSpec();
        }

        @Override
        public void visitDir(FileVisitDetails dirDetails) {
            maybeDelete(dirDetails);
        }

        @Override
        public void visitFile(FileVisitDetails fileDetails) {
            maybeDelete(fileDetails);
        }

        private void maybeDelete(FileVisitDetails fileDetails) {
            RelativePath path = fileDetails.getRelativePath();
            if (!visited.contains(path)) {
                if (preserveSet.isEmpty() || !preserveSpec.isSatisfiedBy(fileDetails)) {
                    try {
                        didWork = deleter.deleteRecursively(fileDetails.getFile());
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                }
            }
        }
    }
}
