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

import org.gradle.api.Action;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;

import javax.annotation.Nullable;
import java.io.File;

@ServiceScope(Scope.Global.class)
public interface FileLockManager {
    /**
     * Creates a lock for the given file with the given mode. Acquires a lock with the given mode, which is held until the lock is
     * released by calling {@link FileLock#close()}. This method blocks until the lock can be acquired.
     *
     * @param target The file to be locked.
     * @param options The lock options.
     * @param targetDisplayName A display name for the target file. This is used in log and error messages.
     */
    FileLock lock(File target, LockOptions options, String targetDisplayName) throws LockTimeoutException;

    /**
     * Creates a lock for the given file with the given mode. Acquires a lock with the given mode, which is held until the lock is
     * released by calling {@link FileLock#close()}. This method blocks until the lock can be acquired.
     *
     * @param target The file to be locked.
     * @param options The lock options.
     * @param targetDisplayName A display name for the target file. This is used in log and error messages.
     * @param operationDisplayName A display name for the operation being performed on the target file. This is used in log and error messages.
     */
    FileLock lock(File target, LockOptions options, String targetDisplayName, String operationDisplayName) throws LockTimeoutException;

    /**
     * Creates a lock for the given file with the given mode. Acquires a lock with the given mode, which is held until the lock is
     * released by calling {@link FileLock#close()}. This method blocks until the lock can be acquired.
     * <p>
     * Enable other processes to request access to the provided lock. Provided action runs when the lock access request is received
     * (it means that the lock is contended).
     *
     * @param target The file to be locked.
     * @param options The lock options.
     * @param targetDisplayName A display name for the target file. This is used in log and error messages.
     * @param operationDisplayName A display name for the operation being performed on the target file. This is used in log and error messages.
     * @param whenContended will be called asynchronously by the thread that listens for cache access requests, when such request is received.
     * Note: currently, implementations are permitted to invoke the action <em>after</em> the lock as been closed.
     */
    FileLock lock(File target, LockOptions options, String targetDisplayName, String operationDisplayName, @Nullable Action<FileLockReleasedSignal> whenContended) throws LockTimeoutException;

    /**
     * These modes can be used either with {@link FileLockManager} or when creating {@link PersistentCache} via {@link CacheBuilder#withLockOptions(LockOptions)}
     */
    enum LockMode {

        /**
         * On demand, single writer, no readers (on demand exclusive mode).
         * <br><br>
         *
         * Supports processes asking for access. Only one process can access the cache at a time.
         * <br><br>
         *
         * For {@link PersistentCache} the file lock is created with {@link PersistentCache#useCache} or {@link PersistentCache#withFileLock} method.
         * Lock is released when another process requests it via ping requests, see {@link org.gradle.cache.internal.DefaultFileLockManager.DefaultFileLock#lock(org.gradle.cache.FileLockManager.LockMode)}.
         * <br><br>
         *
         * Not supported by {@link FileLockManager}.
         */
        OnDemand,

        /**
         * Multiple readers, no writers.
         * <br><br>
         *
         * Used for read-only file locks/caches, many processes can access the lock target/cache concurrently.
         * <br><br>
         *
         * For {@link PersistentCache} this is the default behaviour and the cache lock is created when cache is opened.
         * To release a lock a cache has to be closed.
         */
        Shared,

        /**
         * Single writer, no readers.
         * <br><br>
         *
         * Used for lock targets/caches that are written to. Only one process can access the lock target/cache at a time.
         * <br><br>
         *
         * For {@link PersistentCache} the cache lock is created when cache is opened. To release a lock a cache has to be closed.
         */
        Exclusive,

        /**
         * No locking whatsoever.
         */
        None;

        public boolean isOnDemand() {
            return this == OnDemand;
        }
    }
}
