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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.gradle.api.Transformer;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.specs.Spec;
import org.gradle.util.CollectionUtils;

import java.lang.management.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class GarbageCollectionMonitor {
    final public static int EVENT_WINDOW = 20;
    final static Logger LOGGER = Logging.getLogger(GarbageCollectionMonitor.class);
    final Map<String, SlidingWindow<GarbageCollectionEvent>> events;
    final GarbageCollectorMonitoringStrategy gcStrategy;
    final ScheduledExecutorService pollingExecutor;

    public GarbageCollectionMonitor(ScheduledExecutorService pollingExecutor) {
        this(determineGcStrategy(), pollingExecutor);
    }

    public GarbageCollectionMonitor(GarbageCollectorMonitoringStrategy gcStrategy, ScheduledExecutorService pollingExecutor) {
        this.pollingExecutor = pollingExecutor;
        this.gcStrategy = gcStrategy;

        if (gcStrategy != GarbageCollectorMonitoringStrategy.UNKNOWN) {
            events = ImmutableMap.<String, SlidingWindow<GarbageCollectionEvent>>of(
                gcStrategy.getTenuredPoolName(), new DefaultSlidingWindow<GarbageCollectionEvent>(EVENT_WINDOW),
                gcStrategy.getPermGenPoolName(), new DefaultSlidingWindow<GarbageCollectionEvent>(EVENT_WINDOW)
            );
            pollForValues(gcStrategy.getGarbageCollectorName(), ImmutableList.copyOf(events.keySet()));
        } else {
            events = ImmutableMap.<String, SlidingWindow<GarbageCollectionEvent>>builder().build();
        }
    }

    private static GarbageCollectorMonitoringStrategy determineGcStrategy() {
        JVMStrategy jvmStrategy = JVMStrategy.current();

        final List<String> garbageCollectors = CollectionUtils.collect(ManagementFactory.getGarbageCollectorMXBeans(), new Transformer<String, GarbageCollectorMXBean>() {
            @Override
            public String transform(GarbageCollectorMXBean garbageCollectorMXBean) {
                return garbageCollectorMXBean.getName();
            }
        });
        GarbageCollectorMonitoringStrategy gcStrategy = CollectionUtils.findFirst(jvmStrategy.getGcStrategies(), new Spec<GarbageCollectorMonitoringStrategy>() {
            @Override
            public boolean isSatisfiedBy(GarbageCollectorMonitoringStrategy strategy) {
                return garbageCollectors.contains(strategy.getGarbageCollectorName());
            }
        });

        if (gcStrategy == null) {
            LOGGER.info("Unable to determine a garbage collection monitoring strategy for " + jvmStrategy.toString());
            return GarbageCollectorMonitoringStrategy.UNKNOWN;
        } else {
            return gcStrategy;
        }
    }

    private void pollForValues(String garbageCollectorName, List<String> memoryPoolNames) {
        pollingExecutor.scheduleAtFixedRate(new GarbageCollectionCheck(events, memoryPoolNames, garbageCollectorName), 1, 1, TimeUnit.SECONDS);
    }

    public GarbageCollectionStats getTenuredStats() {
        return getGarbageCollectionStatsWithEmptyDefault(gcStrategy.getTenuredPoolName());
    }

    public GarbageCollectionStats getPermGenStats() {
        return getGarbageCollectionStatsWithEmptyDefault(gcStrategy.getPermGenPoolName());
    }

    private GarbageCollectionStats getGarbageCollectionStatsWithEmptyDefault(final String memoryPoolName) {
        SlidingWindow<GarbageCollectionEvent> slidingWindow;
        if ((memoryPoolName == null) || events.get(memoryPoolName) == null) { // events has no entries on UNKNOWN
            slidingWindow = new DefaultSlidingWindow<GarbageCollectionEvent>(EVENT_WINDOW);
        } else {
            slidingWindow = events.get(memoryPoolName);
        }
        return new GarbageCollectionStats(slidingWindow.snapshot());
    }

    public GarbageCollectorMonitoringStrategy getGcStrategy() {
        return gcStrategy;
    }

    public enum JVMStrategy {
        IBM(GarbageCollectorMonitoringStrategy.IBM_ALL),
        ORACLE_HOTSPOT(GarbageCollectorMonitoringStrategy.ORACLE_PARALLEL_CMS,
                        GarbageCollectorMonitoringStrategy.ORACLE_SERIAL,
                        GarbageCollectorMonitoringStrategy.ORACLE_6_CMS,
                        GarbageCollectorMonitoringStrategy.ORACLE_G1),
        UNSUPPORTED();

        final GarbageCollectorMonitoringStrategy[] gcStrategies;

        JVMStrategy(GarbageCollectorMonitoringStrategy... gcStrategies) {
            this.gcStrategies = gcStrategies;
        }

        static JVMStrategy current() {
            String vmname = System.getProperty("java.vm.name");

            if (vmname.equals("IBM J9 VM")) {
                return IBM;
            }

            if (vmname.startsWith("Java HotSpot(TM)")) {
                return ORACLE_HOTSPOT;
            }

            return UNSUPPORTED;
        }

        public GarbageCollectorMonitoringStrategy[] getGcStrategies() {
            return gcStrategies;
        }

        @Override
        public String toString() {
            return "JVMStrategy{" + System.getProperty("java.vendor") + " version " + System.getProperty("java.version") + "}";
        }
    }
}
