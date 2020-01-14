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

package org.gradle.internal.vfs.watch.impl;

import org.gradle.internal.vfs.SnapshotHierarchy;
import org.gradle.internal.vfs.watch.FileWatcherRegistry;
import org.gradle.internal.vfs.watch.FileWatcherRegistryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.function.Predicate;

public class NoopFileWatcherRegistry implements FileWatcherRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(NoopFileWatcherRegistry.class);

    private static final FileWatcherRegistry INSTANCE = new NoopFileWatcherRegistry();

    @Override
    public void stopWatching(ChangeHandler handler) {
        handler.handleLostState();
    }

    @Override
    public void close() {
    }

    public static class Factory implements FileWatcherRegistryFactory {
        @Override
        public FileWatcherRegistry startWatching(SnapshotHierarchy snapshotHierarchy, Predicate<String> watchFilter, Collection<String> mustWatchDirectories) {
            LOGGER.warn("VFS retention is enabled but file watching is not supported for this platform");
            return INSTANCE;
        }
    }
}
