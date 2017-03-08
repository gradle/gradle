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

import org.gradle.api.Task;
import org.gradle.internal.progress.BuildOperationExecutor.Operation;

public interface ProjectLockService {
    /**
     * Locks the project associated with the given task.  Blocks if the lock is currently held by another task.
     *
     * @param task
     */
    void lockProject(Task task);

    /**
     * Locks the project for the task associated with the given operation.  Blocks if the lock is currently held by another task.
     *
     * @param operation
     * @throws IllegalStateException if this is called outside of {@link #withProjectLock(Task, Operation, Runnable)}
     */
    void lockProject(Operation operation);

    /**
     * Unlocks the project associated with the given task
     *
     * @param task
     * @return true if the task held the lock, false otherwise
     * @throws IllegalMonitorStateException if called from a thread other than the one that locked the project for this task
     */
    boolean unlockProject(Task task);

    /**
     * Unlocks the project for the task associated with the given operation.
     *
     * @param operation
     * @return true if the task held the lock, false otherwise
     * @throws IllegalStateException if this is called outside of {@link #withProjectLock(Task, Operation, Runnable)}
     * @throws IllegalMonitorStateException if called from a thread other than the one that locked the project for the task associated with the operation
     */
    boolean unlockProject(Operation operation);

    /**
     * Returns true if the given project has been locked by any task.
     *
     * @param projectPath
     * @return true if any task has locked the project
     */
    boolean isLocked(String projectPath);

    /**
     * Returns true if the given task holds the lock for its associated project.
     *
     * @param task
     * @return true if the given task holds the lock for its project
     */
    boolean hasLock(Task task);

    /**
     * Returns true if the task associated with the given operation holds the lock for its project.  Returns false otherwise, including if
     * this method is called outside of {@link #withProjectLock(Task, Operation, Runnable)}.
     *
     * @param operation
     * @return true if the task for this operation holds the lock for its project.
     */
    boolean hasLock(Operation operation);

    /**
     * Acquires a lock for the project associated with the given task and executes the {@link Runnable}.  Upon completion of the {@link Runnable},
     * the lock will be released.  Inside the {@link Runnable} calls to {@link #lockProject(Operation)} and {@link #unlockProject(Operation)} will
     * associate the given operation to the given task and any locks will be associated with the task.
     *
     * @param task
     * @param operation
     * @param runnable
     */
    void withProjectLock(Task task, Operation operation, Runnable runnable);

    /**
     * Clears all project locks.
     */
    void clear();
}
