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

import net.rubygrapefruit.platform.Native;
import net.rubygrapefruit.platform.file.FileWatcher;
import net.rubygrapefruit.platform.internal.jni.OsxFileEventFunctions;
import org.gradle.internal.vfs.impl.WatchRootUtil;
import org.gradle.internal.vfs.impl.WatcherEvent;
import org.gradle.internal.vfs.watch.FileWatcherRegistry;
import org.gradle.internal.vfs.watch.FileWatcherRegistryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DarwinFileWatcherRegistry implements FileWatcherRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(DarwinFileWatcherRegistry.class);

    private final FileWatcher watcher;
    private final List<WatcherEvent> events = new ArrayList<>();

    public DarwinFileWatcherRegistry(Set<Path> watchRoots) {
        this.watcher = Native.get(OsxFileEventFunctions.class)
            .startWatching(
                watchRoots.stream()
                    .map(Path::toString)
                    .collect(Collectors.toList()),
                // TODO Figure out a good value for this
                0.3,
                (type, path) -> events.add(WatcherEvent.createEvent(type, path))
            );
    }

    @Override
    public void stopWatching(ChangeHandler handler) throws IOException {
        WatcherEvent.dispatch(events, handler);
    }

    @Override
    public void close() throws IOException {
        watcher.close();
    }

    public static class Factory implements FileWatcherRegistryFactory {
        @Override
        public FileWatcherRegistry startWatching(Set<Path> directories) {
            Set<Path> watchRoots = WatchRootUtil.resolveRootsToWatch(directories);
            LOGGER.warn("Watching {} directory hierarchies to track changes between builds in {} directories", watchRoots.size(), directories.size());
            return new DarwinFileWatcherRegistry(watchRoots);
        }
    }
}
