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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.sun.nio.file.ExtendedWatchEventModifier;
import com.sun.nio.file.SensitivityWatchEventModifier;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.internal.FileUtils;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.filewatch.FileWatcher;
import org.gradle.internal.filewatch.FileWatcherEvent;
import org.gradle.internal.filewatch.FileWatcherListener;
import org.gradle.internal.os.OperatingSystem;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class WatchServiceRegistrar implements FileWatcherListener {
    private static final boolean FILE_TREE_WATCHING_SUPPORTED = OperatingSystem.current().isWindows();
    private static final WatchEvent.Modifier[] WATCH_MODIFIERS =
        FILE_TREE_WATCHING_SUPPORTED
        ? new WatchEvent.Modifier[]{ExtendedWatchEventModifier.FILE_TREE, SensitivityWatchEventModifier.HIGH}
        : new WatchEvent.Modifier[]{SensitivityWatchEventModifier.HIGH};
    private static final WatchEvent.Kind[] WATCH_KINDS = new WatchEvent.Kind[]{StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY};

    private final WatchService watchService;
    private final FileWatcherListener delegate;
    private FileSystemSubset combinedFileSystemSubset;
    private ImmutableCollection<? extends File> currentRoots;
    private final Lock lock = new ReentrantLock();

    WatchServiceRegistrar(WatchService watchService, FileWatcherListener delegate) {
        this.watchService = watchService;
        this.delegate = delegate;
    }

    void watch(FileSystemSubset fileSystemSubset) throws IOException {
        lock.lock();
        try {
            final Iterable<? extends File> roots = fileSystemSubset.getRoots();
            final FileSystemSubset unfiltered = fileSystemSubset.unfiltered();

            Iterable<? extends File> startingWatchPoints = calculateStartingWatchPoints(roots, unfiltered);

            if (currentRoots != null) {
                final ImmutableSet.Builder<File> newStartingPoints = ImmutableSet.builder();
                Iterable<? extends File> combinedRoots = FileUtils.calculateRoots(Iterables.concat(currentRoots, startingWatchPoints));
                for (File file : combinedRoots) {
                    if (!currentRoots.contains(file)) {
                        newStartingPoints.add(file);
                    }
                }
                startingWatchPoints = newStartingPoints.build();
                currentRoots = ImmutableSet.copyOf(combinedRoots);
                combinedFileSystemSubset = FileSystemSubset.builder().add(combinedFileSystemSubset).add(fileSystemSubset).build();
            } else {
                currentRoots = ImmutableSet.copyOf(startingWatchPoints);
                combinedFileSystemSubset = fileSystemSubset;
            }

            for (File dir : startingWatchPoints) {
                if (FILE_TREE_WATCHING_SUPPORTED) {
                    watchDir(dir.toPath());
                } else {
                    Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException {
                            if (inUnfilteredSubsetOrAncestorOfAnyRoot(path.toFile(), roots, unfiltered)) {
                                watchDir(path);
                                return FileVisitResult.CONTINUE;
                            } else {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                        }
                    });
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private Iterable<? extends File> calculateStartingWatchPoints(final Iterable<? extends File> roots, final FileSystemSubset unfiltered) {
        // Turn the requested watch points into actual enclosing directories that exist
        Iterable<File> enclosingDirsThatExist = Iterables.transform(roots, new Function<File, File>() {
            @Override
            public File apply(File input) {
                File target = input;
                while (!target.isDirectory()) {
                    target = target.getParentFile();
                }
                return target;
            }
        });

        // Collapse the set
        return Iterables.filter(FileUtils.calculateRoots(enclosingDirsThatExist), new Predicate<File>() {
            @Override
            public boolean apply(File input) {
                return inUnfilteredSubsetOrAncestorOfAnyRoot(input, roots, unfiltered);
            }
        });
    }

    private void watchDir(Path dir) throws IOException {
        try {
            dir.register(watchService, WATCH_KINDS, WATCH_MODIFIERS);
        } catch (IOException e) {
            // Windows at least will sometimes throw odd exceptions like java.nio.file.AccessDeniedException
            // if the file gets deleted while the watch is being set up.
            // So, we just ignore the exception if the dir doesn't exist anymore
            if (Files.exists(dir)) {
                throw e;
            }
        }
    }

    private boolean inUnfilteredSubsetOrAncestorOfAnyRoot(File file, Iterable<? extends File> roots, FileSystemSubset unfilteredFileSystemSubset) {
        if (unfilteredFileSystemSubset.contains(file)) {
            return true;
        } else {
            String absolutePathWithSeparator = file.getAbsolutePath() + File.separator;
            for (File root : roots) {
                if (root.equals(file) || root.getPath().startsWith(absolutePathWithSeparator)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void onChange(FileWatcher watcher, FileWatcherEvent event) {
        lock.lock();
        try {
            if (event.getType().equals(FileWatcherEvent.Type.UNDEFINED) || event.getFile() == null) {
                delegate.onChange(watcher, event);
                return;
            }

            File file = event.getFile();
            maybeFire(watcher, event);

            if (watcher.isRunning() && file.isDirectory() && event.getType().equals(FileWatcherEvent.Type.CREATE)) {
                try {
                    newDirectory(watcher, file);
                } catch (IOException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void maybeFire(FileWatcher watcher, FileWatcherEvent event) {
        if (combinedFileSystemSubset.contains(event.getFile())) {
            delegate.onChange(watcher, event);
        }
    }

    private void newDirectory(FileWatcher watcher, File dir) throws IOException {
        if (!watcher.isRunning()) {
            return;
        }
        if (dir.exists()) {
            if (!FILE_TREE_WATCHING_SUPPORTED) {
                watchDir(dir.toPath());
            }
            File[] contents = dir.listFiles();
            if (contents != null) {
                for (File file : contents) {
                    maybeFire(watcher, FileWatcherEvent.create(file));
                    if (!watcher.isRunning()) {
                        return;
                    }

                    if (file.isDirectory()) {
                        newDirectory(watcher, file);
                    }
                }
            }
        }
    }
}

