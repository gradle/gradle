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
import org.gradle.internal.snapshot.AtomicSnapshotHierarchyReference;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.snapshot.ReadOnlyVfsRoot;
import org.gradle.internal.snapshot.VfsRoot;
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

public class DefaultFileSystemWatchingHandler implements FileSystemWatchingHandler, Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultFileSystemWatchingHandler.class);
    private static final String FILE_WATCHING_ERROR_MESSAGE_DURING_BUILD = "Unable to watch the file system for changes";
    private static final String FILE_WATCHING_ERROR_MESSAGE_AT_END_OF_BUILD = "Gradle was unable to watch the file system for changes";

    private final FileWatcherRegistryFactory watcherRegistryFactory;
    private final AtomicSnapshotHierarchyReference root;
    private final NotifyingUpdateFunctionRunner updateFunctionRunner;
    private final DaemonDocumentationIndex daemonDocumentationIndex;
    private final LocationsUpdatedByCurrentBuild locationsUpdatedByCurrentBuild;
    private final Set<File> rootProjectDirectoriesForWatching = new HashSet<>();

    private FileWatcherRegistry watchRegistry;
    private Exception reasonForNotWatchingFiles;

    private final VfsRoot.SnapshotDiffListener snapshotDiffListener = (removedSnapshots, addedSnapshots) -> {
        if (watchRegistry != null) {
            watchRegistry.getFileWatcherUpdater().changed(removedSnapshots, addedSnapshots);
        }
    };

    public DefaultFileSystemWatchingHandler(
        FileWatcherRegistryFactory watcherRegistryFactory,
        AtomicSnapshotHierarchyReference root,
        NotifyingUpdateFunctionRunner updateFunctionRunner,
        DaemonDocumentationIndex daemonDocumentationIndex,
        LocationsUpdatedByCurrentBuild locationsUpdatedByCurrentBuild
    ) {
        this.watcherRegistryFactory = watcherRegistryFactory;
        this.root = root;
        this.updateFunctionRunner = updateFunctionRunner;
        this.daemonDocumentationIndex = daemonDocumentationIndex;
        this.locationsUpdatedByCurrentBuild = locationsUpdatedByCurrentBuild;
    }

    @Override
    public void afterBuildStarted(boolean watchingEnabled) {
        reasonForNotWatchingFiles = null;
        root.update(vfsRoot -> {
            if (watchingEnabled) {
                handleWatcherRegistryEvents(vfsRoot, "since last build");
                startWatching(vfsRoot);
                printStatistics(vfsRoot, "retained", "since last build");
            } else {
                stopWatchingAndInvalidateHierarchy(vfsRoot);
            }
        });
    }

    private void updateWatchRegistry(Consumer<FileWatcherRegistry> updateFunction) {
        updateWatchRegistry(updateFunction, () -> {});
    }

    private void updateWatchRegistry(Consumer<FileWatcherRegistry> updateFunction, Runnable noWatchRegistry) {
        root.update(currentRoot -> {
            if (watchRegistry == null) {
                noWatchRegistry.run();
            } else {
                withWatcherChangeErrorHandling(currentRoot, () -> updateFunction.accept(watchRegistry));
            }
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
            root.update(currentRoot -> {
                removeSymbolicLinks(currentRoot);
                handleWatcherRegistryEvents(currentRoot, "for current build");
                if (watchRegistry != null) {
                    withWatcherChangeErrorHandling(currentRoot, () -> watchRegistry.getFileWatcherUpdater().buildFinished());
                }
                printStatistics(currentRoot, "retains", "till next build");
            });
        } else {
            root.update(VfsRoot::invalidateAll);
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
    private void removeSymbolicLinks(VfsRoot currentRoot) {
        SymlinkRemovingFileSystemSnapshotVisitor symlinkRemovingFileSystemSnapshotVisitor = new SymlinkRemovingFileSystemSnapshotVisitor(currentRoot);
        currentRoot.visitSnapshotRoots(snapshotRoot -> snapshotRoot.accept(symlinkRemovingFileSystemSnapshotVisitor));
    }

    /**
     * Start watching the known areas of the file system for changes.
     */
    private void startWatching(VfsRoot currentRoot) {
        if (watchRegistry != null) {
            return;
        }
        try {
            long startTime = System.currentTimeMillis();
            currentRoot.invalidateAll();
            watchRegistry = watcherRegistryFactory.createFileWatcherRegistry(new FileWatcherRegistry.ChangeHandler() {
                @Override
                public void handleChange(FileWatcherRegistry.Type type, Path path) {
                    try {
                        LOGGER.debug("Handling VFS change {} {}", type, path);
                        String absolutePath = path.toString();
                        if (!locationsUpdatedByCurrentBuild.wasLocationUpdated(absolutePath)) {
                            root.update(root -> root.invalidate(absolutePath));
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
            updateFunctionRunner.setSnapshotDiffListener(snapshotDiffListener, this::withWatcherChangeErrorHandling);
            long endTime = System.currentTimeMillis() - startTime;
            LOGGER.warn("Spent {} ms registering watches for file system events", endTime);
            // TODO: Move start watching early enough so that the root is always empty
        } catch (Exception ex) {
            logWatchingError(ex, FILE_WATCHING_ERROR_MESSAGE_DURING_BUILD);
            closeUnderLock();
        }
    }

    private void withWatcherChangeErrorHandling(VfsRoot currentRoot, Runnable runnable) {
        try {
            runnable.run();
        } catch (Exception ex) {
            logWatchingError(ex, FILE_WATCHING_ERROR_MESSAGE_DURING_BUILD);
            stopWatchingAndInvalidateHierarchy(currentRoot);
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
     * the parts that have been changed since calling {@link #startWatching(VfsRoot)}}.
     */
    private void stopWatchingAndInvalidateHierarchy() {
        root.update(this::stopWatchingAndInvalidateHierarchy);
    }

    private void stopWatchingAndInvalidateHierarchy(VfsRoot currentRoot) {
        if (watchRegistry != null) {
            try {
                FileWatcherRegistry toBeClosed = watchRegistry;
                watchRegistry = null;
                updateFunctionRunner.stopListening();
                toBeClosed.close();
            } catch (IOException ex) {
                LOGGER.error("Unable to close file watcher registry", ex);
            }
        }
        currentRoot.invalidateAll();
    }

    private void handleWatcherRegistryEvents(VfsRoot currentRoot, String eventsFor) {
        if (watchRegistry == null) {
            currentRoot.invalidateAll();
            return;
        }
        FileWatcherRegistry.FileWatchingStatistics statistics = watchRegistry.getAndResetStatistics();
        LOGGER.warn("Received {} file system events {}", statistics.getNumberOfReceivedEvents(), eventsFor);
        if (statistics.isUnknownEventEncountered()) {
            LOGGER.warn("Dropped VFS state due to lost state");
            stopWatchingAndInvalidateHierarchy(currentRoot);
        } else if (statistics.getErrorWhileReceivingFileChanges().isPresent()) {
            LOGGER.warn("Dropped VFS state due to error while receiving file changes", statistics.getErrorWhileReceivingFileChanges().get());
            stopWatchingAndInvalidateHierarchy(currentRoot);
        }
    }

    private static void printStatistics(ReadOnlyVfsRoot root, String verb, String statisticsFor) {
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

    private static VirtualFileSystemStatistics getStatistics(ReadOnlyVfsRoot root) {
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
        root.update(currentRoot -> {
            closeUnderLock();
            currentRoot.invalidateAll();
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

    private static class SymlinkRemovingFileSystemSnapshotVisitor implements FileSystemSnapshotVisitor {
        private final VfsRoot root;

        public SymlinkRemovingFileSystemSnapshotVisitor(VfsRoot root) {
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
            root.invalidate(snapshot.getAbsolutePath());
        }
    }
}
