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
import org.gradle.process.internal.health.memory.MemoryReclaim;
import org.gradle.process.internal.health.memory.OsMemoryStatus;
import org.gradle.process.internal.health.memory.OsMemoryStatusListener;

import static org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus.GRACEFUL_EXPIRE;

/**
 * An expiry strategy which only triggers when system memory falls below a threshold.
 */
public class LowMemoryDaemonExpirationStrategy implements DaemonExpirationStrategy, OsMemoryStatusListener {
    private volatile OsMemoryStatus memoryStatus;
    private final double minFreeMemoryPercentage;
    private static final Logger LOGGER = Logging.getLogger(LowMemoryDaemonExpirationStrategy.class);

    // Reasonable default threshold bounds: between 384M and 1G
    public static final long MIN_THRESHOLD_BYTES = 384 * 1024 * 1024;
    public static final long MAX_THRESHOLD_BYTES = 1024 * 1024 * 1024;

    public LowMemoryDaemonExpirationStrategy(double minFreeMemoryPercentage) {
        Preconditions.checkArgument(minFreeMemoryPercentage >= 0, "Free memory percentage must be >= 0");
        Preconditions.checkArgument(minFreeMemoryPercentage <= 1, "Free memory percentage must be <= 1");
        this.minFreeMemoryPercentage = minFreeMemoryPercentage;
    }

    @Override
    public DaemonExpirationResult checkExpiration() {
        OsMemoryStatus localStatus = this.memoryStatus;
        if (localStatus != null) {
            MemoryReclaim reclaim = localStatus.computeMemoryReclaimAmount(this::computeRequiredFreeMemory);
            if (reclaim instanceof MemoryReclaim.Some) {
                MemoryReclaim.Some some = (MemoryReclaim.Some) reclaim;
                LOGGER.info(
                    "after free system {} memory ({}) fell below threshold of {}",
                    some.getType(), NumberUtil.formatBytes(some.getCurrentFree()), NumberUtil.formatBytes(some.getRequiredFree())
                );
                return new DaemonExpirationResult(GRACEFUL_EXPIRE, "to reclaim system " + some.getType() + " memory");
            } else {
                reclaim = localStatus.computeMemoryReclaimAmount(totalMemory ->
                    2 * computeRequiredFreeMemory(totalMemory)
                );
                if (reclaim instanceof MemoryReclaim.Some) {
                    MemoryReclaim.Some some = (MemoryReclaim.Some) reclaim;
                    LOGGER.debug("Nearing low {} memory threshold, free = {}", some.getType(), NumberUtil.formatBytes(some.getCurrentFree()));
                }
            }
        }
        return DaemonExpirationResult.NOT_TRIGGERED;

    }

    private long computeRequiredFreeMemory(Long totalMemory) {
        return Math.min(MAX_THRESHOLD_BYTES, Math.max(MIN_THRESHOLD_BYTES, (long) (totalMemory * minFreeMemoryPercentage)));
    }

    @Override
    public void onOsMemoryStatus(OsMemoryStatus newStatus) {
        this.memoryStatus = newStatus;
    }
}
