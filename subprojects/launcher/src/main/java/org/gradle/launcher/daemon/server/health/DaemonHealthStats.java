/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.internal.util.NumberUtil;
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionInfo;
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionMonitor;
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionStats;
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectorMonitoringStrategy;
import org.gradle.launcher.daemon.server.health.memory.MemoryInfo;
import org.gradle.launcher.daemon.server.stats.DaemonRunningStats;

import java.util.concurrent.ScheduledExecutorService;

import static java.lang.String.format;

public class DaemonHealthStats {

    private final DaemonRunningStats runningStats;
    private final MemoryInfo memoryInfo;
    private final GarbageCollectionInfo gcInfo;
    private final GarbageCollectionMonitor gcMonitor;

    public DaemonHealthStats(DaemonRunningStats runningStats, ScheduledExecutorService scheduledExecutorService) {
        this(runningStats, new MemoryInfo(), new GarbageCollectionInfo(), new GarbageCollectionMonitor(scheduledExecutorService));
    }

    @VisibleForTesting
    DaemonHealthStats(DaemonRunningStats runningStats, MemoryInfo memoryInfo, GarbageCollectionInfo gcInfo, GarbageCollectionMonitor gcMonitor) {
        this.runningStats = runningStats;
        this.memoryInfo = memoryInfo;
        this.gcInfo = gcInfo;
        this.gcMonitor = gcMonitor;
    }

    /**
     * 0-100, the percentage of time spent on doing the work vs time spent in gc
     */
    int getCurrentPerformance() {
        long collectionTime = gcInfo.getCollectionTime();
        long allBuildsTime = runningStats.getAllBuildsTime();

        if (collectionTime > 0 && collectionTime < allBuildsTime) {
            return 100 - NumberUtil.percentOf(collectionTime, allBuildsTime);
        } else {
            return 100;
        }
    }

    /**
     * elegant description of daemon's health
     */
    String getHealthInfo() {
        int nextBuildNum = runningStats.getBuildCount() + 1;
        if (nextBuildNum == 1) {
            return format("Starting build in new daemon [memory: %s]", NumberUtil.formatBytes(memoryInfo.getMaxMemory()));
        } else {
            if (gcMonitor.getGcStrategy() != GarbageCollectorMonitoringStrategy.UNKNOWN) {
                GarbageCollectionStats tenuredStats = gcMonitor.getTenuredStats();
                GarbageCollectionStats permgenStats = gcMonitor.getPermGenStats();
                String message = format("Starting %s build in daemon [uptime: %s, performance: %s%%",
                    NumberUtil.ordinal(nextBuildNum), runningStats.getPrettyUpTime(), getCurrentPerformance());
                if (tenuredStats.getUsage() > 0) {
                    message += format(", GC rate: %.2f/s, tenured heap usage: %s%% of %s", tenuredStats.getRate(), tenuredStats.getUsage(), NumberUtil.formatBytes(tenuredStats.getMax()));
                    if (permgenStats.getUsage() > 0) {
                        message += format(", perm gen usage: %s%% of %s",
                            permgenStats.getUsage(), NumberUtil.formatBytes(permgenStats.getMax()));
                    }
                } else {
                    message += ", no major garbage collections";
                }
                message += "]";
                return message;
            } else {
                return format("Starting %s build in daemon [uptime: %s, performance: %s%%]",
                    NumberUtil.ordinal(nextBuildNum), runningStats.getPrettyUpTime(), getCurrentPerformance());
            }
        }
    }

    GarbageCollectionMonitor getGcMonitor() {
        return gcMonitor;
    }

}
