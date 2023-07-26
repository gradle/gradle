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

import com.google.common.base.Preconditions;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.util.NumberUtil;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy;
import org.gradle.process.internal.health.memory.OsMemoryCategory;
import org.gradle.process.internal.health.memory.OsMemoryStatus;
import org.gradle.process.internal.health.memory.OsMemoryStatusListener;

import javax.annotation.Nullable;
import java.util.concurrent.locks.ReentrantLock;

import static org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus.GRACEFUL_EXPIRE;

/**
 * An expiry strategy which only triggers when system memory falls below a threshold.
 */
public class LowMemoryDaemonExpirationStrategy implements DaemonExpirationStrategy, OsMemoryStatusListener {
    private ReentrantLock lock = new ReentrantLock();
    private OsMemoryStatus memoryStatus;
    private final double minFreeMemoryPercentage;
    private long physicalMemoryThresholdInBytes;
    private long virtualMemoryThresholdInBytes;
    private static final Logger LOGGER = Logging.getLogger(LowMemoryDaemonExpirationStrategy.class);

    // Reasonable default threshold bounds: between 384M and 1G
    public static final long MIN_THRESHOLD_BYTES = 384 * 1024 * 1024;
    public static final long MAX_THRESHOLD_BYTES = 1024 * 1024 * 1024;

    public LowMemoryDaemonExpirationStrategy(double minFreeMemoryPercentage) {
        Preconditions.checkArgument(minFreeMemoryPercentage >= 0, "Free memory percentage must be >= 0");
        Preconditions.checkArgument(minFreeMemoryPercentage <= 1, "Free memory percentage must be <= 1");
        this.minFreeMemoryPercentage = minFreeMemoryPercentage;
    }

    private long normalizeThreshold(final long thresholdIn, final long minValue, final long maxValue) {
        return Math.min(maxValue, Math.max(minValue, thresholdIn));
    }

    @Override
    public DaemonExpirationResult checkExpiration() {
        lock.lock();
        try {
            if (memoryStatus != null) {
                DaemonExpirationResult result = checkExpiry(memoryStatus.getPhysicalMemory(), physicalMemoryThresholdInBytes);
                if (result != null) {
                    return result;
                }
                OsMemoryCategory virtualMemory = memoryStatus.getVirtualMemory();
                if (virtualMemory instanceof OsMemoryCategory.Limited) {
                    result = checkExpiry((OsMemoryCategory.Limited) virtualMemory, virtualMemoryThresholdInBytes);
                    if (result != null) {
                        return result;
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        return DaemonExpirationResult.NOT_TRIGGERED;

    }

    @Nullable
    private DaemonExpirationResult checkExpiry(OsMemoryCategory.Limited memory, long memoryThresholdInBytes) {
        long freeMem = memory.getFree();
        if (freeMem < memoryThresholdInBytes) {
            LOGGER.info("after free system {} memory ({}) fell below threshold of {}", memory.getName(), NumberUtil.formatBytes(freeMem), NumberUtil.formatBytes(memoryThresholdInBytes));
            return new DaemonExpirationResult(
                GRACEFUL_EXPIRE,
                "to reclaim system " + memory.getName() + " memory"
            );
        } else if (freeMem < memoryThresholdInBytes * 2) {
            LOGGER.debug("Nearing low {} memory threshold - {}", memory.getName(), memoryStatus);
        }
        return null;
    }

    @Override
    public void onOsMemoryStatus(OsMemoryStatus newStatus) {
        lock.lock();
        try {
            this.memoryStatus = newStatus;
            this.physicalMemoryThresholdInBytes = normalizeThreshold((long) (memoryStatus.getPhysicalMemory().getTotal() * minFreeMemoryPercentage), MIN_THRESHOLD_BYTES, MAX_THRESHOLD_BYTES);
            OsMemoryCategory virtualMemory = memoryStatus.getVirtualMemory();
            if (virtualMemory instanceof OsMemoryCategory.Limited) {
                this.virtualMemoryThresholdInBytes = normalizeThreshold((long) (((OsMemoryCategory.Limited) virtualMemory).getTotal() * minFreeMemoryPercentage), MIN_THRESHOLD_BYTES, MAX_THRESHOLD_BYTES);
            }
        } finally {
            lock.unlock();
        }
    }
}
