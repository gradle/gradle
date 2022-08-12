/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.resources;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.gradle.internal.UncheckedException;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class LockCache<K, T extends ResourceLock> {
    private final Cache<K, T> resourceLocks = CacheBuilder.newBuilder().weakValues().build();
    private final ResourceLockCoordinationService coordinationService;
    private final ResourceLockContainer owner;

    public LockCache(ResourceLockCoordinationService coordinationService, ResourceLockContainer owner) {
        this.coordinationService = coordinationService;
        this.owner = owner;
    }

    public T getOrRegisterResourceLock(final K key, final AbstractResourceLockRegistry.ResourceLockProducer<K, T> producer) {
        try {
            return resourceLocks.get(key, new Callable<T>() {
                @Override
                public T call() {
                    return createResourceLock(key, producer);
                }
            });
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private T createResourceLock(final K key, final AbstractResourceLockRegistry.ResourceLockProducer<K, T> producer) {
        return producer.create(key, coordinationService, owner);
    }

    public Iterable<T> values() {
        return resourceLocks.asMap().values();
    }
}
