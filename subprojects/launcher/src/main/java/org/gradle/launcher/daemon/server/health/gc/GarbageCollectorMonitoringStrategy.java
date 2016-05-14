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

public enum GarbageCollectorMonitoringStrategy {
    ORACLE_PARALLEL_CMS("PS Old Gen", "PS MarkSweep", 1.2, 80),
    ORACLE_6_CMS("CMS Old Gen", "ConcurrentMarkSweep", 1.2, 80),
    ORACLE_SERIAL("Tenured Gen", "MarkSweepCompact", 1.2, 80),
    ORACLE_G1("G1 Old Gen", "G1 Old Generation", 0.4, 75),
    IBM_ALL("Java heap", "MarkSweepCompact", 0.8, 70),
    UNKNOWN(null, null, -1, -1);

    private final String tenuredPoolName;
    private final String garbageCollectorName;
    private final double gcRateThreshold;
    private final int tenuredUsageThreshold;

    GarbageCollectorMonitoringStrategy(String tenuredPoolName, String garbageCollectorName, double gcRateThreshold, int tenuredUsageThreshold) {
        this.tenuredPoolName = tenuredPoolName;
        this.garbageCollectorName = garbageCollectorName;
        this.gcRateThreshold = gcRateThreshold;
        this.tenuredUsageThreshold = tenuredUsageThreshold;
    }

    public String getTenuredPoolName() {
        return tenuredPoolName;
    }

    public String getGarbageCollectorName() {
        return garbageCollectorName;
    }

    public double getGcRateThreshold() {
        return gcRateThreshold;
    }

    public int getTenuredUsageThreshold() {
        return tenuredUsageThreshold;
    }
}
