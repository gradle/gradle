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
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.MinimalFileTree;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.WorkResults;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.util.GFileUtils;

import javax.annotation.Nullable;
import java.io.File;

public class SyncCopyActionDecorator implements CopyAction {
    private final File baseDestDir;
    private final CopyAction delegate;
    private final PatternFilterable preserveSpec;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;

    SyncCopyActionDecorator(File baseDestDir, CopyAction delegate, DirectoryFileTreeFactory directoryFileTreeFactory) {
        this(baseDestDir, delegate, null, directoryFileTreeFactory);
    }

    public SyncCopyActionDecorator(File baseDestDir, CopyAction delegate, PatternFilterable preserveSpec, DirectoryFileTreeFactory directoryFileTreeFactory) {
        this.baseDestDir = baseDestDir;
        this.delegate = delegate;
        this.preserveSpec = preserveSpec;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
    }

    @Override
    public WorkResult execute(CopyActionProcessingStream stream) {
        boolean cleanupDidWork = cleanup();
        boolean copyDidWork = copy(stream);

        return WorkResults.didWork(cleanupDidWork || copyDidWork);
    }

    private boolean cleanup() {
        DeletingFileVisitor fileVisitor = new DeletingFileVisitor(preserveSpec);
        MinimalFileTree walker = directoryFileTreeFactory.create(baseDestDir).postfix();
        walker.visit(fileVisitor);
        return fileVisitor.didWork;
    }

    private boolean copy(CopyActionProcessingStream stream) {
        WorkResult result = delegate.execute(stream);
        return result.getDidWork();
    }

    private static class DeletingFileVisitor implements FileVisitor {
        private final Spec<FileTreeElement> preserveSpec;
        private final PatternSet preserveSet;
        private boolean didWork;

        private DeletingFileVisitor(@Nullable PatternFilterable preserveSpec) {
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
            if (preserveSet.isEmpty() || !preserveSpec.isSatisfiedBy(fileDetails)) {
                GFileUtils.forceDelete(fileDetails.getFile());
                didWork = true;
            }
        }
    }
}
