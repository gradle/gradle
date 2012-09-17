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

import org.apache.commons.io.FileUtils;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.copy.DeleteActionImpl;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.UncheckedException;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * File store that accepts the target path as the key for the entry.
 *
 * There is always at most one entry for a given key for this file store. If an entry already exists at the given path, it will be overwritten. Paths can contain directory components, which will be
 * created on demand.
 *
 * This file store also provides searching via relative ant path patterns.
 */
public class PathKeyFileStore implements FileStore<String>, FileStoreSearcher<String> {

    public static final String MARKER_FILE_SUFFIX = ".fslck";
    private final Random generator = new Random(System.currentTimeMillis());

    private File baseDir;
    private final DeleteActionImpl deleteAction = new DeleteActionImpl(new IdentityFileResolver());

    public PathKeyFileStore(File baseDir) {
        this.baseDir = baseDir;
    }

    public File getBaseDir() {
        return baseDir;
    }

    public FileStoreEntry move(String path, File source) {
        return saveIntoFileStore(source, getFile(path), true);
    }

    public FileStoreEntry copy(String path, File source) {
        return saveIntoFileStore(source, getFile(path), false);
    }

    private File getFile(String path) {
        return new File(baseDir, path);
    }

    public File getTempFile() {
        long tempLong = generator.nextLong();
        tempLong = tempLong < 0 ? -tempLong : tempLong;
        return new File(baseDir, "temp/" + tempLong);
    }

    public void moveFilestore(File destination) {
        if (baseDir.exists()) {
            try {
                FileUtils.moveDirectory(baseDir, destination);
            } catch (IOException e) {
                throw new UncheckedException(e);
            }
        }
        baseDir = destination;
    }

    public FileStoreEntry add(String key, Action<File> addAction) {
        File destination = getFile(key);
        destination.getParentFile().mkdirs(); //revisit this. found issue when running PathKeyFileStoreTest#can add to filestore
        File markerFile = getMarkerFile(destination);
        try {
            markerFile.createNewFile();
            destination.delete();
            addAction.execute(destination);
        } catch (Exception exception) {
            destination.delete();
            throw new GradleException(String.format("Failed to add file with key '%s' into filestore at '%s' ", key, getBaseDir().getAbsolutePath()), exception);
        } finally {
            markerFile.delete();
        }
        return entryAt(destination);
    }

    protected FileStoreEntry saveIntoFileStore(File source, File destination, boolean isMove) {
        if (!source.exists()) {
            throw new GradleException(String.format("Cannot copy '%s' into filestore @ '%s' as it does not exist", source, destination));
        }
        File parentDir = destination.getParentFile();
        if (!parentDir.mkdirs() && !parentDir.exists()) {
            throw new GradleException(String.format("Unable to create filestore directory %s", parentDir));
        }

        File markerFile = getMarkerFile(destination);
        try {
            markerFile.createNewFile();
            deleteAction.delete(destination);
            if (isMove) {
                FileUtils.moveFile(source, destination);
            } else {
                FileUtils.copyFile(source, destination);
            }
            deleteAction.delete(markerFile);
        } catch (Exception exception) {
            deleteAction.delete(destination);
            deleteAction.delete(markerFile);
            String verb = isMove ? "move" : "copy";
            throw new GradleException(String.format("Failed to %s file '%s' into filestore at '%s' ", verb, source, destination), exception);
        }

        return entryAt(destination);
    }

    private File getMarkerFile(File destination) {
        final File destinationParentFile = destination.getParentFile();
        final String markerFileName = destination.getName() + MARKER_FILE_SUFFIX;
        return new File(destinationParentFile, markerFileName);
    }

    public Set<? extends FileStoreEntry> search(String pattern) {
        final Set<FileStoreEntry> entries = new HashSet<FileStoreEntry>();
        //TODO SF below may emit an awkward INFO-level message that the base dir does not exist
        //('file or directory xxx not found')
        //Consider bailing out early if the baseDir does not exist or reducing the log level
        findFiles(pattern).visit(new EmptyFileVisitor() {
            public void visitFile(FileVisitDetails fileDetails) {
                final File fileStoreFile = fileDetails.getFile();
                if (!fileStoreFile.getName().endsWith(PathKeyFileStore.MARKER_FILE_SUFFIX)) {
                    final File markerFile = getMarkerFile(fileStoreFile);
                    if (!markerFile.exists()) {
                        entries.add(entryAt(fileStoreFile));
                    }
                }

            }
        });

        return entries;
    }

    private DirectoryFileTree findFiles(String pattern) {
        DirectoryFileTree fileTree = new DirectoryFileTree(baseDir);
        PatternFilterable patternSet = new PatternSet();
        patternSet.include(pattern);
        return fileTree.filter(patternSet);
    }

    protected FileStoreEntry entryAt(File file) {
        return entryAt(GFileUtils.relativePath(baseDir, file));
    }

    protected FileStoreEntry entryAt(final String path) {
        return new AbstractFileStoreEntry() {
            public File getFile() {
                return new File(baseDir, path);
            }
        };
    }

    public FileStoreEntry get(String key) {
        final File file = getFile(key);
        final File markerFile = getMarkerFile(file);
        if (markerFile.exists()) {
            deleteAction.delete(file);
            deleteAction.delete(markerFile);
        }
        if (file.exists()) {
            return entryAt(file);
        } else {
            return null;
        }
    }
}
