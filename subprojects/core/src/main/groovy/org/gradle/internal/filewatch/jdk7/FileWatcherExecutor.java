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

import com.sun.nio.file.ExtendedWatchEventModifier;
import com.sun.nio.file.SensitivityWatchEventModifier;
import org.gradle.api.file.DirectoryTree;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.RelativePath;
import org.gradle.api.internal.file.DefaultFileTreeElement;
import org.gradle.api.specs.Spec;
import org.gradle.internal.filewatch.FileWatcher;
import org.gradle.internal.os.OperatingSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

/**
 * Java 7 {@link WatchService} based implementation for monitoring file system changes, used by {@link DefaultFileWatcher}
 */
class FileWatcherExecutor implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileWatcherExecutor.class);
    // http://stackoverflow.com/a/18362404
    // make watch sensitivity as 2 seconds on MacOSX, polls every 2 seconds for changes. Default is 10 seconds.
    static final WatchEvent.Modifier[] WATCH_MODIFIERS = new WatchEvent.Modifier[]{SensitivityWatchEventModifier.HIGH};
    static final WatchEvent.Kind[] WATCH_KINDS = new WatchEvent.Kind[]{StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY};
    static final int POLL_TIMEOUT_MILLIS = 250;
    private final AtomicBoolean runningFlag;
    private final Collection<DirectoryTree> directoryTrees;
    private final Collection<File> files;
    private final WatchEvent.Modifier[] watchModifiers;
    private Map<Path, Set<File>> individualFilesByParentPath;
    private Map<Path, DirectoryTree> pathToDirectoryTree;
    final CountDownLatch waitUntilWatching;
    private FileWatcherChangesNotifier changesNotifier;

    public FileWatcherExecutor(FileWatcher fileWatcher, AtomicBoolean runningFlag, Runnable callback, Collection<DirectoryTree> directoryTrees, Collection<File> files, CountDownLatch waitUntilWatching) {
        this.changesNotifier = createChangesNotifier(fileWatcher, callback);
        this.runningFlag = runningFlag;
        this.directoryTrees = directoryTrees;
        this.files = files;
        this.watchModifiers = createWatchModifiers();
        this.waitUntilWatching = waitUntilWatching;
    }

    protected FileWatcherChangesNotifier createChangesNotifier(FileWatcher fileWatcher, Runnable callback) {
        return new FileWatcherChangesNotifier(fileWatcher, callback);
    }

    private WatchEvent.Modifier[] createWatchModifiers() {
        if (supportsWatchingSubTree()) {
            WatchEvent.Modifier[] modifiers = Arrays.copyOf(WATCH_MODIFIERS, WATCH_MODIFIERS.length + 1);
            modifiers[modifiers.length - 1] = ExtendedWatchEventModifier.FILE_TREE;
            return modifiers;
        } else {
            return WATCH_MODIFIERS;
        }
    }

    protected boolean supportsWatchingSubTree() {
        return OperatingSystem.current().isWindows();
    }

    @Override
    public void run() {
        WatchService watchService = createWatchService();
        try {
            try {
                registerInputs(watchService);
            } catch (IOException e) {
                throw new RuntimeException("IOException in registering watch inputs", e);
            } finally {
                waitUntilWatching.countDown();
            }
            try {
                watchLoop(watchService);
            } catch (InterruptedException e) {
                // ignore
            }
        } finally {
            try {
                watchService.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    protected void watchLoop(WatchService watchService) throws InterruptedException {
        changesNotifier.reset();
        while (watchLoopRunning()) {
            WatchKey watchKey = watchService.poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            if (watchKey != null) {
                changesNotifier.eventReceived();
                handleWatchKey(watchService, watchKey);
            }
            changesNotifier.handlePendingChanges();
        }
    }

    protected void handleWatchKey(WatchService watchService, WatchKey watchKey) {
        Path watchedPath = (Path)watchKey.watchable();

        DirectoryTree watchedTree = pathToDirectoryTree.get(watchedPath);
        Set<File> individualFiles = individualFilesByParentPath.get(watchedPath);

        for (WatchEvent<?> event : watchKey.pollEvents()) {
            WatchEvent.Kind kind = event.kind();

            if (kind == OVERFLOW) {
                // overflow event occurs when some change event might have been lost
                // notify changes in that case
                changesNotifier.addPendingChange();
                continue;
            }

            if (kind.type() == Path.class) {
                WatchEvent<Path> ev = (WatchEvent<Path>) event;
                Path relativePath = ev.context();
                Path fullPath = watchedPath.resolve(relativePath);

                if(watchedTree != null) {
                    FileTreeElement fileTreeElement = toFileTreeElement(fullPath, dirToPath(watchedTree.getDir()).relativize(fullPath));
                    if(!watchedTree.getPatterns().getAsExcludeSpec().isSatisfiedBy(fileTreeElement)) {
                        boolean isDirectory = Files.isDirectory(fullPath, NOFOLLOW_LINKS);
                        if (kind == ENTRY_CREATE) {
                            if (isDirectory) {
                                boolean containsValidFiles = false;
                                if(!supportsWatchingSubTree()) {
                                    try {
                                        containsValidFiles = registerSubTree(watchService, fullPath, watchedTree);
                                    } catch (IOException e) {
                                        // ignore
                                        LOGGER.warn("IOException in registering sub tree " + fullPath.toString(), e);
                                    }
                                } else {
                                    try {
                                        containsValidFiles = doesTreeContainValidFiles(fullPath, watchedTree);
                                    } catch (IOException e) {
                                        // ignore
                                        LOGGER.warn("IOException in scanning sub tree for files " + fullPath.toString(), e);
                                    }
                                }
                                if(containsValidFiles) {
                                    // newly created directory with files will trigger a change. empty directory won't trigger a change
                                    changesNotifier.addPendingChange();
                                }
                            }
                        }
                        if(!isDirectory && watchedTree.getPatterns().getAsIncludeSpec().isSatisfiedBy(fileTreeElement)) {
                            changesNotifier.addPendingChange();
                        }
                    }
                } else if (individualFiles != null) {
                    File fullFile = fullPath.toFile().getAbsoluteFile();
                    if(individualFiles.contains(fullFile)) {
                        changesNotifier.addPendingChange();
                    }
                } else {
                    LOGGER.warn("WatchEvent received on unmapped path " + fullPath.toString());
                }
            }
        }

        watchKey.reset();
    }

    private RelativePath toRelativePath(File file, Path path) {
        return RelativePath.parse(!file.isDirectory(), path.toString());
    }

    private FileTreeElement toFileTreeElement(Path fullPath, Path relativePath) {
        File file = fullPath.toFile();
        return new CustomFileTreeElement(file, toRelativePath(file, relativePath));
    }


    protected boolean watchLoopRunning() {
        return runningFlag.get() && !Thread.currentThread().isInterrupted();
    }

    private void registerInputs(WatchService watchService) throws IOException {
        registerDirTreeInputs(watchService);
        registerIndividualFileInputs(watchService);
    }

    private void registerIndividualFileInputs(WatchService watchService) throws IOException {
        individualFilesByParentPath = new HashMap<Path, Set<File>>();
        for (File file : files) {
            Path parent = dirToPath(file.getParentFile());
            Set<File> children = individualFilesByParentPath.get(parent);
            if (children == null) {
                children = new LinkedHashSet<File>();
                individualFilesByParentPath.put(parent, children);
            }
            children.add(file.getAbsoluteFile());
        }
        for (Path parent : individualFilesByParentPath.keySet()) {
            registerSinglePathNoSubtree(watchService, parent);
        }
    }

    protected Path dirToPath(File dir) {
        return dir.getAbsoluteFile().toPath();
    }

    private void registerDirTreeInputs(WatchService watchService) throws IOException {
        pathToDirectoryTree = new HashMap<Path, DirectoryTree>();
        for (DirectoryTree tree : directoryTrees) {
            registerDirectoryTree(watchService, tree);
        }
    }

    private void registerDirectoryTree(final WatchService watchService, final DirectoryTree tree) throws IOException {
        final Path treePath = dirToPath(tree.getDir());
        if (supportsWatchingSubTree()) {
            registerSinglePath(watchService, treePath);
            pathToDirectoryTree.put(treePath, tree);
        } else {
            registerSubTree(watchService, treePath, tree);
        }
    }

    private boolean registerSubTree(final WatchService watchService, Path subRootPath, final DirectoryTree tree) throws IOException {
        final Spec<FileTreeElement> excludeSpec = tree.getPatterns().getAsExcludeSpec();
        final Spec<FileTreeElement> fileSpec = tree.getPatterns().getAsSpec();
        final Path rootPath = dirToPath(tree.getDir());
        final AtomicBoolean containsValidFiles = new AtomicBoolean(false);
        Files.walkFileTree(subRootPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                FileTreeElement fileTreeElement = toFileTreeElement(dir, rootPath.relativize(dir));
                if (!excludeSpec.isSatisfiedBy(fileTreeElement)) {
                    registerSinglePathNoSubtree(watchService, dir);
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

    private boolean doesTreeContainValidFiles(Path subRootPath, final DirectoryTree tree) throws IOException {
        final Spec<FileTreeElement> fileSpec = tree.getPatterns().getAsSpec();
        final Path rootPath = dirToPath(tree.getDir());
        final AtomicBoolean containsValidFiles = new AtomicBoolean(false);
        Files.walkFileTree(subRootPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {
                if (containsValidFiles.get()) {
                    return FileVisitResult.SKIP_SUBTREE;
                } else {
                    return FileVisitResult.CONTINUE;
                }
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                FileTreeElement fileTreeElement = toFileTreeElement(file, rootPath.relativize(file));
                if (fileSpec.isSatisfiedBy(fileTreeElement)) {
                    containsValidFiles.set(true);
                    return FileVisitResult.SKIP_SIBLINGS;
                } else {
                    return FileVisitResult.CONTINUE;
                }
            }
        });
        return containsValidFiles.get();
    }

    private void registerSinglePath(WatchService watchService, Path path) throws IOException {
        registerSinglePathWithModifier(watchService, path, watchModifiers);
    }

    private void registerSinglePathNoSubtree(WatchService watchService, Path path) throws IOException {
        registerSinglePathWithModifier(watchService, path, WATCH_MODIFIERS);
    }

    private void registerSinglePathWithModifier(WatchService watchService, Path path, WatchEvent.Modifier[] watchModifiers) throws IOException {
        WatchKey key = path.register(watchService, WATCH_KINDS, watchModifiers);
    }

    protected WatchService createWatchService() {
        try {
            return FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw new RuntimeException("IOException in creating WatchService", e);
        }
    }

    private static class CustomFileTreeElement extends DefaultFileTreeElement {
        public CustomFileTreeElement(File file, RelativePath relativePath) {
            super(file, relativePath, null, null);
        }
    }

}
