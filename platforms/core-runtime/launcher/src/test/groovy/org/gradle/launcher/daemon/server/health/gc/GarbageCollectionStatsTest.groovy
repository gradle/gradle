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

package org.gradle.launcher.daemon.server.health.gc

import spock.lang.Specification

import java.lang.management.MemoryUsage;

class GarbageCollectionStatsTest extends Specification {
    def "correctly calculates stats for heaps"() {
        def heapStats = GarbageCollectionStats.forHeap(heapEvents)
        expect:
        heapStats.valid
        heapStats.eventCount == 5
        heapStats.gcRate == 3.0d
        heapStats.maxSizeInBytes == 1000
        heapStats.usedPercent == 50
    }

    def "correctly calculates stats for non-heaps"() {
        def nonHeapStats = GarbageCollectionStats.forNonHeap(nonHeapEvents)
        expect:
        nonHeapStats.valid
        nonHeapStats.eventCount == 5
        nonHeapStats.gcRate == 0
        nonHeapStats.maxSizeInBytes == 1000
        nonHeapStats.usedPercent == 70
    }

    def "reports invalid when maximum memory cannot be determined"() {
        expect:
        !stats.valid
        stats.maxSizeInBytes == -1

        where:
        stats << [
                GarbageCollectionStats.forHeap(withoutMaxMemory(heapEvents)),
                GarbageCollectionStats.forNonHeap(withoutMaxMemory(nonHeapEvents))
        ]
    }

    def "reports invalid when no events are seen"() {
        expect:
        !stats.valid
        stats.gcRate == 0
        stats.maxSizeInBytes == -1
        stats.usedPercent == 0
        where:
        stats << [
                GarbageCollectionStats.forHeap([]),
                GarbageCollectionStats.forNonHeap([])
        ]
    }

    def getHeapEvents() {
        return [
                new GarbageCollectionEvent(0000, new MemoryUsage(0, 100, 1000, 1000), 1),
                new GarbageCollectionEvent(1000, new MemoryUsage(0, 250, 1000, 1000), 2),
                new GarbageCollectionEvent(2000, new MemoryUsage(0, 500, 1000, 1000), 3),
                new GarbageCollectionEvent(3000, new MemoryUsage(0, 750, 1000, 1000), 6),
                new GarbageCollectionEvent(4000, new MemoryUsage(0, 900, 1000, 1000), 13)
        ]
    }

    def getNonHeapEvents() {
        return [
                new GarbageCollectionEvent(0000, new MemoryUsage(0, 500, 1000, 1000), 0),
                new GarbageCollectionEvent(1000, new MemoryUsage(0, 600, 1000, 1000), 0),
                new GarbageCollectionEvent(2000, new MemoryUsage(0, 700, 1000, 1000), 0),
                new GarbageCollectionEvent(3000, new MemoryUsage(0, 800, 1000, 1000), 0),
                new GarbageCollectionEvent(4000, new MemoryUsage(0, 900, 1000, 1000), 0)
        ]
    }

    def withoutMaxMemory(List<GarbageCollectionEvent> events) {
        return events.collect { new GarbageCollectionEvent(
            it.timestamp,
            new MemoryUsage(it.usage.init, it.usage.used, it.usage.committed, -1),
            it.count
        ) }
    }
}
