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

import org.gradle.internal.progress.BuildOperationExecutor.Operation;

public interface ProjectLockService {
    /**
     * Returns true if the given project has been locked by any task.
     *
     * @param projectPath
     * @return true if any task has locked the project
     */
    boolean isLocked(String projectPath);

    /**
     * Returns true if the task associated with the given operation holds the lock for its project.  Returns false otherwise, including if
     * this method is called outside of {@link #withProjectLock(String, Operation, Runnable)}.
     *
     * @param operation
     * @return true if the task for this operation holds the lock for its project.
     */
    boolean hasLock(Operation operation);

    /**
     * Locks the given project for the thread, but does not associate it to an operation.  Use this when a project needs to be locked, but the operation
     * is not known yet.  Does not block and returns true only if the lock was successfully acquired.
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
     * Acquires a lock for the project associated with the given operation and executes the {@link Runnable}.  Upon completion of the {@link Runnable},
     * the lock will be released.
     *
     * @param projectPath
     * @param operation
     * @param runnable
     */
    void withProjectLock(String projectPath, Operation operation, Runnable runnable);

    /**
     * Release any lock for the project associated with the given operation and executes the {@link Runnable}.  Upon completion of the
     * {@link Runnable}, if a lock was held at the time the method was called, then it will be reacquired.  If no lock was held at the
     * time the method was called, then no attempt will be made to acquire a lock on completion.
     *
     * @param operation
     * @param runnable
     */
    void withoutProjectLock(Operation operation, Runnable runnable);
}
