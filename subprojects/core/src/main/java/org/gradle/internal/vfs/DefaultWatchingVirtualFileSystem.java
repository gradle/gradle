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

package org.gradle.internal.vfs;

import com.google.common.collect.Interner;
import com.sun.nio.file.SensitivityWatchEventModifier;
import org.gradle.api.internal.changedetection.state.WellKnownFileLocations;
import org.gradle.internal.file.FileType;
import org.gradle.internal.file.Stat;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.snapshot.CaseSensitivity;
import org.gradle.internal.vfs.impl.DefaultVirtualFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class DefaultWatchingVirtualFileSystem extends DefaultVirtualFileSystem implements WatchingVirtualFileSystem, Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultWatchingVirtualFileSystem.class);

    private final WellKnownFileLocations wellKnownFileLocations;
    private WatchService watchService;

    public DefaultWatchingVirtualFileSystem(
        FileHasher hasher,
        Interner<String> stringInterner,
        Stat stat,
        WellKnownFileLocations wellKnownFileLocations,

        CaseSensitivity caseSensitivity,
        String... defaultExcludes
    ) {
        super(hasher, stringInterner, stat, caseSensitivity, defaultExcludes);
        this.wellKnownFileLocations = wellKnownFileLocations;
    }

    @Override
    public void startWatching() {
        if (watchService != null) {
            throw new IllegalStateException("Watch service already started");
        }
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        Set<File> visited = new HashSet<>();
        root.get().visitCompleteSnapshots(snapshot -> {
            String absolutePath = snapshot.getAbsolutePath();
            File file = new File(absolutePath);
            if (wellKnownFileLocations.isImmutable(absolutePath)) {
                return;
            }
            try {
                if (snapshot.getType() == FileType.Directory) {
                    watch(file, visited);
                }
                File parent = file;
                while (true) {
                    parent = parent.getParentFile();
                    if (parent == null) {
                        break;
                    }
                    if (parent.exists()) {
                        watch(parent, visited);
                        break;
                    }
                }
            } catch (IOException ex) {
                // TODO Handle error here and disable watching
                throw new UncheckedIOException(ex);
            }
        });
    }

    private void watch(File directory, Set<File> visited) throws IOException {
        if (!visited.add(directory)) {
            return;
        }
        // TODO This shouldn't be required, but sometimes we seem to hit it
        if (!directory.exists()) {
            return;
        }
        LOGGER.warn("Start watching {}", directory);
        directory.toPath().register(watchService,
            new WatchEvent.Kind[]{ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY, OVERFLOW},
            SensitivityWatchEventModifier.HIGH);
    }

    @Override
    public void stopWatching() {
        if (watchService == null) {
            return;
        }
        try {
            boolean overflow = false;
            while (!overflow) {
                WatchKey watchKey = watchService.poll();
                if (watchKey == null) {
                    break;
                }
                watchKey.cancel();
                Path watchRoot = (Path) watchKey.watchable();
                LOGGER.debug("Stop watching {}", watchRoot);
                for (WatchEvent<?> event : watchKey.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == OVERFLOW) {
                        LOGGER.info("Too many modifications for path {} since last build, dropping all VFS state", watchRoot);
                        invalidateAll();
                        overflow = true;
                        break;
                    }
                    Path changedPath = watchRoot.resolve((Path) event.context());
                    update(Collections.singleton(changedPath.toString()), () -> {});
                }
            }
        } finally {
            close();
        }
    }

    @Override
    public void close() {
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ex) {
                LOGGER.warn("Couldn't close watch service", ex);
            }
            watchService = null;
        }
    }
}
