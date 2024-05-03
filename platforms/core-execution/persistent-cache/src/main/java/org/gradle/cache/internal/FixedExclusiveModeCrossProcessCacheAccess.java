/*
 * Copyright 2014 the original author or authors.
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

import com.google.common.util.concurrent.Runnables;
import org.gradle.cache.CrossProcessCacheAccess;
import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.LockOptions;
import org.gradle.internal.UncheckedException;

import java.io.File;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.gradle.cache.FileLockManager.LockMode.Exclusive;

/**
 * A {@link CrossProcessCacheAccess} implementation used when a cache is opened with an exclusive lock that is held until the cache is closed. This implementation is simply a no-op for these methods.
 */
public class FixedExclusiveModeCrossProcessCacheAccess extends AbstractCrossProcessCacheAccess {
    private final String cacheDisplayName;
    private final File lockTarget;
    private final LockOptions lockOptions;
    private final FileLockManager lockManager;
    private final CacheInitializationAction initializationAction;
    private final Consumer<FileLock> onOpenAction;
    private final Consumer<FileLock> onCloseAction;
    private FileLock fileLock;

    public FixedExclusiveModeCrossProcessCacheAccess(String cacheDisplayName, File lockTarget, LockOptions lockOptions, FileLockManager lockManager, CacheInitializationAction initializationAction, Consumer<FileLock> onOpenAction, Consumer<FileLock> onCloseAction) {
        assert lockOptions.getMode() == Exclusive;
        this.initializationAction = initializationAction;
        this.onOpenAction = onOpenAction;
        this.onCloseAction = onCloseAction;
        assert lockOptions.getMode() == Exclusive;
        this.cacheDisplayName = cacheDisplayName;
        this.lockTarget = lockTarget;
        this.lockOptions = lockOptions;
        this.lockManager = lockManager;
    }

    @Override
    public void open() {
        if (fileLock != null) {
            throw new IllegalStateException("File lock " + lockTarget + " is already open.");
        }
        final FileLock fileLock = lockManager.lock(lockTarget, lockOptions, cacheDisplayName, "", unused -> {});
        try {
            boolean rebuild = initializationAction.requiresInitialization(fileLock);
            if (rebuild) {
                fileLock.writeFile(() -> initializationAction.initialize(fileLock));
            }
            onOpenAction.accept(fileLock);
        } catch (Exception e) {
            fileLock.close();
            throw UncheckedException.throwAsUncheckedException(e);
        }
        this.fileLock = fileLock;
    }

    @Override
    public void close() {
        if (fileLock != null) {
            try {
                onCloseAction.accept(fileLock);
                fileLock.close();
            } finally {
                fileLock = null;
            }
        }
    }

    @Override
    public Runnable acquireFileLock() {
        return Runnables.doNothing();
    }

    @Override
    public <T> T withFileLock(Supplier<T> factory) {
        return factory.get();
    }

}
