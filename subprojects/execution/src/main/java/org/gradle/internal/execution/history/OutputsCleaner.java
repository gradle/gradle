/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.execution.history;

import com.google.common.collect.Ordering;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.FileType;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshot;
import org.gradle.internal.snapshot.SnapshotUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.Predicate;

/**
 * Cleans outputs, removing empty directories.
 *
 * This class should be used when cleaning output directories when only a subset of the files can be deleted.
 * After cleaning up a few output directories, the method {@link #cleanupDirectories()} cleans the directories which became empty.
 *
 * IMPORTANT: This class is stateful, so it can't be used as a service.
 */
public class OutputsCleaner {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutputsCleaner.class);

    private final Deleter deleter;
    private final PriorityQueue<File> directoriesToDelete;
    private final Predicate<File> fileSafeToDelete;
    private final Predicate<File> dirSafeToDelete;

    private boolean didWork;

    public OutputsCleaner(Deleter deleter, Predicate<File> fileSafeToDelete, Predicate<File> dirSafeToDelete) {
        this.deleter = deleter;
        this.fileSafeToDelete = fileSafeToDelete;
        this.dirSafeToDelete = dirSafeToDelete;
        this.directoriesToDelete = new PriorityQueue<>(10, Ordering.natural().reverse());
    }

    /**
     * Cleans up all locations {@link FileSystemSnapshot}, possible spanning multiple root directories.
     *
     * After cleaning up the files, the empty directories are removed as well.
     */
    public void cleanupOutputs(FileSystemSnapshot snapshot) throws IOException {
        // TODO We could make this faster by visiting the snapshot
        for (Map.Entry<String, FileSystemLocationSnapshot> entry : SnapshotUtil.index(snapshot).entrySet()) {
            cleanupOutput(new File(entry.getKey()), entry.getValue().getType());
        }
        cleanupDirectories();
    }

    /**
     * Cleans up a single location.
     *
     * Does not clean up directories, yet, though remembers them for deletion.
     * You should call {@link #cleanupDirectories()} after you are finished with the calls this method.
     */
    public void cleanupOutput(File file, FileType fileType) throws IOException {
        switch (fileType) {
            case Directory:
                markDirForDeletion(file);
                break;
            case RegularFile:
                if (fileSafeToDelete.test(file)) {
                    if (file.exists()) {
                        LOGGER.debug("Deleting stale output file '{}'.", file);
                        deleter.delete(file);
                        didWork = true;
                    }
                    markParentDirForDeletion(file);
                }
                break;
            case Missing:
                // Ignore missing files
                break;
            default:
                throw new AssertionError("Unknown file type: " + fileType);
        }
    }

    /**
     * Whether some actual deletion happened.
     */
    public boolean getDidWork() {
        return didWork;
    }

    private void markParentDirForDeletion(File f) {
        markDirForDeletion(f.getParentFile());
    }

    private void markDirForDeletion(@Nullable File dir) {
        if (dir != null && dirSafeToDelete.test(dir)) {
            directoriesToDelete.add(dir);
        }
    }

    /**
     * Cleans up empty directories marked for deletion in {@link #cleanupOutput(File, FileType)}.
     */
    public void cleanupDirectories() throws IOException {
        while (true) {
            File directory = directoriesToDelete.poll();
            if (directory == null) {
                break;
            }
            if (isEmpty(directory)) {
                LOGGER.debug("Deleting stale empty output directory '{}'.", directory);
                Files.delete(directory.toPath());
                didWork = true;
                markParentDirForDeletion(directory);
            }
        }
    }

    private boolean isEmpty(File parentDir) {
        String[] children = parentDir.list();
        return children != null && children.length == 0;
    }
}
