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
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.snapshot.SnapshotHierarchy;
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
import java.util.Collection;
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
    private final DelegatingDiffCapturingUpdateFunctionDecorator delegatingUpdateFunctionDecorator;
    private final AtomicReference<FileHierarchySet> producedByCurrentBuild = new AtomicReference<>(DefaultFileHierarchySet.of());
    private final Predicate<String> watchFilter;

    private FileWatcherRegistry watchRegistry;

    private final SnapshotHierarchy.SnapshotDiffListener snapshotDiffListener = (removedSnapshots, addedSnapshots, newRoot) -> {
        if (watchRegistry == null) {
            return newRoot;
        }
        return handleWatcherChanges(newRoot, watchRegistry -> watchRegistry.getFileWatcherUpdater().changed(removedSnapshots, addedSnapshots));
    };

    private volatile boolean buildRunning;

    public WatchingVirtualFileSystem(
        FileWatcherRegistryFactory watcherRegistryFactory,
        AbstractVirtualFileSystem delegate,
        DelegatingDiffCapturingUpdateFunctionDecorator delegatingUpdateFunctionDecorator,
        Predicate<String> watchFilter
    ) {
        super(delegate);
        this.watcherRegistryFactory = watcherRegistryFactory;
        this.delegatingUpdateFunctionDecorator = delegatingUpdateFunctionDecorator;
        this.watchFilter = watchFilter;
    }

    @Override
    public void afterBuildStarted(boolean watchingEnabled) {
        getRoot().update(currentRoot -> {
            if (watchingEnabled) {
                SnapshotHierarchy newRoot = watchRegistry == null
                    ? currentRoot.empty()
                    : handleWatcherRegistryEvents(currentRoot, watchRegistry, "since last build");
                newRoot = startWatching(newRoot);
                printStatistics(newRoot, "retained", "since last build");
                producedByCurrentBuild.set(DefaultFileHierarchySet.of());
                buildRunning = true;
                return newRoot;
            } else {
                return stopWatching(currentRoot);
            }
        });
    }

    private void updateWatchRegistry(Consumer<FileWatcherRegistry> updateFunction) {
        updateWatchRegistry(updateFunction, () -> {});
    }

    private void updateWatchRegistry(Consumer<FileWatcherRegistry> updateFunction, Runnable noWatchRegistry) {
        getRoot().update(currentRoot -> {
            if (watchRegistry == null) {
                noWatchRegistry.run();
                return currentRoot;
            }
            return handleWatcherChanges(currentRoot, updateFunction);
        });
    }

    @Override
    public void updateMustWatchDirectories(Collection<File> mustWatchDirectories) {
        updateWatchRegistry(watchRegistry -> watchRegistry.getFileWatcherUpdater().updateMustWatchDirectories(mustWatchDirectories));
    }

    @Override
    public void beforeBuildFinished(boolean watchingEnabled) {
        if (watchingEnabled) {
            getRoot().update(currentRoot -> {
                buildRunning = false;
                producedByCurrentBuild.set(DefaultFileHierarchySet.of());
                SnapshotHierarchy newRoot = watchRegistry == null
                    ? currentRoot.empty()
                    : handleWatcherRegistryEvents(currentRoot, watchRegistry, "for current build");
                printStatistics(newRoot, "retains", "till next build");
                return newRoot;
            });
        } else {
            invalidateAll();
        }
    }

    /**
     * Start watching the known areas of the file system for changes.
     */
    private SnapshotHierarchy startWatching(SnapshotHierarchy currentRoot) {
        if (watchRegistry != null) {
            return currentRoot;
        }
        try {
            long startTime = System.currentTimeMillis();
            watchRegistry = watcherRegistryFactory.startWatcher(new FileWatcherRegistry.ChangeHandler() {
                @Override
                public void handleChange(FileWatcherRegistry.Type type, Path path) {
                    try {
                        LOGGER.debug("Handling VFS change {} {}", type, path);
                        String absolutePath = path.toString();
                        if (!(buildRunning && producedByCurrentBuild.get().contains(absolutePath))) {
                            getRoot().update(root -> {
                                SnapshotCollectingDiffListener diffListener = new SnapshotCollectingDiffListener(watchFilter);
                                SnapshotHierarchy newRoot = root.invalidate(absolutePath, diffListener);
                                return diffListener.publishSnapshotDiff(snapshotDiffListener, newRoot);
                            });
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error while processing file events", e);
                        stopWatching();
                    }
                }

                @Override
                public void handleLostState() {
                    LOGGER.warn("Dropped VFS state due to lost state");
                    stopWatching();
                }
            });
            delegatingUpdateFunctionDecorator.setSnapshotDiffListener(snapshotDiffListener);
            long endTime = System.currentTimeMillis() - startTime;
            LOGGER.warn("Spent {} ms registering watches for file system events", endTime);
            // TODO: Move start watching early enough so that the root is always empty
            return currentRoot.empty();
        } catch (Exception ex) {
            LOGGER.error("Couldn't create watch service, not tracking changes between builds", ex);
            closeUnderLock();
        }
        return currentRoot.empty();
    }

    private SnapshotHierarchy handleWatcherChanges(SnapshotHierarchy currentRoot, Consumer<FileWatcherRegistry> consumer) {
        try {
            consumer.accept(watchRegistry);
            return currentRoot;
        } catch (WatchingNotSupportedException ex) {
            // No stacktrace here, since this is a known shortcoming of our implementation
            LOGGER.warn("Watching not supported, not tracking changes between builds: {}", ex.getMessage());
            return stopWatching(currentRoot);
        } catch (Exception ex) {
            LOGGER.error("Couldn't update watches, not watching anymore", ex);
            return stopWatching(currentRoot);
        }
    }

    /**
     * Stop watching the known areas of the file system, and invalidate
     * the parts that have been changed since calling {@link #startWatching(SnapshotHierarchy)}}.
     */
    private void stopWatching() {
        getRoot().update(this::stopWatching);
    }

    private SnapshotHierarchy stopWatching(SnapshotHierarchy currentRoot) {
        if (watchRegistry != null) {
            try {
                FileWatcherRegistry toBeClosed = watchRegistry;
                watchRegistry = null;
                delegatingUpdateFunctionDecorator.setSnapshotDiffListener(null);
                toBeClosed.close();
            } catch (IOException ex) {
                LOGGER.error("Couldn't fetch file changes, dropping VFS state", ex);
            }
        }
        return currentRoot.empty();
    }

    private SnapshotHierarchy handleWatcherRegistryEvents(SnapshotHierarchy currentRoot, FileWatcherRegistry watchRegistry, String eventsFor) {
        FileWatcherRegistry.FileWatchingStatistics statistics = watchRegistry.getAndResetStatistics();
        LOGGER.warn("Received {} file system events {}", statistics.getNumberOfReceivedEvents(), eventsFor);
        if (statistics.isUnknownEventEncountered()) {
            LOGGER.warn("Dropped VFS state due to lost state");
            return stopWatching(currentRoot);
        }
        if (statistics.getErrorWhileReceivingFileChanges().isPresent()) {
            LOGGER.warn("Dropped VFS state due to error while receiving file changes", statistics.getErrorWhileReceivingFileChanges().get());
            return stopWatching(currentRoot);
        }
        return currentRoot;
    }

    private static void printStatistics(SnapshotHierarchy root, String verb, String statisticsFor) {
        VirtualFileSystemStatistics statistics = getStatistics(root);
        LOGGER.warn(
            "Virtual file system {} information about {} files, {} directories and {} missing files {}",
            verb,
            statistics.getRetained(FileType.RegularFile),
            statistics.getRetained(FileType.Directory),
            statistics.getRetained(FileType.Missing),
            statisticsFor
        );
    }

    private static VirtualFileSystemStatistics getStatistics(SnapshotHierarchy root) {
        EnumMultiset<FileType> retained = EnumMultiset.create(FileType.class);
        root.visitSnapshotRoots(snapshot -> snapshot.accept(new FileSystemSnapshotVisitor() {
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

    @Override
    public void close() {
        getRoot().update(currentRoot -> {
            closeUnderLock();
            return currentRoot.empty();
        });
    }

    private void closeUnderLock() {
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
