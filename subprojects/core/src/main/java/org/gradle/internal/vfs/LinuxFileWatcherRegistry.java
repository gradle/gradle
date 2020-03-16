/*
 * Copyright 2020 the original author or authors.
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
import net.rubygrapefruit.platform.NativeException;
import net.rubygrapefruit.platform.internal.jni.LinuxFileEventFunctions;
import org.gradle.internal.vfs.watch.FileWatcherRegistry;
import org.gradle.internal.vfs.watch.FileWatcherRegistryFactory;
import org.gradle.internal.vfs.watch.WatchRootUtil;
import org.gradle.internal.vfs.watch.WatchingNotSupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class LinuxFileWatcherRegistry extends AbstractEventDrivenFileWatcherRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(LinuxFileWatcherRegistry.class);

    public LinuxFileWatcherRegistry(Set<Path> watchRoots, ChangeHandler handler) {
        super(
            watchRoots,
            callback -> Native.get(LinuxFileEventFunctions.class).startWatcher(callback),
            handler
        );
    }

    public static class Factory implements FileWatcherRegistryFactory {
        @Override
        public FileWatcherRegistry startWatching(SnapshotHierarchy root, Predicate<String> watchFilter, Collection<File> mustWatchDirectories, ChangeHandler handler) {
            try {
                Set<Path> directories = WatchRootUtil.resolveDirectoriesToWatch(root, watchFilter, mustWatchDirectories).stream()
                    .map(Paths::get)
                    .collect(Collectors.toSet());
                LOGGER.warn("Watching {} directories to track changes between builds", directories.size());
                return new LinuxFileWatcherRegistry(directories, handler);
            } catch (NativeException e) {
                if (e.getMessage().contains("Already watching path: ")) {
                    throw new WatchingNotSupportedException("Unable to watch same file twice via different paths: " + e.getMessage(), e);
                }
                throw e;
            }
        }
    }
}
