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

public interface ProjectLock {
    /**
     * Returns true if this project is locked by any thread.
     *
     * @return true if any thread holds the lock for this project
     */
    boolean isLocked();

    /**
     * Returns true if the current thread holds a lock on a project.  Returns false otherwise, including if
     * this method is called outside of {@link #withProjectLock(Runnable)}.
     *
     * @return true if the task for this operation holds the lock for any project.
     */
    boolean hasProjectLock();

    /**
     * Attempts to acquire a non-blocking lock for the project and execute the {@link Runnable}.  Upon completion of the {@link Runnable},
     * the lock will be released.  If a lock cannot be acquired, this method immediately returns false without
     * executing the {@link Runnable}.
     *
     * @param runnable
     * @return true if a lock was acquired and the {@link Runnable} was executed.  False if the lock could not be acquired and the
     * {@link Runnable} was not executed.
     */
    boolean tryWithProjectLock(Runnable runnable);

    /**
     * Blocks until a lock for the project can be acquired and executes the {@link Runnable}.  Upon completion of the {@link Runnable},
     * the lock will be released.  While blocking to acquire the project lock, all worker leases held by the thread will be released and
     * reacquired once the project lock is obtained.
     *
     * @param runnable
     */
    void withProjectLock(Runnable runnable);

    /**
     * Returns the path of the project associated with this lock.
     */
    String getProjectPath();
}
