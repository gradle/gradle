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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import groovy.lang.Closure;
import org.gradle.api.Transformer;
import org.gradle.api.file.ContentFilterable;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.AbstractFileTreeElement;
import org.gradle.api.internal.file.CopyActionProcessingStreamAction;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.nativeintegration.filesystem.Chmod;

import java.io.File;
import java.io.FilterReader;
import java.io.InputStream;
import java.util.*;

/**
 * A {@link CopyAction} which cleans up the tree as it is visited. Removes duplicate directories and adds in missing directories. Removes empty directories if instructed to do so by copy
 * spec.
 */
public class NormalizingCopyActionDecorator implements CopyAction {

    private final CopyAction delegate;
    private final Chmod chmod;

    public NormalizingCopyActionDecorator(CopyAction delegate, Chmod chmod) {
        this.delegate = delegate;
        this.chmod = chmod;
    }

    public WorkResult execute(final CopyActionProcessingStream stream) {
        final Set<RelativePath> visitedDirs = new HashSet<RelativePath>();
        final ListMultimap<RelativePath, FileCopyDetailsInternal> pendingDirs = ArrayListMultimap.create();

        WorkResult result = delegate.execute(new CopyActionProcessingStream() {
            public void process(final CopyActionProcessingStreamAction action) {


                stream.process(new CopyActionProcessingStreamAction() {
                    public void processFile(FileCopyDetailsInternal details) {
                        if (details.isDirectory()) {
                            RelativePath path = details.getRelativePath();
                            if (!visitedDirs.contains(path)) {
                                pendingDirs.put(path, details);
                            }
                        } else {
                            maybeVisit(details.getRelativePath().getParent(), details.isIncludeEmptyDirs(), action);
                            action.processFile(details);
                        }
                    }
                });

                for (RelativePath path : new LinkedHashSet<RelativePath>(pendingDirs.keySet())) {
                    List<FileCopyDetailsInternal> detailsList = new ArrayList<FileCopyDetailsInternal>(pendingDirs.get(path));
                    for (FileCopyDetailsInternal details : detailsList) {
                        if (details.isIncludeEmptyDirs()) {
                            maybeVisit(path, details.isIncludeEmptyDirs(), action);
                        }
                    }
                }

                visitedDirs.clear();
                pendingDirs.clear();
            }

            private void maybeVisit(RelativePath path, boolean includeEmptyDirs, CopyActionProcessingStreamAction delegateAction) {
                if (path == null || path.getParent() == null || !visitedDirs.add(path)) {
                    return;
                }
                maybeVisit(path.getParent(), includeEmptyDirs, delegateAction);
                List<FileCopyDetailsInternal> detailsForPath = pendingDirs.removeAll(path);

                FileCopyDetailsInternal dir;
                if (detailsForPath.isEmpty()) {
                    // TODO - this is pretty nasty, look at avoiding using a time bomb stub here
                    dir = new StubbedFileCopyDetails(path, includeEmptyDirs, chmod);
                } else {
                    dir = detailsForPath.get(0);
                }
                delegateAction.processFile(dir);
            }
        });

        return result;
    }


    private static class StubbedFileCopyDetails extends AbstractFileTreeElement implements FileCopyDetailsInternal {
        private final RelativePath path;
        private final boolean includeEmptyDirs;
        private long lastModified = System.currentTimeMillis();

        private StubbedFileCopyDetails(RelativePath path, boolean includeEmptyDirs, Chmod chmod) {
            super(chmod);
            this.path = path;
            this.includeEmptyDirs = includeEmptyDirs;
        }

        public boolean isIncludeEmptyDirs() {
            return includeEmptyDirs;
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

        public String getSourceName() {
            throw new UnsupportedOperationException();
        }

        public String getSourcePath() {
            throw new UnsupportedOperationException();
        }

        public RelativePath getRelativeSourcePath() {
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

        public ContentFilterable filter(Transformer<String, String> transformer) {
            throw new UnsupportedOperationException();
        }

        public ContentFilterable expand(Map<String, ?> properties) {
            throw new UnsupportedOperationException();
        }
    }
}
