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
import net.rubygrapefruit.platform.internal.jni.DefaultOsxFileEventFunctions;
import org.gradle.internal.vfs.watch.FileWatcherRegistry;
import org.gradle.internal.vfs.watch.FileWatcherRegistryFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public class DarwinFileWatcherRegistry implements FileWatcherRegistry {

    private final DefaultOsxFileEventFunctions fileEvents;
    private DefaultOsxFileEventFunctions.WatcherThread watcher;

    public DarwinFileWatcherRegistry(Iterable<Path> watchRoots) throws IOException {
        this.fileEvents = Native.get(DefaultOsxFileEventFunctions.class);
        for (Path watchRoot : watchRoots) {
            fileEvents.addRecursiveWatch(watchRoot.toFile());
        }
        this.watcher = fileEvents.startWatch();
    }

    @Override
    public void stopWatching(ChangeHandler handler) throws IOException {
        if (watcher == null) {
            throw new IllegalStateException("Watcher already closed");
        }
        try {
            fileEvents.stopWatch(watcher)
                .forEach(changedPath -> handler.handleChange(Type.MODIFIED, Paths.get(changedPath)));
            watcher = null;
        } catch (Exception ex) {
            throw new IOException("Couldn't get watches", ex);
        }
    }

    @Override
    public void close() throws IOException {
        if (watcher == null) {
            return;
        }
        try {
            fileEvents.stopWatch(watcher);
            watcher = null;
        } catch (InterruptedException ex) {
            // TODO ignored for now
        }
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
        public FileWatcherRegistry startWatching(Set<Path> directories) throws IOException {
            return new DarwinFileWatcherRegistry(resolveRootsToWatch(directories));
        }
    }
}
