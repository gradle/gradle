/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.filewatch.jdk7;

import org.gradle.api.file.DirectoryTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.specs.Spec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

class DirTreeWatchRegistry extends WatchRegistry<DirectoryTree> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DirTreeWatchRegistry.class);
    protected final Map<Path, DirectoryTree> pathToDirectoryTree;

    public static DirTreeWatchRegistry create(WatchStrategy watchStrategy) {
        if(watchStrategy instanceof FileTreeWatchStrategy) {
            return new ExtendedDirTreeWatchRegistry((FileTreeWatchStrategy)watchStrategy);
        } else {
            return new DirTreeWatchRegistry(watchStrategy);
        }
    }

    DirTreeWatchRegistry(WatchStrategy watchStrategy) {
        super(watchStrategy);
        this.pathToDirectoryTree = new HashMap<Path, DirectoryTree>();
    }

    public void register(Iterable<DirectoryTree> trees) throws IOException {
        for(DirectoryTree tree : trees) {
            registerSubDir(tree, dirToPath(tree.getDir()));
        }
    }

    public void handleChange(ChangeDetails changeDetails, FileWatcherChangesNotifier changesNotifier) {
        DirectoryTree watchedTree = pathToDirectoryTree.get(changeDetails.getWatchedPath());
        if(watchedTree != null) {
            FileTreeElement fileTreeElement = toFileTreeElement(changeDetails, watchedTree);
            if(!isExcluded(watchedTree, fileTreeElement)) {
                boolean isDirectory = isDirectory(changeDetails.getFullItemPath());
                if (isDirectory && changeDetails.getChangeType() == ChangeDetails.ChangeType.CREATE) {
                    handleNewDirectory(changeDetails, changesNotifier, watchedTree);
                }
                if(!isDirectory && isIncluded(watchedTree, fileTreeElement)) {
                    handleFileChange(changeDetails, changesNotifier, watchedTree);
                }
            }
        }
    }

    // we should handle new directories even when they aren't explicitly included since "includes" pattern is for files
    protected void handleNewDirectory(ChangeDetails changeDetails, FileWatcherChangesNotifier changesNotifier, DirectoryTree watchedTree) {
        try {
            boolean containsValidFiles = registerSubDir(watchedTree, changeDetails.getFullItemPath());
            if(containsValidFiles) {
                // newly created directory with files will trigger a change. empty directory won't trigger a change
                changesNotifier.addPendingChange();
            }
        } catch (IOException e) {
            // ignore
            LOGGER.warn("IOException in registering sub tree " + changeDetails.getFullItemPath().toString(), e);
            // trigger change in case of IOException
            changesNotifier.addPendingChange();
        }
    }

    protected void handleFileChange(ChangeDetails changeDetails, FileWatcherChangesNotifier changesNotifier, DirectoryTree watchedTree) {
        changesNotifier.addPendingChange();
    }

    private boolean isIncluded(DirectoryTree watchedTree, FileTreeElement fileTreeElement) {
        return watchedTree.getPatterns().getAsIncludeSpec().isSatisfiedBy(fileTreeElement);
    }

    private boolean isExcluded(DirectoryTree watchedTree, FileTreeElement fileTreeElement) {
        return watchedTree.getPatterns().getAsExcludeSpec().isSatisfiedBy(fileTreeElement);
    }

    private FileTreeElement toFileTreeElement(ChangeDetails changeDetails, DirectoryTree watchedTree) {
        return toFileTreeElement(changeDetails.getFullItemPath(), dirToPath(watchedTree.getDir()).relativize(changeDetails.getFullItemPath()));
    }

    /**
     * register directory and it's sub directories for watching
     * also scans the directory for valid files when it's doing the registration so that we don't
     * have to separately scan the directory structure for files.
     *
     * @return true when the directory contains a valid file (satisfied by DirectoryTree's PatternSet)
     */
    protected boolean registerSubDir(final DirectoryTree tree, Path subRootPath) throws IOException {
        final Spec<FileTreeElement> excludeSpec = tree.getPatterns().getAsExcludeSpec();
        final Spec<FileTreeElement> fileSpec = tree.getPatterns().getAsSpec();
        final Path rootPath = dirToPath(tree.getDir());
        final AtomicBoolean containsValidFiles = new AtomicBoolean(false);
        walkFileTree(subRootPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                FileTreeElement fileTreeElement = toFileTreeElement(dir, rootPath.relativize(dir));
                if (!excludeSpec.isSatisfiedBy(fileTreeElement)) {
                    watchDirectory(dir);
                    pathToDirectoryTree.put(dir, tree);
                    return FileVisitResult.CONTINUE;
                } else {
                    return FileVisitResult.SKIP_SUBTREE;
                }
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                FileTreeElement fileTreeElement = toFileTreeElement(file, rootPath.relativize(file));
                if (fileSpec.isSatisfiedBy(fileTreeElement)) {
                    containsValidFiles.set(true);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return containsValidFiles.get();
    }

    // subclass hook for unit tests
    protected boolean isDirectory(Path path) {
        return Files.isDirectory(path);
    }

    // subclass hook for unit tests
    protected Path walkFileTree(Path start, FileVisitor<? super Path> visitor) throws IOException {
        return Files.walkFileTree(start, visitor);
    }
}
