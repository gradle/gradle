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

public class ExclusiveAccessResourceLock extends AbstractTrackedResourceLock {
    private Thread owner;

    public ExclusiveAccessResourceLock(String displayName, ResourceLockCoordinationService coordinationService, ResourceLockContainer owner) {
        super(displayName, coordinationService, owner);
    }

    @Override
    protected boolean acquireLock() {
        Thread currentThread = Thread.currentThread();
        if (owner == currentThread) {
            return true;
        }
        if (owner == null && canAcquire()) {
            owner = currentThread;
            return true;
        }
        return false;
    }

    protected boolean canAcquire() {
        return true;
    }

    @Override
    protected void releaseLock() {
        owner = null;
    }

    @Override
    protected boolean doIsLockedByCurrentThread() {
        return owner == Thread.currentThread();
    }

    @Override
    protected boolean doIsLocked() {
        return owner != null;
    }
}
