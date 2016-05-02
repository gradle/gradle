/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.Nullable;

import java.io.*;
import java.nio.channels.FileLock;

public class LockFileAccess {

    private final RandomAccessFile lockFileAccess;

    private final LockStateAccess lockStateAccess;
    private final LockInfoAccess lockInfoAccess;

    public LockFileAccess(File lockFile, LockStateAccess lockStateAccess) throws FileNotFoundException {
        this.lockFileAccess = new RandomAccessFile(lockFile, "rw");
        this.lockStateAccess = lockStateAccess;
        lockInfoAccess = new LockInfoAccess(this.lockStateAccess.getRegionEnd());
    }

    public void close() throws IOException {
        lockFileAccess.close();
    }

    public void writeLockInfo(int port, long lockId, String pid, String operation) throws IOException {
        LockInfo lockInfo = new LockInfo();
        lockInfo.port = port;
        lockInfo.lockId = lockId;
        lockInfo.pid = pid;
        lockInfo.operation = operation;
        lockInfoAccess.writeLockInfo(lockFileAccess, lockInfo);
    }

    public LockInfo readLockInfo() throws IOException {
        return lockInfoAccess.readLockInfo(lockFileAccess);
    }

    /**
     * Reads the lock state from the lock file, possibly writing out a new lock file if not present or empty.
     */
    public LockState ensureLockState() throws IOException {
        return lockStateAccess.ensureLockState(lockFileAccess);
    }

    public LockState markClean(LockState lockState) throws IOException {
        LockState newState = lockState.completeUpdate();
        lockStateAccess.writeState(lockFileAccess, newState);
        return newState;
    }

    public LockState markDirty(LockState lockState) throws IOException {
        LockState newState = lockState.beforeUpdate();
        lockStateAccess.writeState(lockFileAccess, newState);
        return newState;
    }

    public void clearLockInfo() throws IOException {
        lockInfoAccess.clearLockInfo(lockFileAccess);
    }

    @Nullable
    public FileLock tryLockInfo(boolean shared) throws IOException {
        return lockInfoAccess.tryLock(lockFileAccess, shared);
    }

    @Nullable
    public FileLock tryLockState(boolean shared) throws IOException {
        return lockStateAccess.tryLock(lockFileAccess, shared);
    }

    /**
     * Reads the lock state from the lock file.
     */
    public LockState readLockState() throws IOException {
        return lockStateAccess.readState(lockFileAccess);
    }
}