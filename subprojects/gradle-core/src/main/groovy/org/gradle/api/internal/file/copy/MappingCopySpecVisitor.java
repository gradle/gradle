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

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.file.*;
import org.gradle.api.internal.file.AbstractFileTreeElement;

import java.io.File;
import java.io.FilterReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

public class MappingCopySpecVisitor implements CopySpecVisitor {
    private final CopySpecVisitor visitor;
    private ReadableCopySpec spec;

    public MappingCopySpecVisitor(CopySpecVisitor visitor) {
        this.visitor = visitor;
    }

    public void startVisit(CopyAction action) {
        visitor.startVisit(action);
    }

    public void endVisit() {
        visitor.endVisit();
    }

    public void visitSpec(ReadableCopySpec spec) {
        this.spec = spec;
        visitor.visitSpec(spec);
    }

    public void visitDir(FileVisitDetails dirDetails) {
        visitor.visitDir(new FileVisitDetailsImpl(dirDetails, spec));
    }

    public void visitFile(final FileVisitDetails fileDetails) {
        FileVisitDetailsImpl details = new FileVisitDetailsImpl(fileDetails, spec);
        for (Action<? super FileCopyDetails> action : spec.getAllCopyActions()) {
            action.execute(details);
            if (details.excluded) {
                return;
            }
        }
        visitor.visitFile(details);
    }

    public boolean getDidWork() {
        return visitor.getDidWork();
    }

    private static class FileVisitDetailsImpl extends AbstractFileTreeElement implements FileVisitDetails, FileCopyDetails {
        private final FileVisitDetails fileDetails;
        private final ReadableCopySpec spec;
        private final FilterChain filterChain = new FilterChain();
        private RelativePath relativePath;
        private boolean excluded;

        public FileVisitDetailsImpl(FileVisitDetails fileDetails, ReadableCopySpec spec) {
            this.fileDetails = fileDetails;
            this.spec = spec;
        }

        public String getDisplayName() {
            return fileDetails.toString();
        }

        public void stopVisiting() {
            fileDetails.stopVisiting();
        }

        public File getFile() {
            return fileDetails.getFile();
        }

        public boolean isDirectory() {
            return fileDetails.isDirectory();
        }

        public long getLastModified() {
            return fileDetails.getLastModified();
        }

        public long getSize() {
            return fileDetails.getSize();
        }

        public InputStream open() {
            if (filterChain.hasFilters()) {
                return filterChain.transform(fileDetails.open());
            } else {
                return fileDetails.open();
            }
        }

        public void copyTo(OutputStream outstr) {
            if (filterChain.hasFilters()) {
                super.copyTo(outstr);
            } else {
                fileDetails.copyTo(outstr);
            }
        }

        public boolean copyTo(File target) {
            if (filterChain.hasFilters()) {
                return super.copyTo(target);
            }
            else {
                return fileDetails.copyTo(target);
            }
        }

        public RelativePath getRelativePath() {
            if (relativePath == null) {
                RelativePath path = fileDetails.getRelativePath();
                relativePath = spec.getDestPath().append(path.isFile(), path.getSegments());
            }
            return relativePath;
        }

        public void setRelativePath(RelativePath path) {
            this.relativePath = path;
        }

        public void setName(String name) {
            relativePath = getRelativePath().replaceLastName(name);
        }

        public void setPath(String path) {
            relativePath = RelativePath.parse(getRelativePath().isFile(), path);
        }

        public void exclude() {
            excluded = true;
        }

        public ContentFilterable filter(Closure closure) {
            filterChain.add(closure);
            return this;
        }

        public ContentFilterable filter(Map<String, ?> map, Class<? extends FilterReader> filterType) {
            filterChain.add(filterType, map);
            return this;
        }

        public ContentFilterable filter(Class<? extends FilterReader> filterType) {
            filterChain.add(filterType);
            return this;
        }
    }
}
