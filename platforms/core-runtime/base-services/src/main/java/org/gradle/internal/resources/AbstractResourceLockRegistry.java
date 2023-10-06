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

import com.google.common.collect.ImmutableList;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public abstract class AbstractResourceLockRegistry<K, T extends ResourceLock> implements ResourceLockRegistry, ResourceLockContainer {
    private final LockCache<K, T> resourceLocks;
    private final ConcurrentMap<Long, ThreadLockDetails<T>> threadLocks = new ConcurrentHashMap<Long, ThreadLockDetails<T>>();

    public AbstractResourceLockRegistry(final ResourceLockCoordinationService coordinationService) {
        this.resourceLocks = new LockCache<K, T>(coordinationService, this);
    }

    protected T getOrRegisterResourceLock(final K key, final ResourceLockProducer<K, T> producer) {
        return resourceLocks.getOrRegisterResourceLock(key, producer);
    }

    @Override
    public List<T> getResourceLocksByCurrentThread() {
        return ImmutableList.copyOf(detailsForCurrentThread().locks);
    }

    public <S> S whileDisallowingLockChanges(Factory<S> action) {
        ThreadLockDetails<T> lockDetails = detailsForCurrentThread();
        boolean previous = lockDetails.mayChange;
        lockDetails.mayChange = false;
        try {
            return action.create();
        } finally {
            lockDetails.mayChange = previous;
        }
    }

    public <S> S allowUncontrolledAccessToAnyResource(Factory<S> factory) {
        ThreadLockDetails<T> lockDetails = detailsForCurrentThread();
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
        for (ResourceLock resourceLock : resourceLocks.values()) {
            if (resourceLock.isLocked()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void lockAcquired(ResourceLock resourceLock) {
        ThreadLockDetails<T> lockDetails = detailsForCurrentThread();
        if (!lockDetails.mayChange) {
            throw new IllegalStateException("This thread may not acquire more locks.");
        }
        lockDetails.locks.add(Cast.<T>uncheckedCast(resourceLock));
    }

    public boolean holdsLock() {
        ThreadLockDetails<T> details = detailsForCurrentThread();
        return !details.locks.isEmpty();
    }

    private ThreadLockDetails<T> detailsForCurrentThread() {
        long id = Thread.currentThread().getId();
        ThreadLockDetails<T> lockDetails = threadLocks.get(id);
        if (lockDetails == null) {
            lockDetails = new ThreadLockDetails<T>();
            threadLocks.put(id, lockDetails);
        }
        return lockDetails;
    }

    @Override
    public void lockReleased(ResourceLock resourceLock) {
        ThreadLockDetails<T> lockDetails = threadLocks.get(Thread.currentThread().getId());
        if (!lockDetails.mayChange) {
            throw new IllegalStateException("This thread may not release any locks.");
        }
        lockDetails.locks.remove(resourceLock);
    }

    public boolean mayAttemptToChangeLocks() {
        ThreadLockDetails<T> details = detailsForCurrentThread();
        return details.mayChange && !details.canAccessAnything;
    }

    public boolean isAllowedUncontrolledAccessToAnyResource() {
        return detailsForCurrentThread().canAccessAnything;
    }

    public interface ResourceLockProducer<K, T extends ResourceLock> {
        T create(K key, ResourceLockCoordinationService coordinationService, ResourceLockContainer owner);
    }

    private static class ThreadLockDetails<T extends ResourceLock> {
        // Only accessed by the thread itself, so does not require synchronization
        private boolean mayChange = true;
        private boolean canAccessAnything = false;
        private final List<T> locks = new ArrayList<T>();
    }
}
