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

import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.specs.Spec;
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionStats;
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectorMonitoringStrategy;

import static java.lang.String.format;

public class DaemonMemoryStatus {

    private static final Logger LOGGER = Logging.getLogger(DaemonMemoryStatus.class);

    public static final String ENABLE_PERFORMANCE_MONITORING = "org.gradle.daemon.performance.enable-monitoring";
    public static final String TENURED_USAGE_EXPIRE_AT = "org.gradle.daemon.performance.tenured-usage-expire-at";
    public static final String TENURED_RATE_EXPIRE_AT = "org.gradle.daemon.performance.tenured-rate-expire-at";
    public static final String PERMGEN_USAGE_EXPIRE_AT = "org.gradle.daemon.performance.permgen-usage-expire-at";
    public static final String THRASHING_EXPIRE_AT = "org.gradle.daemon.performance.thrashing-expire-at";

    private static final String TENURED = "tenured";
    private static final String PERMGEN = "perm gen";

    private final DaemonHealthStats stats;
    private final GarbageCollectorMonitoringStrategy strategy;
    private final int tenuredUsageThreshold;
    private final double tenuredRateThreshold;
    private final int permgenUsageThreshold;
    private final double thrashingThreshold;

    public DaemonMemoryStatus(DaemonHealthStats stats) {
        this.stats = stats;
        this.strategy = stats.getGcMonitor().getGcStrategy();
        this.tenuredUsageThreshold = parseValue(TENURED_USAGE_EXPIRE_AT, strategy.getTenuredUsageThreshold());
        this.tenuredRateThreshold = parseValue(TENURED_RATE_EXPIRE_AT, strategy.getGcRateThreshold());
        this.permgenUsageThreshold = parseValue(PERMGEN_USAGE_EXPIRE_AT, strategy.getPermGenUsageThreshold());
        this.thrashingThreshold = parseValue(THRASHING_EXPIRE_AT, strategy.getThrashingThreshold());
    }

    public boolean isTenuredSpaceExhausted() {
        GarbageCollectionStats gcStats = stats.getGcMonitor().getTenuredStats();

        return exceedsThreshold(TENURED, gcStats, new Spec<GarbageCollectionStats>() {
            @Override
            public boolean isSatisfiedBy(GarbageCollectionStats gcStats) {
                return tenuredUsageThreshold != 0
                    && tenuredRateThreshold != 0
                    && gcStats.getEventCount() >= 5
                    && gcStats.getUsage() >= tenuredUsageThreshold
                    && gcStats.getRate() >= tenuredRateThreshold;
            }
        });
    }

    public boolean isPermGenSpaceExhausted() {
        GarbageCollectionStats gcStats = stats.getGcMonitor().getPermGenStats();

        return exceedsThreshold(PERMGEN, gcStats, new Spec<GarbageCollectionStats>() {
            @Override
            public boolean isSatisfiedBy(GarbageCollectionStats gcStats) {
                return permgenUsageThreshold != 0
                    && gcStats.getEventCount() >= 5
                    && gcStats.getUsage() >= permgenUsageThreshold;
            }
        });
    }

    public boolean isThrashing() {
        GarbageCollectionStats gcStats = stats.getGcMonitor().getTenuredStats();

        return exceedsThreshold(TENURED, gcStats, new Spec<GarbageCollectionStats>() {
            @Override
            public boolean isSatisfiedBy(GarbageCollectionStats gcStats) {
                return tenuredUsageThreshold != 0
                    && thrashingThreshold != 0
                    && gcStats.getEventCount() >= 5
                    && gcStats.getUsage() >= tenuredUsageThreshold
                    && gcStats.getRate() >= thrashingThreshold;
            }
        });
    }

    private boolean exceedsThreshold(String pool, GarbageCollectionStats gcStats, Spec<GarbageCollectionStats> spec) {
        if (isEnabled()
            && strategy != GarbageCollectorMonitoringStrategy.UNKNOWN
            && spec.isSatisfiedBy(gcStats)) {

            if (gcStats.getUsage() > 0) {
                LOGGER.debug(String.format("GC rate: %.2f/s %s usage: %s%%", gcStats.getRate(), pool, gcStats.getUsage()));
            } else {
                LOGGER.debug("GC rate: 0.0/s");
            }

            return true;
        }

        return false;
    }

    private boolean isEnabled() {
        String enabledValue = System.getProperty(ENABLE_PERFORMANCE_MONITORING, "true");
        return Boolean.parseBoolean(enabledValue);
    }

    private static int parseValue(String property, int defaultValue) {
        String expireAt = System.getProperty(property);

        if (expireAt == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(expireAt);
        } catch (Exception e) {
            throw new GradleException(format(
                "System property '%s' has incorrect value: '%s'. The value needs to be an integer.",
                property, expireAt));
        }
    }

    private static double parseValue(String property, double defaultValue) {
        String expireAt = System.getProperty(property);

        if (expireAt == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(expireAt);
        } catch (Exception e) {
            throw new GradleException(format(
                "System property '%s' has incorrect value: '%s'. The value needs to be a double.",
                property, expireAt));
        }
    }
}
