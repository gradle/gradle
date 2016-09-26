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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.internal.cache.HeapProportionalCacheSizer;

import java.util.HashMap;
import java.util.Map;

class CacheCapSizer {
    private static final Map<String, Integer> DEFAULT_CAP_SIZES = new HashMap<String, Integer>();

    static {
        DEFAULT_CAP_SIZES.put("fileSnapshots", 10000);
        DEFAULT_CAP_SIZES.put("taskArtifacts", 2000);
        DEFAULT_CAP_SIZES.put("fileHashes", 400000);
        DEFAULT_CAP_SIZES.put("compilationState", 1000);
    }

    final HeapProportionalCacheSizer sizer;
    private final Map<String, Integer> capSizes;

    CacheCapSizer(int maxHeapMB) {
        this.sizer = maxHeapMB > 0 ? new HeapProportionalCacheSizer(maxHeapMB) : new HeapProportionalCacheSizer();
        this.capSizes = calculateCaps();
    }

    CacheCapSizer() {
        this(0);
    }

    private Map<String, Integer> calculateCaps() {
        Map<String, Integer> capSizes = new HashMap<String, Integer>();
        for (Map.Entry<String, Integer> entry : DEFAULT_CAP_SIZES.entrySet()) {
            capSizes.put(entry.getKey(), scaleCacheSize(entry.getValue()));
        }
        return capSizes;
    }

    protected int scaleCacheSize(int referenceValue) {
        return sizer.scaleCacheSize(referenceValue);
    }

    public Integer getMaxSize(String cacheName) {
        return capSizes.get(cacheName);
    }

    public int getNumberOfCaches() {
        return capSizes.size();
    }
}
