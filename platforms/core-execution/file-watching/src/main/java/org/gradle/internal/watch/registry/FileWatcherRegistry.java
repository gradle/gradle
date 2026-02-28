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

package org.gradle.internal.watch.registry;

import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.watch.WatchingNotSupportedException;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public interface FileWatcherRegistry extends Closeable {

    boolean isWatchingAnyLocations();

    interface ChangeHandler {
        void handleChange(Type type, Path path);

        default void handleChangeBatch(Collection<Map.Entry<Type, Path>> changes) {
            for (Map.Entry<Type, Path> entry : changes) {
                handleChange(entry.getKey(), entry.getValue());
            }
        }

        void stopWatchingAfterError();
    }

    enum Type {
        CREATED,
        MODIFIED,
        REMOVED,
        INVALIDATED,
        OVERFLOW
    }

    /**
     * Registers a watchable hierarchy.
     *
     * The watcher registry will only watch for changes in watchable hierarchies.
     * The {@code currentRoot} supplier is evaluated lazily after acquiring the watcher lock,
     * ensuring the VFS state at processing time is used rather than a stale snapshot captured
     * before the lock was acquired.
     *
     * @throws WatchingNotSupportedException when the native watchers can't be updated.
     */
    void registerWatchableHierarchy(File watchableHierarchy, Supplier<SnapshotHierarchy> currentRoot);

    /**
     * Updates the watchers after changes to the root.
     *
     * The {@code currentRoot} supplier is evaluated lazily after acquiring the watcher lock,
     * so it always reflects the VFS state at processing time rather than at notification time.
     * This prevents stale add notifications from being processed when a remove CAS completed
     * after the add CAS but before the notification was dispatched.
     *
     * @throws WatchingNotSupportedException when the native watchers can't be updated.
     */
    void virtualFileSystemContentsChanged(Collection<FileSystemLocationSnapshot> removedSnapshots, Collection<FileSystemLocationSnapshot> addedSnapshots, Supplier<SnapshotHierarchy> currentRoot);

    /**
     * Updates the watchers when the build started.
     *
     * For example, this method checks if watched hierarchies are where we left them after the previous build.
     * Returns the list of VFS paths that the caller should invalidate via {@link org.gradle.internal.vfs.VirtualFileSystem#invalidate}.
     */
    List<String> updateVfsOnBuildStarted(SnapshotHierarchy root, WatchMode watchMode, List<File> unsupportedFileSystems);

    /**
     * Updates the watchers before the build finishes.
     *
     * For example, this removes everything from the root which can't be kept after the current build finished.
     * This is anything which is not within a watchable hierarchy or in a cache directory.
     * Returns the list of VFS paths that the caller should invalidate via {@link org.gradle.internal.vfs.VirtualFileSystem#invalidate}.
     */
    List<String> updateVfsBeforeBuildFinished(SnapshotHierarchy root, int maximumNumberOfWatchedHierarchies, List<File> unsupportedFileSystems);

    /**
     * Updates the watchers after the build finished.
     *
     * This removes content we can't track using file system events, i.e. stuff accessed via symlinks.
     * Returns the list of VFS paths that the caller should invalidate via {@link org.gradle.internal.vfs.VirtualFileSystem#invalidate}.
     */
    List<String> updateVfsAfterBuildFinished(SnapshotHierarchy root);

    /**
     * Get statistics about the received changes.
     */
    FileWatchingStatistics getAndResetStatistics();

    /**
     * Close the watcher registry. Stops watching without handling the changes.
     */
    @Override
    void close() throws IOException;

    interface FileWatchingStatistics {
        Optional<Throwable> getErrorWhileReceivingFileChanges();
        boolean isUnknownEventEncountered();
        int getNumberOfReceivedEvents();
        int getNumberOfWatchedHierarchies();
    }
}
