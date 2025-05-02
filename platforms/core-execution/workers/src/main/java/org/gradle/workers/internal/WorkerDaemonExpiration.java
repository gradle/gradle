/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.workers.internal;

import org.gradle.api.Transformer;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.process.internal.health.memory.MaximumHeapHelper;
import org.gradle.process.internal.health.memory.MemoryAmount;
import org.gradle.process.internal.health.memory.MemoryHolder;

import java.util.ArrayList;
import java.util.List;

public class WorkerDaemonExpiration implements MemoryHolder {

    private static final Logger LOGGER = Logging.getLogger(WorkerDaemonExpiration.class);

    private final WorkerDaemonClientsManager clientsManager;
    private final long osTotalMemory;

    public WorkerDaemonExpiration(WorkerDaemonClientsManager clientsManager, long osTotalMemory) {
        this.clientsManager = clientsManager;
        this.osTotalMemory = osTotalMemory;
    }

    @Override
    public long attemptToRelease(long memoryAmountBytes) throws IllegalArgumentException {
        if (memoryAmountBytes < 0) {
            throw new IllegalArgumentException("Negative memory amount");
        }
        LOGGER.debug("Will attempt to release {} of memory", memoryAmountBytes / 1024 / 1024);
        SimpleMemoryExpirationSelector selector = new SimpleMemoryExpirationSelector(memoryAmountBytes);
        clientsManager.selectIdleClientsToStop(selector);
        return selector.getReleasedBytes();
    }

    /**
     * Simple implementation of memory based expiration.
     *
     * Use the maximum heap size of each daemon, not their actual memory usage.
     * Expire as much daemons as needed to free the requested memory under the threshold.
     */
    private class SimpleMemoryExpirationSelector implements Transformer<List<WorkerDaemonClient>, List<WorkerDaemonClient>> {

        private final long memoryBytesToRelease;
        private long releasedBytes;

        public SimpleMemoryExpirationSelector(long memoryBytesToRelease) {
            this.memoryBytesToRelease = memoryBytesToRelease;
        }

        public long getReleasedBytes() {
            return releasedBytes;
        }

        @Override
        public List<WorkerDaemonClient> transform(List<WorkerDaemonClient> idleClients) {
            int notExpirable = 0;
            List<WorkerDaemonClient> toExpire = new ArrayList<>();
            for (WorkerDaemonClient idleClient : idleClients) {
                if (idleClient.isNotExpirable()) {
                    notExpirable++;
                    continue;
                }
                toExpire.add(idleClient);
                long freed = getMemoryUsage(idleClient);
                releasedBytes += freed;
                if (releasedBytes >= memoryBytesToRelease) {
                    break;
                }
            }
            if (LOGGER.isDebugEnabled() && !toExpire.isEmpty()) {
                // TODO Only log expired workers count, log their "identity" once they are nameable/describable
                LOGGER.debug("Worker Daemon(s) expired to free some system memory {}", toExpire.size());
            }
            if (notExpirable > 0) {
                LOGGER.debug("{} Worker Daemon(s) had expiration disabled and were skipped", notExpirable);
            }
            return toExpire;
        }

        private long getMemoryUsage(WorkerDaemonClient idleClient) {
            // prefer to use the actual memory usage reported by the worker
            try {
                return idleClient.getJvmMemoryStatus().getCommittedMemory();
            } catch (UnsupportedOperationException e) {
                // This means the client does not support reporting jvm memory info
            } catch (IllegalStateException e) {
                // This means the client has not reported memory usage yet
            }

            // if the worker has not reported memory usage yet for some reason, or does not support it,
            // use the max heap as an approximation
            String forkOptionsMaxHeapSize = idleClient.getForkOptions().getJvmOptions().getMaxHeapSize();
            long parsed = MemoryAmount.parseNotation(forkOptionsMaxHeapSize);
            if (parsed != -1) {
                // From fork options
                return parsed;
            }

            // If we don't know what the max heap is, approximate it based on OS total memory
            // according to JVM documentation
            if (osTotalMemory != -1) {
                return new MaximumHeapHelper().getDefaultMaximumHeapSize(osTotalMemory);
            }

            // If we get here, we have no idea how much memory the worker is using
            return 0;
        }
    }
}
