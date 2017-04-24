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

import org.gradle.api.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTrackedResourceLock implements ResourceLock {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTrackedResourceLock.class);

    private final String displayName;

    private final ResourceLockCoordinationService coordinationService;
    private final Action<ResourceLock> lockAction;
    private final Action<ResourceLock> unlockAction;
    private Thread lockingThread;
    private Thread owner;

    public AbstractTrackedResourceLock(String displayName, ResourceLockCoordinationService coordinationService, Action<ResourceLock> lockAction, Action<ResourceLock> unlockAction) {
        this.displayName = displayName;
        this.coordinationService = coordinationService;
        this.lockAction = lockAction;
        this.unlockAction = unlockAction;
    }

    @Override
    public boolean tryLock(Thread owner) {
        failIfNotInResourceLockStateChange();
        if (!isLockedByThread(owner)) {
            if (acquireLock()) {
                LOGGER.debug("{}: acquired lock on {}", owner.getName(), displayName);
                this.lockingThread = Thread.currentThread();
                this.owner = owner;
                lockAction.execute(this);
                coordinationService.getCurrent().registerLocked(this);
                return true;
            } else {
                return false;
            }
        } else {
            return true;
        }
    }

    @Override
    public boolean tryLock() {
        return tryLock(Thread.currentThread());
    }

    @Override
    public void unlock() {
        failIfNotInResourceLockStateChange();
        if (lockingThread == Thread.currentThread() || isLockedByThread(Thread.currentThread())) {
            releaseLock();
            LOGGER.debug("{}: released lock on {}", Thread.currentThread().getName(), displayName);
            unlockAction.execute(this);
            owner = null;
            lockingThread = null;
            coordinationService.notifyStateChange();
        }
    }

    @Override
    public boolean isLocked() {
        failIfNotInResourceLockStateChange();
        return doIsLocked();
    }

    boolean isLockedByThread(Thread owner) {
        return owner == getOwner();
    }

    @Override
    public boolean isLockedByCurrentThread() {
        failIfNotInResourceLockStateChange();
        return isLockedByThread(Thread.currentThread());
    }

    @Override
    public Thread getOwner() {
        return owner;
    }

    @Override
    public Thread getLockingThread() {
        return lockingThread;
    }

    private void failIfNotInResourceLockStateChange() {
        if (coordinationService.getCurrent() == null) {
            throw new IllegalStateException("No ResourceLockState is associated with this thread.");
        }
    }

    abstract protected boolean acquireLock();

    abstract protected void releaseLock();

    abstract protected boolean doIsLocked();

    @Override
    public String getDisplayName() {
        return displayName;
    }
}
