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

package org.gradle.internal.resource.local;

import org.gradle.api.Action;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.internal.file.collections.MinimalFileTree;
import org.gradle.api.internal.file.collections.SingleIncludePatternFileTree;
import org.gradle.api.internal.file.delete.Deleter;
import org.gradle.internal.UncheckedException;
import org.gradle.util.GFileUtils;
import org.gradle.util.RelativePathUtil;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.gradle.internal.FileUtils.hasExtension;

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
public class DefaultPathKeyFileStore implements PathKeyFileStore {

    /*
        When writing a file into the filestore a marker file with this suffix is written alongside,
        then removed after the write. This is used to detect partially written files (due to a serious crash)
        and to silently clean them.
     */
    public static final String IN_PROGRESS_MARKER_FILE_SUFFIX = ".fslck";

    private File baseDir;
    private final Deleter deleter;

    public DefaultPathKeyFileStore(File baseDir) {
        this.baseDir = baseDir;
        IdentityFileResolver fileResolver = new IdentityFileResolver();
        deleter = new Deleter(fileResolver, fileResolver.getFileSystem());
    }

    protected File getBaseDir() {
        return baseDir;
    }

    @Override
    public LocallyAvailableResource move(String path, File source) {
        return saveIntoFileStore(source, getFile(path), true);
    }

    @Override
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
            deleter.delete(file);
            deleter.delete(markerFile);
        }
        return file;
    }

    @Override
    public void moveFilestore(File destination) {
        if (baseDir.exists()) {
            GFileUtils.moveDirectory(baseDir, destination);
        }
        baseDir = destination;
    }

    @Override
    public LocallyAvailableResource add(final String path, final Action<File> addAction) {
        try {
            return doAdd(getFile(path), new Action<File>() {
                @Override
                public void execute(File file) {
                    try {
                        addAction.execute(file);
                    } catch (Throwable e) {
                        throw new FileStoreAddActionException(String.format("Failed to add into filestore '%s' at '%s' ", getBaseDir().getAbsolutePath(), path), e);
                    }
                }
            });
        } catch (FileStoreAddActionException e) {
            throw e;
        } catch (Throwable e) {
            throw new FileStoreException(String.format("Failed to add into filestore '%s' at '%s' ", getBaseDir().getAbsolutePath(), path), e);
        }
    }

    protected LocallyAvailableResource saveIntoFileStore(final File source, final File destination, final boolean isMove) {
        String verb = isMove ? "move" : "copy";

        if (!source.exists()) {
            throw new FileStoreException(String.format("Cannot %s '%s' into filestore @ '%s' as it does not exist", verb, source, destination));
        }

        try {
            return doAdd(destination, new Action<File>() {
                public void execute(File file) {
                    if (isMove) {
                        if (source.isDirectory()) {
                            GFileUtils.moveDirectory(source, file);
                        } else {
                            GFileUtils.moveFile(source, file);
                        }
                    } else {
                        if (source.isDirectory()) {
                            GFileUtils.copyDirectory(source, file);
                        } else {
                            GFileUtils.copyFile(source, file);
                        }
                    }
                }
            });
        } catch (Throwable e) {
            throw new FileStoreException(String.format("Failed to %s file '%s' into filestore at '%s' ", verb, source, destination), e);
        }
    }

    protected LocallyAvailableResource doAdd(File destination, Action<File> action) {
        GFileUtils.parentMkdirs(destination);
        File inProgressMarkerFile = getInProgressMarkerFile(destination);
        GFileUtils.touch(inProgressMarkerFile);
        try {
            deleter.delete(destination);
            action.execute(destination);
        } catch (Throwable t) {
            deleter.delete(destination);
            throw UncheckedException.throwAsUncheckedException(t);
        } finally {
            deleter.delete(inProgressMarkerFile);
        }
        return entryAt(destination);
    }

    @Override
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
        return hasExtension(file, IN_PROGRESS_MARKER_FILE_SUFFIX);
    }

    private boolean isInProgressFile(File file) {
        return getInProgressMarkerFile(file).exists();
    }

    private MinimalFileTree findFiles(String pattern) {
        return new SingleIncludePatternFileTree(baseDir, pattern);
    }

    protected LocallyAvailableResource entryAt(File file) {
        return entryAt(RelativePathUtil.relativePath(baseDir, file));
    }

    protected LocallyAvailableResource entryAt(final String path) {
        return new RelativeToBaseDirResource(path);
    }

    @Override
    public LocallyAvailableResource get(String key) {
        final File file = getFileWhileCleaningInProgress(key);
        if (file.exists()) {
            return new RelativeToBaseDirResource(key);
        } else {
            return null;
        }
    }

    private class RelativeToBaseDirResource extends AbstractLocallyAvailableResource {
        private final String path;

        public RelativeToBaseDirResource(String path) {
            this.path = path;
        }

        public File getFile() {
            // Calculated on demand to deal with moves
            return new File(baseDir, path);
        }
    }
}
