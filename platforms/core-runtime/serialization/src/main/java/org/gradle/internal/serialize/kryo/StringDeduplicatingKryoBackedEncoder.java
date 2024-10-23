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

package org.gradle.internal.serialize.kryo;

import com.esotericsoftware.kryo.io.Output;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.gradle.internal.serialize.AbstractEncoder;
import org.gradle.internal.serialize.FlushableEncoder;
import org.gradle.internal.serialize.PositionAwareEncoder;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;

public class StringDeduplicatingKryoBackedEncoder extends AbstractEncoder implements PositionAwareEncoder, FlushableEncoder, Closeable {

    static final int NULL_STRING = 0;
    static final int NEW_STRING = 1;

    private Object2IntMap<String> strings;

    private final Output output;

    public StringDeduplicatingKryoBackedEncoder(OutputStream outputStream) {
        this(outputStream, 4096);
    }

    public StringDeduplicatingKryoBackedEncoder(OutputStream outputStream, int bufferSize) {
        output = new Output(outputStream, bufferSize);
    }

    @Override
    public void writeByte(byte value) {
        output.writeByte(value);
    }

    @Override
    public void writeBytes(byte[] bytes, int offset, int count) {
        output.writeBytes(bytes, offset, count);
    }

    @Override
    public void writeLong(long value) {
        output.writeLong(value);
    }

    @Override
    public void writeSmallLong(long value) {
        output.writeLong(value, true);
    }

    @Override
    public void writeInt(int value) {
        output.writeInt(value);
    }

    @Override
    public void writeSmallInt(int value) {
        output.writeInt(value, true);
    }

    @Override
    public void writeShort(short value) throws IOException {
        output.writeShort(value);
    }

    @Override
    public void writeFloat(float value) throws IOException {
        output.writeFloat(value);
    }

    @Override
    public void writeDouble(double value) throws IOException {
        output.writeDouble(value);
    }

    @Override
    public void writeBoolean(boolean value) {
        output.writeBoolean(value);
    }

    @Override
    public void writeNullableString(@Nullable CharSequence value) {
        if (value == null) {
            writeStringIndex(NULL_STRING);
            return;
        }
        writeNonnullString(value);
    }

    @Override
    public void writeString(CharSequence value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot encode a null string.");
        }
        writeNonnullString(value);
    }

    private void writeNonnullString(CharSequence value) {
        String key = value.toString();
        if (strings == null) {
            strings = new Object2IntOpenHashMap<String>(1024);
            writeNewString(key);
        } else {
            int index = strings.getOrDefault(key, -1);
            if (index == -1) {
                writeNewString(key);
            } else {
                writeStringIndex(index);
            }
        }
    }

    private void writeNewString(String key) {
        /*
          Actual stored string indices start from 2 so `0` and `1` can be used as special codes:
          - 0 for null
          - 1 for a new string
          And be efficiently encoded as var ints (writeVarInt/readVarInt) to save even more space.
         */
        int newIndex = strings.size() + 2;
        strings.put(key, newIndex);
        writeStringIndex(NEW_STRING);
        output.writeString(key);
    }

    private void writeStringIndex(int index) {
        output.writeVarInt(index, true);
    }

    /**
     * Returns the total number of bytes written by this encoder, some of which may still be buffered.
     */
    @Override
    public long getWritePosition() {
        return output.total();
    }

    @Override
    public void flush() {
        output.flush();
    }

    @Override
    public void close() {
        output.close();
    }

    public void done() {
        strings = null;
    }

}
