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
import org.gradle.internal.concurrent.ManagedScheduledExecutor;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.util.NumberUtil;
import org.gradle.launcher.daemon.server.health.gc.DefaultGarbageCollectionMonitor;
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionInfo;
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionMonitor;
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionStats;
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectorMonitoringStrategy;
import org.gradle.launcher.daemon.server.stats.DaemonRunningStats;

import java.util.Locale;

import static java.lang.String.format;

public class DaemonHealthStats implements Stoppable {

    private final DaemonRunningStats runningStats;
    private final ManagedScheduledExecutor scheduler;
    private final GarbageCollectionInfo gcInfo;
    private GarbageCollectionMonitor gcMonitor;

    public DaemonHealthStats(DaemonRunningStats runningStats, GarbageCollectorMonitoringStrategy strategy, ExecutorFactory executorFactory) {
        this.runningStats = runningStats;
        this.scheduler = executorFactory.createScheduled("Daemon health stats", 1);
        this.gcInfo = new GarbageCollectionInfo();
        this.gcMonitor = new DefaultGarbageCollectionMonitor(strategy, scheduler);
    }

    @VisibleForTesting
    DaemonHealthStats(DaemonRunningStats runningStats, GarbageCollectionInfo gcInfo, GarbageCollectionMonitor gcMonitor) {
        this.runningStats = runningStats;
        this.scheduler = null;
        this.gcInfo = gcInfo;
        this.gcMonitor = gcMonitor;
    }

    @VisibleForTesting
    public GarbageCollectionMonitor getGcMonitor() {
        return gcMonitor;
    }

    @Override
    public void stop() {
        if (scheduler != null) {
            scheduler.stop();
        }
    }

    public GarbageCollectionStats getHeapStats() {
        return gcMonitor.getHeapStats();
    }

    public GarbageCollectionStats getNonHeapStats() {
        return gcMonitor.getNonHeapStats();
    }

    /**
     * Elegant description of daemon's health
     */
    public String getHealthInfo() {
        StringBuilder message = new StringBuilder();
        message.append(format("[uptime: %s, performance: %s%%", runningStats.getPrettyUpTime(), getCurrentPerformance()));

        GarbageCollectionStats heapStats = gcMonitor.getHeapStats();
        if (heapStats.isValid()) {
            message.append(format(Locale.ENGLISH, ", GC rate: %.2f/s", heapStats.getGcRate()));
            message.append(format(", heap usage: %s%% of %s", heapStats.getUsedPercent(), NumberUtil.formatBytes(heapStats.getMaxSizeInBytes())));
        }

        GarbageCollectionStats nonHeapStats = gcMonitor.getNonHeapStats();
        if (nonHeapStats.isValid()) {
            message.append(format(", non-heap usage: %s%% of %s", nonHeapStats.getUsedPercent(), NumberUtil.formatBytes(nonHeapStats.getMaxSizeInBytes())));
        }
        message.append("]");

        return message.toString();
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
