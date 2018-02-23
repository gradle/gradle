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

import org.gradle.internal.util.NumberUtil;

import java.lang.management.MemoryUsage;
import java.util.Set;

public class GarbageCollectionStats {
    final private double rate;
    final private long used;
    final private long max;
    final private long eventCount;

    public GarbageCollectionStats(Set<GarbageCollectionEvent> events) {
        this.rate = calculateRate(events);
        this.used = calculateAverageUsage(events);
        this.max = calculateMaxSize(events);
        this.eventCount = events.size();
    }

    static double calculateRate(Set<GarbageCollectionEvent> events) {
        long firstGC = 0;
        long lastGC = 0;
        long firstCount = 0;
        long lastCount = 0;
        for (GarbageCollectionEvent event : events) {
            // Skip if this was a polling event and the garbage collector did not fire in between events
            if (event.getCount() == lastCount || event.getCount() == 0) {
                continue;
            }

            lastCount = event.getCount();

            if (firstGC == 0) {
                firstGC = event.getTimestamp();
                firstCount = event.getCount();
            } else {
                lastGC = event.getTimestamp();
            }
        }

        if (events.size() < 2 || lastCount == 0) {
            return 0;
        } else {
            long elapsed = lastGC - firstGC;
            long totalCount = lastCount - firstCount;
            return ((double) totalCount) / elapsed * 1000;
        }
    }

    static long calculateAverageUsage(Set<GarbageCollectionEvent> events) {
        if (events.size() < 1) {
            return -1;
        }

        long total = 0;
        long firstCount = 0;
        long lastCount = 0;
        for (GarbageCollectionEvent event : events) {
            // Skip if the garbage collector did not fire in between events
            if (event.getCount() == lastCount || event.getCount() == 0) {
                continue;
            }

            MemoryUsage usage = event.getUsage();
            if (firstCount == 0) {
                firstCount = event.getCount();
                total += usage.getUsed();
            } else {
                total += usage.getUsed() * (event.getCount() - lastCount);
            }

            lastCount = event.getCount();
        }

        if (lastCount == 0 || lastCount == firstCount) {
            return -1;
        } else {
            long totalCount = lastCount - firstCount + 1;
            return total / totalCount;
        }
    }

    static long calculateMaxSize(Set<GarbageCollectionEvent> events) {
        if (events.size() < 1) {
            return -1;
        }

        // Maximum pool size is fixed, so we should only need to get it from the first event
        MemoryUsage usage = events.iterator().next().getUsage();
        return usage.getMax();
    }

    public double getRate() {
        return rate;
    }

    public int getUsage() {
        if (used > 0 && max > 0) {
            return NumberUtil.percentOf(used, max);
        } else {
            return -1;
        }
    }

    public double getUsed() {
        return used;
    }

    public long getMax() {
        return max;
    }

    public long getEventCount() {
        return eventCount;
    }
}
