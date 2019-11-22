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

import com.sun.nio.file.SensitivityWatchEventModifier;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import static org.gradle.internal.vfs.impl.FileWatcherRegistry.EventKind.CREATED;
import static org.gradle.internal.vfs.impl.FileWatcherRegistry.EventKind.DELETED;
import static org.gradle.internal.vfs.impl.FileWatcherRegistry.EventKind.MODIFIED;

public class DefaultFileWatcherRegistry implements FileWatcherRegistry {
    private final Map<Path, WatchKey> registrations = new HashMap<>();
    private final WatchService watchService;

    public DefaultFileWatcherRegistry() {
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Override
    public void watch(Path path) {
        registrations.computeIfAbsent(path, path2 -> {
            if (!Files.exists(path)) {
                // TODO Technically this shouldn't be needed
                return null;
            }
            try {
                System.out.println("> Started watching " + path);
                return path.register(watchService,
                    new WatchEvent.Kind[]{ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY, OVERFLOW},
                    SensitivityWatchEventModifier.HIGH);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });
    }

    @Override
    public void stopAndProcess(ChangeEventHandler handler) {
        all:
        for (WatchKey watchKey : registrations.values()) {
            watchKey.cancel();
            Path watchRoot = (Path) watchKey.watchable();
            for (WatchEvent<?> event : watchKey.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == OVERFLOW) {
                    handler.handleOverflow();
                    break all;
                }
                EventKind convertedKind;
                if (kind == ENTRY_CREATE) {
                    convertedKind = CREATED;
                } else if (kind == ENTRY_MODIFY) {
                    convertedKind = MODIFIED;
                } else if (kind == ENTRY_DELETE) {
                    convertedKind = DELETED;
                } else {
                    throw new AssertionError("Unknown change event kind: " + kind);
                }
                Path path = watchRoot.resolve(((Path) event.context()));
                handler.handleEvent(convertedKind, path);
            }
        }
        registrations.clear();
    }
}
