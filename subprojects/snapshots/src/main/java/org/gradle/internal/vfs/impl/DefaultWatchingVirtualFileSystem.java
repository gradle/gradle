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

import org.gradle.internal.vfs.WatchingVirtualFileSystem;
import org.gradle.internal.vfs.watch.FileWatcherRegistry;
import org.gradle.internal.vfs.watch.FileWatcherRegistryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

public class DefaultWatchingVirtualFileSystem extends AbstractDelegatingVirtualFileSystem implements WatchingVirtualFileSystem, Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultWatchingVirtualFileSystem.class);

    private final FileWatcherRegistryFactory watcherRegistryFactory;
    private final Predicate<String> watchFilter;

    private FileWatcherRegistry watchRegistry;

    public DefaultWatchingVirtualFileSystem(
        FileWatcherRegistryFactory watcherRegistryFactory,
        AbstractVirtualFileSystem delegate,
        Predicate<String> watchFilter
    ) {
        super(delegate);
        this.watcherRegistryFactory = watcherRegistryFactory;
        this.watchFilter = watchFilter;
    }

    @Override
    public void startWatching(Collection<String> mustWatchDirectories) {
        if (watchRegistry != null) {
            throw new IllegalStateException("Watch service already started");
        }
        try {
            long startTime = System.currentTimeMillis();
            watchRegistry = watcherRegistryFactory.startWatching(getRoot(), watchFilter, mustWatchDirectories);
            long endTime = System.currentTimeMillis() - startTime;
            LOGGER.warn("Spent {} ms registering watches for file system events", endTime);
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
            long startTime = System.currentTimeMillis();
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
            LOGGER.warn("Spent {} ms processing file system events since last build", System.currentTimeMillis() - startTime);
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
