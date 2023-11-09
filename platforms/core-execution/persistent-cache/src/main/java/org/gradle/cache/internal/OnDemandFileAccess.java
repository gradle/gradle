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

import org.gradle.cache.FileIntegrityViolationException;
import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.LockTimeoutException;
import org.gradle.cache.internal.filelock.LockOptionsBuilder;
import org.gradle.internal.Factory;

import java.io.File;

public class OnDemandFileAccess extends AbstractFileAccess {
    private final String displayName;
    private final FileLockManager manager;
    private final File targetFile;

    public OnDemandFileAccess(File targetFile, String displayName, FileLockManager manager) {
        this.targetFile = targetFile;
        this.displayName = displayName;
        this.manager = manager;
    }

    @Override
    public <T> T readFile(Factory<? extends T> action) throws LockTimeoutException, FileIntegrityViolationException {
        FileLock lock = manager.lock(targetFile, new LockOptionsBuilder(FileLockManager.LockMode.Shared), displayName);
        try {
            return lock.readFile(action);
        } finally {
            lock.close();
        }
    }

    @Override
    public void updateFile(Runnable action) throws LockTimeoutException, FileIntegrityViolationException {
        FileLock lock = manager.lock(targetFile, new LockOptionsBuilder(FileLockManager.LockMode.Exclusive), displayName);
        try {
            lock.updateFile(action);
        } finally {
            lock.close();
        }
    }

    @Override
    public void writeFile(Runnable action) throws LockTimeoutException {
        FileLock lock = manager.lock(targetFile, new LockOptionsBuilder(FileLockManager.LockMode.Exclusive), displayName);
        try {
            lock.writeFile(action);
        } finally {
            lock.close();
        }
    }
}
