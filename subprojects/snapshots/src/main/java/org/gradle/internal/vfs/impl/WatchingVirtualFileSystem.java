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
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemNode;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.vfs.SnapshotHierarchy;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A {@link org.gradle.internal.vfs.VirtualFileSystem} which uses watches to maintain
 * its contents.
 */
public class WatchingVirtualFileSystem extends AbstractDelegatingVirtualFileSystem implements WatchingAwareVirtualFileSystem, Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WatchingVirtualFileSystem.class);

    private final FileWatcherRegistryFactory watcherRegistryFactory;
    private final DelegatingChangeListenerFactory delegatingChangeListenerFactory;
    private final Predicate<String> watchFilter;
    private final AtomicReference<FileHierarchySet> producedByCurrentBuild = new AtomicReference<>(DefaultFileHierarchySet.of());
    private FileWatcherRegistry watchRegistry;
    private final BlockingQueue<FileEvent> fileEvents = new ArrayBlockingQueue<>(100);
    private volatile boolean buildRunning;

    private static class FileEvent {
        final Path path;
        final FileWatcherRegistry.Type type;
        final boolean lostState;

        public FileEvent(Path path, FileWatcherRegistry.Type type) {
            this.path = path;
            this.type = type;
            this.lostState = false;
        }

        public FileEvent(boolean lostState) {
            this.lostState = lostState;
            this.path = null;
            this.type = null;
        }
    }

    private volatile boolean consumeEvents = true;

    private final VirtualFileSystemChangeListener changeListener = (removedNodes, addedNodes) -> {
        if (!removedNodes.isEmpty() || !addedNodes.isEmpty()) {
            updateWatchRegistry(watchRegistry -> watchRegistry.changed(removedNodes, addedNodes));
        }
    };

    public WatchingVirtualFileSystem(
        FileWatcherRegistryFactory watcherRegistryFactory,
        AbstractVirtualFileSystem delegate,
        DelegatingChangeListenerFactory delegatingChangeListenerFactory,
        Predicate<String> watchFilter
    ) {
        super(delegate);
        this.watcherRegistryFactory = watcherRegistryFactory;
        this.delegatingChangeListenerFactory = delegatingChangeListenerFactory;
        this.watchFilter = watchFilter;

        // stop thread
        Thread eventConsumer = new Thread(() -> {
            try {
                while (consumeEvents) {
                    FileEvent nextEvent = fileEvents.take();
                    try {
                        if (nextEvent.lostState) {
                            LOGGER.warn("Dropped VFS state due to lost state");
                            stopWatching();
                        } else {
                            FileWatcherRegistry.Type type = nextEvent.type;
                            Path path = nextEvent.path;

                            LOGGER.debug("Handling VFS change {} {}", type, path);
                            String absolutePath = path.toString();
                            if (!(buildRunning && producedByCurrentBuild.get().contains(absolutePath))) {
                                ChangeListenerFactory.LifecycleAwareChangeListener changeListener = new ChangeListenerFactory.LifecycleAwareChangeListener() {
                                    private final List<FileSystemNode> removedNodes = new ArrayList<>();
                                    private final List<FileSystemNode> addedNodes = new ArrayList<>();

                                    @Override
                                    public void start() {
                                        removedNodes.clear();
                                        addedNodes.clear();
                                    }

                                    @Override
                                    public void finish() {
                                        if (!removedNodes.isEmpty() || !addedNodes.isEmpty()) {
                                            updateWatchRegistry(watchRegistry -> watchRegistry.changed(removedNodes, addedNodes));
                                        }
                                    }

                                    @Override
                                    public void nodeRemoved(FileSystemNode node) {
                                        removedNodes.add(node);
                                    }

                                    @Override
                                    public void nodeAdded(FileSystemNode node) {
                                        addedNodes.add(node);
                                    }
                                };
                                getRoot().update(root -> root.invalidate(absolutePath, changeListener), changeListener);
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error while processing file events", e);
                        stopWatching();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // stop thread
            }
        });
        eventConsumer.setDaemon(true);
        eventConsumer.setName("File watcher consumer");
        eventConsumer.start();
    }

    @Override
    public void afterStart(boolean watchingEnabled) {
        if (watchingEnabled) {
            startWatching();
            handleWatcherRegistryEvents("since last build");
            printStatistics("retained", "since last build");
            producedByCurrentBuild.set(DefaultFileHierarchySet.of());
            buildRunning = true;
        } else {
            stopWatching();
        }
    }

    private void updateWatchRegistry(Consumer<FileWatcherRegistry> updateFunction) {
        updateWatchRegistry(updateFunction, () -> {});
    }

    private synchronized void updateWatchRegistry(Consumer<FileWatcherRegistry> updateFunction, Runnable noWatchRegistry) {
        if (watchRegistry == null) {
            noWatchRegistry.run();
        } else {
            handleWatcherChanges(updateFunction);
        }
    }

    @Override
    public void updateMustWatchDirectories(Collection<File> mustWatchDirectories) {
        updateWatchRegistry(watchRegistry -> watchRegistry.updateMustWatchDirectories(mustWatchDirectories));
    }

    @Override
    public void beforeComplete(boolean watchingEnabled) {
        if (watchingEnabled) {
            handleWatcherRegistryEvents("for current build");
            buildRunning = false;
            producedByCurrentBuild.set(DefaultFileHierarchySet.of());
            printStatistics("retains", "till next build");
            updateWatchRegistry(watchRegistry -> {}, this::invalidateAll);
        } else {
            invalidateAll();
        }
    }

    /**
     * Start watching the known areas of the file system for changes.
     */
    private synchronized void startWatching() {
        if (watchRegistry != null) {
            return;
        }
        try {
            long startTime = System.currentTimeMillis();
            watchRegistry = watcherRegistryFactory.startWatcher(watchFilter, new FileWatcherRegistry.ChangeHandler() {
                @Override
                public void handleChange(FileWatcherRegistry.Type type, Path path) {
                    try {
                        fileEvents.put(new FileEvent(path, type));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public void handleLostState() {
                    try {
                        fileEvents.put(new FileEvent(true));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }
            });
            delegatingChangeListenerFactory.setVfsChangeListener(changeListener);
            long endTime = System.currentTimeMillis() - startTime;
            LOGGER.warn("Spent {} ms registering watches for file system events", endTime);
        } catch (Exception ex) {
            LOGGER.error("Couldn't create watch service, not tracking changes between builds", ex);
            invalidateAll();
            close();
        }
    }

    private void handleWatcherChanges(Consumer<FileWatcherRegistry> consumer) {
        try {
            consumer.accept(watchRegistry);
        } catch (WatchingNotSupportedException ex) {
            // No stacktrace here, since this is a known shortcoming of our implementation
            LOGGER.warn("Watching not supported, not tracking changes between builds: {}", ex.getMessage());
            stopWatching();
        } catch (Exception ex) {
            LOGGER.error("Couldn't update watches, not watching anymore", ex);
            stopWatching();
        }
    }

    /**
     * Stop watching the known areas of the file system, and invalidate
     * the parts that have been changed since calling {@link #startWatching()}}.
     */
    private void stopWatching() {
        updateWatchRegistry(fileWatcherRegistry -> {
            try {
                delegatingChangeListenerFactory.setVfsChangeListener(null);
                fileWatcherRegistry.close();
            } catch (IOException ex) {
                LOGGER.error("Couldn't fetch file changes, dropping VFS state", ex);
                getRoot().update(SnapshotHierarchy::empty, ChangeListenerFactory.LifecycleAwareChangeListener.NOOP);
            } finally {
                watchRegistry = null;
                fileEvents.clear();
            }
        });
        getRoot().update(SnapshotHierarchy::empty, ChangeListenerFactory.LifecycleAwareChangeListener.NOOP);
    }

    private void handleWatcherRegistryEvents(String eventsFor) {
        updateWatchRegistry(watchRegistry -> {
            FileWatcherRegistry.FileWatchingStatistics statistics = watchRegistry.getAndResetStatistics();
            LOGGER.warn("Received {} file system events {}", statistics.getNumberOfReceivedEvents(), eventsFor);
            if (statistics.isUnknownEventEncountered()) {
                LOGGER.warn("Dropped VFS state due to lost state");
                stopWatching();
            }
            if (statistics.getErrorWhileReceivingFileChanges().isPresent()) {
                LOGGER.warn("Dropped VFS state due to error while receiving file changes", statistics.getErrorWhileReceivingFileChanges().get());
                stopWatching();
            }
        });
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
        getRoot().get().visitSnapshotRoots(snapshot -> snapshot.accept(new FileSystemSnapshotVisitor() {
            @Override
            public boolean preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
                retained.add(directorySnapshot.getType());
                return true;
            }

            @Override
            public void visitFile(CompleteFileSystemLocationSnapshot fileSnapshot) {
                retained.add(fileSnapshot.getType());
            }

            @Override
            public void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
            }
        }));
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
        consumeEvents = false;
        producedByCurrentBuild.set(DefaultFileHierarchySet.of());
        updateWatchRegistry(fileWatcherRegistry -> {
            try {
                fileWatcherRegistry.close();
            } catch (IOException ex) {
                LOGGER.error("Couldn't close watch service", ex);
            }
            watchRegistry = null;
        });
    }
}
