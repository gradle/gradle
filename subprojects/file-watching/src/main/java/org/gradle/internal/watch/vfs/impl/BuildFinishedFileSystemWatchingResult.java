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

public class BuildFinishedFileSystemWatchingResult {
    public static final String DISPLAY_NAME = "Build finished for file system watching";

    private final boolean watchingEnabled;
    private final boolean stoppedWatchingDuringTheBuild;
    private final FileSystemWatchingStatistics statistics;

    public BuildFinishedFileSystemWatchingResult(
        boolean watchingEnabled,
        boolean stoppedWatchingDuringTheBuild,
        @Nullable FileSystemWatchingStatistics statistics
    ) {
        this.watchingEnabled = watchingEnabled;
        this.stoppedWatchingDuringTheBuild = stoppedWatchingDuringTheBuild;
        this.statistics = statistics;
    }

    public boolean isWatchingEnabled() {
        return watchingEnabled;
    }

    public boolean isStoppedWatchingDuringTheBuild() {
        return stoppedWatchingDuringTheBuild;
    }

    @Nullable
    public FileSystemWatchingStatistics getStatistics() {
        return statistics;
    }
}
