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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.*;
import java.nio.channels.FileLock;

public class FileLockAccess {
    
    private final static Logger LOGGER = Logging.getLogger(FileLockAccess.class);

    private static final byte INFORMATION_REGION_PROTOCOL = 3; //should be incremented when information region format changes in an incompatible way
    public static final int INFORMATION_REGION_SIZE = 2052;
    public static final int INFORMATION_REGION_DESCR_CHUNK_LIMIT = 340;

    private final RandomAccessFile lockFileAccess;

    private final StateInfoAccess stateInfoAccess;
    private final int infoRegionPos;

    private File lockFile;
    private String displayName;

    public FileLockAccess(File lockFile, String displayName, StateInfoAccess stateInfoAccess) throws FileNotFoundException {
        this.lockFile = lockFile;
        this.displayName = displayName;
        this.lockFileAccess = new RandomAccessFile(lockFile, "rw");
        this.stateInfoAccess = stateInfoAccess;
        this.infoRegionPos = this.stateInfoAccess.getRegionEnd();
    }

    public void close() throws IOException {
        lockFileAccess.close();
    }

    public void assertStateInfoIntegral() throws IOException {
        stateInfoAccess.assertIntegral(lockFileAccess);
    }

    public void writeOwnerInfo(int port, long lockId, String pid, String operation) throws IOException {
        lockFileAccess.seek(infoRegionPos);
        lockFileAccess.writeByte(INFORMATION_REGION_PROTOCOL);
        lockFileAccess.writeInt(port);
        lockFileAccess.writeLong(lockId);
        lockFileAccess.writeUTF(trimIfNecessary(pid));
        lockFileAccess.writeUTF(trimIfNecessary(operation));
        lockFileAccess.setLength(lockFileAccess.getFilePointer());
    }

    private String trimIfNecessary(String inputString) {
        if(inputString.length() > INFORMATION_REGION_DESCR_CHUNK_LIMIT){
            return inputString.substring(0, INFORMATION_REGION_DESCR_CHUNK_LIMIT);
        } else {
            return inputString;
        }
    }

    public OwnerInfo readOwnerInfo() throws IOException {
        OwnerInfo out = new OwnerInfo();
        if (lockFileAccess.length() <= infoRegionPos) {
            LOGGER.debug("Lock file for {} is too short to contain information region. Ignoring.", displayName);
        } else {
            lockFileAccess.seek(infoRegionPos);
            if (lockFileAccess.readByte() != INFORMATION_REGION_PROTOCOL) {
                throw new IllegalStateException(String.format("Unexpected lock protocol found in lock file '%s' for %s.", lockFile, displayName));
            }
            out.port = lockFileAccess.readInt();
            out.lockId = lockFileAccess.readLong();
            out.pid = lockFileAccess.readUTF();
            out.operation = lockFileAccess.readUTF();
            LOGGER.debug("Read following information from the file lock info region. Port: {}, owner: {}, operation: {}", out.port, out.pid, out.operation);
        }
        return out;
    }

    public void ensureStateInfo() throws IOException {
        stateInfoAccess.ensureStateInfo(lockFileAccess);
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

    public FileLock tryLockOwnerInfo(boolean shared) throws IOException {
        return lockFileAccess.getChannel().tryLock((long) infoRegionPos, (long) (INFORMATION_REGION_SIZE - infoRegionPos), shared);
    }

    public FileLock tryLockStateInfo(boolean shared) throws IOException {
        return stateInfoAccess.tryLock(lockFileAccess, shared);
    }

    public StateInfo readStateInfo() throws IOException {
        return stateInfoAccess.readStateInfo(lockFileAccess);
    }
}