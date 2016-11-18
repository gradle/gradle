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
package org.gradle.process.internal.daemon;

import com.google.common.base.Preconditions;
import org.gradle.api.Transformer;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.process.internal.health.memory.MaximumHeapHelper;
import org.gradle.process.internal.health.memory.MemoryInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WorkerDaemonExpiration {
    private static final Logger LOGGER = Logging.getLogger(WorkerDaemonExpiration.class);

    // Reasonable minimal threshold 384M
    private static final long MIN_THRESHOLD_BYTES = 384 * 1024 * 1024;

    private final WorkerDaemonClientsManager clientsManager;
    private final MemoryInfo memoryInfo;
    private final MaximumHeapHelper maximumHeapHelper;
    private final long memoryThresholdInBytes;

    public WorkerDaemonExpiration(WorkerDaemonClientsManager clientsManager, MemoryInfo memoryInfo) {
        this(clientsManager, memoryInfo, 0.05);
    }

    public WorkerDaemonExpiration(WorkerDaemonClientsManager clientsManager, MemoryInfo memoryInfo, double minFreeMemoryPercentage) {
        Preconditions.checkArgument(minFreeMemoryPercentage >= 0, "Free memory percentage must be >= 0");
        Preconditions.checkArgument(minFreeMemoryPercentage <= 1, "Free memory percentage must be <= 1");
        this.clientsManager = Preconditions.checkNotNull(clientsManager);
        this.memoryInfo = Preconditions.checkNotNull(memoryInfo);
        this.maximumHeapHelper = new MaximumHeapHelper(memoryInfo);
        this.memoryThresholdInBytes = Math.max(MIN_THRESHOLD_BYTES, (long) (memoryInfo.getTotalPhysicalMemory() * minFreeMemoryPercentage));
    }

    /**
     * Eventually expire daemons.
     *
     * Call this before spawning new processes in order to keep resource usage under a sensible threshold.
     */
    public void eventuallyExpireDaemons() {
        eventuallyExpireDaemons(null);
    }

    /**
     * Eventually expire daemons.
     *
     * Call this before spawning new processes in order to keep resource usage under a sensible threshold.
     *
     * @param requestedFreeMemory String notation of requested free memory, e.g. {@literal "512M"} or {@literal "4g"}
     */
    public void eventuallyExpireDaemons(String requestedFreeMemory) {
        long requestedFreeMemoryBytes = maximumHeapHelper.getMaximumHeapSize(requestedFreeMemory);
        clientsManager.selectIdleClientsToStop(new SimpleMemoryExpirationSelector(requestedFreeMemoryBytes));
    }

    /**
     * Simple implementation of memory based expiration.
     *
     * Use the maximum heap size of each daemon, not their actual memory usage.
     * Expire as much daemons as needed to free the requested memory under the threshold.
     */
    private class SimpleMemoryExpirationSelector implements Transformer<List<WorkerDaemonClient>, List<WorkerDaemonClient>> {

        private final long requestedFreeMemoryBytes;

        public SimpleMemoryExpirationSelector(long requestedFreeMemoryBytes) {
            this.requestedFreeMemoryBytes = requestedFreeMemoryBytes;
        }

        @Override
        public List<WorkerDaemonClient> transform(List<WorkerDaemonClient> idleClients) {
            long anticipatedFreeMemory = memoryInfo.getFreePhysicalMemory() - requestedFreeMemoryBytes;
            if (anticipatedFreeMemory < memoryThresholdInBytes) {
                List<WorkerDaemonClient> toExpire = new ArrayList<WorkerDaemonClient>();
                for (WorkerDaemonClient idleClient : idleClients) {
                    LOGGER.info("Expire {} to free some system memory", idleClient);
                    toExpire.add(idleClient);
                    anticipatedFreeMemory += maximumHeapHelper.getMaximumHeapSize(idleClient.getForkOptions().getMaxHeapSize());
                    if (anticipatedFreeMemory >= memoryThresholdInBytes) {
                        break;
                    }
                }
                return toExpire;
            }
            return Collections.emptyList();
        }
    }
}
