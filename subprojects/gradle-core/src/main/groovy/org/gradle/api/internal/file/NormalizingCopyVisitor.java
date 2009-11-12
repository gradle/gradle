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
package org.gradle.api.internal.file;

import org.gradle.api.file.CopyAction;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RelativePath;

import java.io.File;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

public class NormalizingCopyVisitor implements CopySpecVisitor {
    private final CopySpecVisitor visitor;
    private final Set<RelativePath> visitedDirs = new HashSet<RelativePath>();

    public NormalizingCopyVisitor(CopySpecVisitor visitor) {
        this.visitor = visitor;
    }

    public void startVisit(CopyAction action) {
        visitor.startVisit(action);
    }

    public void endVisit() {
        visitedDirs.clear();
        visitor.endVisit();
    }

    public void visitSpec(ReadableCopySpec spec) {
        maybeVisit(spec.getDestPath());
        visitor.visitSpec(spec);
    }

    private void maybeVisit(RelativePath path) {
        maybeVisit(path, new FileVisitDetailsImpl(path));
    }

    private void maybeVisit(RelativePath path, FileVisitDetails dirDetails) {
        if (path == null || path.getParent() == null || !visitedDirs.add(path)) {
            return;
        }
        maybeVisit(path.getParent());
        visitor.visitDir(dirDetails);
    }

    public void visitFile(FileVisitDetails fileDetails) {
        maybeVisit(fileDetails.getRelativePath().getParent());
        visitor.visitFile(fileDetails);
    }

    public void visitDir(FileVisitDetails dirDetails) {
        maybeVisit(dirDetails.getRelativePath(), dirDetails);
    }

    public boolean getDidWork() {
        return visitor.getDidWork();
    }

    private static class FileVisitDetailsImpl extends AbstractFileTreeElement implements FileVisitDetails {
        private final RelativePath path;
        private long lastModified = System.currentTimeMillis();

        private FileVisitDetailsImpl(RelativePath path) {
            this.path = path;
        }

        @Override
        public String getDisplayName() {
            return path.toString();
        }

        public void stopVisiting() {
            throw new UnsupportedOperationException();
        }

        public File getFile() {
            throw new UnsupportedOperationException();
        }

        public boolean isDirectory() {
            throw new UnsupportedOperationException();
        }

        public long getLastModified() {
            return lastModified;
        }

        public long getSize() {
            throw new UnsupportedOperationException();
        }

        public InputStream open() {
            throw new UnsupportedOperationException();
        }

        public RelativePath getRelativePath() {
            return path;
        }
    }
}
