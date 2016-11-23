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

package org.gradle.launcher.daemon.server.health.memory;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.event.ListenerManager;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MemoryStatusBroadcaster {
    private static final Logger LOGGER = Logging.getLogger(MemoryStatusBroadcaster.class);

    private static final int STATUS_INTERVAL = 5;

    private final ScheduledExecutorService scheduledExecutorService;
    private final ListenerBroadcast<MemoryStatusListener> broadcast;
    private final MemoryInfo memoryInfo = new MemoryInfo();

    public MemoryStatusBroadcaster(ScheduledExecutorService scheduledExecutorService, ListenerManager listenerManager) {
        this.scheduledExecutorService = scheduledExecutorService;
        this.broadcast = listenerManager.createAnonymousBroadcaster(MemoryStatusListener.class);
    }

    public void start() {
        try {
            memoryInfo.getFreePhysicalMemory();
            scheduledExecutorService.scheduleAtFixedRate(getMemoryCheck(), 1, STATUS_INTERVAL, TimeUnit.SECONDS);
            LOGGER.debug("Memory status broadcaster started");
        } catch (UnsupportedOperationException e) {
            LOGGER.info("This JVM does not support getting free system memory, so no memory status updates will be broadcast");
        }
    }

    private Runnable getMemoryCheck() {
        return new Runnable() {
            @Override
            public void run() {
                LOGGER.debug("Sending memory status update");
                broadcast.getSource().onMemoryStatusNotification(memoryInfo.getSnapshot());
            }
        };
    }
}
