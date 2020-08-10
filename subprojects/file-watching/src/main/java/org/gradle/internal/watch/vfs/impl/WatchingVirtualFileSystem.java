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
import org.gradle.internal.file.FileType;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.snapshot.CompleteDirectorySnapshot;
import org.gradle.internal.snapshot.CompleteFileSystemLocationSnapshot;
import org.gradle.internal.snapshot.FileSystemSnapshotVisitor;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.vfs.impl.VfsRootReference;
import org.gradle.internal.watch.WatchingNotSupportedException;
import org.gradle.internal.watch.registry.FileWatcherRegistry;
import org.gradle.internal.watch.registry.FileWatcherRegistryFactory;
import org.gradle.internal.watch.registry.SnapshotCollectingDiffListener;
import org.gradle.internal.watch.registry.impl.DaemonDocumentationIndex;
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
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
    public void afterBuildStarted(boolean watchingEnabled, BuildOperationRunner buildOperationRunner) {
        reasonForNotWatchingFiles = null;
        rootReference.update(currentRoot -> {
            return buildOperationRunner.call(new CallableBuildOperation<SnapshotHierarchy>() {
                @Override
                public SnapshotHierarchy call(BuildOperationContext context) {
                    if (watchingEnabled) {
                        SnapshotHierarchy newRoot = currentRoot;
                        boolean startedWatching = false;
                        FileWatcherRegistry.FileWatchingStatistics statistics = watchRegistry == null
                            ? null
                            : watchRegistry.getAndResetStatistics();

                        newRoot = stopWatchingDueToProblemsWhenReceivingFileChanges(newRoot, statistics);
                        if (watchRegistry == null) {
                            startedWatching = true;
                            context.setStatus("Starting file system watching");
                            newRoot = startWatching(newRoot);
                        }
                        context.setResult(new BuildStartedWithFileSystemWatchingEnabled(true, startedWatching, statistics, newRoot));
                        return newRoot;
                    } else {
                        SnapshotHierarchy newRoot = stopWatchingAndInvalidateHierarchy(currentRoot);
                        context.setResult(new BuildStartedWithFileSystemWatchingEnabled(false, false, null, newRoot));
                        return newRoot;
                    }

                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName("Build started with file system watching");
                }
            });
        });
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
    public void beforeBuildFinished(boolean watchingEnabled, BuildOperationRunner buildOperationRunner) {
        rootReference.update(currentRoot -> {
            return buildOperationRunner.call(new CallableBuildOperation<SnapshotHierarchy>() {
                @Override
                public SnapshotHierarchy call(BuildOperationContext context) {
                    watchableHierarchies.clear();
                    if (watchingEnabled) {
                        if (reasonForNotWatchingFiles != null) {
                            // Log exception again so it doesn't get lost.
                            logWatchingError(reasonForNotWatchingFiles, FILE_WATCHING_ERROR_MESSAGE_AT_END_OF_BUILD);
                            reasonForNotWatchingFiles = null;
                        }

                        FileWatcherRegistry.FileWatchingStatistics statistics = watchRegistry == null
                            ? null
                            : watchRegistry.getAndResetStatistics();
                        SnapshotHierarchy newRoot = stopWatchingDueToProblemsWhenReceivingFileChanges(currentRoot, statistics);
                        if (watchRegistry != null) {
                            SnapshotHierarchy rootAfterEvents = newRoot;
                            newRoot = withWatcherChangeErrorHandling(newRoot, () -> watchRegistry.buildFinished(rootAfterEvents));
                        }
                        context.setResult(new BuildFinishedForFileSystemWatching(true, watchRegistry == null, statistics, newRoot));
                        return newRoot;
                    } else {
                        SnapshotHierarchy newRoot = currentRoot.empty();
                        context.setResult(new BuildFinishedForFileSystemWatching(false, false, null, newRoot));
                        return newRoot;
                    }

                }

                @Override
                public BuildOperationDescriptor.Builder description() {
                    return BuildOperationDescriptor.displayName("Build finished with file system watching");
                }
            });
        });
    }

    /**
     * Start watching the known areas of the file system for changes.
     */
    private SnapshotHierarchy startWatching(SnapshotHierarchy currentRoot) {
        try {
            watchRegistry = watcherRegistryFactory.createFileWatcherRegistry(new FileWatcherRegistry.ChangeHandler() {
                @Override
                public void handleChange(FileWatcherRegistry.Type type, Path path) {
                    try {
                        LOGGER.debug("Handling VFS change {} {}", type, path);
                        String absolutePath = path.toString();
                        if (!locationsWrittenByCurrentBuild.wasLocationWritten(absolutePath)) {
                            update((root, diffListener) -> root.invalidate(absolutePath, diffListener));
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
            return currentRoot.empty();
        } catch (Exception ex) {
            logWatchingError(ex, FILE_WATCHING_ERROR_MESSAGE_DURING_BUILD);
            closeUnderLock();
            return currentRoot.empty();
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

    private SnapshotHierarchy stopWatchingDueToProblemsWhenReceivingFileChanges(SnapshotHierarchy currentRoot, @Nullable FileWatcherRegistry.FileWatchingStatistics statistics) {
        if (statistics == null) {
            return currentRoot.empty();
        }
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

    public static class BuildStartedWithFileSystemWatchingEnabled extends WatchingBookkeepingResult {
        private final boolean startedWatching;

        public BuildStartedWithFileSystemWatchingEnabled(
            boolean watchingEnabled, boolean startedWatching,
            @Nullable FileWatcherRegistry.FileWatchingStatistics fileWatchingStatistics,
            SnapshotHierarchy vfsRoot
        ) {
            super(watchingEnabled, fileWatchingStatistics, vfsRoot);
            this.startedWatching = startedWatching;
        }

        public boolean isStartedWatching() {
            return startedWatching;
        }
    }

    public static class BuildFinishedForFileSystemWatching extends WatchingBookkeepingResult {
        private final boolean stoppedWatchingDuringTheBuild;

        public BuildFinishedForFileSystemWatching(
            boolean watchingEnabled, boolean stoppedWatchingDuringTheBuild,
            @Nullable FileWatcherRegistry.FileWatchingStatistics fileWatchingStatistics,
            SnapshotHierarchy vfsRoot
        ) {
            super(watchingEnabled, fileWatchingStatistics, vfsRoot);
            this.stoppedWatchingDuringTheBuild = stoppedWatchingDuringTheBuild;
        }

        public boolean isStoppedWatchingDuringTheBuild() {
            return stoppedWatchingDuringTheBuild;
        }
    }

    public static class WatchingBookkeepingResult {
        private final boolean watchingEnabled;
        private final FileWatcherRegistry.FileWatchingStatistics fileWatchingStatistics;
        private final VirtualFileSystemStatistics vfsStatistics;

        public WatchingBookkeepingResult(
            boolean watchingEnabled,
            @Nullable FileWatcherRegistry.FileWatchingStatistics fileWatchingStatistics,
            SnapshotHierarchy vfsRoot
        ) {
            this.watchingEnabled = watchingEnabled;
            this.fileWatchingStatistics = fileWatchingStatistics;
            this.vfsStatistics = getStatistics(vfsRoot);
        }

        public boolean isWatchingEnabled() {
            return watchingEnabled;
        }

        @Nullable
        public FileWatcherRegistry.FileWatchingStatistics getFileWatchingStatistics() {
            return fileWatchingStatistics;
        }

        public int getRetainedRegularFiles() {
            return vfsStatistics.getRetained(FileType.RegularFile);
        }

        public int getRetainedDirectories() {
            return vfsStatistics.getRetained(FileType.Directory);
        }

        public int getRetainedMissingFiles() {
            return vfsStatistics.getRetained(FileType.Missing);
        }
    }
}
