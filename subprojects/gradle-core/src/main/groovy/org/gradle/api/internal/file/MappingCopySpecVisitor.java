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

import org.apache.tools.ant.util.ReaderInputStream;
import org.gradle.api.file.CopyAction;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.RelativePath;

import java.io.*;

public class MappingCopySpecVisitor implements CopySpecVisitor {
    private final CopySpecVisitor visitor;
    private CopySpecImpl spec;

    public MappingCopySpecVisitor(CopySpecVisitor visitor) {
        this.visitor = visitor;
    }

    public void startVisit(CopyAction action) {
        visitor.startVisit(action);
    }

    public void endVisit() {
        visitor.endVisit();
    }

    public void visitSpec(CopySpecImpl spec) {
        this.spec = spec;
        visitor.visitSpec(spec);
    }

    public void visitDir(FileVisitDetails dirDetails) {
    }

    public void visitFile(final FileVisitDetails fileDetails) {
        visitor.visitFile(new FileVisitDetailsImpl(fileDetails, spec));
    }

    public boolean getDidWork() {
        return visitor.getDidWork();
    }

    private static class FileVisitDetailsImpl extends AbstractFileTreeElement implements FileVisitDetails {
        private final FileVisitDetails fileDetails;
        private final CopySpecImpl spec;

        public FileVisitDetailsImpl(FileVisitDetails fileDetails, CopySpecImpl spec) {
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
            FilterChain filterChain = spec.getFilterChain();
            if (filterChain.hasFilters()) {
                final Reader reader = new InputStreamReader(fileDetails.open());
                filterChain.findFirstFilterChain().setInputSource(reader);
                return new ReaderInputStream(filterChain) {
                    @Override
                    public void close() throws IOException {
                        reader.close();
                    }
                };
            } else {
                return fileDetails.open();
            }
        }

        public void copyTo(OutputStream outstr) {
            if (spec.getFilterChain().hasFilters()) {
                super.copyTo(outstr);
            } else {
                fileDetails.copyTo(outstr);
            }
        }

        public boolean copyTo(File target) {
            if (spec.getFilterChain().hasFilters()) {
                return super.copyTo(target);
            }
            else {
                return fileDetails.copyTo(target);
            }
        }

        public RelativePath getRelativePath() {
            RelativePath relativePath = spec.getDestinationMapper().getPath(fileDetails);
            return new RelativePath(relativePath.isFile(), spec.getDestPath(), relativePath.getSegments());
        }
    }
}
