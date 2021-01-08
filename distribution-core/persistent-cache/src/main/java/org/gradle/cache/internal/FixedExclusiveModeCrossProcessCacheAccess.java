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
import org.gradle.api.Action;
import org.gradle.cache.CrossProcessCacheAccess;
import org.gradle.cache.FileLock;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.FileLockReleasedSignal;
import org.gradle.cache.LockOptions;
import org.gradle.internal.Actions;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;

import java.io.File;

import static org.gradle.cache.FileLockManager.LockMode.Exclusive;

/**
 * A {@link CrossProcessCacheAccess} implementation used when a cache is opened with an exclusive lock that is held until the cache is closed. This implementation is simply a no-op for these methods.
 */
public class FixedExclusiveModeCrossProcessCacheAccess extends AbstractCrossProcessCacheAccess {
    private final static Action<FileLockReleasedSignal> NO_OP_CONTENDED_ACTION = Actions.doNothing();

    private final String cacheDisplayName;
    private final File lockTarget;
    private final LockOptions lockOptions;
    private final FileLockManager lockManager;
    private final CacheInitializationAction initializationAction;
    private final Action<FileLock> onOpenAction;
    private final Action<FileLock> onCloseAction;
    private FileLock fileLock;

    public FixedExclusiveModeCrossProcessCacheAccess(String cacheDisplayName, File lockTarget, LockOptions lockOptions, FileLockManager lockManager, CacheInitializationAction initializationAction, Action<FileLock> onOpenAction, Action<FileLock> onCloseAction) {
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
        final FileLock fileLock = lockManager.lock(lockTarget, lockOptions, cacheDisplayName, "", NO_OP_CONTENDED_ACTION);
        try {
            boolean rebuild = initializationAction.requiresInitialization(fileLock);
            if (rebuild) {
                fileLock.writeFile(new Runnable() {
                    @Override
                    public void run() {
                        initializationAction.initialize(fileLock);
                    }
                });
            }
            onOpenAction.execute(fileLock);
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
                onCloseAction.execute(fileLock);
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
    public <T> T withFileLock(Factory<T> factory) {
        return factory.create();
    }

}
