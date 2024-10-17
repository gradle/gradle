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
import org.gradle.fileevents.internal.OsxFileEventFunctions;
import org.gradle.fileevents.internal.OsxFileEventFunctions.OsxFileWatcher;
import org.gradle.internal.watch.WatchingNotSupportedException;
import org.gradle.internal.watch.registry.FileWatcherProbeRegistry;
import org.gradle.internal.watch.registry.FileWatcherUpdater;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class DarwinFileWatcherRegistryFactory extends AbstractFileWatcherRegistryFactory<OsxFileEventFunctions, OsxFileWatcher> {

    public DarwinFileWatcherRegistryFactory(Predicate<String> immutableLocationsFilter) throws NativeIntegrationUnavailableException {
        super(FileEvents.get(OsxFileEventFunctions.class), immutableLocationsFilter);
    }

    @Override
    protected OsxFileWatcher createFileWatcher(BlockingQueue<FileWatchEvent> fileEvents) throws InterruptedException {
        return fileEventFunctions.newWatcher(fileEvents)
            // TODO Figure out a good value for this
            .withLatency(20, TimeUnit.MICROSECONDS)
            .start();
    }

    @Override
    protected FileWatcherUpdater createFileWatcherUpdater(
        OsxFileWatcher watcher,
        FileWatcherProbeRegistry probeRegistry,
        WatchableHierarchies watchableHierarchies
    ) {
        return new HierarchicalFileWatcherUpdater(watcher, DarwinFileWatcherRegistryFactory::validateLocationToWatch, probeRegistry, watchableHierarchies, root -> Collections.emptyList());
    }

    /**
     * The macOS native watcher reports the canonical path for watched paths.
     * That means that we would not invalidate the right locations in the virtual file system on macOS.
     * Therefore, we disable file system watching when we try to watch a directory whose parent is a symlink.
     *
     * Note that the project directory is canonicalized by Gradle, so the project directory can always be watched.
     */
    private static void validateLocationToWatch(File location) {
        try {
            String canonicalPath = location.getCanonicalPath();
            String absolutePath = location.getAbsolutePath();
            if (!canonicalPath.equals(absolutePath)) {
                throw new WatchingNotSupportedException(String.format(
                    "Unable to watch '%s' since itself or one of its parent is a symbolic link (canonical path: '%s')",
                    absolutePath,
                    canonicalPath
                ));
            }
        } catch (IOException e) {
            throw new WatchingNotSupportedException("Unable to watch '%s' since its canonical path can't be resolved: " + e.getMessage(), e);
        }
    }
}
