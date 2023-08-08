/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.file.impl;

import com.google.common.annotations.VisibleForTesting;
import org.gradle.internal.file.Deleter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

@SuppressWarnings("Since15")
public class DefaultDeleter implements Deleter {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultDeleter.class);

    private final LongSupplier timeProvider;
    private final Predicate<? super File> isSymlink;
    private final boolean runGcOnFailedDelete;

    private static final int DELETE_RETRY_SLEEP_MILLIS = 10;

    @VisibleForTesting
    static final int MAX_REPORTED_PATHS = 16;

    @VisibleForTesting
    static final int EMPTY_DIRECTORY_DELETION_ATTEMPTS = 10;

    @VisibleForTesting
    static final String HELP_FAILED_DELETE_CHILDREN = "Failed to delete some children. This might happen because a process has files open or has its working directory set in the target directory.";
    @VisibleForTesting
    static final String HELP_NEW_CHILDREN = "New files were found. This might happen because a process is still writing to the target directory.";

    public DefaultDeleter(LongSupplier timeProvider, Predicate<? super File> isSymlink, boolean runGcOnFailedDelete) {
        this.timeProvider = timeProvider;
        this.isSymlink = isSymlink;
        this.runGcOnFailedDelete = runGcOnFailedDelete;
    }

    @Override
    public boolean deleteRecursively(File target) throws IOException {
        return deleteRecursively(target, false);
    }

    @Override
    public boolean deleteRecursively(File root, boolean followSymlinks) throws IOException {
        if (root.exists()) {
            return deleteRecursively(root, followSymlinks
                ? Handling.FOLLOW_SYMLINKED_DIRECTORIES
                : Handling.DO_NOT_FOLLOW_SYMLINKS);
        } else {
            return false;
        }
    }

    @Override
    public boolean ensureEmptyDirectory(File target) throws IOException {
        return ensureEmptyDirectory(target, false);
    }

    @Override
    public boolean ensureEmptyDirectory(File root, boolean followSymlinks) throws IOException {
        if (root.exists()) {
            if (root.isDirectory()
                && (followSymlinks || !isSymlink.test(root))) {
                return deleteRecursively(root, followSymlinks
                    ? Handling.KEEP_AND_FOLLOW_SYMLINKED_DIRECTORIES
                    : Handling.KEEP_AND_DO_NOT_FOLLOW_CHILD_SYMLINKS);
            }
            if (!tryHardToDelete(root)) {
                throw new IOException("Couldn't delete " + root);
            }
        }
        if (!root.mkdirs()) {
            throw new IOException("Couldn't create directory: " + root);
        }
        return true;
    }

    @Override
    public boolean delete(File target) throws IOException {
        if (!target.exists()) {
            return false;
        }
        if (!tryHardToDelete(target)) {
            throw new IOException("Couldn't delete " + target);
        }
        return true;
    }

    private boolean deleteRecursively(File root, Handling handling) throws IOException {
        LOGGER.debug("Deleting {}", root);
        long startTime = timeProvider.getAsLong();
        Collection<String> failedPaths = new ArrayList<String>();
        boolean attemptedToRemoveAnything = deleteRecursively(startTime, root, root, handling, failedPaths);
        if (!failedPaths.isEmpty()) {
            throwWithHelpMessage(startTime, root, handling, failedPaths, false);
        }
        return attemptedToRemoveAnything;
    }

    private boolean deleteRecursively(long startTime, File baseDir, File file, Handling handling, Collection<String> failedPaths) throws IOException {

        if (shouldRemoveContentsOf(file, handling)) {
            File[] contents = file.listFiles();

            // Something else may have removed it
            if (contents == null) {
                return false;
            }

            boolean attemptedToDeleteAnything = false;
            for (File item : contents) {
                deleteRecursively(startTime, baseDir, item, handling.getDescendantHandling(), failedPaths);
                attemptedToDeleteAnything = true;
            }

            if (handling.shouldKeepEntry()) {
                return attemptedToDeleteAnything;
            }
        }

        if (!tryHardToDelete(file)) {
            failedPaths.add(file.getAbsolutePath());

            // Fail fast
            if (failedPaths.size() == MAX_REPORTED_PATHS) {
                throwWithHelpMessage(startTime, baseDir, handling, failedPaths, true);
            }
        }
        return true;
    }

    private boolean shouldRemoveContentsOf(File file, Handling handling) {
        return file.isDirectory() && (handling.shouldFollowLinkedDirectory() || !isSymlink.test(file));
    }

    protected boolean deleteFile(File file) {
        try {
            return Files.deleteIfExists(file.toPath()) && !file.exists();
        } catch (IOException e) {
            return false;
        }
    }

    private boolean tryHardToDelete(File file) {
        if (deleteFile(file)) {
            return true;
        }

        // This is copied from Ant (see org.apache.tools.ant.util.FileUtils.tryHardToDelete).
        // This was introduced in Ant by https://github.com/apache/ant/commit/ececc5c3e332b97f962b94a475408606433ee0e6
        // This is a workaround for https://bz.apache.org/bugzilla/show_bug.cgi?id=45786
        if (runGcOnFailedDelete) {
            System.gc();
        }

        int failedAttempts = 1;
        while (failedAttempts < EMPTY_DIRECTORY_DELETION_ATTEMPTS) {
            try {
                Thread.sleep(DELETE_RETRY_SLEEP_MILLIS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            if (deleteFile(file)) {
                return true;
            } else {
                failedAttempts++;
            }
        }
        return false;
    }

    private void throwWithHelpMessage(long startTime, File file, Handling handling, Collection<String> failedPaths, boolean more) throws IOException {
        throw new IOException(buildHelpMessageForFailedDelete(startTime, file, handling, failedPaths, more));
    }

    private String buildHelpMessageForFailedDelete(long startTime, File file, Handling handling, Collection<String> failedPaths, boolean more) {

        StringBuilder help = new StringBuilder("Unable to delete ");
        if (isSymlink.test(file)) {
            help.append("symlink to ");
        }
        if (file.isDirectory()) {
            help.append("directory ");
        } else {
            help.append("file ");
        }
        help.append('\'').append(file).append('\'');

        if (shouldRemoveContentsOf(file, handling)) {
            String absolutePath = file.getAbsolutePath();
            failedPaths.remove(absolutePath);
            if (!failedPaths.isEmpty()) {
                help.append("\n  ").append(HELP_FAILED_DELETE_CHILDREN);
                for (String failed : failedPaths) {
                    help.append("\n  - ").append(failed);
                }
                if (more) {
                    help.append("\n  - and more ...");
                }
            }

            Collection<String> newPaths = listNewPaths(startTime, file, failedPaths);
            if (!newPaths.isEmpty()) {
                help.append("\n  ").append(HELP_NEW_CHILDREN);
                for (String newPath : newPaths) {
                    help.append("\n  - ").append(newPath);
                }
                if (newPaths.size() == MAX_REPORTED_PATHS) {
                    help.append("\n  - and more ...");
                }
            }
        }
        return help.toString();
    }

    private static Collection<String> listNewPaths(long startTime, File directory, Collection<String> failedPaths) {
        List<String> paths = new ArrayList<String>(MAX_REPORTED_PATHS);
        Deque<File> stack = new ArrayDeque<File>();
        stack.push(directory);
        while (!stack.isEmpty() && paths.size() < MAX_REPORTED_PATHS) {
            File current = stack.pop();
            String absolutePath = current.getAbsolutePath();
            if (!current.equals(directory) && !failedPaths.contains(absolutePath) && current.lastModified() >= startTime) {
                paths.add(absolutePath);
            }
            if (current.isDirectory()) {
                File[] children = current.listFiles();
                if (children != null) {
                    for (File child : children) {
                        stack.push(child);
                    }
                }
            }
        }
        return paths;
    }

    private enum Handling {
        KEEP_AND_FOLLOW_SYMLINKED_DIRECTORIES(true, true) {
            @Override
            public Handling getDescendantHandling() {
                return FOLLOW_SYMLINKED_DIRECTORIES;
            }
        },
        KEEP_AND_DO_NOT_FOLLOW_CHILD_SYMLINKS(true, true) {
            @Override
            public Handling getDescendantHandling() {
                return DO_NOT_FOLLOW_SYMLINKS;
            }
        },
        FOLLOW_SYMLINKED_DIRECTORIES(false, true) {
            @Override
            public Handling getDescendantHandling() {
                return FOLLOW_SYMLINKED_DIRECTORIES;
            }
        },
        DO_NOT_FOLLOW_SYMLINKS(false, false) {
            @Override
            public Handling getDescendantHandling() {
                return DO_NOT_FOLLOW_SYMLINKS;
            }
        };

        private final boolean shouldKeepEntry;
        private final boolean shouldFollowLinkedDirectory;

        Handling(boolean shouldKeepEntry, boolean shouldFollowLinkedDirectory) {
            this.shouldKeepEntry = shouldKeepEntry;
            this.shouldFollowLinkedDirectory = shouldFollowLinkedDirectory;
        }

        /**
         * Whether or not the entry with this handling should be kept or deleted.
         */
        public boolean shouldKeepEntry() {
            return shouldKeepEntry;
        }

        /**
         * Whether or not this entry should be followed if it is a symlinked directory.
         */
        public boolean shouldFollowLinkedDirectory() {
            return shouldFollowLinkedDirectory;
        }

        /**
         * How to handle descendants.
         */
        abstract public Handling getDescendantHandling();
    }
}
