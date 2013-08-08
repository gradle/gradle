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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.cache.Store;

public class CachedStoreFactory {

    private final Cache<Configuration, TransientConfigurationResults> cache;

    public CachedStoreFactory(int maxItems) {
        cache = CacheBuilder.newBuilder().maximumSize(maxItems).build();
    }

    public Store<TransientConfigurationResults> createCachedStore(final Configuration configuration) {
        return new SimpleStore(cache, configuration);
    }

    public void close() {
        cache.cleanUp();
    }

    private static class SimpleStore implements Store<TransientConfigurationResults> {
        private Cache<Configuration, TransientConfigurationResults> cache;
        private final Configuration configuration;

        public SimpleStore(Cache<Configuration, TransientConfigurationResults> cache, Configuration configuration) {
            this.cache = cache;
            this.configuration = configuration;
        }

        public TransientConfigurationResults load() {
            return cache.getIfPresent(configuration);
        }

        public void store(TransientConfigurationResults object) {
            cache.put(configuration, object);
        }
    }
}
