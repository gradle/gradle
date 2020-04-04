/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.vfs.impl;

import com.google.common.collect.EnumMultiset;
import com.google.common.collect.Multiset;
import org.gradle.internal.file.DefaultFileHierarchySet;
import org.gradle.internal.file.FileHierarchySet;
import org.gradle.internal.file.FileType;
import org.gradle.internal.vfs.WatchingAwareVirtualFileSystem;
import org.gradle.internal.vfs.watch.FileWatcherRegistry;
import org.gradle.internal.vfs.watch.FileWatcherRegistryFactory;
import org.gradle.internal.vfs.watch.WatchingNotSupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A {@link org.gradle.internal.vfs.VirtualFileSystem} which uses watches to maintain
 * its contents.
 */
public class WatchingVirtualFileSystem extends AbstractDelegatingVirtualFileSystem implements WatchingAwareVirtualFileSystem, Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WatchingVirtualFileSystem.class);

    private final FileWatcherRegistryFactory watcherRegistryFactory;
    private final Predicate<String> watchFilter;
    private final AtomicReference<FileHierarchySet> producedByCurrentBuild = new AtomicReference<>(DefaultFileHierarchySet.of());

    private FileWatcherRegistry watchRegistry;
    private volatile boolean buildRunning;

    public WatchingVirtualFileSystem(
        FileWatcherRegistryFactory watcherRegistryFactory,
        AbstractVirtualFileSystem delegate,
        Predicate<String> watchFilter
    ) {
        super(delegate);
        this.watcherRegistryFactory = watcherRegistryFactory;
        this.watchFilter = watchFilter;
    }

    @Override
    public void afterStart(boolean watchingEnabled) {
        if (watchingEnabled) {
            handleWatcherRegistryEvents("since last build");
            printStatistics("retained", "since last build");
            producedByCurrentBuild.set(DefaultFileHierarchySet.of());
            buildRunning = true;
        } else {
            stopWatching();
            invalidateAll();
        }
    }

    @Override
    public void beforeComplete(boolean watchingEnabled, Supplier<File> rootProjectDirSupplier) {
        if (watchingEnabled) {
            handleWatcherRegistryEvents("for current build");
            stopWatching();
            buildRunning = false;
            producedByCurrentBuild.set(DefaultFileHierarchySet.of());
            printStatistics("retains", "till next build");
            startWatching(rootProjectDirSupplier);
        } else {
            invalidateAll();
        }
    }

    /**
     * Start watching the known areas of the file system for changes.
     *
     * @param rootProjectDirSupplier rootDirectory - always should be watched even when not part of the VFS.
     */
    private void startWatching(Supplier<File> rootProjectDirSupplier) {
        if (watchRegistry != null) {
            throw new IllegalStateException("Watch service already started");
        }
        try {
            long startTime = System.currentTimeMillis();
            File rootProjectDir = rootProjectDirSupplier.get();
            watchRegistry = watcherRegistryFactory.startWatching(getRoot(), watchFilter, Collections.singletonList(rootProjectDir), new FileWatcherRegistry.ChangeHandler() {
                @Override
                public void handleChange(FileWatcherRegistry.Type type, Path path) {
                    LOGGER.debug("Handling VFS change {} {}", type, path);
                    String absolutePath = path.toString();
                    if (!(buildRunning && producedByCurrentBuild.get().contains(absolutePath))) {
                        WatchingVirtualFileSystem.super.update(Collections.singleton(absolutePath), () -> {});
                    }
                }

                @Override
                public void handleLostState() {
                    LOGGER.warn("Dropped VFS state due to lost state");
                    invalidateAll();
                }
            });
            long endTime = System.currentTimeMillis() - startTime;
            LOGGER.warn("Spent {} ms registering watches for file system events", endTime);
        } catch (WatchingNotSupportedException ex) {
            // No stacktrace here, since this is a known shortcoming of our implementation
            LOGGER.warn("Watching not supported, not tracking changes between builds: {}", ex.getMessage());
            invalidateAll();
            close();
        } catch (Exception ex) {
            LOGGER.error("Couldn't create watch service, not tracking changes between builds", ex);
            invalidateAll();
            close();
        }
    }

    /**
     * Stop watching the known areas of the file system, and invalidate
     * the parts that have been changed since calling {@link #startWatching(Supplier)} ()}.
     */
    private void stopWatching() {
        if (watchRegistry == null) {
            return;
        }

        try {
            watchRegistry.close();
        } catch (IOException ex) {
            LOGGER.error("Couldn't fetch file changes, dropping VFS state", ex);
            invalidateAll();
        } finally {
            watchRegistry = null;
        }
    }

    private void handleWatcherRegistryEvents(String eventsFor) {
        if (watchRegistry != null) {
            FileWatcherRegistry.FileWatchingStatistics statistics = watchRegistry.getAndResetStatistics();
            LOGGER.warn("Received {} file system events {}", statistics.getNumberOfReceivedEvents(), eventsFor);
            if (statistics.isUnknownEventEncountered()) {
                LOGGER.warn("Dropped VFS state due to lost state");
            }
            if (statistics.getErrorWhileReceivingFileChanges().isPresent()) {
                LOGGER.warn("Dropped VFS state due to error while receiving file changes", statistics.getErrorWhileReceivingFileChanges().get());
                invalidateAll();
            }
        }
    }

    private void printStatistics(String verb, String statisticsFor) {
        VirtualFileSystemStatistics statistics = getStatistics();
        LOGGER.warn(
            "Virtual file system {} information about {} files, {} directories and {} missing files {}",
            verb,
            statistics.getRetained(FileType.RegularFile),
            statistics.getRetained(FileType.Directory),
            statistics.getRetained(FileType.Missing),
            statisticsFor
        );
    }

    @Override
    public void update(Iterable<String> locations, Runnable action) {
        if (buildRunning) {
            producedByCurrentBuild.updateAndGet(currentValue -> {
                FileHierarchySet newValue = currentValue;
                for (String location : locations) {
                    newValue = newValue.plus(new File(location));
                }
                return newValue;
            });
        }
        super.update(locations, action);
    }

    private VirtualFileSystemStatistics getStatistics() {
        EnumMultiset<FileType> retained = EnumMultiset.create(FileType.class);
        getRoot().visitSnapshots((snapshot, rootOfCompleteHierarchy) -> retained.add(snapshot.getType()));
        return new VirtualFileSystemStatistics(retained);
    }

    private static class VirtualFileSystemStatistics {
        private final Multiset<FileType> retained;

        public VirtualFileSystemStatistics(Multiset<FileType> retained) {
            this.retained = retained;
        }

        public int getRetained(FileType fileType) {
            return retained.count(fileType);
        }
    }

    @Override
    public void close() {
        producedByCurrentBuild.set(DefaultFileHierarchySet.of());
        if (watchRegistry != null) {
            try {
                watchRegistry.close();
            } catch (IOException ex) {
                LOGGER.error("Couldn't close watch service", ex);
            }
            watchRegistry = null;
        }
    }
}
