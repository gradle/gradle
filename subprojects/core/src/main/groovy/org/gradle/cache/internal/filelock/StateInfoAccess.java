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

public class StateInfoAccess {

    private final StateInfoProtocol protocol;

    public StateInfoAccess(StateInfoProtocol protocol) {
        this.protocol = protocol;
    }

    public void ensureStateInfo(RandomAccessFile lockFileAccess) throws IOException {
        if (lockFileAccess.length() < protocol.getSize()) {
            // File did not exist before locking
            markDirty(lockFileAccess); //TODO SF add coverage that we're actually marking dirty here
        }
    }

    public void markClean(RandomAccessFile lockFileAccess, int ownerId) throws IOException {
        writeState(lockFileAccess, new StateInfo(ownerId, false));
    }

    public void markDirty(RandomAccessFile lockFileAccess) throws IOException {
        writeState(lockFileAccess, new StateInfo(StateInfo.UNKNOWN_PREVIOUS_OWNER, true));
    }

    private void writeState(RandomAccessFile lockFileAccess, StateInfo stateInfo) throws IOException {
        lockFileAccess.seek(0);
        lockFileAccess.writeByte(protocol.getVersion());
        protocol.writeState(lockFileAccess, stateInfo);
        assert lockFileAccess.getFilePointer() == protocol.getSize();
    }

    public boolean isIntegral(RandomAccessFile lockFileAccess) throws IOException {
        if (lockFileAccess.length() > 0) {
            lockFileAccess.seek(0);
            //TODO SF throw the exception here and print diagnostics
            return lockFileAccess.readByte() == protocol.getVersion();
        }
        return true;
    }

    public StateInfo readStateInfo(RandomAccessFile lockFileAccess) throws IOException {
        lockFileAccess.seek(1); //skip the protocol byte
        try {
            return protocol.readState(lockFileAccess);
        } catch (EOFException e) {
            return new StateInfo(StateInfo.UNKNOWN_PREVIOUS_OWNER, true);
        }
    }

    public FileLock tryLock(RandomAccessFile lockFileAccess, boolean shared) throws IOException {
        return lockFileAccess.getChannel().tryLock(0, protocol.getSize(), shared);
    }

    public int getRegionEnd() {
        return protocol.getSize();
    }
}