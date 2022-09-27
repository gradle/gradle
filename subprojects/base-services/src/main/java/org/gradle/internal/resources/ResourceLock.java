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

import org.gradle.api.Describable;

/**
 * Represents a lock on an abstract resource.  Implementations of this interface should see that methods fail if they are called
 * outside of a {@link ResourceLockCoordinationService#withStateLock(org.gradle.api.Transformer)} transform.
 */
public interface ResourceLock extends Describable {
    enum Kind {
        SHARED_BUILD_SERVICE,
        UNSPECIFIC
    }

    /**
     * The name that identifies this lock.
     *
     * @return the name of this lock, or null
     */
    String getName();

    /**
     * The kind of this lock.
     *
     * @return the kind of this lock, or {@link Kind#UNSPECIFIC}
     */
    ResourceLock.Kind getKind();

    /**
     * Returns true if this resource is locked by any thread.
     *
     * @return true if any thread holds the lock for this resource
     */
    boolean isLocked();

    /**
     * Returns true if the current thread holds a lock on this resource.  Returns false otherwise.
     *
     * @return true if the task for this operation holds the lock for this resource.
     */
    boolean isLockedByCurrentThread();

    /**
     * Attempt to lock this resource, if not already.  Does not block.
     *
     * @return true if resource is now locked, false otherwise.
     */
    boolean tryLock();

    /**
     * Unlock this resource if it's held by the calling thread.
     */
    void unlock();
}
