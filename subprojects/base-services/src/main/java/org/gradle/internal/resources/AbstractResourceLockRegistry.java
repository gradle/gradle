/*
 * Copyright 2017 the original author or authors.
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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import org.gradle.api.Action;

import java.util.Collection;
import java.util.concurrent.ConcurrentMap;

public abstract class AbstractResourceLockRegistry<T extends ResourceLock> implements ResourceLockRegistry {
    private final ConcurrentMap<String, T> resourceLocks = Maps.newConcurrentMap();
    private final Multimap<Long, ResourceLock> threadResourceLockMap = Multimaps.synchronizedListMultimap(ArrayListMultimap.<Long, ResourceLock>create());
    private final ResourceLockCoordinationService coordinationService;

    public AbstractResourceLockRegistry(final ResourceLockCoordinationService coordinationService) {
        this.coordinationService = coordinationService;
    }

    protected T getOrRegisterResourceLock(String displayName, ResourceLockProducer<T> producer) {
        resourceLocks.putIfAbsent(displayName, producer.create(displayName, coordinationService, getLockAction(), getUnlockAction()));
        return resourceLocks.get(displayName);
    }

    @Override
    public Collection<? extends ResourceLock> getResourceLocks() {
        final Long threadId = Thread.currentThread().getId();
        return ImmutableList.copyOf(threadResourceLockMap.get(threadId));
    }

    @Override
    public boolean hasOpenLocks() {
        for (ResourceLock resourceLock : resourceLocks.values()) {
            if (resourceLock.isLocked()) {
                return true;
            }
        }
        return false;
    }

    private Action<ResourceLock> getLockAction() {
        return new Action<ResourceLock>() {
            @Override
            public void execute(ResourceLock resourceLock) {
                threadResourceLockMap.put(Thread.currentThread().getId(), resourceLock);
            }
        };
    }

    private Action<ResourceLock> getUnlockAction() {
        return new Action<ResourceLock>() {
            @Override
            public void execute(ResourceLock resourceLock) {
                threadResourceLockMap.remove(Thread.currentThread().getId(), resourceLock);
            }
        };
    }

    public interface ResourceLockProducer<T extends ResourceLock> {
        T create(String displayName, ResourceLockCoordinationService coordinationService, Action<ResourceLock> lockAction, Action<ResourceLock> unlockAction);
    }
}
