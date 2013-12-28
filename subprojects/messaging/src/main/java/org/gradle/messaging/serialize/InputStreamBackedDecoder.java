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

package org.gradle.messaging.serialize;

import java.io.*;

public class InputStreamBackedDecoder extends AbstractDecoder implements Decoder, Closeable {
    private final DataInputStream inputStream;

    public InputStreamBackedDecoder(InputStream inputStream) {
        this.inputStream = new DataInputStream(inputStream);
    }

    @Override
    protected int maybeReadBytes(byte[] buffer, int offset, int count) throws IOException {
        return inputStream.read(buffer, offset, count);
    }

    @Override
    protected long maybeSkip(long count) throws IOException {
        return inputStream.skip(count);
    }

    public long readLong() throws IOException {
        return inputStream.readLong();
    }

    public int readInt() throws EOFException, IOException {
        return inputStream.readInt();
    }

    public boolean readBoolean() throws EOFException, IOException {
        return inputStream.readBoolean();
    }

    public String readString() throws EOFException, IOException {
        return inputStream.readUTF();
    }

    public byte readByte() throws IOException {
        return (byte)(inputStream.readByte() & 0xff);
    }

    public void readBytes(byte[] buffer, int offset, int count) throws IOException {
        inputStream.readFully(buffer, offset, count);
    }

    public void close() throws IOException {
        inputStream.close();
    }
}
