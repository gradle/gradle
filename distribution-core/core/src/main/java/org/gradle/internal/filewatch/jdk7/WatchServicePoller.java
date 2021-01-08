/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.filewatch.jdk7;

import org.gradle.api.Transformer;
import org.gradle.internal.Cast;
import org.gradle.internal.filewatch.FileWatcherEvent;
import org.gradle.util.CollectionUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

class WatchServicePoller {
    private static final int POLL_TIMEOUT_SECONDS = 5;
    private final WatchService watchService;

    WatchServicePoller(WatchService watchService) throws IOException {
        this.watchService = watchService;
    }

    @Nullable
    public List<FileWatcherEvent> takeEvents() throws InterruptedException {
        WatchKey watchKey = watchService.poll(POLL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (watchKey != null) {
            return handleWatchKey(watchKey);
        }
        return null;
    }

    private List<FileWatcherEvent> handleWatchKey(WatchKey watchKey) {
        final Path watchedPath = (Path) watchKey.watchable();
        Transformer<FileWatcherEvent, WatchEvent<?>> watchEventTransformer = new Transformer<FileWatcherEvent, WatchEvent<?>>() {
            @Override
            public FileWatcherEvent transform(WatchEvent<?> event) {
                WatchEvent.Kind kind = event.kind();
                File file = null;
                if (kind.type() == Path.class) {
                    WatchEvent<Path> ev = Cast.uncheckedCast(event);
                    file = watchedPath.resolve(ev.context()).toFile();
                }
                return toEvent(kind, file);
            }
        };

        List<WatchEvent<?>> watchEvents = watchKey.pollEvents();
        watchKey.reset();
        if (watchEvents.isEmpty()) {
            return Collections.singletonList(FileWatcherEvent.delete(watchedPath.toFile()));
        } else {
            return CollectionUtils.collect(watchEvents, watchEventTransformer);
        }
    }

    private FileWatcherEvent toEvent(WatchEvent.Kind kind, File file) {
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
            return FileWatcherEvent.create(file);
        } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
            return FileWatcherEvent.delete(file);
        } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
            return FileWatcherEvent.modify(file);
        } else if (kind == StandardWatchEventKinds.OVERFLOW) {
            return FileWatcherEvent.undefined();
        } else {
            throw new IllegalStateException("Unknown watch kind " + kind);
        }
    }
}
