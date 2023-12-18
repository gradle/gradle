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

import org.gradle.internal.file.FileHierarchySet;
import org.gradle.internal.vfs.FileSystemAccess;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps track of how locations should be watched:
 *
 * - Immutable locations are managed by Gradle, i.e. file system changes should never be external,
 *   and therefore these locations should not be watched to cut down on the number of watchers needed.
 *
 * - Locations known to be modified during a build might receive late file events that would invalidate VFS state we just
 *   captured after the changes; to avoid this, after a known modification we assume no further modifications will
 *   happen to the same location, and all file events belong the know modifications instead.
 */
public class FileWatchingFilter implements FileSystemAccess.WriteListener {
    private final FileHierarchySet immutableLocations;
    private final AtomicReference<FileHierarchySet> locationsWrittenByCurrentBuild = new AtomicReference<>(FileHierarchySet.empty());
    private volatile boolean buildRunning;

    public FileWatchingFilter(FileHierarchySet immutableLocations) {
        this.immutableLocations = immutableLocations;
    }

    @Override
    public void locationsWritten(Iterable<String> locations) {
        if (buildRunning) {
            // TODO Should the loop go on the outside to make updates smaller and less likely to collide, and also cheaper to re-run?
            locationsWrittenByCurrentBuild.updateAndGet(currentValue -> {
                FileHierarchySet newValue = currentValue;
                for (String location : locations) {
                    newValue = newValue.plus(location);
                }
                return newValue;
            });
        }
    }

    public boolean shouldWatchLocation(String location) {
        return !locationsWrittenByCurrentBuild.get().contains(location);
    }

    public FileHierarchySet getImmutableLocations() {
        return immutableLocations;
    }

    public void buildStarted() {
        resetLocationsWritten();
        buildRunning = true;
    }

    public void buildFinished() {
        resetLocationsWritten();
        buildRunning = false;
    }

    private void resetLocationsWritten() {
        // Immutable locations never need to be watched
        locationsWrittenByCurrentBuild.set(immutableLocations);
    }
}
