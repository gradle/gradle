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

import com.google.common.annotations.VisibleForTesting;
import net.rubygrapefruit.platform.Native;
import net.rubygrapefruit.platform.file.FileWatcher;
import net.rubygrapefruit.platform.internal.jni.WindowsFileEventFunctions;
import org.gradle.internal.vfs.impl.WatcherEvent;
import org.gradle.internal.vfs.watch.FileWatcherRegistry;
import org.gradle.internal.vfs.watch.FileWatcherRegistryFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WindowsFileWatcherRegistry implements FileWatcherRegistry {

    private final FileWatcher watcher;
    private final List<WatcherEvent> events = new ArrayList<>();

    public WindowsFileWatcherRegistry(Set<Path> watchRoots) {
        this.watcher = Native.get(WindowsFileEventFunctions.class)
            .startWatching(
                watchRoots.stream()
                    .map(Path::toString)
                    .collect(Collectors.toList()),
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

    /**
     * Filters out directories whose ancestor is also among the watched directories.
     */
    @VisibleForTesting
    static Set<Path> resolveRootsToWatch(Set<Path> directories) {
        Set<Path> roots = new HashSet<>();
        directories.stream()
            .sorted(Comparator.comparingInt(Path::getNameCount))
            .filter(path -> {
                Path parent = path;
                while (true) {
                    parent = parent.getParent();
                    if (parent == null) {
                        break;
                    }
                    if (roots.contains(parent)) {
                        return false;
                    }
                }
                return true;
            })
            .forEach(roots::add);
        return roots;
    }

    public static class Factory implements FileWatcherRegistryFactory {
        @Override
        public FileWatcherRegistry startWatching(Set<Path> directories) {
            return new WindowsFileWatcherRegistry(resolveRootsToWatch(directories));
        }
    }
}
