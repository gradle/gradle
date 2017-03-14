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

package org.gradle.internal.work;

public interface ProjectLockService {
    /**
     * Returns true if the given project has been locked by any thread.
     *
     * @param projectPath
     * @return true if any task has locked the project
     */
    boolean isProjectLocked(String projectPath);

    /**
     * Returns true if the current thread holds a lock on a project.  Returns false otherwise, including if
     * this method is called outside of {@link #withProjectLock(String, Runnable)}.
     *
     * @return true if the task for this operation holds the lock for any project.
     */
    boolean hasProjectLock();

    /**
     * Returns true if the current thread holds a lock on the specified project.  Returns false otherwise, including if
     * this method is called outside of {@link #withProjectLock(String, Runnable)}.
     *
     * @return true if the task for this operation holds the lock for the specified project.
     */
    boolean hasProjectLock(String projectPath);

    /**
     * Add a listener to respond any time a project is unlocked.
     *
     * @param projectLockListener
     */
    void addListener(ProjectLockListener projectLockListener);

    /**
     * Remove the given listener.
     *
     * @param projectLockListener
     */
    void removeListener(ProjectLockListener projectLockListener);

    /**
     * Attempts to acquire a non-blocking lock for the project and execute the {@link Runnable}.  Upon completion of the {@link Runnable},
     * the lock will be released.  If a lock cannot be acquired, this method immediately returns false without
     * executing the {@link Runnable}.
     *
     * @param projectPath
     * @param runnable
     * @return true if a lock was acquired and the {@link Runnable} was executed.  False if the lock could not be acquired and the
     * {@link Runnable} was not executed.
     */
    boolean tryWithProjectLock(String projectPath, Runnable runnable);

    /**
     * Blocks until a lock for the project can be acquired and executes the {@link Runnable}.  Upon completion of the {@link Runnable},
     * the lock will be released.  While blocking to acquire the project lock, all worker leases held by the thread will be released and
     * reacquired once the project lock is obtained.
     *
     * @param projectPath
     * @param runnable
     */
    void withProjectLock(String projectPath, Runnable runnable);

    /**
     * Releases all project locks held by the current thread and executes the {@link Runnable}.  Upon completion of the
     * {@link Runnable}, if a lock was held at the time the method was called, then it will be reacquired.  If no locks were held at the
     * time the method was called, then no attempt will be made to reacquire a lock on completion.  While blocking to reacquire the project
     * lock, all worker leases held by the thread will be released and reacquired once the project lock is obtained.
     *
     * @param runnable
     */
    void withoutProjectLock(Runnable runnable);
}
