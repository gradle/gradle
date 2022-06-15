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
     * Get the lock for the state of all projects of the given build. This lock provides exclusive access to the state of all projects in the build.While this lock is held, no project state locks can be held.
     */
    ResourceLock getAllProjectsLock(Path buildIdentityPath);

    /**
     * Get a lock for access to the specified project's state.
     */
    ResourceLock getProjectLock(Path buildIdentityPath, Path projectIdentityPath);

    /**
     * Get a lock for non-isolated tasks for the specified project.
     */
    ResourceLock getTaskExecutionLock(Path buildIdentityPath, Path projectIdentityPath);

    /**
     * Returns any project state locks currently held by this thread.
     *
     * Note: may contain either locks for specific projects (returned by {@link #getProjectLock(Path, Path)}) or the lock for all projects (returned by {@link #getAllProjectsLock(Path)}.
     */
    Collection<? extends ResourceLock> getCurrentProjectLocks();

    /**
     * Releases any project state locks or task execution locks currently held by this thread, allowing the current
     * thread to run as if it were executing an isolated task.
     *
     * Does not release worker lease.
     */
    void runAsIsolatedTask();

    /**
     * Returns {@code true} when this registry grants multiple threads access to projects (but no more than one thread per given project)
     * and {@code false} when this registry grants only a single thread access to projects at any given time.
     */
    boolean getAllowsParallelExecution();

    /**
     * Releases any project state locks or task execution locks currently held by the current thread and executes the {@link Factory}.
     * Upon completion of the {@link Factory}, if a lock was held at the time the method was called, then it will be reacquired.
     * If no locks were held at the time the method was called, then no attempt will be made to reacquire a lock on completion.
     * While blocking to reacquire the project lock, all worker leases held by the thread will be released and reacquired once the project lock is obtained.
     */
    <T> T runAsIsolatedTask(Factory<T> action);

    /**
     * Releases any project state locks or task execution locks currently held by the current thread and executes the {@link Factory}.
     * Upon completion of the {@link Runnable}, if a lock was held at the time the method was called, then it will be reacquired.
     * If no locks were held at the time the method was called, then no attempt will be made to reacquire a lock on completion.
     * While blocking to reacquire the project lock, all worker leases held by the thread will be released and reacquired once the project lock is obtained.
     */
    void runAsIsolatedTask(Runnable action);

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
     * then it is safe to run the action without releasing the project locks. The worker lease is, however, released prior to running the
     * action and reacquired at the end.
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
