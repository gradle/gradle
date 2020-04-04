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

import com.google.common.collect.ImmutableSet;
import net.rubygrapefruit.platform.Native;
import net.rubygrapefruit.platform.internal.jni.OsxFileEventFunctions;
import org.gradle.internal.vfs.watch.FileWatcherRegistry;
import org.gradle.internal.vfs.watch.FileWatcherRegistryFactory;
import org.gradle.internal.vfs.watch.WatchRootUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class DarwinFileWatcherRegistry extends AbstractEventDrivenFileWatcherRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(DarwinFileWatcherRegistry.class);

    public DarwinFileWatcherRegistry(Set<Path> watchRoots, ChangeHandler handler) {
        super(
            watchRoots,
            callback -> Native.get(OsxFileEventFunctions.class)
                .startWatcher(
                    // TODO Figure out a good value for this
                    20, TimeUnit.MICROSECONDS,
                    callback
                ),
            handler
        );
    }

    public static class Factory implements FileWatcherRegistryFactory {
        @Override
        public FileWatcherRegistry startWatching(SnapshotHierarchy snapshotHierarchy, Predicate<String> watchFilter, Collection<File> mustWatchDirectories, ChangeHandler handler) {
            Set<String> mustWatchDirectoryPrefixes = ImmutableSet.copyOf(
                mustWatchDirectories.stream()
                    .map(path -> path.getAbsolutePath() + File.separator)
                    ::iterator
            );
            Set<String> directories = WatchRootUtil.resolveDirectoriesToWatch(
                snapshotHierarchy,
                path -> watchFilter.test(path) || startsWithAnyPrefix(path, mustWatchDirectoryPrefixes),
                mustWatchDirectories);
            Set<Path> watchRoots = WatchRootUtil.resolveRootsToWatch(directories);
            LOGGER.warn("Watching {} directory hierarchies to track changes between builds in {} directories", watchRoots.size(), directories.size());
            return new DarwinFileWatcherRegistry(watchRoots, handler);
        }

        private static boolean startsWithAnyPrefix(String path, Set<String> prefixes) {
            for (String prefix : prefixes) {
                if (path.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }
    }
}
