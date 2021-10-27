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

package org.gradle.internal.resources;

import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.internal.Pair;

import java.util.Map;
import java.util.concurrent.Semaphore;

public class SharedResourceLeaseRegistry extends AbstractResourceLockRegistry<String, SharedResourceLeaseRegistry.SharedResourceLease> {
    private final Map<String, Pair<Integer, Semaphore>> sharedResources = Maps.newConcurrentMap();

    public SharedResourceLeaseRegistry(ResourceLockCoordinationService coordinationService) {
        super(coordinationService);
    }

    public void registerSharedResource(String name, int leases) {
        sharedResources.put(name, Pair.of(leases, new Semaphore(leases)));
    }

    public ResourceLock getResourceLock(final String sharedResource, final int leases) {
        String displayName = "lease of " + leases + " for " + sharedResource;

        // We don't want to cache lock instances here since it's valid for multiple threads to hold a lock on a given resource for a given number of leases.
        // For that reason we don't want to reuse lock instances, as it's very possible they can be concurrently held by multiple threads.
        return createResourceLock(displayName, new ResourceLockProducer<String, SharedResourceLease>() {
            @Override
            public SharedResourceLease create(String displayName, ResourceLockCoordinationService coordinationService, Action<ResourceLock> lockAction, Action<ResourceLock> unlockAction) {
                return new SharedResourceLease(displayName, coordinationService, lockAction, unlockAction, sharedResource, leases);
            }
        });
    }

    public class SharedResourceLease extends AbstractTrackedResourceLock {
        private final int leases;
        private final Pair<Integer, Semaphore> semaphore;
        private Thread ownerThread;
        private boolean active = false;

        SharedResourceLease(String displayName, ResourceLockCoordinationService coordinationService, Action<ResourceLock> lockAction, Action<ResourceLock> unlockAction, String sharedResource, int leases) {
            super(displayName, coordinationService, lockAction, unlockAction);
            this.leases = leases;
            this.semaphore = sharedResources.get(sharedResource);
        }

        @Override
        protected boolean acquireLock() {
            if (leases > semaphore.getLeft()) {
                throw new IllegalArgumentException("Cannot acquire lock on " + getDisplayName() + " as max available leases is " + semaphore.getLeft());
            }

            if (semaphore.getRight().tryAcquire(leases)) {
                active = true;
                ownerThread = Thread.currentThread();
            }

            return doIsLockedByCurrentThread();
        }

        @Override
        protected void releaseLock() {
            if (Thread.currentThread() != ownerThread) {
                throw new UnsupportedOperationException("Lock cannot be released from non-owner thread.");
            }

            semaphore.getRight().release(leases);
            active = false;
            ownerThread = null;
        }

        @Override
        protected boolean doIsLocked() {
            return active;
        }

        @Override
        protected boolean doIsLockedByCurrentThread() {
            return active && ownerThread == Thread.currentThread();
        }
    }
}
