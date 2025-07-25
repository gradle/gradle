/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.cache.internal.locklistener;

import org.gradle.cache.FileLockReleasedSignal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A special contention handler that doesn't support negotiating for locks.
 * It cannot be used to acquire an {@link org.gradle.cache.FileLockManager.LockMode#OnDemand} lock because of that.
 * <p>
 * This class is intended to be used by non-Gradle clients of the persistent-cache module.
 */
@NullMarked
public class RejectingFileLockContentionHandler implements FileLockContentionHandler {
    @Override
    public void start(long lockId, Consumer<FileLockReleasedSignal> whenContended) {
        // FixedExclusiveModeCrossProcessCacheAccess supplies a no-op contention callback (it never yields a lock).
        // Not providing a contention callback has some side effects on other implementations of FileLockContentionHandler, so we cannot
        // just throw here. Instead, we ask to release the lock immediately.
        whenContended.accept(() -> {});
    }

    @Override
    public void stop(long lockId) {}

    @Override
    public int reservePort() {
        return INVALID_PORT;
    }

    @Override
    public boolean maybePingOwner(int port, long lockId, String displayName, long timeElapsed, @Nullable FileLockReleasedSignal signal) {
        // This assumes that DefaultFileLockManager never tries to negotiate for the lock if it doesn't receive a valid port for it.
        throw new UnsupportedOperationException("Cannot ping owners");
    }

    @Override
    public boolean isRunning() {
        return true;
    }
}
