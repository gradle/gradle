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

/**
 * Thrown on timeout acquiring a lock on a file.
 */
public class LockTimeoutException extends RuntimeException {
    private final String lockDisplayName;
    private final String ownerPid;
    private final String requestingPid;
    private final String ownerOperation;
    private final String requestingOperation;
    private final File lockFile;

    public LockTimeoutException(String lockDisplayName, String ownerPid, String requestingPid, String ownerOperation, String requestingOperation, File lockFile) {
        super(String.format("Timeout waiting to lock %s. It is currently in use by another Gradle instance.%nOwner PID: %s%nOur PID: %s%nOwner Operation: %s%nOur operation: %s%nLock file: %s", lockDisplayName, ownerPid, requestingPid, ownerOperation, requestingOperation, lockFile));
        this.lockDisplayName = lockDisplayName;
        this.ownerPid = ownerPid;
        this.requestingPid = requestingPid;
        this.ownerOperation = ownerOperation;
        this.requestingOperation = requestingOperation;
        this.lockFile = lockFile;
    }

    public String getLockDisplayName() {
        return lockDisplayName;
    }

    public String getOwnerPid() {
        return ownerPid;
    }

    public String getRequestingPid() {
        return requestingPid;
    }

    public String getOwnerOperation() {
        return ownerOperation;
    }

    public String getRequestingOperation() {
        return requestingOperation;
    }

    public File getLockFile() {
        return lockFile;
    }
}
