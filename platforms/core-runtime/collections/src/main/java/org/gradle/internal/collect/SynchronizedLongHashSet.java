/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.collect;

/**
 * A thread-safe primitive long hash set using open addressing with linear probing.
 *
 * Uses 0 as the empty sentinel and -1 as the tombstone for deleted entries,
 * so neither value can be stored as an element.
 *
 * Zero per-element object allocation — only allocates a new backing array when rehashing.
 */
public class SynchronizedLongHashSet {

    private static final long EMPTY = 0;
    private static final long TOMBSTONE = -1;
    private static final int INITIAL_CAPACITY = 16;

    private long[] table;
    private int liveCount;
    private int occupiedCount; // live + tombstones, used for load factor check

    public SynchronizedLongHashSet() {
        table = new long[INITIAL_CAPACITY];
    }

    /**
     * Adds the given value to the set.
     *
     * @throws IllegalArgumentException if value is 0 or -1 (reserved sentinels)
     */
    public synchronized void add(long id) {
        if (occupiedCount * 4 >= table.length * 3) {
            rehash();
        }
        int mask = table.length - 1;
        int index = hash(id) & mask;
        int firstTombstone = -1;
        while (true) {
            long v = table[index];
            if (v == id) {
                return;
            }
            if (v == EMPTY) {
                if (firstTombstone >= 0) {
                    table[firstTombstone] = id;
                    // Reusing a tombstone slot: occupiedCount stays the same
                } else {
                    table[index] = id;
                    occupiedCount++;
                }
                liveCount++;
                return;
            }
            if (v == TOMBSTONE && firstTombstone < 0) {
                firstTombstone = index;
            }
            index = (index + 1) & mask;
        }
    }

    public synchronized boolean contains(long id) {
        int mask = table.length - 1;
        int index = hash(id) & mask;
        while (true) {
            long v = table[index];
            if (v == id) {
                return true;
            }
            if (v == EMPTY) {
                return false;
            }
            index = (index + 1) & mask;
        }
    }

    /**
     * Removes the given value from the set.
     *
     * @return true if the value was present and removed, false if not found
     */
    public synchronized boolean remove(long id) {
        int mask = table.length - 1;
        int index = hash(id) & mask;
        while (true) {
            long v = table[index];
            if (v == id) {
                table[index] = TOMBSTONE;
                liveCount--;
                // occupiedCount stays the same — tombstone still occupies the slot
                return true;
            }
            if (v == EMPTY) {
                return false;
            }
            index = (index + 1) & mask;
        }
    }

    public synchronized int size() {
        return liveCount;
    }

    private void rehash() {
        long[] oldTable = table;
        int newCapacity = oldTable.length * 2;
        // If most entries are tombstones, don't grow — just clean up
        if (liveCount * 4 < oldTable.length) {
            newCapacity = oldTable.length;
        }
        table = new long[newCapacity];
        int mask = newCapacity - 1;
        liveCount = 0;
        occupiedCount = 0;
        for (long v : oldTable) {
            if (v != EMPTY && v != TOMBSTONE) {
                int index = hash(v) & mask;
                while (table[index] != EMPTY) {
                    index = (index + 1) & mask;
                }
                table[index] = v;
                liveCount++;
                occupiedCount++;
            }
        }
    }

    private static int hash(long id) {
        int h = (int) (id ^ (id >>> 32));
        return h ^ (h >>> 16);
    }
}
