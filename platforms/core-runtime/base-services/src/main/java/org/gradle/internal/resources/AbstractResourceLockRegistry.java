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

import org.gradle.internal.Factory;

public abstract class AbstractResourceLockRegistry<K, T extends ResourceLock> implements ResourceLockRegistry, ResourceLockContainer {

    private final LockCache<K, T> resourceLocks;

    @SuppressWarnings("this-escape")
    public AbstractResourceLockRegistry(final ResourceLockCoordinationService coordinationService) {
        this.resourceLocks = new LockCache<K, T>(coordinationService, this);
    }

    protected T getOrRegisterResourceLock(final K key, final ResourceLockProducer<K, T> producer) {
        return resourceLocks.getOrRegisterResourceLock(key, producer);
    }

    /**
     * Runs the given action, during which the current thread may not acquire or release any lock of this registry.
     */
    public abstract <S> S whileDisallowingLockChanges(Factory<S> action);

    @Override
    public boolean hasOpenLocks() {
        for (ResourceLock resourceLock : resourceLocks.values()) {
            if (resourceLock.isLocked()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return true if the current thread holds any lock of this registry.
     */
    public abstract boolean holdsLock();

    /**
     * Return true if the current thread holds the given lock of this registry.
     */
    public abstract boolean holdsLock(ResourceLock lock);

    /**
     * Return true if the current thread is allowed to acquire or release locks of this registry.
     */
    public abstract boolean mayAttemptToChangeLocks();

    // Thread.getId() is deprecated since JDK 19, but the replacement Thread.threadId()
    // does not exist on JDK 17. The method is not deprecated for removal, so we should
    // be fine for now.
    @SuppressWarnings("deprecation")
    protected static long currentThreadId() {
        return Thread.currentThread().getId();
    }

    public interface ResourceLockProducer<K, T extends ResourceLock> {
        T create(K key, ResourceLockCoordinationService coordinationService, ResourceLockContainer owner);
    }

}
