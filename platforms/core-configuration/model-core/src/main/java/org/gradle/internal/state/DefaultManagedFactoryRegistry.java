/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.state;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import javax.annotation.Nullable;

public class DefaultManagedFactoryRegistry implements ManagedFactoryRegistry {
    private final ManagedFactoryRegistry parent;
    private final Cache<Integer, ManagedFactory> managedFactoryCache = CacheBuilder.newBuilder().build();

    public DefaultManagedFactoryRegistry(ManagedFactoryRegistry parent) {
        this.parent = parent;
    }

    public DefaultManagedFactoryRegistry() {
        this(null);
    }

    public ManagedFactoryRegistry withFactories(ManagedFactory... factories) {
        for (ManagedFactory factory : factories) {
            register(factory);
        }
        return this;
    }

    @Override
    @Nullable
    public ManagedFactory lookup(int id) {
        ManagedFactory factory = managedFactoryCache.getIfPresent(id);
        if (factory == null && parent != null) {
            factory = parent.lookup(id);
        }
        return factory;
    }

    private void register(ManagedFactory factory) {
        ManagedFactory existing = managedFactoryCache.getIfPresent(factory.getId());
        if (existing != null) {
            throw new IllegalArgumentException("A managed factory with type " + existing.getClass().getSimpleName() + " (id: " + existing.getId() + ") has already been registered.");
        }
        managedFactoryCache.put(factory.getId(), factory);
    }
}
