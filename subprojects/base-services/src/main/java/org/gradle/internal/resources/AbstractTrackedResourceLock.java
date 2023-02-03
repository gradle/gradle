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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractTrackedResourceLock implements ResourceLock {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractTrackedResourceLock.class);

    private final String displayName;

    private final ResourceLockCoordinationService coordinationService;
    private final ResourceLockContainer owner;

    public AbstractTrackedResourceLock(String displayName, ResourceLockCoordinationService coordinationService, ResourceLockContainer owner) {
        this.displayName = displayName;
        this.coordinationService = coordinationService;
        this.owner = owner;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + getDisplayName();
    }

    @Override
    public boolean tryLock() {
        if (!isLockedByCurrentThread()) {
            if (acquireLock()) {
                LOGGER.debug("{}: acquired lock on {}", Thread.currentThread().getName(), displayName);
                try {
                    owner.lockAcquired(this);
                } catch (RuntimeException e) {
                    releaseLock();
                    throw e;
                }
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
    public void unlock() {
        if (isLockedByCurrentThread()) {
            releaseLock();
            LOGGER.debug("{}: released lock on {}", Thread.currentThread().getName(), displayName);
            try {
                owner.lockReleased(this);
            } finally {
                coordinationService.getCurrent().registerUnlocked(this);
            }
        }
    }

    @Override
    public boolean isLocked() {
        failIfNotInResourceLockStateChange();
        return doIsLocked();
    }

    @Override
    public boolean isLockedByCurrentThread() {
        failIfNotInResourceLockStateChange();
        return doIsLockedByCurrentThread();
    }

    private void failIfNotInResourceLockStateChange() {
        if (coordinationService.getCurrent() == null) {
            throw new IllegalStateException("No ResourceLockState is associated with this thread.");
        }
    }

    abstract protected boolean acquireLock();

    abstract protected void releaseLock();

    abstract protected boolean doIsLocked();

    abstract protected boolean doIsLockedByCurrentThread();

    @Override
    public String getDisplayName() {
        return displayName;
    }
}
