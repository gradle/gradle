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
import com.google.common.collect.Maps;
import org.gradle.internal.serialize.AbstractEncoder;
import org.gradle.internal.serialize.FlushableEncoder;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.OutputStream;
import java.util.Map;

public class StringDeduplicatingKryoBackedEncoder extends AbstractEncoder implements FlushableEncoder, Closeable {
    private Map<String, Integer> strings;

    private final Output output;

    public StringDeduplicatingKryoBackedEncoder(OutputStream outputStream) {
        this(outputStream, 4096);
    }

    public StringDeduplicatingKryoBackedEncoder(OutputStream outputStream, int bufferSize) {
        output = new Output(outputStream, bufferSize);
    }

    public void writeByte(byte value) {
        output.writeByte(value);
    }

    public void writeBytes(byte[] bytes, int offset, int count) {
        output.writeBytes(bytes, offset, count);
    }

    public void writeLong(long value) {
        output.writeLong(value);
    }

    public void writeSmallLong(long value) {
        output.writeLong(value, true);
    }

    public void writeInt(int value) {
        output.writeInt(value);
    }

    public void writeSmallInt(int value) {
        output.writeInt(value, true);
    }

    public void writeBoolean(boolean value) {
        output.writeBoolean(value);
    }

    public void writeString(CharSequence value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot encode a null string.");
        }
        writeNullableString(value);
    }

    public void writeNullableString(@Nullable CharSequence value) {
        if (value == null) {
            output.writeByte((byte) 0);
            return;
        } else {
            if (strings == null) {
                strings = Maps.newHashMap();
            }
            output.writeByte((byte) 1);
        }
        String string = value.toString();
        Integer index = strings.get(string);
        if (index == null) {
            index = strings.size();
            strings.put(string, index);
            output.writeInt(index, true);
            output.writeString(string);
        } else {
            output.writeInt(index, true);
        }
    }

    /**
     * Returns the total number of bytes written by this encoder, some of which may still be buffered.
     */
    public long getWritePosition() {
        return output.total();
    }

    public void flush() {
        output.flush();
    }

    public void close() {
        output.close();
    }

    public void done() {
        strings = null;
    }
}
