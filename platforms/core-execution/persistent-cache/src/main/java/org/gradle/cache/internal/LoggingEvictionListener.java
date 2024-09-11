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

package org.gradle.cache.internal;

import com.google.common.cache.Cache;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

class LoggingEvictionListener implements RemovalListener<Object, Object> {
    private static final String EVICTION_MITIGATION_MESSAGE = "\nPerformance may suffer from in-memory cache misses. Increase max heap size of Gradle build process to reduce cache misses.";

    private final AtomicInteger evictionCounter = new AtomicInteger(0);
    private final String cacheId;
    private final int maxSize;
    private final int logInterval;
    private final Logger logger;
    private Cache<Object, Object> cache;

    LoggingEvictionListener(String cacheId, int maxSize, Logger logger) {
        this.cacheId = cacheId;
        this.maxSize = maxSize;
        this.logInterval = maxSize / 10;
        this.logger = logger;
    }

    public void setCache(Cache<Object, Object> cache) {
        this.cache = cache;
    }

    @Override
    public void onRemoval(RemovalNotification<Object, Object> notification) {
        if (notification.getCause() == RemovalCause.SIZE) {
            if (evictionCounter.get() % logInterval == 0) {
                logger.info("Cache entries evicted. In-memory cache of {}: Size{{}} MaxSize{{}}, {} {}", cacheId, cache.size(), maxSize, cache.stats(), EVICTION_MITIGATION_MESSAGE);
            }
            evictionCounter.incrementAndGet();
        }
    }
}
