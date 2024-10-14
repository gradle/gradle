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

package org.gradle.internal.watch.registry.impl;

import net.rubygrapefruit.platform.NativeIntegrationUnavailableException;
import org.gradle.fileevents.FileEvents;
import org.gradle.fileevents.FileWatchEvent;
import org.gradle.fileevents.internal.LinuxFileEventFunctions;
import org.gradle.fileevents.internal.LinuxFileEventFunctions.LinuxFileWatcher;
import org.gradle.internal.file.FileType;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.watch.registry.FileWatcherProbeRegistry;
import org.gradle.internal.watch.registry.FileWatcherUpdater;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class LinuxFileWatcherRegistryFactory extends AbstractFileWatcherRegistryFactory<LinuxFileEventFunctions, LinuxFileWatcher> {

    public LinuxFileWatcherRegistryFactory(Predicate<String> immutableLocationsFilter) throws NativeIntegrationUnavailableException {
        super(FileEvents.get(LinuxFileEventFunctions.class), immutableLocationsFilter);
    }

    @Override
    protected LinuxFileWatcher createFileWatcher(BlockingQueue<FileWatchEvent> fileEvents) throws InterruptedException {
        return fileEventFunctions.newWatcher(fileEvents)
            .start();
    }

    @Override
    protected FileWatcherUpdater createFileWatcherUpdater(LinuxFileWatcher watcher, FileWatcherProbeRegistry probeRegistry, WatchableHierarchies watchableHierarchies) {
        return new NonHierarchicalFileWatcherUpdater(watcher, probeRegistry, watchableHierarchies, new LinuxMovedDirectoryHandler(watcher, watchableHierarchies));
    }

    private static class LinuxMovedDirectoryHandler implements AbstractFileWatcherUpdater.MovedDirectoryHandler {
        private final LinuxFileWatcher watcher;
        private final WatchableHierarchies watchableHierarchies;

        public LinuxMovedDirectoryHandler(LinuxFileWatcher watcher, WatchableHierarchies watchableHierarchies) {
            this.watcher = watcher;
            this.watchableHierarchies = watchableHierarchies;
        }

        @Override
        public Collection<File> stopWatchingMovedDirectories(SnapshotHierarchy vfsRoot) {
            Collection<File> directoriesToCheck = vfsRoot.rootSnapshots()
                .filter(snapshot -> snapshot.getType() != FileType.Missing)
                .filter(watchableHierarchies::shouldWatch)
                .map(snapshot -> {
                    switch (snapshot.getType()) {
                        case RegularFile:
                            return new File(snapshot.getAbsolutePath()).getParentFile();
                        case Directory:
                            return new File(snapshot.getAbsolutePath());
                        default:
                            throw new IllegalArgumentException("Unexpected file type:" + snapshot.getType());
                    }
                })
                .collect(Collectors.toList());
            return watcher.stopWatchingMovedPaths(directoriesToCheck);
        }
    }
}
