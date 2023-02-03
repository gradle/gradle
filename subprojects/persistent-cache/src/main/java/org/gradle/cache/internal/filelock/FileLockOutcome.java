/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.cache.internal.filelock;

import java.nio.channels.FileLock;

public abstract class FileLockOutcome {
    public static final FileLockOutcome LOCKED_BY_ANOTHER_PROCESS = new FileLockOutcome() {
    };
    public static final FileLockOutcome LOCKED_BY_THIS_PROCESS = new FileLockOutcome() {
    };

    public boolean isLockWasAcquired() {
        return false;
    }

    public FileLock getFileLock() {
        throw new IllegalStateException("Lock was not acquired");
    }

    static FileLockOutcome acquired(FileLock fileLock) {
        return new FileLockOutcome() {
            @Override
            public boolean isLockWasAcquired() {
                return true;
            }

            @Override
            public FileLock getFileLock() {
                return fileLock;
            }
        };
    }
}
