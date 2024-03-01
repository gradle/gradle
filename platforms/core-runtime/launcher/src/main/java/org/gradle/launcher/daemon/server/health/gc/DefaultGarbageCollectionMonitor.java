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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.specs.Spec;
import org.gradle.internal.time.Time;
import org.gradle.util.internal.CollectionUtils;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DefaultGarbageCollectionMonitor implements GarbageCollectionMonitor {
    public static final String DISABLE_POLLING_SYSTEM_PROPERTY = "org.gradle.daemon.gc.polling.disabled";

    private static final int POLL_INTERVAL_SECONDS = 1;
    private static final int POLL_DELAY_SECONDS = 1;
    private static final int EVENT_WINDOW = 20;

    private final SlidingWindow<GarbageCollectionEvent> heapEvents;
    private final SlidingWindow<GarbageCollectionEvent> nonHeapEvents;
    private final GarbageCollectorMonitoringStrategy gcStrategy;
    private final ScheduledExecutorService pollingExecutor;

    public DefaultGarbageCollectionMonitor(GarbageCollectorMonitoringStrategy gcStrategy, ScheduledExecutorService pollingExecutor) {
        this.pollingExecutor = pollingExecutor;
        this.gcStrategy = gcStrategy;
        this.heapEvents = new DefaultSlidingWindow<GarbageCollectionEvent>(EVENT_WINDOW);
        this.nonHeapEvents = new DefaultSlidingWindow<GarbageCollectionEvent>(EVENT_WINDOW);
        if (gcStrategy != GarbageCollectorMonitoringStrategy.UNKNOWN && !Boolean.getBoolean(DISABLE_POLLING_SYSTEM_PROPERTY)) {
            pollForValues();
        }
    }

    private void pollForValues() {
        GarbageCollectorMXBean garbageCollectorMXBean = CollectionUtils.findFirst(ManagementFactory.getGarbageCollectorMXBeans(), new Spec<GarbageCollectorMXBean>() {
            @Override
            public boolean isSatisfiedBy(GarbageCollectorMXBean element) {
                return element.getName().equals(gcStrategy.getGarbageCollectorName());
            }
        });
        pollingExecutor.scheduleAtFixedRate(new GarbageCollectionCheck(Time.clock(), garbageCollectorMXBean, gcStrategy.getHeapPoolName(), heapEvents, gcStrategy.getNonHeapPoolName(), nonHeapEvents), POLL_DELAY_SECONDS, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public GarbageCollectionStats getHeapStats() {
        return GarbageCollectionStats.forHeap(heapEvents.snapshot());
    }

    @Override
    public GarbageCollectionStats getNonHeapStats() {
        return GarbageCollectionStats.forNonHeap(nonHeapEvents.snapshot());
    }

    @VisibleForTesting
    public SlidingWindow<GarbageCollectionEvent> getHeapEvents() {
        return heapEvents;
    }

    @VisibleForTesting
    public SlidingWindow<GarbageCollectionEvent> getNonHeapEvents() {
        return nonHeapEvents;
    }
}
