/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.launcher.daemon.server.health;

import org.gradle.internal.TimeProvider;
import org.gradle.internal.TrueTimeProvider;
import org.gradle.internal.util.NumberUtil;
import org.gradle.util.Clock;

import static java.lang.String.format;

class DaemonStats {

    private final Clock totalTime;
    private final TimeProvider timeProvider;
    private final GCStats gcStats;
    private final long comittedMemory;
    private final long maxMemory;

    private int buildCount;
    private long currentBuildStart;
    private long allBuildsTime;

    DaemonStats() {
        this(Runtime.getRuntime().totalMemory(), Runtime.getRuntime().maxMemory(),
                new Clock(), new TrueTimeProvider(), new GCStats());
    }

    DaemonStats(long comittedMemory, long maxMemory, Clock startTime, TimeProvider timeProvider, GCStats gcStats) {
        this.comittedMemory = comittedMemory;
        this.maxMemory = maxMemory;
        this.totalTime = startTime;
        this.timeProvider = timeProvider;
        this.gcStats = gcStats;
    }

    /**
     * Informs the stats that build started and returns an elegant description of daemon stats
     */
    public String buildStarted() {
        ++buildCount;
        currentBuildStart = timeProvider.getCurrentTime();
        if (buildCount == 1) {
            return format("Starting build in new daemon [memory: %s]", prettyBytes(maxMemory));
        } else {
            return format("Executing %s build in daemon [uptime: %s, performance: %s%%, memory: %s%% of %s]",
                    IntegerTextUtil.ordinal(buildCount), totalTime.getTime(), performance(allBuildsTime, gcStats), NumberUtil.percent(maxMemory, comittedMemory), prettyBytes(maxMemory));
        }
    }

    /**
     * Informs the stats that the build finished
     */
    public void buildFinished() {
        allBuildsTime += timeProvider.getCurrentTime() - currentBuildStart;
    }

    //TODO SF rework, possibly find different place for below

    private static int performance(long totalTime, GCStats gcStats) {
        //TODO SF consider not showing (or show '-') when getCollectionTime() returns 0
        return 100 - NumberUtil.percent(totalTime, gcStats.getCollectionTime());
    }

    private static String prettyBytes(long bytes) {
        int unit = 1000;
        if (bytes < unit) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        char pre = "kMGTPE".charAt(exp - 1);
        return format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }
}
