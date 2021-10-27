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

import org.gradle.internal.Factory;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.util.Path;

import java.util.Collection;

@ServiceScope(Scopes.BuildSession.class)
public interface ProjectLeaseRegistry {
    /**
     * Get a lock for the specified project.
     */
    ResourceLock getProjectLock(Path buildIdentityPath, Path projectIdentityPath);

    /**
     * Returns any projects locks currently held by this thread.
     */
    Collection<? extends ResourceLock> getCurrentProjectLocks();

    /**
     * Releases any project locks currently held by this thread.
     */
    void releaseCurrentProjectLocks();

    /**
     * Returns {@code true} when this registry grants multiple threads access to projects (but no more than one thread per given project)
     * and {@code false} when this registry grants only a single thread access to projects at any given time.
     */
    boolean getAllowsParallelExecution();

    /**
     * Releases all project locks held by the current thread and executes the {@link Factory}.  Upon completion of the
     * {@link Factory}, if a lock was held at the time the method was called, then it will be reacquired.  If no locks were held at the
     * time the method was called, then no attempt will be made to reacquire a lock on completion.  While blocking to reacquire the project
     * lock, all worker leases held by the thread will be released and reacquired once the project lock is obtained.
     */
    <T> T withoutProjectLock(Factory<T> action);

    /**
     * Releases all project locks held by the current thread and executes the {@link Runnable}.  Upon completion of the
     * {@link Runnable}, if a lock was held at the time the method was called, then it will be reacquired.  If no locks were held at the
     * time the method was called, then no attempt will be made to reacquire a lock on completion.  While blocking to reacquire the project
     * lock, all worker leases held by the thread will be released and reacquired once the project lock is obtained.
     */
    void withoutProjectLock(Runnable action);

    /**
     * Allows the given code to access the mutable state of any project, regardless of which other threads may be accessing the project.
     *
     * DO NOT USE THIS METHOD. It is here to allow some very specific backwards compatibility.
     */
    <T> T allowUncontrolledAccessToAnyProject(Factory<T> factory);

    boolean isAllowedUncontrolledAccessToAnyProject();

    /**
     * Performs some blocking action. If the current thread is allowed to make changes to project locks, then release all locks
     * then run the action and reacquire any locks.
     * If the current thread is not allowed to make changes to the project locks (via {@link #whileDisallowingProjectLockChanges(Factory)},
     * then it is safe to run the action without releasing the locks.
     */
    void blocking(Runnable action);

    /**
     * Runs the given action and disallows the current thread from attempting to acquire or release any project locks.
     * Applying this constraint means that the thread will not block waiting for a project lock and cause a deadlock.
     * This constraint also means that it does not need to release its project locks when it needs to block while waiting for some operation to complete.
     *
     * <p>While in this method, calls to {@link #blocking(Runnable)} will not cause this thread to release or reacquire any project locks.</p>
     *
     * <p>This should become the default and only behaviour for all worker threads, and locks are acquired and released only when starting and finishing an execution node.
     * For now, this is an opt-in behaviour.</p>
     */
    <T> T whileDisallowingProjectLockChanges(Factory<T> action);
}
