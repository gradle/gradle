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

package org.gradle.internal.resources

import org.gradle.api.Action

import java.util.concurrent.atomic.AtomicBoolean


class TestTrackedResourceLock extends AbstractTrackedResourceLock {
    final AtomicBoolean lockedState = new AtomicBoolean()
    final AtomicBoolean hasLock = new AtomicBoolean()

    TestTrackedResourceLock(String displayName, ResourceLockCoordinationService coordinationService, Action<ResourceLock> lockAction, Action<ResourceLock> unlockAction) {
        super(displayName, coordinationService, lockAction, unlockAction)
    }

    TestTrackedResourceLock(String displayName, ResourceLockCoordinationService coordinationService, Action<ResourceLock> lockAction, Action<ResourceLock> unlockAction, boolean lockedState) {
        this(displayName, coordinationService, lockAction, unlockAction, lockedState, false)
    }

    TestTrackedResourceLock(String displayName, ResourceLockCoordinationService coordinationService, Action<ResourceLock> lockAction, Action<ResourceLock> unlockAction, boolean lockedState, boolean hasLock) {
        super(displayName, coordinationService, lockAction, unlockAction)
        this.lockedState.set(lockedState)
        if (lockedState) {
            this.hasLock.set(hasLock)
        }
    }

    @Override
    boolean doIsLocked() {
        return lockedState.get()
    }

    @Override
    boolean doIsLockedByCurrentThread() {
        return hasLock.get()
    }

    @Override
    protected boolean acquireLock() {
        if (!lockedState.get()) {
            hasLock.set(true)
            lockedState.set(true)
            return true
        } else {
            return false
        }
    }

    @Override
    protected void releaseLock() {
        hasLock.set(false)
        lockedState.set(false)
    }

    boolean getLockedState() {
        return lockedState.get()
    }

    void setLockedState(boolean lockedState) {
        this.lockedState.set(lockedState)
    }
}
