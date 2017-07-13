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

import org.gradle.api.Transformer;

public interface ResourceLockCoordinationService {
    /**
     * Gets the current {@link ResourceLockState} active in this thread.  This must be called in the context
     * of a {@link #withStateLock(Transformer)} transform.
     *
     * @return the current {@link ResourceLockState} or null if not in a transform.
     */
    ResourceLockState getCurrent();

    /**
     * Attempts to atomically change the state of resource locks.  Only one thread can alter the resource lock
     * states at one time.  Other threads will block until the resource lock state is free.  The provided
     * {@link Transformer} should return a {@link org.gradle.internal.resources.ResourceLockState.Disposition}
     * that tells the resource coordinator how to proceed:
     *
     * FINISHED - All locks were acquired, release the state lock
     * FAILED - One or more locks were not acquired, roll back any locks that were acquired and release the state lock
     * RETRY - One or more locks were not acquired, roll back any locks that were acquired and block waiting for the
     * state to change, then run the transform again
     *
     * @param stateLockAction
     *
     * @return true if the lock state changes finished successfully, otherwise false.
     */
    boolean withStateLock(Transformer<ResourceLockState.Disposition, ResourceLockState> stateLockAction);

    /**
     * Notify other threads about changes to resource locks.
     */
    void notifyStateChange();
}
