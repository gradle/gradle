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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.concurrent.StoppableScheduledExecutor;
import org.gradle.internal.event.ListenerManager;

import java.util.concurrent.TimeUnit;

public class DefaultMemoryManager implements MemoryManager, Stoppable {
    private static final Logger LOGGER = Logging.getLogger(MemoryManager.class);
    public static final int STATUS_INTERVAL_SECONDS = 5;

    private final MemoryInfo memoryInfo;
    private final ListenerManager listenerManager;
    private final StoppableScheduledExecutor scheduler;
    private final JvmMemoryStatusListener jvmBroadcast;
    private final OsMemoryStatusListener osBroadcast;
    private final boolean osMemoryStatusSupported;

    public DefaultMemoryManager(MemoryInfo memoryInfo, ListenerManager listenerManager, ExecutorFactory executorFactory) {
        this.memoryInfo = memoryInfo;
        this.listenerManager = listenerManager;
        this.scheduler = executorFactory.createScheduled("Memory manager", STATUS_INTERVAL_SECONDS * 2, TimeUnit.SECONDS);
        this.jvmBroadcast = listenerManager.getBroadcaster(JvmMemoryStatusListener.class);
        this.osBroadcast = listenerManager.getBroadcaster(OsMemoryStatusListener.class);
        this.osMemoryStatusSupported = supportsOsMemoryStatus();
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
        if (!osMemoryStatusSupported) {
            LOGGER.info("This JVM does not support getting OS memory, so no OS memory status updates will be broadcast");
        }
    }

    @Override
    public void stop() {
        scheduler.stop();
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
                LOGGER.warn("Failed to collect memory status: {}", ex.getMessage(), ex);
            }
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
