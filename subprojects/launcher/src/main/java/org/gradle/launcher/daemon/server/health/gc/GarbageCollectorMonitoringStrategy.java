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

import org.gradle.api.Transformer;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.specs.Spec;
import org.gradle.internal.jvm.Jvm;
import org.gradle.util.internal.CollectionUtils;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.List;

public enum GarbageCollectorMonitoringStrategy {

    ORACLE_PARALLEL_CMS("PS Old Gen", "Metaspace", "PS MarkSweep", 1.2, 80, 80, 5.0),
    ORACLE_6_CMS("CMS Old Gen", "Metaspace", "ConcurrentMarkSweep", 1.2, 80, 80, 5.0),
    ORACLE_SERIAL("Tenured Gen", "Metaspace", "MarkSweepCompact", 1.2, 80, 80, 5.0),
    ORACLE_G1("G1 Old Gen", "Metaspace", "G1 Old Generation", 0.4, 75, 80, 2.0),
    IBM_ALL("Java heap", "Not Used", "MarkSweepCompact", 0.8, 70, -1, 6.0),
    UNKNOWN(null, null, null, -1, -1, -1, -1);

    private static final Logger LOGGER = Logging.getLogger(GarbageCollectionMonitor.class);

    private final String heapPoolName;
    private final String nonHeapPoolName;
    private final String garbageCollectorName;
    private final double gcRateThreshold;
    private final int heapUsageThreshold;
    private final int nonHeapUsageThreshold;
    private final double thrashingThreshold;

    GarbageCollectorMonitoringStrategy(String heapPoolName, String nonHeapPoolName, String garbageCollectorName, double gcRateThreshold, int heapUsageThreshold, int nonHeapUsageThreshold, double thrashingThreshold) {
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

    public String getGarbageCollectorName() {
        return garbageCollectorName;
    }

    public double getGcRateThreshold() {
        return gcRateThreshold;
    }

    public int getHeapUsageThreshold() {
        return heapUsageThreshold;
    }

    public String getNonHeapPoolName() {
        return nonHeapPoolName;
    }

    public int getNonHeapUsageThreshold() {
        return nonHeapUsageThreshold;
    }

    public double getThrashingThreshold() {
        return thrashingThreshold;
    }

    public static GarbageCollectorMonitoringStrategy determineGcStrategy() {
        final List<String> garbageCollectors = CollectionUtils.collect(ManagementFactory.getGarbageCollectorMXBeans(), new Transformer<String, GarbageCollectorMXBean>() {
            @Override
            public String transform(GarbageCollectorMXBean garbageCollectorMXBean) {
                return garbageCollectorMXBean.getName();
            }
        });
        GarbageCollectorMonitoringStrategy gcStrategy = CollectionUtils.findFirst(GarbageCollectorMonitoringStrategy.values(), new Spec<GarbageCollectorMonitoringStrategy>() {
            @Override
            public boolean isSatisfiedBy(GarbageCollectorMonitoringStrategy strategy) {
                return garbageCollectors.contains(strategy.getGarbageCollectorName());
            }
        });

        if (gcStrategy == null) {
            LOGGER.info("Unable to determine a garbage collection monitoring strategy for {}", Jvm.current());
            return GarbageCollectorMonitoringStrategy.UNKNOWN;
        } else {
            List<String> memoryPools = CollectionUtils.collect(ManagementFactory.getMemoryPoolMXBeans(), new Transformer<String, MemoryPoolMXBean>() {
                @Override
                public String transform(MemoryPoolMXBean memoryPoolMXBean) {
                    return memoryPoolMXBean.getName();
                }
            });
            if (!memoryPools.contains(gcStrategy.heapPoolName) || !memoryPools.contains(gcStrategy.nonHeapPoolName)) {
                LOGGER.info("Unable to determine which memory pools to monitor for {}", Jvm.current());
                return GarbageCollectorMonitoringStrategy.UNKNOWN;
            }
            return gcStrategy;
        }
    }
}
