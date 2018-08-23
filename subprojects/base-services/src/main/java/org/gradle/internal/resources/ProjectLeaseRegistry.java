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

import org.gradle.util.Path;

import java.util.Collection;
import java.util.concurrent.Callable;

public interface ProjectLeaseRegistry {
    /**
     * Get a lock for the specified project.
     *
     * @param gradleIdentityPath
     * @param projectIdentityPath
     * @return the requested {@link ResourceLock}
     */
    ResourceLock getProjectLock(Path gradleIdentityPath, Path projectIdentityPath);

    /**
     * Get a lock for the specified project.
     *
     * @param projectIdentityPath
     * @return the requested {@link ResourceLock}
     */
    ResourceLock getProjectLock(Path projectIdentityPath);

    /**
     * Releases all project locks held by the current thread and executes the {@link Callable}.  Upon completion of the
     * {@link Callable}, if a lock was held at the time the method was called, then it will be reacquired.  If no locks were held at the
     * time the method was called, then no attempt will be made to reacquire a lock on completion.  While blocking to reacquire the project
     * lock, all worker leases held by the thread will be released and reacquired once the project lock is obtained.
     */
    <T> T withoutProjectLock(Callable<T> action);

    /**
     * Releases all project locks held by the current thread and executes the {@link Runnable}.  Upon completion of the
     * {@link Runnable}, if a lock was held at the time the method was called, then it will be reacquired.  If no locks were held at the
     * time the method was called, then no attempt will be made to reacquire a lock on completion.  While blocking to reacquire the project
     * lock, all worker leases held by the thread will be released and reacquired once the project lock is obtained.
     */
    void withoutProjectLock(Runnable action);

    /**
     * Returns any projects locks currently held by this thread.
     */
    Collection<? extends ResourceLock> getCurrentProjectLocks();
}
