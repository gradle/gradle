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

import java.util.Collection;

public interface ResourceLockRegistry {
    /**
     * Get all of the resource locks held by the current thread.
     */
    Collection<? extends ResourceLock> getResourceLocksByCurrentThread();

    /**
     * Returns true if the registry has any locks that are being held by a thread.
     *
     * @return true if any locks in the registry are currently held.
     */
    boolean hasOpenLocks();
}
