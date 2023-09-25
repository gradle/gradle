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
package org.gradle.cache;

import java.io.Closeable;
import java.io.File;

public interface FileLock extends Closeable, FileAccess {
    /**
     * Returns true if the most recent mutation method ({@link #updateFile(Runnable)} or {@link #writeFile(Runnable)} attempted by any process succeeded
     * (ie a process did not crash while updating the target file).
     *
     * Returns false if no mutation method has ever been called for the target file.
     */
    boolean getUnlockedCleanly();

    /**
     * Returns true if the given file is used by this lock.
     */
    boolean isLockFile(File file);

    /**
     * Closes this lock, releasing the lock and any resources associated with it.
     */
    @Override
    void close();

    /**
     * Returns some memento of the current state of this target file.
     */
    State getState();

    /**
     * The actual mode of the lock. May be different to what was requested.
     */
    FileLockManager.LockMode getMode();

    /**
     * An immutable snapshot of the state of a lock.
     */
    interface State {
        boolean canDetectChanges();
        boolean isInInitialState();
        boolean hasBeenUpdatedSince(State state);
    }
}
