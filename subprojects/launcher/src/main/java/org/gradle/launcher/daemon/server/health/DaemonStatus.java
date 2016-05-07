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
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectionStats;
import org.gradle.launcher.daemon.server.health.gc.GarbageCollectorMonitoringStrategy;

import static java.lang.String.format;

public class DaemonStatus {
    public static final String ENABLE_PERFORMANCE_MONITORING = "org.gradle.daemon.performance.enable-monitoring";
    public static final String TENURED_USAGE_EXPIRE_AT = "org.gradle.daemon.performance.tenured-usage-expire-at";
    public static final String TENURED_RATE_EXPIRE_AT = "org.gradle.daemon.performance.tenured-rate-expire-at";

    private final DaemonStats stats;

    public DaemonStatus(DaemonStats stats) {
        this.stats = stats;
    }

    boolean isDaemonTired() {
        String enabledValue = System.getProperty(ENABLE_PERFORMANCE_MONITORING, "true");
        Boolean enabled = Boolean.parseBoolean(enabledValue);
        return enabled && isTenuredSpaceExhausted();
    }

    public boolean isTenuredSpaceExhausted() {
        GarbageCollectorMonitoringStrategy strategy = stats.getGcMonitor().getGcStrategy();
        if (strategy != GarbageCollectorMonitoringStrategy.UNKNOWN) {
            int tenuredUsageThreshold = parseValue(TENURED_USAGE_EXPIRE_AT, strategy.getTenuredUsageThreshold());
            if (tenuredUsageThreshold == 0) {
                return false;
            }
            double tenuredRateThreshold = parseValue(TENURED_RATE_EXPIRE_AT, strategy.getGcRateThreshold());
            if (tenuredRateThreshold == 0) {
                return false;
            }
            GarbageCollectionStats gcStats = stats.getGcMonitor().getTenuredStats();
            if (gcStats.getEventCount() >= 5
                && gcStats.getUsage() >= tenuredUsageThreshold
                && gcStats.getRate() >= tenuredRateThreshold) {
                return true;
            }
        }
        return false;
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
