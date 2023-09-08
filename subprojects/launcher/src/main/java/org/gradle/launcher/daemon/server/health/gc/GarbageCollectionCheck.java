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

import org.gradle.internal.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.List;

public class GarbageCollectionCheck implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(GarbageCollectionCheck.class);

    private final Clock clock;
    private final GarbageCollectorMXBean garbageCollectorMXBean;

    private final String heapMemoryPool;
    private final SlidingWindow<GarbageCollectionEvent> heapEvents;

    private final String nonHeapMemoryPool;
    private final SlidingWindow<GarbageCollectionEvent> nonHeapEvents;

    public GarbageCollectionCheck(Clock clock, GarbageCollectorMXBean garbageCollectorMXBean, String heapMemoryPool, SlidingWindow<GarbageCollectionEvent> heapEvents, String nonHeapMemoryPool, SlidingWindow<GarbageCollectionEvent> nonHeapEvents) {
        this.clock = clock;
        this.garbageCollectorMXBean = garbageCollectorMXBean;
        this.heapMemoryPool = heapMemoryPool;
        this.heapEvents = heapEvents;
        this.nonHeapMemoryPool = nonHeapMemoryPool;
        this.nonHeapEvents = nonHeapEvents;
    }

    @Override
    public void run() {
        try {
            List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
            for (MemoryPoolMXBean memoryPoolMXBean : memoryPoolMXBeans) {
                String poolName = memoryPoolMXBean.getName();
                if (memoryPoolMXBean.getType() == MemoryType.HEAP && poolName.equals(heapMemoryPool)) {
                    GarbageCollectionEvent latest = heapEvents.latest();
                    long currentCount = garbageCollectorMXBean.getCollectionCount();
                    // There has been a GC event
                    if (latest == null || latest.getCount() != currentCount) {
                        heapEvents.slideAndInsert(new GarbageCollectionEvent(clock.getCurrentTime(), memoryPoolMXBean.getCollectionUsage(), currentCount));
                    }
                }
                if (memoryPoolMXBean.getType() == MemoryType.NON_HEAP && poolName.equals(nonHeapMemoryPool)) {
                    nonHeapEvents.slideAndInsert(new GarbageCollectionEvent(clock.getCurrentTime(), memoryPoolMXBean.getUsage(), -1));
                }
            }
        } catch (Throwable t) {
            // this class is used as task in a scheduled executor service, so it must not throw any throwable,
            // otherwise the further invocations of this task get automatically and silently cancelled
            LOGGER.debug("Exception while checking garbage collection", t);
        }
    }
}
