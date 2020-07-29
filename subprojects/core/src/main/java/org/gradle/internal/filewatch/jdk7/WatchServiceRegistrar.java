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

import com.google.common.base.Throwables;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.filewatch.FileWatcher;
import org.gradle.internal.filewatch.FileWatcherEvent;
import org.gradle.internal.filewatch.FileWatcherListener;
import org.gradle.internal.os.OperatingSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class WatchServiceRegistrar implements FileWatcherListener {
    private final static Logger LOG = LoggerFactory.getLogger(WatchServiceRegistrar.class);
    private static final boolean FILE_TREE_WATCHING_SUPPORTED = OperatingSystem.current().isWindows() && !JavaVersion.current().isJava9Compatible();
    private static final WatchEvent.Modifier[] WATCH_MODIFIERS = instantiateWatchModifiers();
    private static final WatchEvent.Kind[] WATCH_KINDS = new WatchEvent.Kind[]{StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY};

    private final WatchService watchService;
    private final FileWatcherListener delegate;
    private final Lock lock = new ReentrantLock(true);
    private final WatchPointsRegistry watchPointsRegistry;
    private final HashMap<Path, WatchKey> watchKeys = new HashMap<Path, WatchKey>();

    WatchServiceRegistrar(WatchService watchService, FileWatcherListener delegate) {
        this.watchService = watchService;
        this.delegate = delegate;
        this.watchPointsRegistry = new WatchPointsRegistry(!FILE_TREE_WATCHING_SUPPORTED);
    }

    private static WatchEvent.Modifier[] instantiateWatchModifiers() {
        if (JavaVersion.current().isJava9Compatible()) {
            return new WatchEvent.Modifier[]{};
        } else {
            // use reflection to support older JVMs while supporting Java 9
            WatchEvent.Modifier highSensitive = instantiateEnum("com.sun.nio.file.SensitivityWatchEventModifier", "HIGH");
            if (FILE_TREE_WATCHING_SUPPORTED) {
                WatchEvent.Modifier fileTree = instantiateEnum("com.sun.nio.file.ExtendedWatchEventModifier", "FILE_TREE");
                return new WatchEvent.Modifier[]{fileTree, highSensitive};
            } else {
                return new WatchEvent.Modifier[]{highSensitive};
            }
        }
    }

    private static WatchEvent.Modifier instantiateEnum(String className, String enumName) {
        try {
            return (WatchEvent.Modifier) Enum.valueOf(Cast.uncheckedNonnullCast(Class.forName(className)), enumName);
        } catch (ClassNotFoundException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    void watch(FileSystemSubset fileSystemSubset) throws IOException {
        lock.lock();
        try {
            LOG.debug("Begin - adding watches for {}", fileSystemSubset);
            final WatchPointsRegistry.Delta delta = watchPointsRegistry.appendFileSystemSubset(fileSystemSubset, getCurrentWatchPoints());
            Iterable<? extends File> startingWatchPoints = delta.getStartingWatchPoints();

            for (File dir : startingWatchPoints) {
                LOG.debug("Begin - handling starting point {}", dir);
                final Path dirPath = dir.toPath();
                watchDir(dirPath);
                if (!FILE_TREE_WATCHING_SUPPORTED) {
                    Files.walkFileTree(dirPath, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) throws IOException {
                            if (!path.equals(dirPath)) {
                                if (delta.shouldWatch(path.toFile())) {
                                    watchDir(path);
                                    return FileVisitResult.CONTINUE;
                                } else {
                                    LOG.debug("Skipping watching for {}, filtered by WatchPointsRegistry", path);
                                    return FileVisitResult.SKIP_SUBTREE;
                                }
                            } else {
                                return FileVisitResult.CONTINUE;
                            }
                        }
                    });
                }
                LOG.debug("End - handling starting point {}", dir);
            }
            LOG.debug("End - adding watches for {}", fileSystemSubset);
        } finally {
            lock.unlock();
        }
    }

    private Iterable<File> getCurrentWatchPoints() {
        List<File> currentWatchPoints = new LinkedList<File>();
        for (Map.Entry<Path, WatchKey> entry : watchKeys.entrySet()) {
            if (entry.getValue().isValid()) {
                currentWatchPoints.add(entry.getKey().toFile());
            }
        }
        return currentWatchPoints;
    }

    protected void watchDir(Path dir) throws IOException {
        LOG.debug("Registering watch for {}", dir);
        if (Thread.currentThread().isInterrupted()) {
            LOG.debug("Skipping adding watch since current thread is interrupted.");
        }

        // check if directory is already watched
        // on Windows, check if any parent is already watched
        for (Path path = dir; path != null; path = FILE_TREE_WATCHING_SUPPORTED ? path.getParent() : null) {
            WatchKey previousWatchKey = watchKeys.get(path);
            if (previousWatchKey != null && previousWatchKey.isValid()) {
                LOG.debug("Directory {} is already watched and the watch is valid, not adding another one.", path);
                return;
            }
        }

        int retryCount = 0;
        IOException lastException = null;
        while (retryCount++ < 2) {
            try {
                WatchKey watchKey = dir.register(watchService, WATCH_KINDS, WATCH_MODIFIERS);
                watchKeys.put(dir, watchKey);
                return;
            } catch (IOException e) {
                LOG.debug("Exception in registering for watching of " + dir, e);
                lastException = e;

                if (e instanceof NoSuchFileException) {
                    LOG.debug("Return silently since directory doesn't exist.");
                    return;
                }

                if (e instanceof FileSystemException && e.getMessage() != null && e.getMessage().contains("Bad file descriptor")) {
                    // retry after getting "Bad file descriptor" exception
                    LOG.debug("Retrying after 'Bad file descriptor'");
                    continue;
                }

                // Windows at least will sometimes throw odd exceptions like java.nio.file.AccessDeniedException
                // if the file gets deleted while the watch is being set up.
                // So, we just ignore the exception if the dir doesn't exist anymore
                if (!Files.exists(dir)) {
                    // return silently when directory doesn't exist
                    LOG.debug("Return silently since directory doesn't exist.");
                    return;
                } else {
                    // no retry
                    throw e;
                }
            }
        }
        LOG.debug("Retry count exceeded, throwing last exception");
        throw lastException;
    }


    @Override
    public void onChange(FileWatcher watcher, FileWatcherEvent event) {
        lock.lock();
        try {
            if (event.getType().equals(FileWatcherEvent.Type.UNDEFINED) || event.getFile() == null) {
                LOG.debug("Calling onChange with event {}", event);
                deliverEventToDelegate(watcher, event);
                return;
            }

            File file = event.getFile();
            maybeFire(watcher, event);

            if (event.getType().equals(FileWatcherEvent.Type.CREATE) && file.isDirectory()) {
                try {
                    maybeWatchNewDirectory(watcher, file);
                } catch (IOException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    protected void deliverEventToDelegate(FileWatcher watcher, FileWatcherEvent event) {
        if(!Thread.currentThread().isInterrupted()) {
            try {
                delegate.onChange(watcher, event);
            } catch (RuntimeException e) {
                if (Throwables.getRootCause(e) instanceof InterruptedException) {
                    // delivery was interrupted, return silently
                    return;
                }
                throw e;
            }
        } else {
            LOG.debug("Skipping event delivery since current thread is interrupted.");
        }
    }

    private void maybeFire(FileWatcher watcher, FileWatcherEvent event) {
        if (watchPointsRegistry.shouldFire(event.getFile())) {
            LOG.debug("Calling onChange with event {}", event);
            deliverEventToDelegate(watcher, event);
        } else {
            LOG.debug("Ignoring event {}", event);
        }
    }

    private void maybeWatchNewDirectory(FileWatcher watcher, File dir) throws IOException {
        LOG.debug("Begin - maybeWatchNewDirectory {}", dir);
        if (isStopRequested(watcher)) {
            LOG.debug("Stop requested, returning.");
            return;
        }
        if (!watchPointsRegistry.shouldWatch(dir)) {
            LOG.debug("Ignoring watching {}", dir);
            return;
        }
        if (dir.exists()) {
            if (!FILE_TREE_WATCHING_SUPPORTED) {
                watchDir(dir.toPath());
            }
            File[] contents = dir.listFiles();
            if (contents != null) {
                for (File file : contents) {
                    if (isStopRequested(watcher)) {
                        LOG.debug("Stop requested, returning.");
                        return;
                    }
                    maybeFire(watcher, FileWatcherEvent.create(file));
                    if (file.isDirectory()) {
                        maybeWatchNewDirectory(watcher, file);
                    }
                }
            }
        }
        LOG.debug("End - maybeWatchNewDirectory {}", dir);
    }

    private boolean isStopRequested(FileWatcher watcher) {
        return Thread.currentThread().isInterrupted() || !watcher.isRunning();
    }

}

