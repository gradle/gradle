/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.oldresult;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.cache.Store;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Factory;

import java.util.concurrent.atomic.AtomicLong;

public class CachedStoreFactory {

    private static final Logger LOG = Logging.getLogger(CachedStoreFactory.class);

    private final Cache<ConfigurationInternal, TransientConfigurationResults> cache;
    private final Stats stats;

    public CachedStoreFactory(int maxItems) {
        cache = CacheBuilder.newBuilder().maximumSize(maxItems).build();
        stats = new Stats();
    }

    public Store<TransientConfigurationResults> createCachedStore(final ConfigurationInternal configuration) {
        return new SimpleStore(cache, configuration, stats);
    }

    public void close() {
        LOG.info("Resolved configuration cache: cache reads: "
                + stats.readsFromCache + ", disk reads: "
                + stats.readsFromDisk + " (avg: " + stats.getDiskReadsAvgMs() + ", total: " + stats.diskReadsTotalMs + ")");
    }

    private static class Stats {
        private final AtomicLong diskReadsTotalMs = new AtomicLong();
        private final AtomicLong readsFromCache = new AtomicLong();
        private final AtomicLong readsFromDisk = new AtomicLong();

        public void readFromDisk(long start) {
            long duration = System.currentTimeMillis() - start;
            readsFromDisk.incrementAndGet();
            diskReadsTotalMs.addAndGet(duration);
        }

        public void readFromCache() {
            readsFromCache.incrementAndGet();
        }

        public long getDiskReadsAvgMs() {
            if (readsFromDisk.get() == 0) {
                return 0;
            }
            return diskReadsTotalMs.get() / readsFromDisk.get();
        }
    }

    private static class SimpleStore implements Store<TransientConfigurationResults> {
        private Cache<ConfigurationInternal, TransientConfigurationResults> cache;
        private final ConfigurationInternal configuration;
        private Stats stats;

        public SimpleStore(Cache<ConfigurationInternal, TransientConfigurationResults> cache, ConfigurationInternal configuration, Stats stats) {
            this.cache = cache;
            this.configuration = configuration;
            this.stats = stats;
        }

        public TransientConfigurationResults load(Factory<TransientConfigurationResults> createIfNotPresent) {
            TransientConfigurationResults out = cache.getIfPresent(configuration);
            if (out != null) {
                stats.readFromCache();
                return out;
            }
            long start = System.currentTimeMillis();
            TransientConfigurationResults value = createIfNotPresent.create();
            stats.readFromDisk(start);
            cache.put(configuration, value);
            return value;
        }
    }
}
