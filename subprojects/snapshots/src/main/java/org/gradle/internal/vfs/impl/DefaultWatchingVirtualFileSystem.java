/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.vfs.impl;

import org.gradle.internal.file.FileType;
import org.gradle.internal.vfs.WatchingVirtualFileSystem;
import org.gradle.internal.vfs.watch.FileWatcherRegistry;
import org.gradle.internal.vfs.watch.FileWatcherRegistryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class DefaultWatchingVirtualFileSystem extends AbstractDelegatingVirtualFileSystem implements WatchingVirtualFileSystem, Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultWatchingVirtualFileSystem.class);

    private final FileWatcherRegistryFactory watcherRegistryFactory;
    private final Predicate<Path> watchFilter;

    private FileWatcherRegistry watchRegistry;

    public DefaultWatchingVirtualFileSystem(
        FileWatcherRegistryFactory watcherRegistryFactory,
        AbstractVirtualFileSystem delegate,
        Predicate<Path> watchFilter
    ) {
        super(delegate);
        this.watcherRegistryFactory = watcherRegistryFactory;
        this.watchFilter = watchFilter;
    }

    @Override
    public void startWatching() {
        if (watchRegistry != null) {
            throw new IllegalStateException("Watch service already started");
        }
        try {
            Set<Path> watchedDirectories = new HashSet<>();
            getRoot().visitSnapshots(snapshot -> {
                Path path = Paths.get(snapshot.getAbsolutePath());

                // We don't watch things that shouldn't be watched
                if (!watchFilter.test(path)) {
                    return;
                }

                // For existing files and directories we watch the parent directory,
                // so we learn if the entry itself disappears or gets modified.
                // In case of a missing file we need to find the closest existing
                // ancestor to watch so we can learn if the missing file respawns.
                Path ancestor = path;
                while (true) {
                    ancestor = ancestor.getParent();
                    if (ancestor == null) {
                        break;
                    }
                    if (Files.exists(ancestor)) {
                        watchedDirectories.add(ancestor);
                        break;
                    }
                }

                // For directory entries we watch the directory itself,
                // so we learn about new children spawning. If the directory
                // has children, it would be watched through them already.
                // This is here to make sure we also watch empty directories.
                if (snapshot.getType() == FileType.Directory) {
                    watchedDirectories.add(path);
                }
            });
            watchRegistry = watcherRegistryFactory.startWatching(watchedDirectories);
        } catch (Exception ex) {
            LOGGER.error("Couldn't create watch service, not tracking changes between builds", ex);
            invalidateAll();
            close();
        }
    }

    @Override
    public void stopWatching() {
        if (watchRegistry == null) {
            return;
        }

        AtomicInteger count = new AtomicInteger();
        AtomicBoolean unknownEventEncountered = new AtomicBoolean();
        try {
            watchRegistry.stopWatching(new FileWatcherRegistry.ChangeHandler() {
                @Override
                public void handleChange(FileWatcherRegistry.Type type, Path path) {
                    count.incrementAndGet();
                    LOGGER.debug("Handling VFS change {} {}", type, path);
                    update(Collections.singleton(path.toString()), () -> {});
                }

                @Override
                public void handleLostState() {
                    unknownEventEncountered.set(true);
                    LOGGER.warn("Dropped VFS state due to lost state");
                    invalidateAll();
                }
            });
            if (!unknownEventEncountered.get()) {
                LOGGER.warn("Received {} file system events since last build", count);
            }
        } catch (IOException ex) {
            LOGGER.error("Couldn't fetch file changes, dropping VFS state", ex);
            invalidateAll();
        } finally {
            close();
        }
    }

    @Override
    public void close() {
        if (watchRegistry != null) {
            try {
                watchRegistry.close();
            } catch (IOException ex) {
                LOGGER.error("Couldn't close watch service", ex);
            }
            watchRegistry = null;
        }
    }
}
