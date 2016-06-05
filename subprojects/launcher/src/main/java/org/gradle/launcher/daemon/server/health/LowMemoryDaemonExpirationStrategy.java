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
import com.google.common.base.Preconditions;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.util.NumberUtil;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy;

import static org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus.GRACEFUL_EXPIRE;

/** An expiry strategy which only triggers when system memory falls below a threshold. */
public class LowMemoryDaemonExpirationStrategy implements DaemonExpirationStrategy {
    private final MemoryInfo memoryInfo;
    protected final long minFreeMemoryBytes;
    private static final Logger LOG = Logging.getLogger(LowMemoryDaemonExpirationStrategy.class);

    public static final String EXPIRATION_REASON = "to reclaim system memory";

    private LowMemoryDaemonExpirationStrategy(MemoryInfo memoryInfo, long minFreeMemoryBytes) {
        Preconditions.checkArgument(minFreeMemoryBytes >= 0);
        this.memoryInfo = Preconditions.checkNotNull(memoryInfo);
        this.minFreeMemoryBytes = minFreeMemoryBytes;
    }

    /**
     * Creates an expiration strategy which expires the daemon when free memory drops below the specific byte count.
     *
     * @param minFreeMemoryBytes when free memory drops below this positive value in bytes, the daemon will expire.
     */
    public static LowMemoryDaemonExpirationStrategy belowFreeBytes(long minFreeMemoryBytes) {
        return new LowMemoryDaemonExpirationStrategy(new MemoryInfo(), minFreeMemoryBytes);
    }

    /**
     * Creates an expiration strategy which expires the daemon when free memory drops below the specific % of total.
     *
     * @param minFreeMemoryPercentage when free memory drops below this percentage between 0 and 1, the daemon will expire.
     */
    public static LowMemoryDaemonExpirationStrategy belowFreePercentage(double minFreeMemoryPercentage) {
        return belowFreePercentage(minFreeMemoryPercentage, new MemoryInfo());
    }

    @VisibleForTesting
    static LowMemoryDaemonExpirationStrategy belowFreePercentage(double minFreeMemoryPercentage, MemoryInfo memInfo) {
        Preconditions.checkArgument(minFreeMemoryPercentage >= 0, "Free memory percentage must be >= 0");
        Preconditions.checkArgument(minFreeMemoryPercentage <= 1, "Free memory percentage must be <= 1");

        return new LowMemoryDaemonExpirationStrategy(
            memInfo,
            (long) (memInfo.getTotalPhysicalMemory() * minFreeMemoryPercentage)
        );
    }

    public DaemonExpirationResult checkExpiration() {
        long freeMem = memoryInfo.getFreePhysicalMemory();
        if (freeMem < minFreeMemoryBytes) {
            LOG.info("after free system memory (" + NumberUtil.formatBytes(freeMem) + ") fell below threshold of " + NumberUtil.formatBytes(minFreeMemoryBytes));
            return new DaemonExpirationResult(GRACEFUL_EXPIRE, EXPIRATION_REASON);
        } else {
            return DaemonExpirationResult.NOT_TRIGGERED;
        }
    }
}
