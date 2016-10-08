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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;

public class LockStateAccess {
    private final LockStateSerializer protocol;
    private static final int REGION_START = 0;
    private static final int STATE_CONTENT_START = 1;
    private final int stateRegionSize;

    public LockStateAccess(LockStateSerializer protocol) {
        this.protocol = protocol;
        stateRegionSize = STATE_CONTENT_START + protocol.getSize();
    }

    public LockState ensureLockState(RandomAccessFile lockFileAccess) throws IOException {
        if (lockFileAccess.length() == 0) {
            // File did not exist before locking, use some initial state
            LockState state = protocol.createInitialState();
            writeState(lockFileAccess, state);
            return state;
        } else {
            return readState(lockFileAccess);
        }
    }

    public void writeState(RandomAccessFile lockFileAccess, LockState lockState) throws IOException {
        ByteArrayOutputStream outstr = new ByteArrayOutputStream();
        DataOutputStream dataOutput = new DataOutputStream(outstr);
        dataOutput.writeByte(protocol.getVersion());
        protocol.write(dataOutput, lockState);
        dataOutput.flush();

        lockFileAccess.seek(REGION_START);
        lockFileAccess.write(outstr.toByteArray());
        assert lockFileAccess.getFilePointer() == stateRegionSize;
    }

    public LockState readState(RandomAccessFile lockFileAccess) throws IOException {
        try {
            byte[] buffer = new byte[stateRegionSize];
            lockFileAccess.seek(REGION_START);

            int readPos = 0;
            while (readPos < buffer.length) {
                int nread = lockFileAccess.read(buffer, readPos, buffer.length - readPos);
                if (nread < 0) {
                    break;
                }
                readPos += nread;
            }

            ByteArrayInputStream inputStream = new ByteArrayInputStream(buffer, 0, readPos);
            DataInputStream dataInput = new DataInputStream(inputStream);

            byte protocolVersion = dataInput.readByte();
            if (protocolVersion != protocol.getVersion()) {
                throw new IllegalStateException(String.format("Unexpected lock protocol found in lock file. Expected %s, found %s.", protocol.getVersion(), protocolVersion));
            }
            return protocol.read(dataInput);
        } catch (EOFException e) {
            return protocol.createInitialState();
        }
    }

    public FileLock tryLock(RandomAccessFile lockFileAccess, boolean shared) throws IOException {
        try {
            return lockFileAccess.getChannel().tryLock(REGION_START, stateRegionSize, shared);
        } catch (OverlappingFileLockException e) {
            return null;
        }
    }

    public int getRegionEnd() {
        return stateRegionSize;
    }
}
