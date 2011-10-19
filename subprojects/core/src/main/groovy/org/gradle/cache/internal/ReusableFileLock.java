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

public class ReusableFileLock implements ManualFileLock {
    private final String displayName;
    private final FileLockManager manager;
    private final File targetFile;
    private FileLock lock;

    public ReusableFileLock(File targetFile, String displayName, FileLockManager manager) {
        this.targetFile = targetFile;
        this.displayName = displayName;
        this.manager = manager;
    }

    public void lock() {
        lock = manager.lock(targetFile, FileLockManager.LockMode.Exclusive, displayName);
    }

    public void unlock() {
        lock.close();
        lock = null;
    }

    public void close() {
    }

    public boolean getUnlockedCleanly() {
        throw new UnsupportedOperationException();
    }

    public boolean isLockFile(File file) {
        throw new UnsupportedOperationException();
    }

    public <T> T readFromFile(Callable<T> action) throws LockTimeoutException {
        return getLock().readFromFile(action);
    }

    public void writeToFile(Runnable action) throws LockTimeoutException {
        getLock().writeToFile(action);
    }

    private FileLock getLock() {
        if (lock == null) {
            throw new IllegalStateException("CacheFileLock must be explicitly locked");
        }
        return new NoOpFileLock();
    }
}
