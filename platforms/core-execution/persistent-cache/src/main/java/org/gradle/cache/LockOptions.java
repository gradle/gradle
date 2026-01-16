/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.cache;

public interface LockOptions {

    FileLockManager.LockMode getMode();

    boolean isUseCrossVersionImplementation();

    /**
     * Ensures that the acquired lock represents a file state on the file system.
     * <p>
     * This safeguard is necessary for safe lock cleanup.
     * </p>
     *
     * <p>
     * Example Scenario:
     * <ol>
     * <li>Process 1 acquires the lock. Process 2 attempts to acquire it and waits.</li>
     * <li>Process 1 deletes the lock file from the disk, then releases the lock.</li>
     * <li>Process 2 then acquires the lock.</li>
     * </ol>
     *
     * At this point, Process 2 holds a lock on a file that no longer exists on the disk.
     * This option prevents this invalid lock state.
     *
     * <p>This option is not supported for shared or cross-version locks.</p>
     */
    boolean isEnsureAcquiredLockRepresentsStateOnFileSystem();

    /**
     * Creates a copy of this options instance using the given mode.
     *
     * @param mode the mode to overwrite the current mode with
     */
    LockOptions copyWithMode(FileLockManager.LockMode mode);
}
