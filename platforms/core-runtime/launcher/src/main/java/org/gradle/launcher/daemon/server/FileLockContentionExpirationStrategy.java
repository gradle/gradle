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

package org.gradle.launcher.daemon.server;

import org.gradle.cache.internal.locklistener.FileLockContentionHandler;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationResult;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStrategy;

/**
 * If the file lock contention handler is not running, the daemon is in a bad state and should expire immediately.
 */
public class FileLockContentionExpirationStrategy implements DaemonExpirationStrategy {
    private final FileLockContentionHandler fileLockContentionHandler;

    public FileLockContentionExpirationStrategy(FileLockContentionHandler fileLockContentionHandler) {
        this.fileLockContentionHandler = fileLockContentionHandler;
    }

    @Override
    public DaemonExpirationResult checkExpiration() {
        if (!fileLockContentionHandler.isRunning()) {
            return new DaemonExpirationResult(DaemonExpirationStatus.IMMEDIATE_EXPIRE, "Unable to coordinate lock ownership with other builds.");
        }
        return DaemonExpirationResult.NOT_TRIGGERED;
    }
}
