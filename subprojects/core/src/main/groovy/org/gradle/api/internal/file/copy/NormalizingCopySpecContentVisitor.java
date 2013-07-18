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
import org.gradle.api.Action;
import org.gradle.api.file.ContentFilterable;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.AbstractFileTreeElement;
import org.gradle.api.tasks.WorkResult;

import java.io.File;
import java.io.FilterReader;
import java.io.InputStream;
import java.util.*;

/**
 * A {@link CopySpecContentVisitor} which cleans up the tree as it is visited. Removes duplicate directories and adds in missing directories. Removes empty directories if instructed to do so by copy
 * spec.
 */
public class NormalizingCopySpecContentVisitor implements CopySpecContentVisitor {

    private final CopySpecContentVisitor delegate;

    public NormalizingCopySpecContentVisitor(CopySpecContentVisitor delegate) {
        this.delegate = delegate;
    }

    public WorkResult visit(final Action<Action<? super FileCopyDetailsInternal>> visitor) {
        final Set<RelativePath> visitedDirs = new HashSet<RelativePath>();
        final ListMultimap<RelativePath, FileCopyDetailsInternal> pendingDirs = ArrayListMultimap.create();

        WorkResult result = delegate.visit(new Action<Action<? super FileCopyDetailsInternal>>() {
            public void execute(final Action<? super FileCopyDetailsInternal> delegateAction) {
                visitor.execute(new Action<FileCopyDetailsInternal>() {
                    public void execute(FileCopyDetailsInternal details) {
                        if (details.isDirectory()) {
                            RelativePath path = details.getRelativePath();
                            if (!visitedDirs.contains(path)) {
                                pendingDirs.put(path, details);
                            }
                        } else {
                            maybeVisit(details.getRelativePath().getParent(), details.getCopySpec(), delegateAction);
                            delegateAction.execute(details);
                        }
                    }
                });

                for (RelativePath path : new LinkedHashSet<RelativePath>(pendingDirs.keySet())) {
                    List<FileCopyDetailsInternal> detailsList = new ArrayList<FileCopyDetailsInternal>(pendingDirs.get(path));
                    for (FileCopyDetailsInternal details : detailsList) {
                        if (details.getCopySpec().getIncludeEmptyDirs()) {
                            maybeVisit(path, details.getCopySpec(), delegateAction);
                        }
                    }
                }

                visitedDirs.clear();
                pendingDirs.clear();
            }

            private void maybeVisit(RelativePath path, CopySpecInternal copySpec, Action<? super FileCopyDetailsInternal> delegateAction) {
                if (path == null || path.getParent() == null || !visitedDirs.add(path)) {
                    return;
                }
                maybeVisit(path.getParent(), copySpec, delegateAction);
                List<FileCopyDetailsInternal> detailsForPath = pendingDirs.removeAll(path);

                FileCopyDetailsInternal dir;
                if (detailsForPath.isEmpty()) {
                    // TODO - this is pretty nasty, look at avoiding using a time bomb stub here
                    dir = new StubbedFileCopyDetails(path, copySpec);
                } else {
                    dir = detailsForPath.get(0);
                }
                delegateAction.execute(dir);
            }
        });

        return result;
    }


    private static class StubbedFileCopyDetails extends AbstractFileTreeElement implements FileCopyDetailsInternal {
        private final RelativePath path;
        private long lastModified = System.currentTimeMillis();
        private final CopySpecInternal copySpec;

        private StubbedFileCopyDetails(RelativePath path, CopySpecInternal copySpec) {
            this.path = path;
            this.copySpec = copySpec;
        }

        public CopySpecInternal getCopySpec() {
            return copySpec;
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
