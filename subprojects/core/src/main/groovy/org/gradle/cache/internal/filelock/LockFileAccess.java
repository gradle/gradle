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
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.cache.internal.stream.RandomAccessFileInputStream;
import org.gradle.cache.internal.stream.RandomAccessFileOutputStream;

import java.io.*;
import java.nio.channels.FileLock;

public class LockFileAccess {
    
    private final static Logger LOGGER = Logging.getLogger(LockFileAccess.class);

    public static final int INFORMATION_REGION_SIZE = 2052;

    private final RandomAccessFile lockFileAccess;

    private final StateInfoAccess stateInfoAccess;
    private final LockInfoSerializer lockInfoSerializer = new LockInfoSerializer();
    private final int infoRegionPos;

    private File lockFile;
    private String displayName;

    public LockFileAccess(File lockFile, String displayName, StateInfoAccess stateInfoAccess) throws FileNotFoundException {
        this.lockFile = lockFile;
        this.displayName = displayName;
        this.lockFileAccess = new RandomAccessFile(lockFile, "rw");
        this.stateInfoAccess = stateInfoAccess;
        this.infoRegionPos = this.stateInfoAccess.getRegionEnd();
    }

    public void close() throws IOException {
        lockFileAccess.close();
    }

    public void writeOwnerInfo(int port, long lockId, String pid, String operation) throws IOException {
        lockFileAccess.seek(infoRegionPos);

        DataOutputStream outstr = new DataOutputStream(new BufferedOutputStream(new RandomAccessFileOutputStream(lockFileAccess)));
        outstr.writeByte(lockInfoSerializer.getVersion());
        LockInfo lockInfo = new LockInfo();
        lockInfo.port = port;
        lockInfo.lockId = lockId;
        lockInfo.pid = pid;
        lockInfo.operation = operation;
        lockInfoSerializer.write(outstr, lockInfo);
        outstr.flush();

        lockFileAccess.setLength(lockFileAccess.getFilePointer());
    }

    public LockInfo readOwnerInfo() throws IOException {
        if (lockFileAccess.length() <= infoRegionPos) {
            LOGGER.debug("Lock file for {} is too short to contain information region. Ignoring.", displayName);
            return new LockInfo();
        } else {
            lockFileAccess.seek(infoRegionPos);

            DataInputStream inputStream = new DataInputStream(new BufferedInputStream(new RandomAccessFileInputStream(lockFileAccess)));
            if (inputStream.readByte() != lockInfoSerializer.getVersion()) {
                throw new IllegalStateException(String.format("Unexpected lock protocol found in lock file '%s' for %s.", lockFile, displayName));
            }
            return lockInfoSerializer.readState(inputStream);
        }
    }

    /**
     * Reads the state info from the lock file, possibly generating a new lock file if not present or empty.
     */
    public LockState ensureStateInfo() throws IOException {
        return stateInfoAccess.ensureStateInfo(lockFileAccess);
    }

    public void markClean(int ownerId) throws IOException {
        stateInfoAccess.markClean(lockFileAccess, ownerId);
    }

    public void markDirty() throws IOException {
        stateInfoAccess.markDirty(lockFileAccess);
    }

    public void clearOwnerInfo() throws IOException {
        lockFileAccess.setLength(infoRegionPos);
    }

    @Nullable
    public FileLock tryLockOwnerInfo(boolean shared) throws IOException {
        return lockFileAccess.getChannel().tryLock(infoRegionPos, (long) (INFORMATION_REGION_SIZE - infoRegionPos), shared);
    }

    @Nullable
    public FileLock tryLockStateInfo(boolean shared) throws IOException {
        return stateInfoAccess.tryLock(lockFileAccess, shared);
    }

    /**
     * Reads the state info from the lock file.
     */
    public LockState readStateInfo() throws IOException {
        return stateInfoAccess.readStateInfo(lockFileAccess);
    }
}