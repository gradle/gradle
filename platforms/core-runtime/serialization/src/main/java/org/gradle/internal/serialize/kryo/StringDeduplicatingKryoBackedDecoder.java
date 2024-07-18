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

import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import org.gradle.internal.serialize.AbstractDecoder;
import org.gradle.internal.serialize.Decoder;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import static org.gradle.internal.serialize.kryo.StringDeduplicatingKryoBackedEncoder.NEW_STRING;
import static org.gradle.internal.serialize.kryo.StringDeduplicatingKryoBackedEncoder.NULL_STRING;

/**
 * Note that this decoder uses buffering, so will attempt to read beyond the end of the encoded data. This means you should use this type only when this decoder will be used to decode the entire
 * stream.
 */
public class StringDeduplicatingKryoBackedDecoder extends AbstractDecoder implements Decoder, Closeable {
    private static final int INITIAL_CAPACITY = 32;
    private static final String[] INITIAL_CAPACITY_MARKER = {};
    private final Input input;
    private final InputStream inputStream;
    private String[] strings = INITIAL_CAPACITY_MARKER;
    /**
     * Actual stored string indices start from 2 so `0` and `1` can be used as special codes:
     * <ul>
     *     <li>0 for null</li>
     *     <li>1 for a new string</li>
     * </ul>
     * And be efficiently encoded as var ints (writeVarInt/readVarInt) to save even more space.
     *
     * @see StringDeduplicatingKryoBackedEncoder#NULL_STRING
     * @see StringDeduplicatingKryoBackedEncoder#NEW_STRING
     **/
    private int nextString = 2;
    private long extraSkipped;

    public StringDeduplicatingKryoBackedDecoder(InputStream inputStream) {
        this(inputStream, 4096);
    }

    public StringDeduplicatingKryoBackedDecoder(InputStream inputStream, int bufferSize) {
        this.inputStream = inputStream;
        input = new Input(this.inputStream, bufferSize);
    }

    @Override
    protected int maybeReadBytes(byte[] buffer, int offset, int count) {
        return input.read(buffer, offset, count);
    }

    @Override
    protected long maybeSkip(long count) throws IOException {
        // Work around some bugs in Input.skip()
        int remaining = input.limit() - input.position();
        if (remaining == 0) {
            long skipped = inputStream.skip(count);
            if (skipped > 0) {
                extraSkipped += skipped;
            }
            return skipped;
        } else if (count <= remaining) {
            input.setPosition(input.position() + (int) count);
            return count;
        } else {
            input.setPosition(input.limit());
            return remaining;
        }
    }

    private RuntimeException maybeEndOfStream(KryoException e) throws EOFException {
        if (e.getMessage().equals("Buffer underflow.")) {
            throw (EOFException) new EOFException().initCause(e);
        }
        throw e;
    }

    @Override
    public byte readByte() throws EOFException {
        try {
            return input.readByte();
        } catch (KryoException e) {
            throw maybeEndOfStream(e);
        }
    }

    @Override
    public void readBytes(byte[] buffer, int offset, int count) throws EOFException {
        try {
            input.readBytes(buffer, offset, count);
        } catch (KryoException e) {
            throw maybeEndOfStream(e);
        }
    }

    @Override
    public long readLong() throws EOFException {
        try {
            return input.readLong();
        } catch (KryoException e) {
            throw maybeEndOfStream(e);
        }
    }

    @Override
    public long readSmallLong() throws EOFException, IOException {
        try {
            return input.readLong(true);
        } catch (KryoException e) {
            throw maybeEndOfStream(e);
        }
    }

    @Override
    public int readInt() throws EOFException {
        try {
            return input.readInt();
        } catch (KryoException e) {
            throw maybeEndOfStream(e);
        }
    }

    @Override
    public int readSmallInt() throws EOFException {
        try {
            return input.readInt(true);
        } catch (KryoException e) {
            throw maybeEndOfStream(e);
        }
    }

    @Override
    public short readShort() throws EOFException, IOException {
        try {
            return input.readShort();
        } catch (KryoException e) {
            throw maybeEndOfStream(e);
        }
    }

    @Override
    public float readFloat() throws EOFException, IOException {
        try {
            return input.readFloat();
        } catch (KryoException e) {
            throw maybeEndOfStream(e);
        }
    }

    @Override
    public double readDouble() throws EOFException, IOException {
        try {
            return input.readDouble();
        } catch (KryoException e) {
            throw maybeEndOfStream(e);
        }
    }

    @Override
    public boolean readBoolean() throws EOFException {
        try {
            return input.readBoolean();
        } catch (KryoException e) {
            throw maybeEndOfStream(e);
        }
    }

    @Override
    public String readString() throws EOFException {
        return readNullableString();
    }

    @Override
    public String readNullableString() throws EOFException {
        try {
            int index = readStringIndex();
            switch (index) {
                case NULL_STRING:
                    return null;
                case NEW_STRING:
                    return readNewString();
                default:
                    return strings[index];
            }
        } catch (KryoException e) {
            throw maybeEndOfStream(e);
        }
    }

    private int readStringIndex() {
        return input.readVarInt(true);
    }

    private String readNewString() {
        if (nextString >= strings.length) {
            strings = growStringArray(strings);
        }
        String string = input.readString();
        strings[nextString++] = string;
        return string;
    }

    private static String[] growStringArray(String[] strings) {
        String[] grow = new String[strings == INITIAL_CAPACITY_MARKER ? INITIAL_CAPACITY : strings.length * 3 / 2];
        System.arraycopy(strings, 0, grow, 0, strings.length);
        return grow;
    }

    /**
     * Returns the total number of bytes consumed by this decoder. Some additional bytes may also be buffered by this decoder but have not been consumed.
     */
    public long getReadPosition() {
        return input.total() + extraSkipped;
    }

    @Override
    public void close() throws IOException {
        strings = null;
        input.close();
    }
}
