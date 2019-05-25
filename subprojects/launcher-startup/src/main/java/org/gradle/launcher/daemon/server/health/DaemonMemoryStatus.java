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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.specs.Spec;
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionStats;

public class DaemonMemoryStatus {

    private static final Logger LOGGER = Logging.getLogger(DaemonMemoryStatus.class);

    public static final String ENABLE_PERFORMANCE_MONITORING = "org.gradle.daemon.performance.enable-monitoring";

    private static final String HEAP = "heap";
    private static final String NON_HEAP = "non-heap";

    private final DaemonHealthStats stats;
    private final int heapUsageThreshold;
    private final double heapRateThreshold;
    private final int nonHeapUsageThreshold;
    private final double thrashingThreshold;

    public DaemonMemoryStatus(DaemonHealthStats stats, int heapUsageThreshold, double heapRateThreshold, int nonHeapUsageThreshold, double thrashingThreshold) {
        this.stats = stats;
        this.heapUsageThreshold = heapUsageThreshold;
        this.heapRateThreshold = heapRateThreshold;
        this.nonHeapUsageThreshold = nonHeapUsageThreshold;
        this.thrashingThreshold = thrashingThreshold;
    }

    public boolean isHeapSpaceExhausted() {
        GarbageCollectionStats gcStats = stats.getHeapStats();

        return exceedsThreshold(HEAP, gcStats, new Spec<GarbageCollectionStats>() {
            @Override
            public boolean isSatisfiedBy(GarbageCollectionStats gcStats) {
                return heapUsageThreshold != 0
                    && heapRateThreshold != 0
                    && gcStats.isValid()
                    && gcStats.getUsedPercent() >= heapUsageThreshold
                    && gcStats.getGcRate() >= heapRateThreshold;
            }
        });
    }

    public boolean isNonHeapSpaceExhausted() {
        GarbageCollectionStats gcStats = stats.getNonHeapStats();

        return exceedsThreshold(NON_HEAP, gcStats, new Spec<GarbageCollectionStats>() {
            @Override
            public boolean isSatisfiedBy(GarbageCollectionStats gcStats) {
                return nonHeapUsageThreshold != 0
                    && gcStats.isValid()
                    && gcStats.getUsedPercent() >= nonHeapUsageThreshold;
            }
        });
    }

    public boolean isThrashing() {
        GarbageCollectionStats gcStats = stats.getHeapStats();

        return exceedsThreshold(HEAP, gcStats, new Spec<GarbageCollectionStats>() {
            @Override
            public boolean isSatisfiedBy(GarbageCollectionStats gcStats) {
                return heapUsageThreshold != 0
                    && thrashingThreshold != 0
                    && gcStats.isValid()
                    && gcStats.getUsedPercent() >= heapUsageThreshold
                    && gcStats.getGcRate() >= thrashingThreshold;
            }
        });
    }

    private boolean exceedsThreshold(String pool, GarbageCollectionStats gcStats, Spec<GarbageCollectionStats> spec) {
        if (isEnabled() && spec.isSatisfiedBy(gcStats)) {
            if (gcStats.isValid() && gcStats.getUsedPercent() > 0) {
                LOGGER.debug(String.format("%s: GC rate: %.2f/s %s usage: %s%%", pool, gcStats.getGcRate(), pool, gcStats.getUsedPercent()));
            } else {
                LOGGER.debug("{}: GC rate: 0.0/s", pool);
            }

            return true;
        }

        return false;
    }

    private boolean isEnabled() {
        String enabledValue = System.getProperty(ENABLE_PERFORMANCE_MONITORING, "true");
        return Boolean.parseBoolean(enabledValue);
    }
}
