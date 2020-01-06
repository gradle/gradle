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

import com.sun.nio.file.SensitivityWatchEventModifier;
import org.gradle.internal.vfs.watch.FileWatcherRegistry;
import org.gradle.internal.vfs.watch.FileWatcherRegistryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Set;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class JdkFileWatcherRegistry implements FileWatcherRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(JdkFileWatcherRegistry.class);

    private final WatchService watchService;

    public JdkFileWatcherRegistry(WatchService watchService, Iterable<Path> watchRoots) throws IOException {
        this.watchService = watchService;
        for (Path watchRoot : watchRoots) {
            LOGGER.debug("Started watching {}", watchRoot);
            watchRoot.register(watchService,
                new WatchEvent.Kind[]{ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY, OVERFLOW},
                SensitivityWatchEventModifier.HIGH);
        }
    }

    @Override
    public void stopWatching(ChangeHandler handler) throws IOException {
        try {
            boolean overflow = false;
            while (!overflow) {
                WatchKey watchKey = watchService.poll();
                if (watchKey == null) {
                    break;
                }
                watchKey.cancel();
                Path watchRoot = (Path) watchKey.watchable();
                LOGGER.debug("Stopped watching {}", watchRoot);
                for (WatchEvent<?> event : watchKey.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == OVERFLOW) {
                        LOGGER.warn("Too many modifications for path {} since last build, dropping all VFS state", watchRoot);
                        handler.handleLostState();
                        overflow = true;
                        break;
                    }
                    Path changedPath = watchRoot.resolve((Path) event.context());
                    Type type;
                    if (kind == ENTRY_CREATE) {
                        type = Type.CREATED;
                    } else if (kind == ENTRY_MODIFY) {
                        type = Type.MODIFIED;
                    } else if (kind == ENTRY_DELETE) {
                        type = Type.REMOVED;
                    } else {
                        throw new AssertionError();
                    }
                    handler.handleChange(type, changedPath);
                }
            }
        } finally {
            close();
        }
    }

    @Override
    public void close() throws IOException {
        watchService.close();
    }

    public static class Factory implements FileWatcherRegistryFactory {
        @Override
        public FileWatcherRegistry startWatching(Set<Path> directories) throws IOException {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            LOGGER.warn("Watching {} directories to track changes between builds", directories.size());
            return new JdkFileWatcherRegistry(watchService, directories);
        }
    }
}
