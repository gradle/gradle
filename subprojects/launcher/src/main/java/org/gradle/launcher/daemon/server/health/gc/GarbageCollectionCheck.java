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

import org.gradle.api.specs.Spec;
import org.gradle.util.CollectionUtils;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.List;
import java.util.Map;

public class GarbageCollectionCheck implements Runnable {
    final Map<String, SlidingWindow<GarbageCollectionEvent>> events;
    final List<String> memoryPools;
    final String garbageCollector;

    public GarbageCollectionCheck(Map<String, SlidingWindow<GarbageCollectionEvent>> events, List<String> memoryPools, String garbageCollector) {
        this.events = events;
        this.memoryPools = memoryPools;
        this.garbageCollector = garbageCollector;
    }

    @Override
    public void run() {
        List<GarbageCollectorMXBean> garbageCollectorMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        GarbageCollectorMXBean garbageCollectorMXBean = CollectionUtils.findFirst(garbageCollectorMXBeans, new Spec<GarbageCollectorMXBean>() {
            @Override
            public boolean isSatisfiedBy(GarbageCollectorMXBean mbean) {
                return mbean.getName().equals(garbageCollector);
            }
        });

        List<MemoryPoolMXBean> memoryPoolMXBeans = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean memoryPoolMXBean : memoryPoolMXBeans) {
            String pool = memoryPoolMXBean.getName();
            if (memoryPools.contains(pool)) {
                GarbageCollectionEvent event = new GarbageCollectionEvent(System.currentTimeMillis(), memoryPoolMXBean.getCollectionUsage(), garbageCollectorMXBean.getCollectionCount());
                events.get(pool).slideAndInsert(event);
            }
        }
    }
}
