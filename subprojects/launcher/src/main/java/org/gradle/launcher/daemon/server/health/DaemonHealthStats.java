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
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.concurrent.StoppableScheduledExecutor;
import org.gradle.internal.util.NumberUtil;
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionInfo;
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionMonitor;
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionStats;
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectorMonitoringStrategy;
import org.gradle.launcher.daemon.server.stats.DaemonRunningStats;

import static java.lang.String.format;

public class DaemonHealthStats implements Stoppable {

    private final DaemonRunningStats runningStats;
    private final StoppableScheduledExecutor scheduler;
    private final GarbageCollectionInfo gcInfo;
    private final GarbageCollectionMonitor gcMonitor;

    public DaemonHealthStats(DaemonRunningStats runningStats, ExecutorFactory executorFactory) {
        this.runningStats = runningStats;
        this.scheduler = executorFactory.createScheduled("Daemon health stats", 1);
        this.gcInfo = new GarbageCollectionInfo();
        this.gcMonitor = new GarbageCollectionMonitor(scheduler);
    }

    @VisibleForTesting
    DaemonHealthStats(DaemonRunningStats runningStats, GarbageCollectionInfo gcInfo, GarbageCollectionMonitor gcMonitor) {
        this.runningStats = runningStats;
        this.scheduler = null;
        this.gcInfo = gcInfo;
        this.gcMonitor = gcMonitor;
    }

    @Override
    public void stop() {
        if (scheduler != null) {
            scheduler.stop();
        }
    }

    GarbageCollectionMonitor getGcMonitor() {
        return gcMonitor;
    }

    /**
     * elegant description of daemon's health
     */
    String getHealthInfo() {
        int nextBuildNum = runningStats.getBuildCount() + 1;
        if (nextBuildNum == 1) {
            return getFirstBuildHealthInfo();
        } else {
            return getBuildHealthInfo(nextBuildNum);
        }
    }

    private String getFirstBuildHealthInfo() {
        return format("Starting build in new daemon [memory: %s]", NumberUtil.formatBytes(Runtime.getRuntime().maxMemory()));
    }

    private String getBuildHealthInfo(int nextBuildNum) {
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

    /**
     * 0-100, the percentage of time spent on doing the work vs time spent in gc
     */
    private int getCurrentPerformance() {
        long collectionTime = gcInfo.getCollectionTime();
        long allBuildsTime = runningStats.getAllBuildsTime();

        if (collectionTime > 0 && collectionTime < allBuildsTime) {
            return 100 - NumberUtil.percentOf(collectionTime, allBuildsTime);
        } else {
            return 100;
        }
    }
}
