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

package org.gradle.launcher.daemon.server.health.gc;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.jvm.Jvm;
import org.gradle.util.internal.CollectionUtils;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryManagerMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.util.List;

public class GarbageCollectorMonitoringStrategy {

    public static final GarbageCollectorMonitoringStrategy ORACLE_PARALLEL_CMS =
        new GarbageCollectorMonitoringStrategy("PS Old Gen", "Metaspace", "PS MarkSweep", 1.2, 80, 80, 5.0);
    public static final GarbageCollectorMonitoringStrategy ORACLE_6_CMS =
        new GarbageCollectorMonitoringStrategy("CMS Old Gen", "Metaspace", "ConcurrentMarkSweep", 1.2, 80, 80, 5.0);
    public static final GarbageCollectorMonitoringStrategy ORACLE_SERIAL =
        new GarbageCollectorMonitoringStrategy("Tenured Gen", "Metaspace", "MarkSweepCompact", 1.2, 80, 80, 5.0);
    public static final GarbageCollectorMonitoringStrategy ORACLE_G1 =
        new GarbageCollectorMonitoringStrategy("G1 Old Gen", "Metaspace", "G1 Old Generation", 0.4, 75, 80, 2.0);
    public static final GarbageCollectorMonitoringStrategy IBM_ALL =
        new GarbageCollectorMonitoringStrategy("Java heap", "Not Used", "MarkSweepCompact", 0.8, 70, -1, 6.0);
    public static final GarbageCollectorMonitoringStrategy UNKNOWN =
        new GarbageCollectorMonitoringStrategy(null, null, null, -1, -1, -1, -1);

    public static final List<GarbageCollectorMonitoringStrategy> STRATEGIES = ImmutableList.of(
        ORACLE_PARALLEL_CMS, ORACLE_6_CMS, ORACLE_SERIAL, ORACLE_G1, IBM_ALL, UNKNOWN
    );

    private static final Logger LOGGER = Logging.getLogger(GarbageCollectionMonitor.class);

    private final String heapPoolName;
    private final String nonHeapPoolName;
    private final String garbageCollectorName;
    private final double gcRateThreshold;
    private final int heapUsageThreshold;
    private final int nonHeapUsageThreshold;
    private final double thrashingThreshold;

    @VisibleForTesting
    public GarbageCollectorMonitoringStrategy(String heapPoolName, String nonHeapPoolName, String garbageCollectorName, double gcRateThreshold, int heapUsageThreshold, int nonHeapUsageThreshold, double thrashingThreshold) {
        this.heapPoolName = heapPoolName;
        this.nonHeapPoolName = nonHeapPoolName;
        this.garbageCollectorName = garbageCollectorName;
        this.gcRateThreshold = gcRateThreshold;
        this.heapUsageThreshold = heapUsageThreshold;
        this.nonHeapUsageThreshold = nonHeapUsageThreshold;
        this.thrashingThreshold = thrashingThreshold;
    }

    public String getHeapPoolName() {
        return heapPoolName;
    }

    public String getNonHeapPoolName() {
        return nonHeapPoolName;
    }

    public String getGarbageCollectorName() {
        return garbageCollectorName;
    }

    public double getGcRateThreshold() {
        return gcRateThreshold;
    }

    public int getHeapUsageThreshold() {
        return heapUsageThreshold;
    }

    public int getNonHeapUsageThreshold() {
        return nonHeapUsageThreshold;
    }

    public double getThrashingThreshold() {
        return thrashingThreshold;
    }

    public boolean isAboveHeapUsageThreshold(int percent) {
        return heapUsageThreshold != -1 && percent >= heapUsageThreshold;
    }

    public boolean isAboveNonHeapUsageThreshold(int percent) {
        return nonHeapUsageThreshold != -1 && percent >= nonHeapUsageThreshold;
    }

    public boolean isAboveGcRateThreshold(double gcEventsPerSec) {
        return gcRateThreshold != -1 && gcEventsPerSec >= gcRateThreshold;
    }

    public boolean isAboveGcThrashingThreshold(double gcEventsPerSec) {
        return thrashingThreshold != -1 && gcEventsPerSec >= thrashingThreshold;
    }

    public static GarbageCollectorMonitoringStrategy determineGcStrategy() {
        List<String> garbageCollectors = CollectionUtils.collect(ManagementFactory.getGarbageCollectorMXBeans(), MemoryManagerMXBean::getName);
        GarbageCollectorMonitoringStrategy gcStrategy = CollectionUtils.findFirst(STRATEGIES, strategy -> garbageCollectors.contains(strategy.getGarbageCollectorName()));

        // TODO: These messages we print below are not actionable. Ideally, we would instruct the user to file an issue
        // noting the GC parameters they are using so that we can add that GC to our STRATEGIES.
        if (gcStrategy == null) {
            LOGGER.info("Unable to determine a garbage collection monitoring strategy for {}", Jvm.current());
            return GarbageCollectorMonitoringStrategy.UNKNOWN;
        }

        List<String> memoryPools = CollectionUtils.collect(ManagementFactory.getMemoryPoolMXBeans(), MemoryPoolMXBean::getName);
        if (!memoryPools.contains(gcStrategy.heapPoolName) || !memoryPools.contains(gcStrategy.nonHeapPoolName)) {
            LOGGER.info("Unable to determine which memory pools to monitor for {}", Jvm.current());
            return GarbageCollectorMonitoringStrategy.UNKNOWN;
        }

        return gcStrategy;
    }
}
