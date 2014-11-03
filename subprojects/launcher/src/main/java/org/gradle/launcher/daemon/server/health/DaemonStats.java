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
    private final long committedMemory;
    private final long maxMemory;

    private int buildCount;
    private long currentBuildStart;
    private long allBuildsTime;
    private int currentPerformance;

    DaemonStats() {
        this(Runtime.getRuntime().totalMemory(), Runtime.getRuntime().maxMemory(),
                new Clock(), new TrueTimeProvider(), new GCStats());
    }

    DaemonStats(long committedMemory, long maxMemory, Clock startTime, TimeProvider timeProvider, GCStats gcStats) {
        this.committedMemory = committedMemory;
        this.maxMemory = maxMemory;
        this.totalTime = startTime;
        this.timeProvider = timeProvider;
        this.gcStats = gcStats;
    }

    /**
     * Informs the stats that build started
     */
    void buildStarted() {
        ++buildCount;
        currentBuildStart = timeProvider.getCurrentTime();
    }

    /**
     * Informs the stats that the build finished
     */
    void buildFinished() {
        allBuildsTime += timeProvider.getCurrentTime() - currentBuildStart;
        currentPerformance = performance(allBuildsTime, gcStats);
    }

    private static int performance(long totalTime, GCStats gcStats) {
        //TODO SF consider not showing (or show '-') when getCollectionTime() returns 0
        return 100 - NumberUtil.percentOf(gcStats.getCollectionTime(), totalTime);
    }

    /**
     * 0-100, the percentage of time spent on doing the work vs time spent in gc
     */
    int getCurrentPerformance() {
        return currentPerformance;
    }

    /**
     * elegant description of daemon's health
     */
    String getHealthInfo() {
        if (buildCount == 1) {
            return format("Starting build in new daemon [memory: %s]", NumberUtil.formatBytes(maxMemory));
        } else {
            return format("Starting %s build in daemon [uptime: %s, performance: %s%%, memory: %s%% of %s]",
                    NumberUtil.ordinal(buildCount), totalTime.getTime(), currentPerformance, getMemoryUsed(), NumberUtil.formatBytes(maxMemory));
        }
    }

    /**
     * 0-100, the percentage of memory used of total memory available to the process
     */
    int getMemoryUsed() {
        return NumberUtil.percentOf(committedMemory, maxMemory);
    }
}
