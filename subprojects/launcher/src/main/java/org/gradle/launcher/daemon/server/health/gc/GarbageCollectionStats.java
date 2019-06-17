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

import com.google.common.collect.Iterables;
import org.gradle.internal.util.NumberUtil;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

public class GarbageCollectionStats {
    private final double gcRate;
    private final int usedPercent;
    private final long maxSizeInBytes;
    private final long eventCount;

    private GarbageCollectionStats(double gcRate, long usedSizeInBytes, long maxSizeInBytes, long eventCount) {
        this.gcRate = gcRate;
        if (maxSizeInBytes > 0) {
            this.usedPercent = NumberUtil.percentOf(usedSizeInBytes, maxSizeInBytes);
        } else {
            this.usedPercent = 0;
        }
        this.maxSizeInBytes = maxSizeInBytes;
        this.eventCount = eventCount;
    }

    static GarbageCollectionStats forHeap(Collection<GarbageCollectionEvent> events) {
        if (events.isEmpty()) {
            return noData();
        } else {
            return new GarbageCollectionStats(
                    calculateRate(events),
                    calculateAverageUsage(events),
                    findMaxSize(events),
                    events.size()
            );
        }
    }

    static GarbageCollectionStats forNonHeap(Collection<GarbageCollectionEvent> events) {
        if (events.isEmpty()) {
            return noData();
        } else {
            return new GarbageCollectionStats(
                    0, // non-heap spaces are not garbage collected
                    calculateAverageUsage(events),
                    findMaxSize(events),
                    events.size()
            );
        }
    }

    private static GarbageCollectionStats noData() {
        return new GarbageCollectionStats(0, 0, -1, 0);
    }

    private static double calculateRate(Collection<GarbageCollectionEvent> events) {
        if (events.size() < 2) {
            // not enough data points
            return 0;
        }
        GarbageCollectionEvent first = events.iterator().next();
        GarbageCollectionEvent last = Iterables.getLast(events);
        // Total number of garbage collection events observed in the window
        long gcCountDelta = last.getCount() - first.getCount();
        // Time interval between the first event in the window and the last
        long timeDelta = TimeUnit.MILLISECONDS.toSeconds(last.getTimestamp() - first.getTimestamp());
        return (double)gcCountDelta / timeDelta;
    }

    private static long calculateAverageUsage(Collection<GarbageCollectionEvent> events) {
        long sum = 0;
        for (GarbageCollectionEvent event : events) {
            sum += event.getUsage().getUsed();
        }
        return sum / events.size();
    }

    private static long findMaxSize(Collection<GarbageCollectionEvent> events) {
        // Maximum pool size is fixed, so we should only need to get it from the first event
        GarbageCollectionEvent first = events.iterator().next();
        return first.getUsage().getMax();
    }

    public double getGcRate() {
        return gcRate;
    }

    public int getUsedPercent() {
        return usedPercent;
    }

    public long getMaxSizeInBytes() {
        return maxSizeInBytes;
    }

    public boolean isValid() {
        return eventCount >= 5 && maxSizeInBytes > 0;
    }
}
