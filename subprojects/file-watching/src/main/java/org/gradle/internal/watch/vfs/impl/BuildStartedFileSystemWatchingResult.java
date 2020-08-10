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

package org.gradle.internal.watch.vfs.impl;

import org.gradle.internal.snapshot.SnapshotHierarchy;
import org.gradle.internal.watch.registry.FileWatcherRegistry;

import javax.annotation.Nullable;

public class BuildStartedFileSystemWatchingResult extends FileSystemWatchingLifecycleResult {
    public static final String DISPLAY_NAME = "Build started for file system watching";

    private final boolean startedWatching;

    public BuildStartedFileSystemWatchingResult(
        boolean watchingEnabled, boolean startedWatching,
        @Nullable FileWatcherRegistry.FileWatchingStatistics fileWatchingStatistics,
        SnapshotHierarchy vfsRoot
    ) {
        super(watchingEnabled, fileWatchingStatistics, vfsRoot);
        this.startedWatching = startedWatching;
    }

    public boolean isStartedWatching() {
        return startedWatching;
    }
}
