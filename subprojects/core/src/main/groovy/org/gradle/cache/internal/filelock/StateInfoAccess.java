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

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;

import static org.gradle.internal.UncheckedException.throwAsUncheckedException;

public class StateInfoAccess {

    static final byte STATE_REGION_PROTOCOL = 2; //should be incremented when state region format changes in an incompatible way
    private static final int STATE_REGION_SIZE = 5;
    private static final int STATE_REGION_POS = 0;
    private static final int UNKNOWN_PREVIOUS_OWNER = 0;

    public void ensureStateInfo(RandomAccessFile lockFileAccess) throws IOException {
        if (lockFileAccess.length() < STATE_REGION_SIZE) {
            // File did not exist before locking
            markClean(lockFileAccess, UNKNOWN_PREVIOUS_OWNER);
        }
    }

    public void markClean(RandomAccessFile lockFileAccess, int ownerId) throws IOException {
        lockFileAccess.seek(STATE_REGION_POS);
        lockFileAccess.writeByte(STATE_REGION_PROTOCOL);
        lockFileAccess.writeInt(ownerId);
        assert lockFileAccess.getFilePointer() == STATE_REGION_SIZE + STATE_REGION_POS;
    }

    public void markDirty(RandomAccessFile lockFileAccess) throws IOException {
        lockFileAccess.seek(STATE_REGION_POS);
        lockFileAccess.writeByte(STATE_REGION_PROTOCOL);
        lockFileAccess.writeInt(UNKNOWN_PREVIOUS_OWNER);
        assert lockFileAccess.getFilePointer() == STATE_REGION_SIZE + STATE_REGION_POS;
    }

    public boolean isIntegral(RandomAccessFile lockFileAccess) throws IOException {
        if (lockFileAccess.length() > 0) {
            lockFileAccess.seek(STATE_REGION_POS);
            return lockFileAccess.readByte() == STATE_REGION_PROTOCOL;
        }
        return true;
    }

    private int readPreviousOwnerId(RandomAccessFile lockFileAccess) {
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

    public StateInfo readStateInfo(RandomAccessFile lockFileAccess) {
        int id =  readPreviousOwnerId(lockFileAccess);
        return new StateInfo(id, id == UNKNOWN_PREVIOUS_OWNER);
    }

    public FileLock tryLock(RandomAccessFile lockFileAccess, boolean shared) throws IOException {
        return lockFileAccess.getChannel().tryLock((long) STATE_REGION_POS, (long) STATE_REGION_SIZE, shared);
    }

    public int getRegionEnd() {
        return STATE_REGION_POS + STATE_REGION_SIZE;
    }
}