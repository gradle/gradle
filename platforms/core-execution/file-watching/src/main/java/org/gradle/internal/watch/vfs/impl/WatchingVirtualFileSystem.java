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
import org.gradle.fileevents.internal.InotifyInstanceLimitTooLowException;
import org.gradle.fileevents.internal.InotifyWatchesLimitTooLowException;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.snapshot.FileSystemNode;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.vfs.impl.AbstractVirtualFileSystem;
import org.gradle.internal.watch.WatchingNotSupportedException;
import org.gradle.internal.watch.registry.FileWatcherRegistry;
import org.gradle.internal.watch.registry.FileWatcherRegistryFactory;
import org.gradle.internal.watch.registry.WatchMode;
import org.gradle.internal.watch.registry.impl.FileSystemWatchingDocumentationIndex;
import org.gradle.internal.watch.registry.impl.SnapshotCollectingDiffListener;
import org.gradle.internal.watch.vfs.BuildFinishedFileSystemWatchingBuildOperationType;
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem;
import org.gradle.internal.watch.vfs.BuildStartedFileSystemWatchingBuildOperationType;
import org.gradle.internal.watch.vfs.FileChangeListeners;
import org.gradle.internal.watch.vfs.FileSystemWatchingStatistics;
import org.gradle.internal.watch.vfs.VfsLogging;
import org.gradle.internal.watch.vfs.WatchableFileSystemDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class WatchingVirtualFileSystem extends AbstractVirtualFileSystem implements BuildLifecycleAwareVirtualFileSystem, Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(WatchingVirtualFileSystem.class);
    private static final String FILE_WATCHING_ERROR_MESSAGE_DURING_BUILD = "Unable to watch the file system for changes";
    private static final String FILE_WATCHING_ERROR_MESSAGE_AT_END_OF_BUILD = "Gradle was unable to watch the file system for changes";

    private final FileWatcherRegistryFactory watcherRegistryFactory;
    private final FileSystemWatchingDocumentationIndex fileSystemWatchingDocumentationIndex;
    private final FileWatchingFilter locationsWrittenByCurrentBuild;
    private final WatchableFileSystemDetector watchableFileSystemDetector;
    private final FileChangeListeners fileChangeListeners;
    private final List<File> unsupportedFileSystems = new ArrayList<>();
    private Logger warningLogger = LOGGER;

    /**
     * Watchable hierarchies registered before the {@link FileWatcherRegistry} has been started.
     */
    private final Set<File> watchableHierarchiesRegisteredEarly = new LinkedHashSet<>();

    private volatile FileWatcherRegistry watchRegistry;
    private Exception reasonForNotWatchingFiles;
    private boolean stateInvalidatedAtStartOfBuild;

    public WatchingVirtualFileSystem(
        FileWatcherRegistryFactory watcherRegistryFactory,
        SnapshotHierarchy root,
        FileSystemWatchingDocumentationIndex fileSystemWatchingDocumentationIndex,
        FileWatchingFilter locationsWrittenByCurrentBuild,
        WatchableFileSystemDetector watchableFileSystemDetector,
        FileChangeListeners fileChangeListeners
    ) {
        super(root);
        this.watcherRegistryFactory = watcherRegistryFactory;
        this.fileSystemWatchingDocumentationIndex = fileSystemWatchingDocumentationIndex;
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
            try {
                diffListener.publishSnapshotDiff((removedSnapshots, addedSnapshots) ->
                    watchRegistry.virtualFileSystemContentsChanged(removedSnapshots, addedSnapshots, this::currentRoot)
                );
                return newRoot;
            } catch (Exception ex) {
                logWatchingError(ex, FILE_WATCHING_ERROR_MESSAGE_DURING_BUILD);
                stopWatchingAndInvalidateHierarchyAfterError();
                return currentRoot();
            }
        }
    }

    @Override
    public boolean afterBuildStarted(
        WatchMode watchMode,
        VfsLogging vfsLogging,
        BuildOperationRunner buildOperationRunner
    ) {
        warningLogger = watchMode.loggerForWarnings(LOGGER);
        stateInvalidatedAtStartOfBuild = false;
        reasonForNotWatchingFiles = null;
        SnapshotHierarchy currentRoot = currentRoot();
        buildOperationRunner.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                if (watchMode.isEnabled()) {
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
                            replaceRoot(currentRoot.empty());
                            startWatching(watchMode, unsupportedFileSystems);
                        } else {
                            replaceRoot(currentRoot.empty());
                        }
                        statisticsSinceLastBuild = null;
                    } else {
                        FileWatcherRegistry.FileWatchingStatistics statistics = watchRegistry.getAndResetStatistics();
                        if (hasDroppedStateBecauseOfErrorsReceivedWhileWatching(statistics) || !couldDetectUnsupportedFileSystems) {
                            closeUnderLock();
                            replaceRoot(currentRoot.empty());
                        } else {
                            List<String> pathsToInvalidate = watchRegistry.updateVfsOnBuildStarted(currentRoot, watchMode, unsupportedFileSystems);
                            stateInvalidatedAtStartOfBuild = !pathsToInvalidate.isEmpty();
                            invalidate(pathsToInvalidate);
                        }
                        statisticsSinceLastBuild = new DefaultFileSystemWatchingStatistics(statistics, currentRoot());
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
                } else {
                    context.setResult(BuildStartedFileSystemWatchingBuildOperationType.Result.WATCHING_DISABLED);
                    closeUnderLock();
                    replaceRoot(currentRoot.empty());
                }
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName(BuildStartedFileSystemWatchingBuildOperationType.DISPLAY_NAME)
                    .details(BuildStartedFileSystemWatchingBuildOperationType.Details.INSTANCE);
            }
        });
        return watchRegistry != null;
    }

    @Override
    public void registerWatchableHierarchy(File watchableHierarchy) {
        if (watchRegistry == null) {
            watchableHierarchiesRegisteredEarly.add(watchableHierarchy);
            return;
        }
        try {
            watchRegistry.registerWatchableHierarchy(watchableHierarchy, this::currentRoot);
        } catch (Exception ex) {
            logWatchingError(ex, FILE_WATCHING_ERROR_MESSAGE_DURING_BUILD);
            stopWatchingAndInvalidateHierarchyAfterError();
        }
    }

    @Override
    public void beforeBuildFinished(
        WatchMode watchMode,
        VfsLogging vfsLogging,
        BuildOperationRunner buildOperationRunner,
        int maximumNumberOfWatchedHierarchies
    ) {
        SnapshotHierarchy currentRoot = currentRoot();
        buildOperationRunner.run(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                watchableHierarchiesRegisteredEarly.clear();
                if (watchMode.isEnabled()) {
                    if (reasonForNotWatchingFiles != null) {
                        // Log exception again so it doesn't get lost.
                        logWatchingError(reasonForNotWatchingFiles, FILE_WATCHING_ERROR_MESSAGE_AT_END_OF_BUILD);
                        reasonForNotWatchingFiles = null;
                    }
                    FileSystemWatchingStatistics statisticsDuringBuild;
                    boolean stoppedWatchingDuringTheBuild;
                    if (watchRegistry == null) {
                        statisticsDuringBuild = null;
                        stoppedWatchingDuringTheBuild = true;
                        replaceRoot(currentRoot.empty());
                    } else {
                        FileWatcherRegistry.FileWatchingStatistics statistics = watchRegistry.getAndResetStatistics();
                        if (hasDroppedStateBecauseOfErrorsReceivedWhileWatching(statistics)) {
                            closeUnderLock();
                            replaceRoot(currentRoot.empty());
                            stoppedWatchingDuringTheBuild = true;
                        } else {
                            // We'll clean this up further after the daemon has finished with the build, see afterBuildFinished()
                            try {
                                List<String> pathsToInvalidate = watchRegistry.updateVfsBeforeBuildFinished(currentRoot, maximumNumberOfWatchedHierarchies, unsupportedFileSystems);
                                invalidate(pathsToInvalidate);
                            } catch (Exception ex) {
                                logWatchingError(ex, FILE_WATCHING_ERROR_MESSAGE_DURING_BUILD);
                                closeUnderLock();
                                replaceRoot(currentRoot.empty());
                            }
                            stoppedWatchingDuringTheBuild = false;
                        }
                        statisticsDuringBuild = new DefaultFileSystemWatchingStatistics(statistics, currentRoot());
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
                } else {
                    context.setResult(BuildFinishedFileSystemWatchingBuildOperationType.Result.WATCHING_DISABLED);
                    replaceRoot(currentRoot.empty());
                }
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName(BuildFinishedFileSystemWatchingBuildOperationType.DISPLAY_NAME)
                    .details(BuildFinishedFileSystemWatchingBuildOperationType.Details.INSTANCE);
            }
        });

        // Log problems to daemon log
        warningLogger = LOGGER;
    }

    @Override
    public void afterBuildFinished() {
        SnapshotHierarchy currentRoot = currentRoot();
        FileWatcherRegistry watchRegistry = this.watchRegistry;
        if (watchRegistry != null) {
            try {
                List<String> pathsToInvalidate = watchRegistry.updateVfsAfterBuildFinished(currentRoot);
                if (!pathsToInvalidate.isEmpty()) {
                    invalidate(pathsToInvalidate);
                }
            } catch (Exception ex) {
                logWatchingError(ex, FILE_WATCHING_ERROR_MESSAGE_DURING_BUILD);
                stopWatchingAndInvalidateHierarchyAfterError();
            }
        } else {
            // Drop everything if we can't watch the file system
            replaceRoot(currentRoot.empty());
        }
    }

    /**
     * Start watching the known areas of the file system for changes.
     * The caller must have already cleared the VFS before calling this method.
     */
    private void startWatching(WatchMode watchMode, List<File> unsupportedFileSystems) {
        try {
            watchRegistry = watcherRegistryFactory.createFileWatcherRegistry(new FilterChangesToOutputsChangesHandler(locationsWrittenByCurrentBuild,
                new CompositeChangeHandler(
                    new InvalidateVfsChangeHandler(),
                    new BroadcastingChangeHandler()
                )));
            // VFS is already empty (caller cleared the VFS), so there is nothing to clean up.
            // We still call updateVfsOnBuildStarted for its side effects (e.g. updating unwatchable file systems).
            watchRegistry.updateVfsOnBuildStarted(currentRoot(), watchMode, unsupportedFileSystems);
            watchableHierarchiesRegisteredEarly.forEach(watchableHierarchy -> watchRegistry.registerWatchableHierarchy(watchableHierarchy, this::currentRoot));
            watchableHierarchiesRegisteredEarly.clear();
        } catch (Exception ex) {
            logWatchingError(ex, FILE_WATCHING_ERROR_MESSAGE_DURING_BUILD);
            closeUnderLock();
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
        private final FileWatchingFilter locationsWrittenByCurrentBuild;
        private final FileWatcherRegistry.ChangeHandler delegate;

        public FilterChangesToOutputsChangesHandler(FileWatchingFilter locationsWrittenByCurrentBuild, FileWatcherRegistry.ChangeHandler delegate) {
            this.locationsWrittenByCurrentBuild = locationsWrittenByCurrentBuild;
            this.delegate = delegate;
        }

        @Override
        public void handleChange(FileWatcherRegistry.Type type, Path path) {
            if (locationsWrittenByCurrentBuild.shouldWatchLocation(path.toString())) {
                delegate.handleChange(type, path);
            }
        }

        @Override
        public void handleChangeBatch(Collection<Map.Entry<FileWatcherRegistry.Type, Path>> changes) {
            java.util.List<Map.Entry<FileWatcherRegistry.Type, Path>> filtered = new java.util.ArrayList<>();
            for (Map.Entry<FileWatcherRegistry.Type, Path> entry : changes) {
                if (locationsWrittenByCurrentBuild.shouldWatchLocation(entry.getValue().toString())) {
                    filtered.add(entry);
                }
            }
            if (!filtered.isEmpty()) {
                delegate.handleChangeBatch(filtered);
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
            invalidateAndNotify(Collections.singletonList(path.toString()), diffListener -> new VfsChangeLoggingNodeDiffListener(type, path, diffListener));
        }

        @Override
        public void handleChangeBatch(Collection<Map.Entry<FileWatcherRegistry.Type, Path>> changes) {
            List<String> paths = new java.util.ArrayList<>(changes.size());
            for (Map.Entry<FileWatcherRegistry.Type, Path> entry : changes) {
                paths.add(entry.getValue().toString());
            }
            invalidateAndNotify(paths, diffListener -> new BatchVfsChangeLoggingNodeDiffListener(changes, diffListener));
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
        public void handleChangeBatch(Collection<Map.Entry<FileWatcherRegistry.Type, Path>> changes) {
            for (Map.Entry<FileWatcherRegistry.Type, Path> entry : changes) {
                fileChangeListeners.broadcastChange(entry.getKey(), entry.getValue());
            }
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
        public void handleChangeBatch(Collection<Map.Entry<FileWatcherRegistry.Type, Path>> changes) {
            handlers.forEach(handler -> handler.handleChangeBatch(changes));
        }

        @Override
        public void stopWatchingAfterError() {
            handlers.forEach(FileWatcherRegistry.ChangeHandler::stopWatchingAfterError);
        }
    }

    private static class BatchVfsChangeLoggingNodeDiffListener implements SnapshotHierarchy.NodeDiffListener {
        private final Collection<Map.Entry<FileWatcherRegistry.Type, Path>> changes;
        private final SnapshotHierarchy.NodeDiffListener delegate;
        private boolean alreadyLogged;

        public BatchVfsChangeLoggingNodeDiffListener(Collection<Map.Entry<FileWatcherRegistry.Type, Path>> changes, SnapshotHierarchy.NodeDiffListener delegate) {
            this.changes = changes;
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
                if (changes.size() == 1) {
                    Map.Entry<FileWatcherRegistry.Type, Path> first = changes.iterator().next();
                    LOGGER.debug("Handling VFS change {} {}", first.getKey(), first.getValue());
                } else {
                    LOGGER.debug("Handling {} VFS changes", changes.size());
                }
            }
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

    private void logWatchingError(Exception exception, String fileWatchingErrorMessage) {
        if (exception instanceof InotifyInstanceLimitTooLowException) {
            warningLogger.warn("{}. The inotify instance limit is too low. {}",
                fileWatchingErrorMessage,
                fileSystemWatchingDocumentationIndex.getLinkToSection("sec:inotify_instances_limit")
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

    private void stopWatchingAndInvalidateHierarchyAfterError() {
        warningLogger.error("Stopping file watching and invalidating VFS after an error happened");
        closeUnderLock();
        replaceRoot(currentRoot().empty());
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
        closeUnderLock();
        replaceRoot(currentRoot().empty());
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
