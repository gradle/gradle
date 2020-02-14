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

import net.rubygrapefruit.platform.file.FileWatcher;
import net.rubygrapefruit.platform.file.FileWatcherCallback;
import org.gradle.internal.vfs.impl.WatcherEvent;
import org.gradle.internal.vfs.watch.FileWatcherRegistry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AbstractEventDrivenFileWatcherRegistry implements FileWatcherRegistry {
    private final FileWatcher watcher;
    private final AtomicInteger numberOfReceivedEvents = new AtomicInteger();
    private final AtomicBoolean unknownEventEncountered = new AtomicBoolean();

    public AbstractEventDrivenFileWatcherRegistry(FileWatcherCreator watcherCreator, ChangeHandler handler) {
        ChangeHandler statisticsGatheringHandler = new ChangeHandler() {
            @Override
            public void handleChange(Type type, Path path) {
                numberOfReceivedEvents.incrementAndGet();
                handler.handleChange(type, path);
            }

            @Override
            public void handleLostState() {
                unknownEventEncountered.set(true);
                handler.handleLostState();
            }
        };
        this.watcher = watcherCreator.createWatcher((type, path) -> WatcherEvent.createEvent(type, path).dispatch(statisticsGatheringHandler));
    }

    @Override
    public FileWatchingStatistics stopWatching() throws IOException {
        // Make sure events stop arriving before we start dispatching
        watcher.close();
        return new FileWatchingStatistics(unknownEventEncountered.get(), numberOfReceivedEvents.get());
    }

    @Override
    public void close() throws IOException {
        watcher.close();
    }

    protected interface FileWatcherCreator {
        FileWatcher createWatcher(FileWatcherCallback callback);
    }
}
