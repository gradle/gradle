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

import java.io.Closeable;
import java.io.File;

public interface FileLock extends Closeable, FileAccess {
    /**
     * Returns true if the most recent mutation method ({@link #updateFile(Runnable)} or {@link #writeFile(Runnable)} attempted by any process succeeded
     * (ie a process did not crash while updating the target file).
     *
     * Returns false if no mutation method has been called for the target file.
     */
    boolean getUnlockedCleanly();

    /**
     * Informs if this lock is currently acquired by the same process as the when it was acquired previously.
     * If single process keeps acquiring and releasing this lock, the method returns false.
     * If different processes interleave acquiring and releasing this lock, the method may return true.
     * Returns true if there was not previous owner.
     */
    boolean getHasNewOwner();

    /**
     * Returns true if the given file is used by this lock.
     */
    boolean isLockFile(File file);

    /**
     * Closes this lock, releasing the lock and any resources associated with it.
     */
    void close();

    /**
     * The actual mode of the lock. May be different to what was requested.
     */
    FileLockManager.LockMode getMode();

    /**
     * @return unique id of this lock
     */
    long getLockId();
}
