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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

public abstract class AbstractResourceLockRegistry<K, T extends ResourceLock> implements ResourceLockRegistry {
    private final Cache<K, T> resourceLocks = CacheBuilder.newBuilder().weakValues().build();
    private final ConcurrentMap<Long, ThreadLockDetails> threadLocks = new ConcurrentHashMap<Long, ThreadLockDetails>();
    private final ResourceLockCoordinationService coordinationService;

    public AbstractResourceLockRegistry(final ResourceLockCoordinationService coordinationService) {
        this.coordinationService = coordinationService;
    }

    protected T getOrRegisterResourceLock(final K key, final ResourceLockProducer<K, T> producer) {
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

    protected T createResourceLock(final K key, final ResourceLockProducer<K, T> producer) {
        return producer.create(key, coordinationService, getLockAction(), getUnlockAction());
    }

    @Override
    public Collection<? extends ResourceLock> getResourceLocksByCurrentThread() {
        return ImmutableList.copyOf(detailsForCurrentThread().locks);
    }

    public <S> S whileDisallowingLockChanges(Factory<S> action) {
        ThreadLockDetails lockDetails = detailsForCurrentThread();
        boolean previous = lockDetails.mayChange;
        lockDetails.mayChange = false;
        try {
            return action.create();
        } finally {
            lockDetails.mayChange = previous;
        }
    }

    public <S> S allowUncontrolledAccessToAnyResource(Factory<S> factory) {
        ThreadLockDetails lockDetails = detailsForCurrentThread();
        boolean previous = lockDetails.canAccessAnything;
        lockDetails.canAccessAnything = true;
        try {
            return factory.create();
        } finally {
            lockDetails.canAccessAnything = previous;
        }
    }

    @Override
    public boolean hasOpenLocks() {
        for (ResourceLock resourceLock : resourceLocks.asMap().values()) {
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
                associateResourceLock(resourceLock);
            }
        };
    }

    private Action<ResourceLock> getUnlockAction() {
        return new Action<ResourceLock>() {
            @Override
            public void execute(ResourceLock resourceLock) {
                unassociateResourceLock(resourceLock);
            }
        };
    }

    public void associateResourceLock(ResourceLock resourceLock) {
        ThreadLockDetails lockDetails = detailsForCurrentThread();
        if (!lockDetails.mayChange) {
            throw new IllegalStateException("This thread may not acquire more locks.");
        }
        lockDetails.locks.add(resourceLock);
    }

    private ThreadLockDetails detailsForCurrentThread() {
        long id = Thread.currentThread().getId();
        ThreadLockDetails lockDetails = threadLocks.get(id);
        if (lockDetails == null) {
            lockDetails = new ThreadLockDetails();
            threadLocks.put(id, lockDetails);
        }
        return lockDetails;
    }

    public void unassociateResourceLock(ResourceLock resourceLock) {
        ThreadLockDetails lockDetails = threadLocks.get(Thread.currentThread().getId());
        if (!lockDetails.mayChange) {
            throw new IllegalStateException("This thread may not release any locks.");
        }
        lockDetails.locks.remove(resourceLock);
    }

    public boolean mayAttemptToChangeLocks() {
        ThreadLockDetails details = detailsForCurrentThread();
        return details.mayChange && !details.canAccessAnything;
    }

    public boolean isAllowedUncontrolledAccessToAnyResource() {
        return detailsForCurrentThread().canAccessAnything;
    }

    public interface ResourceLockProducer<K, T extends ResourceLock> {
        T create(K key, ResourceLockCoordinationService coordinationService, Action<ResourceLock> lockAction, Action<ResourceLock> unlockAction);
    }

    private static class ThreadLockDetails {
        // Only accessed by the thread itself, so does not require synchronization
        private boolean mayChange = true;
        private boolean canAccessAnything = false;
        private final List<ResourceLock> locks = new ArrayList<ResourceLock>();
    }
}
