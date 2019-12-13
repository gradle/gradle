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

import net.rubygrapefruit.platform.file.FileWatcherCallback;
import org.gradle.internal.vfs.watch.FileWatcherRegistry;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.gradle.internal.vfs.watch.FileWatcherRegistry.Type.CREATED;
import static org.gradle.internal.vfs.watch.FileWatcherRegistry.Type.INVALIDATE;
import static org.gradle.internal.vfs.watch.FileWatcherRegistry.Type.MODIFIED;
import static org.gradle.internal.vfs.watch.FileWatcherRegistry.Type.REMOVED;

public interface WatcherEvent {
    void dispatch(FileWatcherRegistry.ChangeHandler handler);

    static WatcherEvent createEvent(FileWatcherCallback.Type type, String path) {
        switch (type) {
            case CREATED:
                return new ChangeEvent(CREATED, path);
            case MODIFIED:
                return new ChangeEvent(MODIFIED, path);
            case REMOVED:
                return new ChangeEvent(REMOVED, path);
            case INVALIDATE:
                return new ChangeEvent(INVALIDATE, path);
            case UNKNOWN:
                return StateLostEvent.INSTANCE;
            default:
                throw new AssertionError();
        }
    }

    static void dispatch(Iterable<WatcherEvent> events, FileWatcherRegistry.ChangeHandler handler) throws IOException {
        try {
            events.forEach(watcherEvent -> watcherEvent.dispatch(handler));
        } catch (Exception ex) {
            throw new IOException("Couldn't get watches", ex);
        }
    }

    class ChangeEvent implements WatcherEvent {
        private final FileWatcherRegistry.Type type;
        private final Path path;

        public ChangeEvent(FileWatcherRegistry.Type type, String path) {
            this.type = type;
            this.path = Paths.get(path);
        }

        @Override
        public void dispatch(FileWatcherRegistry.ChangeHandler handler) {
            handler.handleChange(type, path);
        }
    }

    class StateLostEvent implements WatcherEvent {
        public static final WatcherEvent INSTANCE = new StateLostEvent();

        @Override
        public void dispatch(FileWatcherRegistry.ChangeHandler handler) {
            handler.handleLostState();
        }
    }
}
