/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.filewatch;

import org.gradle.initialization.BuildCancellationToken;
import org.gradle.initialization.BuildGateToken;

public class DefaultFileSystemChangeWaiterFactory implements FileSystemChangeWaiterFactory {
    public static final String QUIET_PERIOD_SYSPROP = "org.gradle.internal.filewatch.quietperiod";
    public static final String GATED_BUILD_SYSPROP = "org.gradle.internal.continuous.gated";

    private final FileWatcherFactory fileWatcherFactory;
    private final long quietPeriodMillis;
    private final boolean gatedBuild;

    public DefaultFileSystemChangeWaiterFactory(FileWatcherFactory fileWatcherFactory) {
        this(fileWatcherFactory, getDefaultQuietPeriod(), isGatedBuild());
    }

    private static long getDefaultQuietPeriod() {
        return Long.getLong(QUIET_PERIOD_SYSPROP, 250L);
    }

    private static boolean isGatedBuild() {
        return Boolean.getBoolean(GATED_BUILD_SYSPROP);
    }

    public DefaultFileSystemChangeWaiterFactory(FileWatcherFactory fileWatcherFactory, long quietPeriodMillis, boolean gatedBuild) {
        this.fileWatcherFactory = fileWatcherFactory;
        this.quietPeriodMillis = quietPeriodMillis;
        this.gatedBuild = gatedBuild;
    }

    @Override
    public FileSystemChangeWaiter createChangeWaiter(PendingChangesListener listener, BuildCancellationToken cancellationToken, BuildGateToken buildGateToken) {
        return new DefaultGatedChangeWaiter(new DefaultFileSystemChangeWaiter(fileWatcherFactory, listener, quietPeriodMillis, cancellationToken), cancellationToken, buildGateToken, gatedBuild);
    }
}
