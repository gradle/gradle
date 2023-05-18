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

import com.google.common.collect.ImmutableList;
import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.internal.jni.InotifyInstanceLimitTooLowException;
import net.rubygrapefruit.platform.internal.jni.InotifyWatchesLimitTooLowException;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.snapshot.FileSystemNode;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.vfs.impl.AbstractVirtualFileSystem;
import org.gradle.internal.watch.WatchingNotSupportedException;
import org.gradle.internal.watch.registry.FileWatcherRegistry;
import org.gradle.internal.watch.registry.FileWatcherRegistryFactory;
import org.gradle.internal.watch.registry.WatchMode;
import org.gradle.internal.watch.registry.impl.DaemonDocumentationIndex;
import org.gradle.internal.watch.registry.impl.SnapshotCollectingDiffListener;
import org.gradle.internal.watch.vfs.BuildFinishedFileSystemWatchingBuildOperationType;
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem;
import org.gradle.internal.watch.vfs.BuildStartedFileSystemWatchingBuildOperationType;
import org.gradle.internal.watch.vfs.FileChangeListeners;
import org.gradle.internal.watch.vfs.FileSystemWatchingInformation;
import org.gradle.internal.watch.vfs.FileSystemWatchingStatistics;
import org.gradle.internal.watch.vfs.VfsLogging;
import org.gradle.internal.watch.vfs.WatchLogging;
import org.gradle.internal.watch.vfs.WatchableFileSystemDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class WatchingVirtualFileSystem extends AbstractVirtualFileSystem implements BuildLifecycleAwareVirtualFileSystem, FileSystemWatchingInformation, Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WatchingVirtualFileSystem.class);
    private static final String FILE_WATCHING_ERROR_MESSAGE_DURING_BUILD = "Unable to watch the file system for changes";
    private static final String FILE_WATCHING_ERROR_MESSAGE_AT_END_OF_BUILD = "Gradle was unable to watch the file system for changes";

    private final FileWatcherRegistryFactory watcherRegistryFactory;
    private final DaemonDocumentationIndex daemonDocumentationIndex;
    private final LocationsWrittenByCurrentBuild locationsWrittenByCurrentBuild;
    private final WatchableFileSystemDetector watchableFileSystemDetector;
    private final FileChangeListeners fileChangeListeners;
    private final List<File> unsupportedFileSystems = new ArrayList<>();
    private Logger warningLogger = LOGGER;

    /**
     * Watchable hierarchies registered before the {@link FileWatcherRegistry} has been started.
     */
    private final Set<File> watchableHierarchiesRegisteredEarly = new LinkedHashSet<>();

    private FileWatcherRegistry watchRegistry;
    private Exception reasonForNotWatchingFiles;
    private boolean stateInvalidatedAtStartOfBuild;

    public WatchingVirtualFileSystem(
        FileWatcherRegistryFactory watcherRegistryFactory,
        SnapshotHierarchy root,
        DaemonDocumentationIndex daemonDocumentationIndex,
        LocationsWrittenByCurrentBuild locationsWrittenByCurrentBuild,
        WatchableFileSystemDetector watchableFileSystemDetector,
        FileChangeListeners fileChangeListeners
    ) {
        super(root);
        this.watcherRegistryFactory = watcherRegistryFactory;
        this.daemonDocumentationIndex = daemonDocumentationIndex;
        this.locationsWrittenByCurrentBuild = locationsWrittenByCurrentBuild;
        this.watchableFileSystemDetector = watchableFileSystemDetector;
        this.fileChangeListeners = fileChangeListeners;
    }

    @Override
    protected SnapshotHierarchy updateNotifyingListeners(UpdateFunction updateFunction) {
        if (watchRegistry == null) {
            return updateFunction.update(SnapshotHierarchy.NodeDiffListener.NOOP);
        } else {
            SnapshotCollectingDiffListener diffListener = new SnapshotCollectingDiffListener();
            SnapshotHierarchy newRoot = updateFunction.update(diffListener);
            return withWatcherChangeErrorHandling(newRoot, () -> diffListener.publishSnapshotDiff((removedSnapshots, addedSnapshots) ->
                watchRegistry.virtualFileSystemContentsChanged(removedSnapshots, addedSnapshots, newRoot)
            ));
        }
    }

    @Override
    public boolean afterBuildStarted(
        WatchMode watchMode,
        VfsLogging vfsLogging,
        WatchLogging watchLogging,
        BuildOperationRunner buildOperationRunner
    ) {
        warningLogger = watchMode.loggerForWarnings(LOGGER);
        stateInvalidatedAtStartOfBuild = false;
        reasonForNotWatchingFiles = null;
        updateRootUnderLock(currentRoot -> buildOperationRunner.call(new CallableBuildOperation<SnapshotHierarchy>() {
            @Override
            public SnapshotHierarchy call(BuildOperationContext context) {
                if (watchMode.isEnabled()) {
                    SnapshotHierarchy newRoot;
                    boolean couldDetectUnsupportedFileSystems;
                    try {
                        unsupportedFileSystems.clear();
                        if (watchMode == WatchMode.DEFAULT) {
                            watchableFileSystemDetector.detectUnsupportedFileSystems().forEach(unsupportedFileSystems::add);
                        }
                        couldDetectUnsupportedFileSystems = true;
                    } catch (NativeException e) {
                        couldDetectUnsupportedFileSystems = false;
                        LOGGER.info("Unable to list file systems to check whether they can be watched. Disabling watching. Reason: {}", e.getMessage());
                    }
                    FileSystemWatchingStatistics statisticsSinceLastBuild;
                    if (watchRegistry == null) {
                        if (couldDetectUnsupportedFileSystems) {
                            context.setStatus("Starting file system watching");
                            newRoot = startWatching(currentRoot, watchMode, unsupportedFileSystems);
                        } else {
                            newRoot = currentRoot.empty();
                        }
                        statisticsSinceLastBuild = null;
                    } else {
                        FileWatcherRegistry.FileWatchingStatistics statistics = watchRegistry.getAndResetStatistics();
                        if (hasDroppedStateBecauseOfErrorsReceivedWhileWatching(statistics) || !couldDetectUnsupportedFileSystems) {
                            newRoot = stopWatchingAndInvalidateHierarchyAfterError(currentRoot);
                        } else {
                            newRoot = watchRegistry.updateVfsOnBuildStarted(currentRoot, watchMode, unsupportedFileSystems);
                        }
                        stateInvalidatedAtStartOfBuild = newRoot != currentRoot;
                        statisticsSinceLastBuild = new DefaultFileSystemWatchingStatistics(statistics, newRoot);
                        if (vfsLogging == VfsLogging.VERBOSE) {
                            LOGGER.warn("Received {} file system events since last build while watching {} locations",
                                statisticsSinceLastBuild.getNumberOfReceivedEvents(),
                                statisticsSinceLastBuild.getNumberOfWatchedHierarchies());
                            LOGGER.warn("Virtual file system retained information about {} files, {} directories and {} missing files since last build",
                                statisticsSinceLastBuild.getRetainedRegularFiles(),
                                statisticsSinceLastBuild.getRetainedDirectories(),
                                statisticsSinceLastBuild.getRetainedMissingFiles()
                            );
                            if (stateInvalidatedAtStartOfBuild) {
                                LOGGER.warn("Parts of the virtual file system have been invalidated since they didn't support watching");
                            }
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
        return watchRegistry != null;
    }

    @Override
    public void registerWatchableHierarchy(File watchableHierarchy) {
        updateRootUnderLock(currentRoot -> {
            if (watchRegistry == null) {
                watchableHierarchiesRegisteredEarly.add(watchableHierarchy);
                return currentRoot;
            }
            return withWatcherChangeErrorHandling(
                currentRoot,
                () -> watchRegistry.registerWatchableHierarchy(watchableHierarchy, currentRoot)
            );
        });
    }

    @Override
    public void beforeBuildFinished(
        WatchMode watchMode,
        VfsLogging vfsLogging,
        WatchLogging watchLogging,
        BuildOperationRunner buildOperationRunner,
        int maximumNumberOfWatchedHierarchies
    ) {
        updateRootUnderLock(currentRoot -> buildOperationRunner.call(new CallableBuildOperation<SnapshotHierarchy>() {
            @Override
            public SnapshotHierarchy call(BuildOperationContext context) {
                watchableHierarchiesRegisteredEarly.clear();
                if (watchMode.isEnabled()) {
                    if (reasonForNotWatchingFiles != null) {
                        // Log exception again so it doesn't get lost.
                        logWatchingError(reasonForNotWatchingFiles, FILE_WATCHING_ERROR_MESSAGE_AT_END_OF_BUILD, watchMode);
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
                            newRoot = stopWatchingAndInvalidateHierarchyAfterError(currentRoot);
                        } else {
                            newRoot = withWatcherChangeErrorHandling(currentRoot, () -> watchRegistry.updateVfsOnBuildFinished(currentRoot, watchMode, maximumNumberOfWatchedHierarchies, unsupportedFileSystems));
                        }
                        statisticsDuringBuild = new DefaultFileSystemWatchingStatistics(statistics, newRoot);
                        if (vfsLogging == VfsLogging.VERBOSE) {
                            LOGGER.warn("Received {} file system events during the current build while watching {} locations",
                                statisticsDuringBuild.getNumberOfReceivedEvents(),
                                statisticsDuringBuild.getNumberOfWatchedHierarchies());
                            LOGGER.warn("Virtual file system retains information about {} files, {} directories and {} missing files until next build",
                                statisticsDuringBuild.getRetainedRegularFiles(),
                                statisticsDuringBuild.getRetainedDirectories(),
                                statisticsDuringBuild.getRetainedMissingFiles()
                            );
                            if (stateInvalidatedAtStartOfBuild) {
                                LOGGER.warn("Parts of the virtual file system have been removed at the start of the build since they didn't support watching");
                            }
                        }
                    }
                    boolean stoppedWatchingDuringTheBuild = watchRegistry == null;
                    context.setResult(new BuildFinishedFileSystemWatchingBuildOperationType.Result() {
                        private final boolean stateInvalidatedAtStartOfBuild = WatchingVirtualFileSystem.this.stateInvalidatedAtStartOfBuild;

                        @Override
                        public boolean isWatchingEnabled() {
                            return true;
                        }

                        @Override
                        public boolean isStoppedWatchingDuringTheBuild() {
                            return stoppedWatchingDuringTheBuild;
                        }

                        @Override
                        public boolean isStateInvalidatedAtStartOfBuild() {
                            return stateInvalidatedAtStartOfBuild;
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
        // Log problems to daemon log
        warningLogger = LOGGER;
    }

    /**
     * Start watching the known areas of the file system for changes.
     */
    @CheckReturnValue
    private SnapshotHierarchy startWatching(SnapshotHierarchy currentRoot, WatchMode watchMode, List<File> unsupportedFileSystems) {
        try {
            watchRegistry = watcherRegistryFactory.createFileWatcherRegistry(new FilterChangesToOutputsChangesHandler(locationsWrittenByCurrentBuild,
                new CompositeChangeHandler(
                    new InvalidateVfsChangeHandler(),
                    new BroadcastingChangeHandler()
                )));
            SnapshotHierarchy newRoot = watchRegistry.updateVfsOnBuildStarted(currentRoot.empty(), watchMode, unsupportedFileSystems);
            watchableHierarchiesRegisteredEarly.forEach(watchableHierarchy -> watchRegistry.registerWatchableHierarchy(watchableHierarchy, newRoot));
            watchableHierarchiesRegisteredEarly.clear();
            return newRoot;
        } catch (Exception ex) {
            logWatchingError(ex, FILE_WATCHING_ERROR_MESSAGE_DURING_BUILD, null);
            closeUnderLock();
            return currentRoot.empty();
        }
    }

    @Override
    public boolean isWatchingAnyLocations() {
        FileWatcherRegistry watchRegistry = this.watchRegistry;
        if (watchRegistry != null) {
            return watchRegistry.isWatchingAnyLocations();
        }
        return false;
    }

    private static class FilterChangesToOutputsChangesHandler implements FileWatcherRegistry.ChangeHandler {
        private final LocationsWrittenByCurrentBuild locationsWrittenByCurrentBuild;
        private final FileWatcherRegistry.ChangeHandler delegate;

        public FilterChangesToOutputsChangesHandler(LocationsWrittenByCurrentBuild locationsWrittenByCurrentBuild, FileWatcherRegistry.ChangeHandler delegate) {
            this.locationsWrittenByCurrentBuild = locationsWrittenByCurrentBuild;
            this.delegate = delegate;
        }

        @Override
        public void handleChange(FileWatcherRegistry.Type type, Path path) {
            if (!locationsWrittenByCurrentBuild.wasLocationWritten(path.toString())) {
                delegate.handleChange(type, path);
            }
        }

        @Override
        public void stopWatchingAfterError() {
            delegate.stopWatchingAfterError();
        }
    }

    private class InvalidateVfsChangeHandler implements FileWatcherRegistry.ChangeHandler {
        @Override
        public void handleChange(FileWatcherRegistry.Type type, Path path) {
            updateRootUnderLock(root -> updateNotifyingListeners(
                diffListener -> root.invalidate(path.toString(), new VfsChangeLoggingNodeDiffListener(type, path, diffListener))
            ));
        }

        @Override
        public void stopWatchingAfterError() {
            stopWatchingAndInvalidateHierarchyAfterError();
        }
    }

    private class BroadcastingChangeHandler implements FileWatcherRegistry.ChangeHandler {
        @Override
        public void handleChange(FileWatcherRegistry.Type type, Path path) {
            fileChangeListeners.broadcastChange(type, path);
        }

        @Override
        public void stopWatchingAfterError() {
            fileChangeListeners.broadcastWatchingError();
        }
    }

    private static class CompositeChangeHandler implements FileWatcherRegistry.ChangeHandler {
        private final List<FileWatcherRegistry.ChangeHandler> handlers;

        public CompositeChangeHandler(FileWatcherRegistry.ChangeHandler... handlers) {
            this.handlers = ImmutableList.copyOf(handlers);
        }

        @Override
        public void handleChange(FileWatcherRegistry.Type type, Path path) {
            handlers.forEach(handler -> handler.handleChange(type, path));
        }

        @Override
        public void stopWatchingAfterError() {
            handlers.forEach(FileWatcherRegistry.ChangeHandler::stopWatchingAfterError);
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
            logWatchingError(ex, FILE_WATCHING_ERROR_MESSAGE_DURING_BUILD, null);
            return stopWatchingAndInvalidateHierarchyAfterError(currentRoot);
        }
    }

    private void logWatchingError(Exception exception, String fileWatchingErrorMessage, @Nullable WatchMode watchMode) {
        if (exception instanceof InotifyInstanceLimitTooLowException) {
            warningLogger.warn("{}. The inotify instance limit is too low. {}",
                fileWatchingErrorMessage,
                daemonDocumentationIndex.getLinkToSection("sec:inotify_instances_limit")
            );
        } else if (exception instanceof InotifyWatchesLimitTooLowException) {
            warningLogger.warn("{}. The inotify watches limit is too low.", fileWatchingErrorMessage);
        } else if (exception instanceof WatchingNotSupportedException) {
            // No stacktrace here, since this is a known shortcoming of our implementation
            warningLogger.warn("{}. {}.", fileWatchingErrorMessage, exception.getMessage());
        } else {
            warningLogger.warn(fileWatchingErrorMessage, exception);
        }
        reasonForNotWatchingFiles = exception;
    }

    /**
     * Stop watching the known areas of the file system, and invalidate
     * the parts that have been changed since calling {@link #startWatching(SnapshotHierarchy, WatchMode, List)}}.
     */
    private void stopWatchingAndInvalidateHierarchyAfterError() {
        updateRootUnderLock(this::stopWatchingAndInvalidateHierarchyAfterError);
    }

    private SnapshotHierarchy stopWatchingAndInvalidateHierarchyAfterError(SnapshotHierarchy currentRoot) {
        warningLogger.error("Stopping file watching and invalidating VFS after an error happened");
        return stopWatchingAndInvalidateHierarchy(currentRoot);
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
            warningLogger.warn("Dropped VFS state due to lost state");
            return true;
        }
        if (statistics.getErrorWhileReceivingFileChanges().isPresent()) {
            warningLogger.warn("Dropped VFS state due to error while receiving file changes", statistics.getErrorWhileReceivingFileChanges().get());
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        LOGGER.debug("Closing VFS, dropping state");
        updateRootUnderLock(currentRoot -> {
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
