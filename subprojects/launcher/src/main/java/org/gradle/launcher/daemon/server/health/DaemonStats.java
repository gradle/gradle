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
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionMonitor;
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionStats;
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectorMonitoringStrategy;
import org.gradle.util.Clock;

import java.util.concurrent.ScheduledExecutorService;

import static java.lang.String.format;

public class DaemonStats {

    private final Clock totalTime;
    private final TimeProvider timeProvider;
    private final MemoryInfo memory;
    private final GarbageCollectionMonitor gcMonitor;

    private int buildCount;
    private long currentBuildStart;
    private long allBuildsTime;
    private int currentPerformance;

    DaemonStats(ScheduledExecutorService scheduledExecutorService) {
        this(new Clock(), new TrueTimeProvider(), new MemoryInfo(), new GarbageCollectionMonitor(scheduledExecutorService));
    }

    public DaemonStats(Clock totalTime, TimeProvider timeProvider, MemoryInfo memory, GarbageCollectionMonitor gcMonitor) {
        this.totalTime = totalTime;
        this.timeProvider = timeProvider;
        this.memory = memory;
        this.gcMonitor = gcMonitor;
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
        long buildTime = Math.max(timeProvider.getCurrentTime() - currentBuildStart, 1);
        allBuildsTime += buildTime;
        currentPerformance = performance(allBuildsTime, memory);
    }

    private static int performance(long totalTime, MemoryInfo memoryInfo) {
        //TODO SF consider not showing (or show '-') when getCollectionTime() returns 0
        if (memoryInfo.getCollectionTime() > 0 && memoryInfo.getCollectionTime() < totalTime) {
            return 100 - NumberUtil.percentOf(memoryInfo.getCollectionTime(), totalTime);
        } else {
            return 100;
        }
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
            return format("Starting build in new daemon [memory: %s]", NumberUtil.formatBytes(memory.getMaxMemory()));
        } else {
            String message = format("Starting %s build in daemon [uptime: %s, performance: %s%%",
                NumberUtil.ordinal(buildCount), totalTime.getTime(), getCurrentPerformance());
            if (gcMonitor.getGcStrategy() != GarbageCollectorMonitoringStrategy.UNKNOWN) {
                GarbageCollectionStats tenuredStats = gcMonitor.getTenuredStats();
                if (tenuredStats.getUsage() > 0) {
                    message += format(", GC rate: %.2f/s, tenured heap usage: %s%% of %s", tenuredStats.getRate(), tenuredStats.getUsage(), NumberUtil.formatBytes(tenuredStats.getMax()));
                } else {
                    message += ", no major garbage collections";
                }
            }
            return message + "]";
        }
    }

    GarbageCollectionMonitor getGcMonitor() {
        return gcMonitor;
    }
}
