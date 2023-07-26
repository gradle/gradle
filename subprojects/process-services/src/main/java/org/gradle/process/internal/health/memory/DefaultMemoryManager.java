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
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.concurrent.ManagedScheduledExecutor;
import org.gradle.internal.event.ListenerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class DefaultMemoryManager implements MemoryManager, Stoppable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultMemoryManager.class);
    public static final int STATUS_INTERVAL_SECONDS = 5;
    private static final double DEFAULT_MIN_FREE_MEMORY_PERCENTAGE = 0.1D; // 10%
    private static final long MIN_THRESHOLD_BYTES = 384 * 1024 * 1024; // 384M

    private final double minFreeMemoryPercentage;
    private final OsMemoryInfo osMemoryInfo;
    private final JvmMemoryInfo jvmMemoryInfo;
    private final ListenerManager listenerManager;
    private final ManagedScheduledExecutor scheduler;
    private final JvmMemoryStatusListener jvmBroadcast;
    private final OsMemoryStatusListener osBroadcast;
    private final boolean osMemoryStatusSupported;
    private final Object holdersLock = new Object();
    private final Object memoryLock = new Object();
    private final List<MemoryHolder> holders = new ArrayList<MemoryHolder>();
    private OsMemoryStatus currentOsMemoryStatus;
    private final OsMemoryStatusListener osMemoryStatusListener;

    public DefaultMemoryManager(OsMemoryInfo osMemoryInfo, JvmMemoryInfo jvmMemoryInfo, ListenerManager listenerManager, ExecutorFactory executorFactory) {
        this(osMemoryInfo, jvmMemoryInfo, listenerManager, executorFactory, DEFAULT_MIN_FREE_MEMORY_PERCENTAGE, true);
    }

    @VisibleForTesting
    DefaultMemoryManager(OsMemoryInfo osMemoryInfo, JvmMemoryInfo jvmMemoryInfo, ListenerManager listenerManager, ExecutorFactory executorFactory, double minFreeMemoryPercentage, boolean autoFree) {
        Preconditions.checkArgument(minFreeMemoryPercentage >= 0, "Free memory percentage must be >= 0");
        Preconditions.checkArgument(minFreeMemoryPercentage <= 1, "Free memory percentage must be <= 1");
        this.minFreeMemoryPercentage = minFreeMemoryPercentage;
        this.osMemoryInfo = osMemoryInfo;
        this.jvmMemoryInfo = jvmMemoryInfo;
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
            osMemoryInfo.getOsSnapshot();
            return true;
        } catch (UnsupportedOperationException ex) {
            return false;
        }
    }

    private void start() {
        scheduler.scheduleAtFixedRate(new MemoryCheck(), STATUS_INTERVAL_SECONDS, STATUS_INTERVAL_SECONDS, TimeUnit.SECONDS);
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
                boolean freedPhysical = freeSpecificMemory(currentOsMemoryStatus.getPhysicalMemory(), memoryAmountBytes);
                boolean freedVirtual = freeSpecificMemory(currentOsMemoryStatus.getVirtualMemory(), memoryAmountBytes);
                // If we've freed memory, invalidate the current OS memory snapshot
                if (freedPhysical || freedVirtual) {
                    currentOsMemoryStatus = null;
                }
            } else {
                LOGGER.debug("There is no current snapshot of OS memory available - memory cannot be freed until a new memory status update occurs");
            }
        }
    }

    private boolean freeSpecificMemory(OsMemoryCategory status, long memoryAmountBytes) {
        if (status instanceof OsMemoryCategory.Unknown) {
            // no need to free
            return false;
        }
        long totalMemory = ((OsMemoryCategory.Limited) status).getTotal();
        long freeMemory = ((OsMemoryCategory.Limited) status).getFree();
        long requestedFreeMemory = getMemoryThresholdInBytes(totalMemory) + (memoryAmountBytes > 0 ? memoryAmountBytes : 0);
        long newFreeMemory = doRequestFreeMemory(status.getName(), requestedFreeMemory, freeMemory);
        return newFreeMemory > freeMemory;
    }

    private long doRequestFreeMemory(String name, long requestedFreeMemory, long freeMemory) {
        long toReleaseMemory = requestedFreeMemory;
        if (freeMemory < requestedFreeMemory) {
            LOGGER.debug("{} {} memory requested, {} free", requestedFreeMemory, name, freeMemory);
            List<MemoryHolder> memoryHolders;
            synchronized (holdersLock) {
                memoryHolders = new ArrayList<MemoryHolder>(holders);
            }
            for (MemoryHolder holder : memoryHolders) {
                long released = holder.attemptToRelease(toReleaseMemory);
                toReleaseMemory -= released;
                freeMemory += released;
                if (freeMemory >= requestedFreeMemory) {
                    break;
                }
            }

            LOGGER.debug("{} {} memory requested, {} released, {} free", requestedFreeMemory, name, requestedFreeMemory - toReleaseMemory, freeMemory);
        }
        return freeMemory;
    }

    private long getMemoryThresholdInBytes(long totalMemory) {
        return Math.max(MIN_THRESHOLD_BYTES, (long) (totalMemory * minFreeMemoryPercentage));
    }

    private class MemoryCheck implements Runnable {
        @Override
        public void run() {
            try {
                if (osMemoryStatusSupported) {
                    OsMemoryStatus os = osMemoryInfo.getOsSnapshot();
                    osBroadcast.onOsMemoryStatus(os);
                }
                JvmMemoryStatus jvm = jvmMemoryInfo.getJvmSnapshot();
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
        synchronized (holdersLock) {
            holders.add(holder);
        }
    }

    @Override
    public void removeMemoryHolder(MemoryHolder holder) {
        synchronized (holdersLock) {
            holders.remove(holder);
        }
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
