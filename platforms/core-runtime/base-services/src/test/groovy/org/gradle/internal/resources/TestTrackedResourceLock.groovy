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


import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class TestTrackedResourceLock extends AbstractTrackedResourceLock {
    final AtomicBoolean lockedState = new AtomicBoolean()
    final AtomicReference<Thread> owner = new AtomicReference<>()

    TestTrackedResourceLock(String displayName, ResourceLockCoordinationService coordinationService, ResourceLockContainer owner) {
        super(displayName, coordinationService, owner)
    }

    TestTrackedResourceLock(String displayName, ResourceLockCoordinationService coordinationService, ResourceLockContainer owner, boolean lockedState, boolean hasLock) {
        super(displayName, coordinationService, owner)
        this.lockedState.set(lockedState)
        if (hasLock) {
            this.owner.set(Thread.currentThread())
        }
    }

    @Override
    boolean doIsLocked() {
        return lockedState.get()
    }

    @Override
    boolean doIsLockedByCurrentThread() {
        return owner.get() == Thread.currentThread()
    }

    @Override
    protected boolean acquireLock() {
        if (!lockedState.get()) {
            owner.set(Thread.currentThread())
            lockedState.set(true)
            return true
        } else {
            return false
        }
    }

    @Override
    protected void releaseLock() {
        owner.set(null)
        lockedState.set(false)
    }

    boolean getLockedState() {
        return lockedState.get()
    }

    void setLockedState(boolean lockedState) {
        this.lockedState.set(lockedState)
    }
}
