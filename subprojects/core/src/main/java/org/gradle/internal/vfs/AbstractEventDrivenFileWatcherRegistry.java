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
import org.gradle.internal.vfs.impl.WatcherEvent;
import org.gradle.internal.vfs.watch.FileWatcherRegistry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AbstractEventDrivenFileWatcherRegistry implements FileWatcherRegistry {
    private final FileWatcher watcher;
    private final List<WatcherEvent> events = new ArrayList<>();

    public AbstractEventDrivenFileWatcherRegistry(FileWatcherCreator watcherCreator) {
        this.watcher = watcherCreator.createWatcher(events);
    }

    @Override
    public void stopWatching(ChangeHandler handler) throws IOException {
        // Make sure events stop arriving before we start dispatching
        watcher.close();
        WatcherEvent.dispatch(events, handler);
    }

    @Override
    public void close() throws IOException {
        watcher.close();
    }

    protected interface FileWatcherCreator {
        FileWatcher createWatcher(Collection<WatcherEvent> events);
    }
}
