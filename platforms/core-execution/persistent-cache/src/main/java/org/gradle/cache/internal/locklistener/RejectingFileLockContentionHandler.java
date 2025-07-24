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
 * A special contention handler that doesn't support communicating contention.
 */
@NullMarked
public class RejectingFileLockContentionHandler implements FileLockContentionHandler {
    @Override
    public void start(long lockId, Consumer<FileLockReleasedSignal> whenContended) {
        throw new UnsupportedOperationException("Cannot listen for contention signals");
    }

    @Override
    public void stop(long lockId) {}

    @Override
    public int reservePort() {
        // This is a special value recognized by File
        return -1;
    }

    @Override
    public boolean maybePingOwner(int port, long lockId, String displayName, long timeElapsed, @Nullable FileLockReleasedSignal signal) {
        throw new UnsupportedOperationException("Cannot ping owners");
    }

    @Override
    public boolean isRunning() {
        return true;
    }
}
