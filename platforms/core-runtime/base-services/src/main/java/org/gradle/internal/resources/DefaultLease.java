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

public class DefaultLease extends AbstractTrackedResourceLock {
    private final LeaseHolder parent;
    private Thread ownerThread;

    public DefaultLease(String displayName, ResourceLockCoordinationService coordinationService, ResourceLockContainer owner, LeaseHolder parent) {
        super(displayName, coordinationService, owner);
        this.parent = parent;
    }

    @Override
    protected boolean doIsLocked() {
        return ownerThread != null;
    }

    @Override
    protected boolean doIsLockedByCurrentThread() {
        return Thread.currentThread() == ownerThread;
    }

    @Override
    protected boolean acquireLock() {
        if (parent.grantLease()) {
            ownerThread = Thread.currentThread();
        }
        return ownerThread != null;
    }

    @Override
    protected void releaseLock() {
        if (Thread.currentThread() != ownerThread) {
            // Not implemented - not yet required. Please implement if required
            throw new UnsupportedOperationException("Must complete operation from owner thread.");
        }
        parent.releaseLease();
        ownerThread = null;
    }
}
