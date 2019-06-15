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
import com.google.common.collect.Lists;
import org.gradle.internal.UncheckedException;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

public class DefaultManagedFactoryRegistry implements ManagedFactoryRegistry {
    private final ManagedFactoryRegistry parent;
    private final List<ManagedFactory> factories = Lists.newArrayList();
    private final Cache<Class<?>, Optional<ManagedFactory>> managedFactoryCache = CacheBuilder.newBuilder().weakKeys().build();

    public DefaultManagedFactoryRegistry(ManagedFactoryRegistry parent, ManagedFactory... factories) {
        this.parent = parent;
        this.factories.addAll(Arrays.asList(factories));
    }

    public DefaultManagedFactoryRegistry(ManagedFactory... factories) {
        this(null, factories);
    }

    @Override
    @Nullable
    public <T> ManagedFactory lookup(final Class<T> type) {
        try {
            return managedFactoryCache.get(type, new Callable<Optional<ManagedFactory>>() {
                @Override
                public Optional<ManagedFactory> call() throws Exception {
                    for (ManagedFactory factory : factories) {
                        if (factory.canCreate(type)) {
                            return Optional.of(factory);
                        }
                    }

                    return Optional.empty();
                }
            }).orElseGet(new Supplier<ManagedFactory>() {
                @Override
                public ManagedFactory get() {
                    if (parent != null) {
                        return parent.lookup(type);
                    }
                    return null;
                }
            });
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void register(Class<?> type, ManagedFactory factory) {
        managedFactoryCache.put(type, Optional.of(factory));
    }
}
