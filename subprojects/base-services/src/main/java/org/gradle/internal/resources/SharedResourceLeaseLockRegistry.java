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

import java.util.Map;
import java.util.concurrent.Semaphore;

public class SharedResourceLeaseLockRegistry extends AbstractResourceLockRegistry<String, SharedResourceLeaseLockRegistry.SharedResourceLease> {
    private final Map<String, Semaphore> sharedResources = Maps.newConcurrentMap();

    public SharedResourceLeaseLockRegistry(ResourceLockCoordinationService coordinationService) {
        super(coordinationService);
    }

    public void registerSharedResource(String name, int leases) {
        sharedResources.put(name, new Semaphore(leases));
    }

    public ResourceLock getResourceLock(final String sharedResource, final int leases, final Thread ownerThread) {
        String displayName = "Lease of " + leases + " for " + sharedResource + " by " + ownerThread;
        return getOrRegisterResourceLock(displayName, new ResourceLockProducer<String, SharedResourceLease>() {
            @Override
            public SharedResourceLease create(String displayName, ResourceLockCoordinationService coordinationService, Action<ResourceLock> lockAction, Action<ResourceLock> unlockAction) {
                return new SharedResourceLease(displayName, coordinationService, lockAction, unlockAction, sharedResource, leases, ownerThread);
            }
        });
    }

    public class SharedResourceLease extends AbstractTrackedResourceLock {
        private final int leases;
        private final Semaphore semaphore;
        private final Thread ownerThread;
        private boolean active = false;

        SharedResourceLease(String displayName, ResourceLockCoordinationService coordinationService, Action<ResourceLock> lockAction, Action<ResourceLock> unlockAction, String sharedResource, int leases, Thread ownerThread) {
            super(displayName, coordinationService, lockAction, unlockAction);
            this.leases = leases;
            this.semaphore = sharedResources.get(sharedResource);
            this.ownerThread = ownerThread;
        }

        @Override
        protected boolean acquireLock() {
            if (semaphore.tryAcquire(leases)) {
                active = true;
            }

            return active;
        }

        @Override
        protected void releaseLock() {
            if (Thread.currentThread() != ownerThread) {
                throw new UnsupportedOperationException("Lock cannot be released from non-owner thread.");
            }

            semaphore.release(leases);
            active = false;
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
