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

import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.internal.vfs.impl.AbstractVirtualFileSystem;
import org.gradle.internal.watch.registry.WatchMode;
import org.gradle.internal.watch.vfs.BuildFinishedFileSystemWatchingBuildOperationType;
import org.gradle.internal.watch.vfs.BuildLifecycleAwareVirtualFileSystem;
import org.gradle.internal.watch.vfs.BuildStartedFileSystemWatchingBuildOperationType;
import org.gradle.internal.watch.vfs.VfsLogging;
import org.gradle.internal.watch.vfs.WatchLogging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * A {@link VirtualFileSystem} which is not able to register any watches.
 */
public class WatchingNotSupportedVirtualFileSystem extends AbstractVirtualFileSystem implements BuildLifecycleAwareVirtualFileSystem {

    private static final Logger LOGGER = LoggerFactory.getLogger(WatchingNotSupportedVirtualFileSystem.class);

    public WatchingNotSupportedVirtualFileSystem(SnapshotHierarchy root) {
        super(root);
    }

    @Override
    protected SnapshotHierarchy updateNotifyingListeners(UpdateFunction updateFunction) {
        return updateFunction.update(SnapshotHierarchy.NodeDiffListener.NOOP);
    }

    @Override
    public boolean afterBuildStarted(
        WatchMode watchMode,
        VfsLogging vfsLogging,
        WatchLogging watchLogging,
        BuildOperationRunner buildOperationRunner
    ) {
        if (watchMode == WatchMode.ENABLED) {
            LOGGER.warn("Watching the file system is not supported.");
        }
        updateRootUnderLock(vfsRoot -> buildOperationRunner.call(new CallableBuildOperation<SnapshotHierarchy>() {
            @Override
            public SnapshotHierarchy call(BuildOperationContext context) {
                context.setResult(BuildStartedFileSystemWatchingBuildOperationType.Result.WATCHING_DISABLED);
                return vfsRoot.empty();
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName(BuildStartedFileSystemWatchingBuildOperationType.DISPLAY_NAME)
                    .details(BuildStartedFileSystemWatchingBuildOperationType.Details.INSTANCE);
            }
        }));
        return false;
    }

    @Override
    public void registerWatchableHierarchy(File rootDirectoryForWatching, File probeLocation) {
    }

    @Override
    public void beforeBuildFinished(
        WatchMode watchMode,
        VfsLogging vfsLogging,
        WatchLogging watchLogging,
        BuildOperationRunner buildOperationRunner,
        int maximumNumberOfWatchedHierarchies
    ) {
        updateRootUnderLock(vfsRoot -> buildOperationRunner.call(new CallableBuildOperation<SnapshotHierarchy>() {
            @Override
            public SnapshotHierarchy call(BuildOperationContext context) {
                context.setResult(BuildFinishedFileSystemWatchingBuildOperationType.Result.WATCHING_DISABLED);
                return vfsRoot.empty();
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName(BuildFinishedFileSystemWatchingBuildOperationType.DISPLAY_NAME)
                    .details(BuildFinishedFileSystemWatchingBuildOperationType.Details.INSTANCE);
            }
        }));
    }

    @Override
    public void afterBuildFinished() {
    }

    @Override
    public boolean isWatchingAnyLocations() {
        return false;
    }
}
