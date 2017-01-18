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

package org.gradle.process.internal.health.memory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.concurrent.StoppableScheduledExecutor;
import org.gradle.internal.event.ListenerManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DefaultMemoryManager implements MemoryManager, Stoppable {
    private static final Logger LOGGER = Logging.getLogger(MemoryManager.class);
    public static final int STATUS_INTERVAL_SECONDS = 5;
    private static final long MIN_THRESHOLD_BYTES = 384 * 1024 * 1024; // 384M

    private final double minFreeMemoryPercentage;
    private final MemoryInfo memoryInfo;
    private final ListenerManager listenerManager;
    private final StoppableScheduledExecutor scheduler;
    private final JvmMemoryStatusListener jvmBroadcast;
    private final OsMemoryStatusListener osBroadcast;
    private final boolean osMemoryStatusSupported;
    private final Object holdersLock = new Object();
    private final Object memoryLock = new Object();
    private final List<MemoryHolder> holders = new ArrayList<MemoryHolder>();
    private OsMemoryStatus currentOsMemoryStatus;
    private final OsMemoryStatusListener osMemoryStatusListener;

    public DefaultMemoryManager(MemoryInfo memoryInfo, ListenerManager listenerManager, ExecutorFactory executorFactory, double minFreeMemoryPercentage) {
        this(memoryInfo, listenerManager, executorFactory, minFreeMemoryPercentage, true);
    }

    @VisibleForTesting
    DefaultMemoryManager(MemoryInfo memoryInfo, ListenerManager listenerManager, ExecutorFactory executorFactory, double minFreeMemoryPercentage, boolean autoFree) {
        Preconditions.checkArgument(minFreeMemoryPercentage >= 0, "Free memory percentage must be >= 0");
        Preconditions.checkArgument(minFreeMemoryPercentage <= 1, "Free memory percentage must be <= 1");
        this.minFreeMemoryPercentage = minFreeMemoryPercentage;
        this.memoryInfo = memoryInfo;
        this.listenerManager = listenerManager;
        this.scheduler = executorFactory.createScheduled("Memory manager", 1);
        this.jvmBroadcast = listenerManager.getBroadcaster(JvmMemoryStatusListener.class);
        this.osBroadcast = listenerManager.getBroadcaster(OsMemoryStatusListener.class);
        this.osMemoryStatusSupported = supportsOsMemoryStatus();
        this.osMemoryStatusListener = new OsMemoryListener(autoFree);
        start();
    }

    private boolean supportsOsMemoryStatus() {
        try {
            memoryInfo.getOsSnapshot();
            return true;
        } catch (UnsupportedOperationException ex) {
            return false;
        }
    }

    private void start() {
        scheduler.scheduleAtFixedRate(new MemoryCheck(), 0, STATUS_INTERVAL_SECONDS, TimeUnit.SECONDS);
        LOGGER.debug("Memory status broadcaster started");
        if (osMemoryStatusSupported) {
            addListener(osMemoryStatusListener);
        } else {
            LOGGER.info("This JVM does not support getting OS memory, so no OS memory status updates will be broadcast");
        }
    }

    @Override
    public void stop() {
        scheduler.stop();
        listenerManager.removeListener(osMemoryStatusListener);
    }

    @Override
    public void requestFreeMemory(long memoryAmountBytes) {
        synchronized (memoryLock) {
            if (currentOsMemoryStatus != null) {
                long totalPhysicalMemory = currentOsMemoryStatus.getTotalPhysicalMemory();
                long requestedFreeMemory = getMemoryThresholdInBytes(totalPhysicalMemory) + (memoryAmountBytes > 0 ? memoryAmountBytes : 0);
                long freeMemory = currentOsMemoryStatus.getFreePhysicalMemory();
                long newFreeMemory = doRequestFreeMemory(requestedFreeMemory, freeMemory);
                // If we've freed memory, invalidate the current OS memory snapshot
                if (newFreeMemory > freeMemory) {
                    currentOsMemoryStatus = null;
                }
            } else {
                LOGGER.debug("There is no current snapshot of OS memory available - memory cannot be freed until a new memory status update occurs");
            }
        }
    }

    private long doRequestFreeMemory(long requestedFreeMemory, long freeMemory) {
        long toReleaseMemory = requestedFreeMemory;
        if (freeMemory < requestedFreeMemory) {
            LOGGER.debug("{} memory requested, {} free", requestedFreeMemory, freeMemory);
            synchronized (holdersLock) {
                for (MemoryHolder holder : holders) {
                    long released = holder.attemptToRelease(toReleaseMemory);
                    toReleaseMemory -= released;
                    freeMemory += released;
                    if (freeMemory >= requestedFreeMemory) {
                        break;
                    }
                }
            }
            LOGGER.debug("{} memory requested, {} released, {} free", requestedFreeMemory, requestedFreeMemory - toReleaseMemory, freeMemory);
        }
        return freeMemory;
    }

    private long getMemoryThresholdInBytes(long totalPhysicalMemory) {
        return Math.max(MIN_THRESHOLD_BYTES, (long) (totalPhysicalMemory * minFreeMemoryPercentage));
    }

    private class MemoryCheck implements Runnable {
        @Override
        public void run() {
            try {
                if (osMemoryStatusSupported) {
                    OsMemoryStatus os = memoryInfo.getOsSnapshot();
                    LOGGER.debug("Emitting OS memory status event {}", os);
                    osBroadcast.onOsMemoryStatus(os);
                }
                JvmMemoryStatus jvm = memoryInfo.getJvmSnapshot();
                LOGGER.debug("Emitting JVM memory status event {}", jvm);
                jvmBroadcast.onJvmMemoryStatus(jvm);
            } catch (Exception ex) {
                LOGGER.debug("Failed to collect memory status: {}", ex.getMessage(), ex);
            }
        }
    }

    private class OsMemoryListener implements OsMemoryStatusListener {
        private final boolean autoFree;

        private OsMemoryListener(boolean autoFree) {
            this.autoFree = autoFree;
        }

        @Override
        public void onOsMemoryStatus(OsMemoryStatus os) {
            currentOsMemoryStatus = os;
            if (autoFree) {
                requestFreeMemory(0);
            }
        }
    }

    @Override
    public void addMemoryHolder(MemoryHolder holder) {
        holders.add(holder);
    }

    @Override
    public void removeMemoryHolder(MemoryHolder holder) {
        holders.remove(holder);
    }

    @Override
    public void addListener(JvmMemoryStatusListener listener) {
        listenerManager.addListener(listener);
    }

    @Override
    public void addListener(OsMemoryStatusListener listener) {
        listenerManager.addListener(listener);
    }

    @Override
    public void removeListener(JvmMemoryStatusListener listener) {
        listenerManager.removeListener(listener);
    }

    @Override
    public void removeListener(OsMemoryStatusListener listener) {
        listenerManager.removeListener(listener);
    }
}
