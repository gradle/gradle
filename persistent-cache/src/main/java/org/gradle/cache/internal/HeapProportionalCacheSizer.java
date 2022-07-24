/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.cache.internal;

public class HeapProportionalCacheSizer {
    public static final String CACHE_RESERVED_SYSTEM_PROPERTY = "org.gradle.cache.reserved.mb";
    private static final int DEFAULT_SIZES_MAX_HEAP_MB = 910; // when -Xmx1024m, Runtime.maxMemory() returns about 910
    private static final int ASSUMED_USED_HEAP = 150; // assume that Gradle itself uses about 150MB heap

    private static final double MIN_RATIO = 0.2d;

    private final int maxHeapMB;
    private final double sizingRatio;
    private final int reservedHeap;

    public HeapProportionalCacheSizer(int maxHeapMB) {
        this.maxHeapMB = maxHeapMB;
        this.reservedHeap = ASSUMED_USED_HEAP + Integer.getInteger(CACHE_RESERVED_SYSTEM_PROPERTY, 0);
        this.sizingRatio = calculateRatioToDefaultAvailableHeap();
    }

    public HeapProportionalCacheSizer() {
        this(calculateMaxHeapMB());
    }

    private static int calculateMaxHeapMB() {
        return (int) (Runtime.getRuntime().maxMemory() / (1024 * 1024));
    }

    private double calculateRatioToDefaultAvailableHeap() {
        double defaultAvailableHeapSpace = DEFAULT_SIZES_MAX_HEAP_MB - ASSUMED_USED_HEAP;
        double availableHeapSpace = maxHeapMB - reservedHeap;
        double ratioToDefaultAvailableHeap = availableHeapSpace / defaultAvailableHeapSpace;
        return Math.max(ratioToDefaultAvailableHeap, MIN_RATIO);
    }

    public int scaleCacheSize(int referenceValue) {
        return scaleCacheSize(referenceValue, 100);
    }

    private int scaleCacheSize(int referenceValue, int granularity) {
        if (referenceValue < granularity) {
            throw new IllegalArgumentException("reference value must be larger than granularity");
        }
        int scaledValue = (int) ((double) referenceValue * sizingRatio) / granularity * granularity;
        return Math.max(scaledValue, granularity);
    }
}
