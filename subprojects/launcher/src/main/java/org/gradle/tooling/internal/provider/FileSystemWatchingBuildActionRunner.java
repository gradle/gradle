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

package org.gradle.tooling.internal.provider;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.api.internal.changedetection.state.FileHasherStatistics;
import org.gradle.internal.file.StatStatistics;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.VirtualFileSystemServices;
import org.gradle.internal.snapshot.impl.DirectorySnapshotterStatistics;
import org.gradle.internal.watch.options.FileSystemWatchingSettingsFinalizedProgressDetails;
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem;
import org.gradle.internal.watch.vfs.VfsLogging;
import org.gradle.internal.watch.vfs.WatchLogging;
import org.gradle.internal.watch.vfs.WatchMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileSystemWatchingBuildActionRunner implements BuildActionRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileSystemWatchingBuildActionRunner.class);

    private final BuildOperationProgressEventEmitter eventEmitter;
    private final BuildActionRunner delegate;

    public FileSystemWatchingBuildActionRunner(
        BuildOperationProgressEventEmitter eventEmitter,
        BuildActionRunner delegate
    ) {
        this.eventEmitter = eventEmitter;
        this.delegate = delegate;
    }

    @Override
    public Result run(BuildAction action, BuildController buildController) {
        GradleInternal gradle = buildController.getGradle();
        StartParameterInternal startParameter = gradle.getStartParameter();
        ServiceRegistry services = gradle.getServices();
        BuildLifecycleAwareVirtualFileSystem virtualFileSystem = services.get(BuildLifecycleAwareVirtualFileSystem.class);
        StatStatistics.Collector statStatisticsCollector = services.get(StatStatistics.Collector.class);
        FileHasherStatistics.Collector fileHasherStatisticsCollector = services.get(FileHasherStatistics.Collector.class);
        DirectorySnapshotterStatistics.Collector directorySnapshotterStatisticsCollector = services.get(DirectorySnapshotterStatistics.Collector.class);
        BuildOperationRunner buildOperationRunner = services.get(BuildOperationRunner.class);

        WatchMode watchFileSystemMode = startParameter.getWatchFileSystemMode();
        VfsLogging verboseVfsLogging = startParameter.isVfsVerboseLogging()
            ? VfsLogging.VERBOSE
            : VfsLogging.NORMAL;
        WatchLogging debugWatchLogging = startParameter.isWatchFileSystemDebugLogging()
            ? WatchLogging.DEBUG
            : WatchLogging.NORMAL;

        LOGGER.info("Watching the file system is {}", watchFileSystemMode.getDescription());
        if (watchFileSystemMode.isEnabled()) {
            dropVirtualFileSystemIfRequested(startParameter, virtualFileSystem);
        }
        if (verboseVfsLogging == VfsLogging.VERBOSE) {
            logVfsStatistics("since last build", statStatisticsCollector, fileHasherStatisticsCollector, directorySnapshotterStatisticsCollector);
        }

        boolean actuallyWatching = virtualFileSystem.afterBuildStarted(watchFileSystemMode, verboseVfsLogging, debugWatchLogging, buildOperationRunner);
        //noinspection Convert2Lambda
        eventEmitter.emitNowForCurrent(new FileSystemWatchingSettingsFinalizedProgressDetails() {
            @Override
            public boolean isEnabled() {
                return actuallyWatching;
            }
        });

        try {
            return delegate.run(action, buildController);
        } finally {
            int maximumNumberOfWatchedHierarchies = VirtualFileSystemServices.getMaximumNumberOfWatchedHierarchies(startParameter);
            virtualFileSystem.beforeBuildFinished(watchFileSystemMode, verboseVfsLogging, debugWatchLogging, buildOperationRunner, maximumNumberOfWatchedHierarchies);
            if (verboseVfsLogging == VfsLogging.VERBOSE) {
                logVfsStatistics("during current build", statStatisticsCollector, fileHasherStatisticsCollector, directorySnapshotterStatisticsCollector);
            }
        }
    }

    private static void logVfsStatistics(
        String title,
        StatStatistics.Collector statStatisticsCollector,
        FileHasherStatistics.Collector fileHasherStatisticsCollector,
        DirectorySnapshotterStatistics.Collector directorySnapshotterStatisticsCollector
    ) {
        LOGGER.warn("VFS> Statistics {}:", title);
        LOGGER.warn("VFS> > Stat: {}", statStatisticsCollector.collect());
        LOGGER.warn("VFS> > FileHasher: {}", fileHasherStatisticsCollector.collect());
        LOGGER.warn("VFS> > DirectorySnapshotter: {}", directorySnapshotterStatisticsCollector.collect());
    }

    private static void dropVirtualFileSystemIfRequested(StartParameterInternal startParameter, BuildLifecycleAwareVirtualFileSystem virtualFileSystem) {
        if (VirtualFileSystemServices.isDropVfs(startParameter)) {
            virtualFileSystem.invalidateAll();
        }
    }
}
