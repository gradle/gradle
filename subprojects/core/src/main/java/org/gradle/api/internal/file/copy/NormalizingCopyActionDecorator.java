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
import org.gradle.api.Transformer;
import org.gradle.api.file.ConfigurableFilePermissions;
import org.gradle.api.file.ContentFilterable;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.ExpandDetails;
import org.gradle.api.file.FilePermissions;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.AbstractFileTreeElement;
import org.gradle.api.internal.file.CopyActionProcessingStreamAction;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.WorkResult;
import org.gradle.internal.file.Chmod;
import org.gradle.util.internal.GFileUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FilterReader;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @Override
    public WorkResult execute(final CopyActionProcessingStream stream) {
        final Set<RelativePath> visitedDirs = new HashSet<>();
        final ListMultimap<RelativePath, FileCopyDetailsInternal> pendingDirs = ArrayListMultimap.create();

        return delegate.execute(action -> {
            stream.process(details -> {
                if (details.isDirectory()) {
                    RelativePath path = details.getRelativePath();
                    if (!visitedDirs.contains(path)) {
                        pendingDirs.put(path, details);
                    }
                } else {
                    maybeVisit(details.getRelativePath().getParent(), details.getSpecResolver(), action, visitedDirs, pendingDirs);
                    action.processFile(details);
                }
            });

            for (RelativePath path : new LinkedHashSet<>(pendingDirs.keySet())) {
                List<FileCopyDetailsInternal> detailsList = new ArrayList<>(pendingDirs.get(path));
                for (FileCopyDetailsInternal details : detailsList) {
                    if (details.getSpecResolver().getIncludeEmptyDirs()) {
                        maybeVisit(path, details.getSpecResolver(), action, visitedDirs, pendingDirs);
                    }
                }
            }

            visitedDirs.clear();
            pendingDirs.clear();
        });
    }

    private void maybeVisit(@Nullable RelativePath path, CopySpecResolver specResolver, CopyActionProcessingStreamAction delegateAction, Set<RelativePath> visitedDirs, ListMultimap<RelativePath, FileCopyDetailsInternal> pendingDirs) {
        if (path == null || path.getParent() == null || !visitedDirs.add(path)) {
            return;
        }

        List<FileCopyDetailsInternal> detailsForPath = pendingDirs.removeAll(path);
        FileCopyDetailsInternal dir;
        if (detailsForPath.isEmpty()) {
            // TODO - this is pretty nasty, look at avoiding using a time bomb stub here
            dir = new ParentDirectoryStub(specResolver, path, chmod);
        } else {
            dir = detailsForPath.get(0);
        }
        maybeVisit(path.getParent(), dir.getSpecResolver(), delegateAction, visitedDirs, pendingDirs);

        delegateAction.processFile(dir);
    }

    private static class ParentDirectoryStub extends AbstractFileTreeElement implements FileCopyDetailsInternal {
        private final RelativePath path;

        private final long lastModified = System.currentTimeMillis();

        private final CopySpecResolver specResolver;

        private ParentDirectoryStub(CopySpecResolver specResolver, RelativePath path, Chmod chmod) {
            super(chmod);
            this.path = path;
            this.specResolver = specResolver;
        }

        private static UnsupportedOperationException unsupported() {
            return new UnsupportedOperationException("It's a synthetic FileCopyDetails just to create parent directories");
        }

        @Override
        public String getDisplayName() {
            return path.toString();
        }

        @Override
        public File getFile() {
            throw unsupported();
        }

        @Override
        public boolean isDirectory() {
            return true;
        }

        @Override
        public long getLastModified() {
            return lastModified;
        }

        @Override
        public long getSize() {
            throw unsupported();
        }

        @Override
        public InputStream open() {
            throw unsupported();
        }

        @Override
        public RelativePath getRelativePath() {
            return path;
        }

        @Override
        public boolean copyTo(File target) {
            try {
                GFileUtils.mkdirs(target);
                getChmod().chmod(target, getPermissions().toUnixNumeric());
                return true;
            } catch (Exception e) {
                throw new CopyFileElementException(String.format("Could not copy %s to '%s'.", getDisplayName(), target), e);
            }
        }

        @Override
        public FilePermissions getPermissions() {
            Provider<FilePermissions> specMode = specResolver.getImmutableDirPermissions();
            if (specMode.isPresent()) {
                return specMode.get();
            }

            return super.getPermissions();
        }

        @Override
        public void exclude() {
            throw unsupported();
        }

        @Override
        public void setName(String name) {
            throw unsupported();
        }

        @Override
        public void setPath(String path) {
            throw unsupported();
        }

        @Override
        public void setRelativePath(RelativePath path) {
            throw unsupported();
        }

        @Override
        public void setMode(int mode) {
            throw unsupported();
        }

        @Override
        public void permissions(Action<? super ConfigurableFilePermissions> configureAction) {
            throw unsupported();
        }

        @Override
        public void setPermissions(FilePermissions permissions) {
            throw unsupported();
        }

        @Override
        public void setDuplicatesStrategy(DuplicatesStrategy strategy) {
            throw unsupported();
        }

        @Override
        public DuplicatesStrategy getDuplicatesStrategy() {
            throw unsupported();
        }

        @Override
        public boolean isDefaultDuplicatesStrategy() {
            throw unsupported();
        }

        @Override
        public String getSourceName() {
            throw unsupported();
        }

        @Override
        public String getSourcePath() {
            throw unsupported();
        }

        @Override
        public RelativePath getRelativeSourcePath() {
            throw unsupported();
        }

        @Override
        public ContentFilterable filter(Map<String, ?> properties, Class<? extends FilterReader> filterType) {
            throw unsupported();
        }

        @Override
        public ContentFilterable filter(Class<? extends FilterReader> filterType) {
            throw unsupported();
        }

        @Override
        public ContentFilterable filter(Closure closure) {
            throw unsupported();
        }

        @Override
        public ContentFilterable filter(Transformer<String, String> transformer) {
            throw unsupported();
        }

        @Override
        public ContentFilterable expand(Map<String, ?> properties) {
            throw unsupported();
        }

        @Override
        public ContentFilterable expand(Map<String, ?> properties, Action<? super ExpandDetails> action) {
            throw unsupported();
        }

        @Override
        public CopySpecResolver getSpecResolver() {
            return specResolver;
        }
    }
}
