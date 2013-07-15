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
import org.gradle.api.file.ContentFilterable;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCopyDetails;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.AbstractFileTreeElement;

import java.io.File;
import java.io.FilterReader;
import java.io.InputStream;
import java.util.*;

/**
 * A {@link CopySpecContentVisitor} which cleans up the tree as it is visited. Removes duplicate directories and adds in missing directories. Removes empty directories if instructed to do so by copy spec.
 */
public class NormalizingCopySpecContentVisitor extends DelegatingCopySpecContentVisitor {
    private CopySpecInternal spec;
    private final Set<RelativePath> visitedDirs = new HashSet<RelativePath>();
    private final Map<RelativePath, FileCopyDetails> pendingDirs = new HashMap<RelativePath, FileCopyDetails>();

    public NormalizingCopySpecContentVisitor(CopySpecContentVisitor visitor) {
        super(visitor);
    }

    @Override
    public void visitSpec(CopySpecInternal spec) {
        this.spec = spec;
        getVisitor().visitSpec(spec);
    }

    public void endVisit() {
        if (spec.getIncludeEmptyDirs()) {
            for (RelativePath path : new ArrayList<RelativePath>(pendingDirs.keySet())) {
                maybeVisit(path);
            }
        }
        visitedDirs.clear();
        pendingDirs.clear();
        getVisitor().endVisit();
    }

    private void maybeVisit(RelativePath path) {
        if (path == null || path.getParent() == null || !visitedDirs.add(path)) {
            return;
        }
        maybeVisit(path.getParent());
        FileCopyDetails dir = pendingDirs.remove(path);
        if (dir == null) {
            // TODO - this is pretty nasty, look at avoiding using a time bomb stub here
            dir = new FileCopyDetailsImpl(path);
        }
        getVisitor().visit(dir);
    }

    @Override
    public void visit(FileCopyDetails details) {
        if (details.isDirectory()) {
            RelativePath path = details.getRelativePath();
            if (!visitedDirs.contains(path)) {
                pendingDirs.put(path, details);
            }
        } else {
            maybeVisit(details.getRelativePath().getParent());
            getVisitor().visit(details);
        }
    }

    private static class FileCopyDetailsImpl extends AbstractFileTreeElement implements FileCopyDetails {
        private final RelativePath path;
        private long lastModified = System.currentTimeMillis();

        private FileCopyDetailsImpl(RelativePath path) {
            this.path = path;
        }

        @Override
        public String getDisplayName() {
            return path.toString();
        }

        public File getFile() {
            throw new UnsupportedOperationException();
        }

        public boolean isDirectory() {
            return !path.isFile();
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

        public void exclude() {
            throw new UnsupportedOperationException();
        }

        public void setName(String name) {
            throw new UnsupportedOperationException();
        }

        public void setPath(String path) {
            throw new UnsupportedOperationException();
        }

        public void setRelativePath(RelativePath path) {
            throw new UnsupportedOperationException();
        }

        public void setMode(int mode) {
            throw new UnsupportedOperationException();
        }

        public void setDuplicatesStrategy(DuplicatesStrategy strategy) {
            throw new UnsupportedOperationException();
        }

        public DuplicatesStrategy getDuplicatesStrategy() {
            throw new UnsupportedOperationException();
        }

        public ContentFilterable filter(Map<String, ?> properties, Class<? extends FilterReader> filterType) {
            throw new UnsupportedOperationException();
        }

        public ContentFilterable filter(Class<? extends FilterReader> filterType) {
            throw new UnsupportedOperationException();
        }

        public ContentFilterable filter(Closure closure) {
            throw new UnsupportedOperationException();
        }

        public ContentFilterable expand(Map<String, ?> properties) {
            throw new UnsupportedOperationException();
        }
    }
}
