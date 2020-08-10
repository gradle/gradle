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

import javax.annotation.Nullable;

public class BuildStartedFileSystemWatchingResult {
    public static final String DISPLAY_NAME = "Build started for file system watching";

    private final boolean watchingEnabled;
    private final boolean startedWatching;
    private final FileSystemWatchingStatistics statistics;

    public BuildStartedFileSystemWatchingResult(
        boolean watchingEnabled,
        boolean startedWatching,
        @Nullable FileSystemWatchingStatistics statistics
    ) {
        this.watchingEnabled = watchingEnabled;
        this.startedWatching = startedWatching;
        this.statistics = statistics;
    }

    public boolean isStartedWatching() {
        return startedWatching;
    }

    public boolean isWatchingEnabled() {
        return watchingEnabled;
    }

    @Nullable
    public FileSystemWatchingStatistics getStatistics() {
        return statistics;
    }
}
