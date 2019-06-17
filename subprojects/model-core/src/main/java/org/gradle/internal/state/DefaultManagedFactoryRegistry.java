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

    public DefaultManagedFactoryRegistry(ManagedFactoryRegistry parent, ManagedFactory... factories) {
        this.parent = parent;
        for (ManagedFactory factory : factories) {
            managedFactoryCache.put(factory.getId(), factory);
        }
    }

    public DefaultManagedFactoryRegistry(ManagedFactory... factories) {
        this(null, factories);
    }

    @Override
    @Nullable
    public <T> ManagedFactory lookup(int id) {
        ManagedFactory factory = managedFactoryCache.getIfPresent(id);
        if (factory == null && parent != null) {
            factory = parent.lookup(id);
        }
        return factory;
    }

    @Override
    public void register(ManagedFactory factory) {
        managedFactoryCache.put(factory.getId(), factory);
    }
}
