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
import org.gradle.api.internal.file.FileSystemSubset;
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
    private final Lock lock = new ReentrantLock();
    private final WatchPointsRegistry watchPointsRegistry = new WatchPointsRegistry();

    WatchServiceRegistrar(WatchService watchService, FileWatcherListener delegate) {
        this.watchService = watchService;
        this.delegate = delegate;
    }

    void watch(FileSystemSubset fileSystemSubset) throws IOException {
        lock.lock();
        try {
            final WatchPointsRegistry.Delta delta = watchPointsRegistry.appendFileSystemSubset(fileSystemSubset);
            Iterable<? extends File> startingWatchPoints = delta.getStartingWatchPoints();

            for (File dir : startingWatchPoints) {
                if (FILE_TREE_WATCHING_SUPPORTED) {
                    watchDir(dir.toPath());
                } else {
                    Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException {
                            if (delta.inUnfilteredSubsetOrAncestorOfAnyRoot(path.toFile())) {
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
        if (watchPointsRegistry.shouldFire(event.getFile())) {
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

