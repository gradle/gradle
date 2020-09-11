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

package org.gradle.internal.watch.vfs;

import org.gradle.internal.operations.BuildOperationType;

import javax.annotation.Nullable;

public interface BuildFinishedFileSystemWatchingBuildOperationType extends BuildOperationType<BuildFinishedFileSystemWatchingBuildOperationType.Details, BuildFinishedFileSystemWatchingBuildOperationType.Result> {
    String DISPLAY_NAME = "Build finished for file system watching";

    interface Details {
        Details INSTANCE = new Details() {};
    }

    interface Result {
        Result WATCHING_DISABLED = new Result() {
            @Override
            public boolean isWatchingEnabled() {
                return false;
            }

            @Override
            public boolean isStoppedWatchingDuringTheBuild() {
                return false;
            }

            @Override
            public FileSystemWatchingStatistics getStatistics() {
                return null;
            }
        };

        boolean isWatchingEnabled();

        boolean isStoppedWatchingDuringTheBuild();

        @Nullable
        FileSystemWatchingStatistics getStatistics();
    }
}
