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
     * Get a lock for the specified project.
     *
     * @param projectPath
     * @return the requested {@link ProjectLock}
     */
    ProjectLock getProjectLock(String projectPath);

    /**
     * Returns true if the current thread holds a lock on any project.  Returns false otherwise, including if
     * this method is called outside of {@link ProjectLock#withProjectLock(Runnable)}.
     *
     * @return true if the task for this operation holds the lock for any project.
     */
    boolean hasProjectLock();

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
     * Releases all project locks held by the current thread and executes the {@link Runnable}.  Upon completion of the
     * {@link Runnable}, if a lock was held at the time the method was called, then it will be reacquired.  If no locks were held at the
     * time the method was called, then no attempt will be made to reacquire a lock on completion.  While blocking to reacquire the project
     * lock, all worker leases held by the thread will be released and reacquired once the project lock is obtained.
     *
     * @param runnable
     */
    void withoutProjectLock(Runnable runnable);
}
