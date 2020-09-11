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

import net.rubygrapefruit.platform.internal.jni.InotifyInstanceLimitTooLowException;
import net.rubygrapefruit.platform.internal.jni.InotifyWatchesLimitTooLowException;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.snapshot.FileSystemNode;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.vfs.impl.VfsRootReference;
import org.gradle.internal.watch.WatchingNotSupportedException;
import org.gradle.internal.watch.registry.FileWatcherRegistry;
import org.gradle.internal.watch.registry.FileWatcherRegistryFactory;
import org.gradle.internal.watch.registry.SnapshotCollectingDiffListener;
import org.gradle.internal.watch.registry.impl.DaemonDocumentationIndex;
import org.gradle.internal.watch.vfs.BuildFinishedFileSystemWatchingBuildOperationType;
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem;
import org.gradle.internal.watch.vfs.BuildStartedFileSystemWatchingBuildOperationType;
import org.gradle.internal.watch.vfs.FileSystemWatchingStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class WatchingVirtualFileSystem implements BuildLifecycleAwareVirtualFileSystem, Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WatchingVirtualFileSystem.class);
    private static final String FILE_WATCHING_ERROR_MESSAGE_DURING_BUILD = "Unable to watch the file system for changes";
    private static final String FILE_WATCHING_ERROR_MESSAGE_AT_END_OF_BUILD = "Gradle was unable to watch the file system for changes";

    private final FileWatcherRegistryFactory watcherRegistryFactory;
    private final VfsRootReference rootReference;
    private final DaemonDocumentationIndex daemonDocumentationIndex;
    private final LocationsWrittenByCurrentBuild locationsWrittenByCurrentBuild;
    private final Set<File> watchableHierarchies = new HashSet<>();

    private FileWatcherRegistry watchRegistry;
    private Exception reasonForNotWatchingFiles;

    public WatchingVirtualFileSystem(
        FileWatcherRegistryFactory watcherRegistryFactory,
        VfsRootReference rootReference,
        DaemonDocumentationIndex daemonDocumentationIndex,
        LocationsWrittenByCurrentBuild locationsWrittenByCurrentBuild
    ) {
        this.watcherRegistryFactory = watcherRegistryFactory;
        this.rootReference = rootReference;
        this.daemonDocumentationIndex = daemonDocumentationIndex;
        this.locationsWrittenByCurrentBuild = locationsWrittenByCurrentBuild;
    }

    @Override
    public SnapshotHierarchy getRoot() {
        return rootReference.getRoot();
    }

    @Override
    public void update(UpdateFunction updateFunction) {
        rootReference.update(currentRoot -> updateRootNotifyingWatchers(currentRoot, updateFunction));
    }

    private SnapshotHierarchy updateRootNotifyingWatchers(SnapshotHierarchy currentRoot, UpdateFunction updateFunction) {
        if (watchRegistry == null) {
            return updateFunction.update(currentRoot, SnapshotHierarchy.NodeDiffListener.NOOP);
        } else {
            SnapshotCollectingDiffListener diffListener = new SnapshotCollectingDiffListener();
            SnapshotHierarchy newRoot = updateFunction.update(currentRoot, diffListener);
            return withWatcherChangeErrorHandling(newRoot, () -> diffListener.publishSnapshotDiff((removedSnapshots, addedSnapshots) ->
                watchRegistry.virtualFileSystemContentsChanged(removedSnapshots, addedSnapshots, newRoot)
            ));
        }
    }

    @Override
    public void afterBuildStarted(boolean watchingEnabled, VfsLogging vfsLogging, WatchLogging watchLogging, BuildOperationRunner buildOperationRunner) {
        reasonForNotWatchingFiles = null;
        rootReference.update(currentRoot -> buildOperationRunner.call(new CallableBuildOperation<SnapshotHierarchy>() {
            @Override
            public SnapshotHierarchy call(BuildOperationContext context) {
                if (watchingEnabled) {
                    SnapshotHierarchy newRoot;
                    FileSystemWatchingStatistics statisticsSinceLastBuild;
                    if (watchRegistry == null) {
                        context.setStatus("Starting file system watching");
                        startWatching(currentRoot);
                        newRoot = currentRoot.empty();
                        statisticsSinceLastBuild = null;
                    } else {
                        FileWatcherRegistry.FileWatchingStatistics statistics = watchRegistry.getAndResetStatistics();
                        if (hasDroppedStateBecauseOfErrorsReceivedWhileWatching(statistics)) {
                            newRoot = stopWatchingAndInvalidateHierarchy(currentRoot);
                        } else {
                            newRoot = currentRoot;
                        }
                        statisticsSinceLastBuild = new DefaultFileSystemWatchingStatistics(statistics, newRoot);
                        if (vfsLogging == VfsLogging.VERBOSE) {
                            LOGGER.warn("Received {} file system events since last build while watching {} hierarchies",
                                statisticsSinceLastBuild.getNumberOfReceivedEvents(),
                                statisticsSinceLastBuild.getNumberOfWatchedHierarchies());
                            LOGGER.warn("Virtual file system retained information about {} files, {} directories and {} missing files since last build",
                                statisticsSinceLastBuild.getRetainedRegularFiles(),
                                statisticsSinceLastBuild.getRetainedDirectories(),
                                statisticsSinceLastBuild.getRetainedMissingFiles()
                            );
                        }
                    }
                    if (watchRegistry != null) {
                        watchRegistry.setDebugLoggingEnabled(watchLogging == WatchLogging.DEBUG);
                    }
                    context.setResult(new BuildStartedFileSystemWatchingBuildOperationType.Result() {
                                          @Override
                                          public boolean isWatchingEnabled() {
                                              return true;
                                          }

                                          @Override
                                          public boolean isStartedWatching() {
                                              return statisticsSinceLastBuild == null;
                                          }

                                          @Override
                                          public FileSystemWatchingStatistics getStatistics() {
                                              return statisticsSinceLastBuild;
                                          }
                                      }
                    );
                    return newRoot;
                } else {
                    context.setResult(BuildStartedFileSystemWatchingBuildOperationType.Result.WATCHING_DISABLED);
                    return stopWatchingAndInvalidateHierarchy(currentRoot);
                }
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName(BuildStartedFileSystemWatchingBuildOperationType.DISPLAY_NAME)
                    .details(BuildStartedFileSystemWatchingBuildOperationType.Details.INSTANCE);
            }
        }));
    }

    @Override
    public void registerWatchableHierarchy(File watchableHierarchy) {
        rootReference.update(currentRoot -> {
            if (watchRegistry == null) {
                watchableHierarchies.add(watchableHierarchy);
                return currentRoot;
            }
            return withWatcherChangeErrorHandling(
                currentRoot,
                () -> watchRegistry.registerWatchableHierarchy(watchableHierarchy, currentRoot)
            );
        });
    }

    @Override
    public void beforeBuildFinished(boolean watchingEnabled, VfsLogging vfsLogging, WatchLogging watchLogging, BuildOperationRunner buildOperationRunner, int maximumNumberOfWatchedHierarchies) {
        rootReference.update(currentRoot -> buildOperationRunner.call(new CallableBuildOperation<SnapshotHierarchy>() {
            @Override
            public SnapshotHierarchy call(BuildOperationContext context) {
                watchableHierarchies.clear();
                if (watchingEnabled) {
                    if (reasonForNotWatchingFiles != null) {
                        // Log exception again so it doesn't get lost.
                        logWatchingError(reasonForNotWatchingFiles, FILE_WATCHING_ERROR_MESSAGE_AT_END_OF_BUILD);
                        reasonForNotWatchingFiles = null;
                    }
                    SnapshotHierarchy newRoot;
                    FileSystemWatchingStatistics statisticsDuringBuild;
                    if (watchRegistry == null) {
                        statisticsDuringBuild = null;
                        newRoot = currentRoot.empty();
                    } else {
                        FileWatcherRegistry.FileWatchingStatistics statistics = watchRegistry.getAndResetStatistics();
                        if (hasDroppedStateBecauseOfErrorsReceivedWhileWatching(statistics)) {
                            newRoot = stopWatchingAndInvalidateHierarchy(currentRoot);
                        } else {
                            newRoot = withWatcherChangeErrorHandling(currentRoot, () -> watchRegistry.buildFinished(currentRoot, maximumNumberOfWatchedHierarchies));
                        }
                        statisticsDuringBuild = new DefaultFileSystemWatchingStatistics(statistics, newRoot);
                        if (vfsLogging == VfsLogging.VERBOSE) {
                            LOGGER.warn("Received {} file system events during the current build while watching {} hierarchies",
                                statisticsDuringBuild.getNumberOfReceivedEvents(),
                                statisticsDuringBuild.getNumberOfWatchedHierarchies());
                            LOGGER.warn("Virtual file system retains information about {} files, {} directories and {} missing files until next build",
                                statisticsDuringBuild.getRetainedRegularFiles(),
                                statisticsDuringBuild.getRetainedDirectories(),
                                statisticsDuringBuild.getRetainedMissingFiles()
                            );
                        }
                    }
                    boolean stoppedWatchingDuringTheBuild = watchRegistry == null;
                    context.setResult(new BuildFinishedFileSystemWatchingBuildOperationType.Result() {
                        @Override
                        public boolean isWatchingEnabled() {
                            return true;
                        }

                        @Override
                        public boolean isStoppedWatchingDuringTheBuild() {
                            return stoppedWatchingDuringTheBuild;
                        }

                        @Override
                        public FileSystemWatchingStatistics getStatistics() {
                            return statisticsDuringBuild;
                        }
                    });
                    return newRoot;
                } else {
                    context.setResult(BuildFinishedFileSystemWatchingBuildOperationType.Result.WATCHING_DISABLED);
                    return currentRoot.empty();
                }
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName(BuildFinishedFileSystemWatchingBuildOperationType.DISPLAY_NAME)
                    .details(BuildFinishedFileSystemWatchingBuildOperationType.Details.INSTANCE);
            }
        }));
    }

    /**
     * Start watching the known areas of the file system for changes.
     */
    private void startWatching(SnapshotHierarchy currentRoot) {
        try {
            watchRegistry = watcherRegistryFactory.createFileWatcherRegistry(new FileWatcherRegistry.ChangeHandler() {
                @Override
                public void handleChange(FileWatcherRegistry.Type type, Path path) {
                    try {
                        String absolutePath = path.toString();
                        if (!locationsWrittenByCurrentBuild.wasLocationWritten(absolutePath)) {
                            update((root, diffListener) -> root.invalidate(absolutePath, new VfsChangeLoggingNodeDiffListener(type, path, diffListener)));
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
            watchableHierarchies.forEach(watchableHierarchy -> watchRegistry.registerWatchableHierarchy(watchableHierarchy, currentRoot));
            watchableHierarchies.clear();
        } catch (Exception ex) {
            logWatchingError(ex, FILE_WATCHING_ERROR_MESSAGE_DURING_BUILD);
            closeUnderLock();
        }
    }

    private static class VfsChangeLoggingNodeDiffListener implements SnapshotHierarchy.NodeDiffListener {
        private final FileWatcherRegistry.Type type;
        private final Path path;
        private final SnapshotHierarchy.NodeDiffListener delegate;
        private boolean alreadyLogged;

        public VfsChangeLoggingNodeDiffListener(FileWatcherRegistry.Type type, Path path, SnapshotHierarchy.NodeDiffListener delegate) {
            this.type = type;
            this.path = path;
            this.delegate = delegate;
        }

        @Override
        public void nodeRemoved(FileSystemNode node) {
            maybeLogVfsChangeMessage();
            delegate.nodeRemoved(node);
        }

        @Override
        public void nodeAdded(FileSystemNode node) {
            maybeLogVfsChangeMessage();
            delegate.nodeAdded(node);
        }

        private void maybeLogVfsChangeMessage() {
            if (!alreadyLogged) {
                alreadyLogged = true;
                LOGGER.debug("Handling VFS change {} {}", type, path);
            }
        }
    }

    private SnapshotHierarchy withWatcherChangeErrorHandling(SnapshotHierarchy currentRoot, Runnable runnable) {
        return withWatcherChangeErrorHandling(currentRoot, () -> {
            runnable.run();
            return currentRoot;
        });
    }

    private SnapshotHierarchy withWatcherChangeErrorHandling(SnapshotHierarchy currentRoot, Supplier<SnapshotHierarchy> supplier) {
        try {
            return supplier.get();
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
        rootReference.update(this::stopWatchingAndInvalidateHierarchy);
    }

    private SnapshotHierarchy stopWatchingAndInvalidateHierarchy(SnapshotHierarchy currentRoot) {
        if (watchRegistry != null) {
            try {
                FileWatcherRegistry toBeClosed = watchRegistry;
                watchRegistry = null;
                toBeClosed.close();
            } catch (IOException ex) {
                LOGGER.error("Unable to close file watcher registry", ex);
            }
        }
        return currentRoot.empty();
    }

    private boolean hasDroppedStateBecauseOfErrorsReceivedWhileWatching(FileWatcherRegistry.FileWatchingStatistics statistics) {
        if (statistics.isUnknownEventEncountered()) {
            LOGGER.warn("Dropped VFS state due to lost state");
            return true;
        }
        if (statistics.getErrorWhileReceivingFileChanges().isPresent()) {
            LOGGER.warn("Dropped VFS state due to error while receiving file changes", statistics.getErrorWhileReceivingFileChanges().get());
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        rootReference.update(currentRoot -> {
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
}
