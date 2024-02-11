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
import org.slf4j.LoggerFactory;

class LoggingEvictionListener implements RemovalListener<Object, Object> {
    private static Logger logger = LoggerFactory.getLogger(LoggingEvictionListener.class);
    private static final String EVICTION_MITIGATION_MESSAGE = "\nPerformance may suffer from in-memory cache misses. Increase max heap size of Gradle build process to reduce cache misses.";
    volatile int evictionCounter;
    private final String cacheId;
    private Cache<Object, Object> cache;
    private final int maxSize;
    private final int logInterval;

    LoggingEvictionListener(String cacheId, int maxSize) {
        this.cacheId = cacheId;
        this.maxSize = maxSize;
        this.logInterval = maxSize / 10;
    }

    public void setCache(Cache<Object, Object> cache) {
        this.cache = cache;
    }

    @Override
    public void onRemoval(RemovalNotification<Object, Object> notification) {
        if (notification.getCause() == RemovalCause.SIZE) {
            if (evictionCounter % logInterval == 0) {
                logger.info("Cache entries evicted. In-memory cache of {}: Size{{}} MaxSize{{}}, {} {}", cacheId, cache.size(), maxSize, cache.stats(), EVICTION_MITIGATION_MESSAGE);
            }
            evictionCounter++;
        }
    }
}
