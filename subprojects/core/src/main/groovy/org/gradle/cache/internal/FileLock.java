/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.cache.internal;

import java.io.File;
import java.util.concurrent.Callable;

public interface FileLock {
    /**
     * Returns true if the most recent {@link #writeToFile(Runnable)} succeeded (ie a process did not crash while updating the target file)
     */
    boolean getUnlockedCleanly();

    /**
     * Returns true if the given file is used by this lock.
     */
    boolean isLockFile(File file);

    /**
     * Runs the given action under a shared or exclusive lock.
     *
     * <p>If an exclusive or shared lock is already held, the lock level is not changed and the action is simply executed. If no lock is already held, a shared lock is acquired,
     * the action executed, and the lock released.
     */
    <T> T readFromFile(Callable<T> action) throws LockTimeoutException;

    /**
     * Runs the given action under an exclusive lock. If the given action fails, the lock is marked as uncleanly unlocked.
     *
     * <p>If an exclusive lock is already held, the lock level is not changed and the action is simply executed. If a shared lock is already held, the lock is escalated to an
     * exclusive lock, and reverted back to a shared lock when the action completes. If no lock is already held, an exclusive lock is acquired, the action executed, and the lock
     * released.
     */
    void writeToFile(Runnable action) throws LockTimeoutException;

    /**
     * Closes this lock, releasing the lock and any resources associated with it.
     */
    void close();
}
