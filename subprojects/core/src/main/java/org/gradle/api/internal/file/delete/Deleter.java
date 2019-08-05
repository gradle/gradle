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
package org.gradle.api.internal.file.delete;

import org.gradle.api.Action;
import org.gradle.api.file.DeleteSpec;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class Deleter {
    private static final Logger LOGGER = LoggerFactory.getLogger(Deleter.class);

    private final FileResolver fileResolver;
    private final FileSystem fileSystem;
    private final Supplier<Long> timeProvider;
    private final boolean runGcOnFailedDelete;

    private static final int DELETE_RETRY_SLEEP_MILLIS = 10;

    static final int MAX_REPORTED_PATHS = 16;

    static final String HELP_FAILED_DELETE_CHILDREN = "Failed to delete some children. This might happen because a process has files open or has its working directory set in the target directory.";
    static final String HELP_NEW_CHILDREN = "New files were found. This might happen because a process is still writing to the target directory.";

    public Deleter(FileResolver fileResolver, FileSystem fileSystem, Supplier<Long> timeProvider, boolean runGcOnFailedDelete) {
        this.fileResolver = fileResolver;
        this.fileSystem = fileSystem;
        this.timeProvider = timeProvider;
        this.runGcOnFailedDelete = runGcOnFailedDelete;
    }

    public boolean delete(Action<? super DeleteSpec> action) {
        DeleteSpecInternal deleteSpec = new DefaultDeleteSpec();
        action.execute(deleteSpec);
        FileCollectionInternal roots = fileResolver.resolveFiles(deleteSpec.getPaths());
        return deleteInternal(
            roots,
            file -> file.isDirectory() && (deleteSpec.isFollowSymlinks() || !fileSystem.isSymlink(file))
        );
    }

    private boolean deleteInternal(Iterable<File> roots, Predicate<? super File> filter) {
        boolean didWork = false;
        for (File root : roots) {
            if (!root.exists()) {
                continue;
            }
            LOGGER.debug("Deleting {}", root);
            didWork = true;
            deleteRoot(root, filter);
        }
        return didWork;
    }

    private void deleteRoot(File file, Predicate<? super File> filter) {
        long startTime = timeProvider.get();
        Collection<String> failedPaths = new ArrayList<>();
        deleteRecursively(startTime, file, file, filter, failedPaths);
        if (!failedPaths.isEmpty()) {
            throwWithHelpMessage(startTime, file, filter, failedPaths, false);
        }
    }

    private void deleteRecursively(long startTime, File baseDir, File file, Predicate<? super File> filter, Collection<String> failedPaths) {

        if (filter.test(file)) {
            File[] contents = file.listFiles();

            // Something else may have removed it
            if (contents == null) {
                return;
            }

            for (File item : contents) {
                deleteRecursively(startTime, baseDir, item, filter, failedPaths);
            }
        }

        if (!deleteFile(file)) {
            handleFailedDelete(file, failedPaths);

            // Fail fast
            if (failedPaths.size() == MAX_REPORTED_PATHS) {
                throwWithHelpMessage(startTime, baseDir, filter, failedPaths, true);
            }
        }
    }

    protected boolean deleteFile(File file) {
        return file.delete() && !file.exists();
    }

    private void handleFailedDelete(File file, Collection<String> failedPaths) {
        // This is copied from Ant (see org.apache.tools.ant.util.FileUtils.tryHardToDelete).
        // It mentions that there is a bug in the Windows JDK implementations that this is a valid
        // workaround for. I've been unable to find a definitive reference to this bug.
        // The thinking is that if this is good enough for Ant, it's good enough for us.
        if (runGcOnFailedDelete) {
            System.gc();
        }
        try {
            Thread.sleep(DELETE_RETRY_SLEEP_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }

        if (!deleteFile(file)) {
            failedPaths.add(file.getAbsolutePath());
        }
    }

    private void throwWithHelpMessage(long startTime, File file, Predicate<? super File> filter, Collection<String> failedPaths, boolean more) {
        throw new RuntimeException(buildHelpMessageForFailedDelete(startTime, file, filter, failedPaths, more));
    }

    private String buildHelpMessageForFailedDelete(long startTime, File file, Predicate<? super File> filter, Collection<String> failedPaths, boolean more) {

        StringBuilder help = new StringBuilder("Unable to delete ");
        if (Files.isSymbolicLink(file.toPath())) {
            help.append("symlink to ");
        }
        if (file.isDirectory()) {
            help.append("directory ");
        } else {
            help.append("file ");
        }
        help.append('\'').append(file).append('\'');

        if (filter.test(file)) {
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
        List<String> paths = new ArrayList<>(MAX_REPORTED_PATHS);
        Deque<File> stack = new ArrayDeque<>();
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
}
