/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.filestore;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.file.DeleteAction;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.internal.file.collections.MinimalFileTree;
import org.gradle.api.internal.file.collections.SingleIncludePatternFileTree;
import org.gradle.api.internal.file.copy.DeleteActionImpl;
import org.gradle.internal.filestore.FileStore;
import org.gradle.internal.filestore.FileStoreSearcher;
import org.gradle.internal.resource.local.AbstractLocallyAvailableResource;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * File store that accepts the target path as the key for the entry.
 *
 * This implementation is explicitly NOT THREAD SAFE. Concurrent access must be organised externally.
 * <p>
 * There is always at most one entry for a given key for this file store. If an entry already exists at the given path, it will be overwritten.
 * Paths can contain directory components, which will be created on demand.
 * <p>
 * This file store is self repairing in so far that any files partially written before a fatal error will be ignored and
 * removed at a later time.
 * <p>
 * This file store also provides searching via relative ant path patterns.
 */
public class PathKeyFileStore implements FileStore<String>, FileStoreSearcher<String> {

    /*
        When writing a file into the filestore a marker file with this suffix is written alongside,
        then removed after the write. This is used to detect partially written files (due to a serious crash)
        and to silently clean them.
     */
    public static final String IN_PROGRESS_MARKER_FILE_SUFFIX = ".fslck";

    private File baseDir;
    private final DeleteAction deleteAction = new DeleteActionImpl(new IdentityFileResolver());

    public PathKeyFileStore(File baseDir) {
        this.baseDir = baseDir;
    }

    protected File getBaseDir() {
        return baseDir;
    }

    public LocallyAvailableResource move(String path, File source) {
        return saveIntoFileStore(source, getFile(path), true);
    }

    public LocallyAvailableResource copy(String path, File source) {
        return saveIntoFileStore(source, getFile(path), false);
    }

    private File getFile(String path) {
        return new File(baseDir, path);
    }

    private File getFileWhileCleaningInProgress(String path) {
        File file = getFile(path);
        File markerFile = getInProgressMarkerFile(file);
        if (markerFile.exists()) {
            deleteAction.delete(file);
            deleteAction.delete(markerFile);
        }
        return file;
    }

    public void moveFilestore(File destination) {
        if (baseDir.exists()) {
            GFileUtils.moveDirectory(baseDir, destination);
        }
        baseDir = destination;
    }

    public LocallyAvailableResource add(String path, Action<File> addAction) {
        String error = String.format("Failed to add into filestore '%s' at '%s' ", getBaseDir().getAbsolutePath(), path);
        return doAdd(getFile(path), error, addAction);
    }

    protected LocallyAvailableResource saveIntoFileStore(final File source, final File destination, final boolean isMove) {
        String verb = isMove ? "move" : "copy";

        if (!source.exists()) {
            throw new GradleException(String.format("Cannot %s '%s' into filestore @ '%s' as it does not exist", verb, source, destination));
        }

        String error = String.format("Failed to %s file '%s' into filestore at '%s' ", verb, source, destination);

        return doAdd(destination, error, new Action<File>() {
            public void execute(File file) {
                if (isMove) {
                    GFileUtils.moveFile(source, destination);
                } else {
                    GFileUtils.copyFile(source, destination);
                }
            }
        });
    }

    protected LocallyAvailableResource doAdd(File destination, String failureDescription, Action<File> action) {
        try {
            GFileUtils.parentMkdirs(destination);
            File inProgressMarkerFile = getInProgressMarkerFile(destination);
            GFileUtils.touch(inProgressMarkerFile);
            try {
                deleteAction.delete(destination);
                action.execute(destination);
            } catch (Throwable t) {
                deleteAction.delete(destination);
                throw t;
            } finally {
                deleteAction.delete(inProgressMarkerFile);
            }
        } catch (Throwable t) {
            throw new GradleException(failureDescription, t);
        }
        return entryAt(destination);
    }

    public Set<? extends LocallyAvailableResource> search(String pattern) {
        if (!getBaseDir().exists()) {
            return Collections.emptySet();
        }

        final Set<LocallyAvailableResource> entries = new HashSet<LocallyAvailableResource>();
        findFiles(pattern).visit(new EmptyFileVisitor() {
            public void visitFile(FileVisitDetails fileDetails) {
                final File file = fileDetails.getFile();
                // We cannot clean in progress markers, or in progress files here because
                // the file system visitor stuff can't handle the file system mutating while visiting
                if (!isInProgressMarkerFile(file) && !isInProgressFile(file)) {
                    entries.add(entryAt(file));
                }
            }
        });

        return entries;
    }

    private File getInProgressMarkerFile(File file) {
        return new File(file.getParent(), file.getName() + IN_PROGRESS_MARKER_FILE_SUFFIX);
    }

    private boolean isInProgressMarkerFile(File file) {
        return file.getName().endsWith(IN_PROGRESS_MARKER_FILE_SUFFIX);
    }

    private boolean isInProgressFile(File file) {
        return getInProgressMarkerFile(file).exists();
    }

    private MinimalFileTree findFiles(String pattern) {
        return new SingleIncludePatternFileTree(baseDir, pattern);
    }

    protected LocallyAvailableResource entryAt(File file) {
        return entryAt(GFileUtils.relativePath(baseDir, file));
    }

    protected LocallyAvailableResource entryAt(final String path) {
        return new AbstractLocallyAvailableResource() {
            public File getFile() {
                return new File(baseDir, path);
            }
        };
    }

    public LocallyAvailableResource get(String key) {
        final File file = getFileWhileCleaningInProgress(key);
        if (file.exists()) {
            return entryAt(file);
        } else {
            return null;
        }
    }
}
