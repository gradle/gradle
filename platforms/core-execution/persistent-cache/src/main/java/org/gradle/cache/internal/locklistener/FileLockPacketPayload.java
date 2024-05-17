/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.cache.internal.locklistener;

import com.google.common.collect.ImmutableList;
import org.gradle.api.UncheckedIOException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;

import static org.gradle.cache.internal.locklistener.FileLockPacketType.UNKNOWN;

public class FileLockPacketPayload {

    public static final int MAX_BYTES = 1 + 8 + 1; // protocolVersion + lockId + type
    private static final byte PROTOCOL_VERSION = 1;
    private static final ImmutableList<FileLockPacketType> TYPES = ImmutableList.copyOf(FileLockPacketType.values());

    private final long lockId;
    private final FileLockPacketType type;

    private FileLockPacketPayload(long lockId, FileLockPacketType type) {
        this.lockId = lockId;
        this.type = type;
    }

    public long getLockId() {
        return lockId;
    }

    public FileLockPacketType getType() {
        return type;
    }

    public static byte[] encode(long lockId, FileLockPacketType type) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream dataOutput = new DataOutputStream(out);
        try {
            dataOutput.writeByte(PROTOCOL_VERSION);
            dataOutput.writeLong(lockId);
            dataOutput.writeByte(type.ordinal());
            dataOutput.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to encode lockId " + lockId + " and type " + type, e);
        }
        return out.toByteArray();
    }

    public static FileLockPacketPayload decode(byte[] bytes, int length) throws IOException {
        DataInputStream dataInput = new DataInputStream(new ByteArrayInputStream(bytes));
        byte version = dataInput.readByte();
        if (version != PROTOCOL_VERSION) {
            throw new IllegalArgumentException(String.format("Unexpected protocol version %s received in lock contention notification message", version));
        }
        long lockId = dataInput.readLong();
        FileLockPacketType type = readType(dataInput, length);
        return new FileLockPacketPayload(lockId, type);
    }

    private static FileLockPacketType readType(DataInputStream dataInput, int length) throws IOException {
        if (length < MAX_BYTES) {
            return UNKNOWN;
        }
        try {
            int ordinal = dataInput.readByte();
            if (ordinal < TYPES.size()) {
                return TYPES.get(ordinal);
            }
        } catch (EOFException ignore) {
            // old versions don't send a type
        }
        return UNKNOWN;
    }

}
