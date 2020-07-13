/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.watch.vfs.impl;

import com.google.common.collect.EnumMultiset;
import com.google.common.collect.Multiset;
import net.rubygrapefruit.platform.internal.jni.InotifyInstanceLimitTooLowException;
import net.rubygrapefruit.platform.internal.jni.InotifyWatchesLimitTooLowException;
import org.gradle.internal.file.FileMetadata.AccessType;
import org.gradle.internal.file.FileType;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.vfs.impl.AbstractVirtualFileSystem;
import org.gradle.internal.vfs.impl.SnapshotCollectingDiffListener;
import org.gradle.internal.watch.WatchingNotSupportedException;
import org.gradle.internal.watch.registry.FileWatcherRegistry;
import org.gradle.internal.watch.registry.FileWatcherRegistryFactory;
import org.gradle.internal.watch.registry.impl.DaemonDocumentationIndex;
import org.gradle.internal.watch.vfs.FileSystemWatchingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class DefaultFileSystemWatchingHandler implements FileSystemWatchingHandler, Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFileSystemWatchingHandler.class);
    private static final String FILE_WATCHING_ERROR_MESSAGE_DURING_BUILD = "Unable to watch the file system for changes";
    private static final String FILE_WATCHING_ERROR_MESSAGE_AT_END_OF_BUILD = "Gradle was unable to watch the file system for changes";

    private final FileWatcherRegistryFactory watcherRegistryFactory;
    private final AbstractVirtualFileSystem virtualFileSystem;
    private final DelegatingDiffCapturingUpdateFunctionDecorator delegatingUpdateFunctionDecorator;
    private final Predicate<String> watchFilter;
    private final DaemonDocumentationIndex daemonDocumentationIndex;
    private final RecentlyCapturedSnapshots recentlyCapturedSnapshots;
    private final Set<File> rootProjectDirectoriesForWatching = new HashSet<>();

    private FileWatcherRegistry watchRegistry;
    private Exception reasonForNotWatchingFiles;

    private final SnapshotHierarchy.SnapshotDiffListener snapshotDiffListener = (removedSnapshots, addedSnapshots) -> {
        if (watchRegistry != null) {
            watchRegistry.getFileWatcherUpdater().changed(removedSnapshots, addedSnapshots);
        }
    };

    private volatile boolean buildRunning;

    public DefaultFileSystemWatchingHandler(
        FileWatcherRegistryFactory watcherRegistryFactory,
        AbstractVirtualFileSystem virtualFileSystem,
        DelegatingDiffCapturingUpdateFunctionDecorator delegatingUpdateFunctionDecorator,
        Predicate<String> watchFilter,
        DaemonDocumentationIndex daemonDocumentationIndex,
        RecentlyCapturedSnapshots recentlyCapturedSnapshots
    ) {
        this.watcherRegistryFactory = watcherRegistryFactory;
        this.virtualFileSystem = virtualFileSystem;
        this.delegatingUpdateFunctionDecorator = delegatingUpdateFunctionDecorator;
        this.watchFilter = watchFilter;
        this.daemonDocumentationIndex = daemonDocumentationIndex;
        this.recentlyCapturedSnapshots = recentlyCapturedSnapshots;
    }

    @Override
    public void afterBuildStarted(boolean watchingEnabled) {
        reasonForNotWatchingFiles = null;
        virtualFileSystem.getRoot().update(currentRoot -> {
            if (watchingEnabled) {
                SnapshotHierarchy newRoot = handleWatcherRegistryEvents(currentRoot, "since last build");
                newRoot = startWatching(newRoot);
                printStatistics(newRoot, "retained", "since last build");
                buildRunning = true;
                return newRoot;
            } else {
                return stopWatchingAndInvalidateHierarchy(currentRoot);
            }
        });
    }

    private void updateWatchRegistry(Consumer<FileWatcherRegistry> updateFunction) {
        updateWatchRegistry(updateFunction, () -> {});
    }

    private void updateWatchRegistry(Consumer<FileWatcherRegistry> updateFunction, Runnable noWatchRegistry) {
        virtualFileSystem.getRoot().update(currentRoot -> {
            if (watchRegistry == null) {
                noWatchRegistry.run();
                return currentRoot;
            }
            return withWatcherChangeErrorHandling(currentRoot, () -> updateFunction.accept(watchRegistry));
        });
    }

    @Override
    public void buildRootDirectoryAdded(File buildRootDirectory) {
        synchronized (rootProjectDirectoriesForWatching) {
            rootProjectDirectoriesForWatching.add(buildRootDirectory);
            updateWatchRegistry(watchRegistry -> watchRegistry.getFileWatcherUpdater().updateRootProjectDirectories(rootProjectDirectoriesForWatching));
        }
    }

    @Override
    public void beforeBuildFinished(boolean watchingEnabled) {
        synchronized (rootProjectDirectoriesForWatching) {
            rootProjectDirectoriesForWatching.clear();
        }
        if (watchingEnabled) {
            if (reasonForNotWatchingFiles != null) {
                // Log exception again so it doesn't get lost.
                logWatchingError(reasonForNotWatchingFiles, FILE_WATCHING_ERROR_MESSAGE_AT_END_OF_BUILD);
                reasonForNotWatchingFiles = null;
            }
            virtualFileSystem.getRoot().update(currentRoot -> {
                buildRunning = false;
                SnapshotHierarchy newRoot = removeSymbolicLinks(currentRoot);
                newRoot = handleWatcherRegistryEvents(newRoot, "for current build");
                if (watchRegistry != null) {
                    newRoot = withWatcherChangeErrorHandling(newRoot, () -> watchRegistry.getFileWatcherUpdater().buildFinished());
                }
                printStatistics(newRoot, "retains", "till next build");
                return newRoot;
            });
        } else {
            virtualFileSystem.invalidateAll();
        }
    }

    /**
     * Removes all files which are reached via symbolic links from the VFS.
     *
     * Currently, we don't model symbolic links in the VFS.
     * We can only watch the sources of symbolic links.
     * When the target of symbolic link changes, we do not get informed about those changes.
     * Therefore, we maintain the state of symbolic links between builds and we need to remove them from the VFS.
     */
    private SnapshotHierarchy removeSymbolicLinks(SnapshotHierarchy currentRoot) {
        SymlinkRemovingFileSystemSnapshotVisitor symlinkRemovingFileSystemSnapshotVisitor = new SymlinkRemovingFileSystemSnapshotVisitor(currentRoot);
        currentRoot.visitSnapshotRoots(snapshotRoot -> snapshotRoot.accept(symlinkRemovingFileSystemSnapshotVisitor));
        return symlinkRemovingFileSystemSnapshotVisitor.getRootWithSymlinksRemoved();
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
            watchRegistry = watcherRegistryFactory.createFileWatcherRegistry(new FileWatcherRegistry.ChangeHandler() {
                @Override
                public void handleChange(FileWatcherRegistry.Type type, Path path) {
                    try {
                        LOGGER.debug("Handling VFS change {} {}", type, path);
                        String absolutePath = path.toString();
                        if (!(buildRunning && recentlyCapturedSnapshots.isProducedByCurrentBuild(absolutePath))) {
                            virtualFileSystem.getRoot().update(root -> {
                                SnapshotCollectingDiffListener diffListener = new SnapshotCollectingDiffListener(watchFilter);
                                SnapshotHierarchy newRoot = root.invalidate(absolutePath, diffListener);
                                return withWatcherChangeErrorHandling(
                                    newRoot,
                                    () -> diffListener.publishSnapshotDiff(snapshotDiffListener)
                                );
                            });
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error while processing file events", e);
                        stopWatchingAndInvalidateHierarchy();
                    }
                }

                @Override
                public void handleLostState() {
                    LOGGER.warn("Dropped VFS state due to lost state");
                    stopWatchingAndInvalidateHierarchy();
                }
            });
            watchRegistry.getFileWatcherUpdater().updateRootProjectDirectories(rootProjectDirectoriesForWatching);
            delegatingUpdateFunctionDecorator.setSnapshotDiffListener(snapshotDiffListener, this::withWatcherChangeErrorHandling);
            long endTime = System.currentTimeMillis() - startTime;
            LOGGER.warn("Spent {} ms registering watches for file system events", endTime);
            // TODO: Move start watching early enough so that the root is always empty
            return currentRoot.empty();
        } catch (Exception ex) {
            logWatchingError(ex, FILE_WATCHING_ERROR_MESSAGE_DURING_BUILD);
            closeUnderLock();
            return currentRoot.empty();
        }
    }

    private SnapshotHierarchy withWatcherChangeErrorHandling(SnapshotHierarchy currentRoot, Runnable runnable) {
        try {
            runnable.run();
            return currentRoot;
        } catch (Exception ex) {
            logWatchingError(ex, FILE_WATCHING_ERROR_MESSAGE_DURING_BUILD);
            return stopWatchingAndInvalidateHierarchy(currentRoot);
        }
    }

    private void logWatchingError(Exception exception, String fileWatchingErrorMessage) {
        if (exception instanceof InotifyInstanceLimitTooLowException) {
            LOGGER.warn("{}. The inotify instance limit is too low. See {} for more details.",
                fileWatchingErrorMessage,
                daemonDocumentationIndex.getLinkToSection("sec:inotify_instances_limit")
            );
        } else if (exception instanceof InotifyWatchesLimitTooLowException) {
            LOGGER.warn("{}. The inotify watches limit is too low.", fileWatchingErrorMessage);
        } else if (exception instanceof WatchingNotSupportedException) {
            // No stacktrace here, since this is a known shortcoming of our implementation
            LOGGER.warn("{}. {}.", fileWatchingErrorMessage, exception.getMessage());
        } else {
            LOGGER.warn(fileWatchingErrorMessage, exception);
        }
        reasonForNotWatchingFiles = exception;
    }

    /**
     * Stop watching the known areas of the file system, and invalidate
     * the parts that have been changed since calling {@link #startWatching(SnapshotHierarchy)}}.
     */
    private void stopWatchingAndInvalidateHierarchy() {
        virtualFileSystem.getRoot().update(this::stopWatchingAndInvalidateHierarchy);
    }

    private SnapshotHierarchy stopWatchingAndInvalidateHierarchy(SnapshotHierarchy currentRoot) {
        if (watchRegistry != null) {
            try {
                FileWatcherRegistry toBeClosed = watchRegistry;
                watchRegistry = null;
                delegatingUpdateFunctionDecorator.stopListening();
                toBeClosed.close();
            } catch (IOException ex) {
                LOGGER.error("Unable to close file watcher registry", ex);
            }
        }
        return currentRoot.empty();
    }

    private SnapshotHierarchy handleWatcherRegistryEvents(SnapshotHierarchy currentRoot, String eventsFor) {
        if (watchRegistry == null) {
            return currentRoot.empty();
        }
        FileWatcherRegistry.FileWatchingStatistics statistics = watchRegistry.getAndResetStatistics();
        LOGGER.warn("Received {} file system events {}", statistics.getNumberOfReceivedEvents(), eventsFor);
        if (statistics.isUnknownEventEncountered()) {
            LOGGER.warn("Dropped VFS state due to lost state");
            return stopWatchingAndInvalidateHierarchy(currentRoot);
        }
        if (statistics.getErrorWhileReceivingFileChanges().isPresent()) {
            LOGGER.warn("Dropped VFS state due to error while receiving file changes", statistics.getErrorWhileReceivingFileChanges().get());
            return stopWatchingAndInvalidateHierarchy(currentRoot);
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
    public void close() {
        virtualFileSystem.getRoot().update(currentRoot -> {
            closeUnderLock();
            return currentRoot.empty();
        });
    }

    private void closeUnderLock() {
        if (watchRegistry != null) {
            try {
                watchRegistry.close();
            } catch (IOException ex) {
                LOGGER.error("Couldn't close watch service", ex);
            } finally {
                watchRegistry = null;
            }
        }
    }

    private class SymlinkRemovingFileSystemSnapshotVisitor implements FileSystemSnapshotVisitor {
        private SnapshotHierarchy root;

        public SymlinkRemovingFileSystemSnapshotVisitor(SnapshotHierarchy root) {
            this.root = root;
        }

        @Override
        public boolean preVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
            boolean accessedViaSymlink = directorySnapshot.getAccessType() == AccessType.VIA_SYMLINK;
            if (accessedViaSymlink) {
                invalidateSymlink(directorySnapshot);
            }
            return !accessedViaSymlink;
        }

        @Override
        public void visitFile(CompleteFileSystemLocationSnapshot fileSnapshot) {
            if (fileSnapshot.getAccessType() == AccessType.VIA_SYMLINK) {
                invalidateSymlink(fileSnapshot);
            }
        }

        @Override
        public void postVisitDirectory(CompleteDirectorySnapshot directorySnapshot) {
        }

        private void invalidateSymlink(CompleteFileSystemLocationSnapshot snapshot) {
            root = delegatingUpdateFunctionDecorator
                .decorate((root, diffListener) -> root.invalidate(snapshot.getAbsolutePath(), diffListener))
                .updateRoot(root);
        }

        public SnapshotHierarchy getRootWithSymlinksRemoved() {
            return root;
        }
    }
}
