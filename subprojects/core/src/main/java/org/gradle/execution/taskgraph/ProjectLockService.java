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

package org.gradle.execution.taskgraph;

public interface ProjectLockService {
    /**
     * Returns true if the given project has been locked by any thread.
     *
     * @param projectPath
     * @return true if any task has locked the project
     */
    boolean isLocked(String projectPath);

    /**
     * Returns true if the current thread holds a lock on a project.  Returns false otherwise, including if
     * this method is called outside of {@link #withProjectLock(String, Runnable)}.
     *
     * @return true if the task for this operation holds the lock for its project.
     */
    boolean hasLock();

    /**
     * Locks the given project for the thread.  Does not block and returns true only if the lock was successfully acquired.
     *
     * @param projectPath
     * @return true if the lock has been acquired, false otherwise.
     */
    boolean lockProject(String projectPath);

    /**
     * Releases any lock acquired by this thread on the given project.
     *
     * @param projectPath
     */
    void unlockProject(String projectPath);

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
     * Acquires a lock for the project and executes the {@link Runnable}.  Upon completion of the {@link Runnable},
     * the lock will be released.
     *
     * @param projectPath
     * @param runnable
     * @throws UnsupportedOperationException If the current thread is already associated with a lock on another project.  For instance,
     * if this method is called inside another call for a different project.
     */
    void withProjectLock(String projectPath, Runnable runnable);

    /**
     * Release any lock for the project associated with the current thread and executes the {@link Runnable}.  Upon completion of the
     * {@link Runnable}, if a lock was held at the time the method was called, then it will be reacquired.  If no lock was held at the
     * time the method was called, then no attempt will be made to acquire a lock on completion.
     *
     * @param runnable
     */
    void withoutProjectLock(Runnable runnable);
}
