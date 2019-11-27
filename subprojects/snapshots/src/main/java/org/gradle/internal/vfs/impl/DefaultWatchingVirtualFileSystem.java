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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
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
            watchRegistry = watcherRegistryFactory.createRegistry();
            Set<Path> visited = new HashSet<>();
            getRoot().visitCompleteSnapshots(snapshot -> {
                String absolutePath = snapshot.getAbsolutePath();
                Path path = Paths.get(absolutePath);
                if (!watchFilter.test(path)) {
                    return;
                }
                try {
                    if (snapshot.getType() == FileType.Directory) {
                        watch(path, visited);
                    }
                    Path parent = path;
                    while (true) {
                        parent = parent.getParent();
                        if (parent == null) {
                            break;
                        }
                        if (Files.exists(parent)) {
                            watch(parent, visited);
                            break;
                        }
                    }
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (Exception ex) {
            LOGGER.error("Couldn't create watch service, not tracking changes between builds", ex);
            invalidateAll();
            close();
        }
    }

    private void watch(Path directory, Set<Path> visited) throws IOException {
        if (!visited.add(directory)) {
            return;
        }
        LOGGER.warn("Start watching {}", directory);
        watchRegistry.registerWatchPoint(directory);
    }

    @Override
    public void stopWatching() {
        if (watchRegistry == null) {
            return;
        }
        try {
            watchRegistry.stopWatching(new FileWatcherRegistry.ChangeHandler() {
                @Override
                public void handleChange(FileWatcherRegistry.Type type, Path path) {
                    update(Collections.singleton(path.toString()), () -> {
                    });
                }

                @Override
                public void handleOverflow() {
                    invalidateAll();
                }
            });
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
