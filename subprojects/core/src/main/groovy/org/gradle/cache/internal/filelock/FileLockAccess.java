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

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

public class FileLockAccess {
    
    private final static Logger LOGGER = Logging.getLogger(FileLockAccess.class);

    public static final byte STATE_REGION_PROTOCOL = 2; //should be incremented when state region format changes in an incompatible way
    public static final int STATE_REGION_SIZE = 5;
    public static final int STATE_REGION_POS = 0;
    public static final byte INFORMATION_REGION_PROTOCOL = 3; //should be incremented when information region format changes in an incompatible way
    public static final int INFORMATION_REGION_POS = STATE_REGION_POS + STATE_REGION_SIZE;
    public static final int INFORMATION_REGION_SIZE = 2052;
    public static final int INFORMATION_REGION_DESCR_CHUNK_LIMIT = 340;
    public static final int UNKNOWN_PREVIOUS_OWNER = 0;

    public static final short PROTOCOL_VERSION = STATE_REGION_PROTOCOL + INFORMATION_REGION_PROTOCOL;

    private final RandomAccessFile lockFileAccess;
    private File lockFile;
    private String displayName;

    public FileLockAccess(File lockFile, String displayName) throws FileNotFoundException {
        this.lockFile = lockFile;
        this.displayName = displayName;
        this.lockFileAccess = new RandomAccessFile(lockFile, "rw");
    }

    public void close() throws IOException {
        lockFileAccess.close();
    }

    public void assertStateInfoIntegral() throws IOException {
        if (lockFileAccess.length() > 0) {
            lockFileAccess.seek(STATE_REGION_POS);
            if (lockFileAccess.readByte() != STATE_REGION_PROTOCOL) {
                throw new IllegalStateException(String.format("Unexpected lock protocol found in lock file '%s' for %s.", lockFile, displayName));
            }
        }
    }

    public void writeOwnerInfo(int port, long lockId, String pid, String operation) throws IOException {
        lockFileAccess.seek(INFORMATION_REGION_POS);
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
        if (lockFileAccess.length() <= INFORMATION_REGION_POS) {
            LOGGER.debug("Lock file for {} is too short to contain information region. Ignoring.", displayName);
        } else {
            lockFileAccess.seek(INFORMATION_REGION_POS);
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

    private int readPreviousOwnerId() {
        try {
            lockFileAccess.seek(STATE_REGION_POS + 1);
            return lockFileAccess.readInt();
        } catch (EOFException e) {
            // Process has crashed writing to lock file
            return UNKNOWN_PREVIOUS_OWNER;
        } catch (Exception e) {
            throw throwAsUncheckedException(e);
        }
    }

    public void ensureStateInfo() throws IOException {
        if (lockFileAccess.length() < STATE_REGION_SIZE) {
            // File did not exist before locking
            markClean(UNKNOWN_PREVIOUS_OWNER);
        }
    }

    public void markClean(int ownerId) throws IOException {
        lockFileAccess.seek(STATE_REGION_POS);
        lockFileAccess.writeByte(STATE_REGION_PROTOCOL);
        lockFileAccess.writeInt(ownerId);
        assert lockFileAccess.getFilePointer() == STATE_REGION_SIZE + STATE_REGION_POS;
    }

    public void markDirty() throws IOException {
        lockFileAccess.seek(STATE_REGION_POS);
        lockFileAccess.writeByte(STATE_REGION_PROTOCOL);
        lockFileAccess.writeInt(UNKNOWN_PREVIOUS_OWNER);
        assert lockFileAccess.getFilePointer() == STATE_REGION_SIZE + STATE_REGION_POS;
    }

    public void clearOwnerInfo() throws IOException {
        lockFileAccess.setLength(INFORMATION_REGION_POS);
    }

    public FileLock tryLockOwnerInfo(boolean shared) throws IOException {
        return lockFileAccess.getChannel().tryLock((long) INFORMATION_REGION_POS, (long) (INFORMATION_REGION_SIZE - INFORMATION_REGION_POS), shared);
    }

    public FileLock tryLockStateInfo(boolean shared) throws IOException {
        return lockFileAccess.getChannel().tryLock((long) STATE_REGION_POS, (long) STATE_REGION_SIZE, shared);
    }

    public StateInfo readStateInfo() {
        return new StateInfo(readPreviousOwnerId());
    }
}