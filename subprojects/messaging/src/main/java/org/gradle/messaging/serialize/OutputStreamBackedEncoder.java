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

import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class OutputStreamBackedEncoder extends AbstractEncoder implements Closeable, FlushableEncoder {
    private final DataOutputStream outputStream;

    public OutputStreamBackedEncoder(OutputStream outputStream) {
        this.outputStream = new DataOutputStream(outputStream);
    }

    public void writeLong(long value) throws IOException {
        outputStream.writeLong(value);
    }

    public void writeInt(int value) throws IOException {
        outputStream.writeInt(value);
    }

    public void writeBoolean(boolean value) throws IOException {
        outputStream.writeBoolean(value);
    }

    public void writeString(CharSequence value) throws IOException {
        if (value == null) {
            throw new IllegalArgumentException("Cannot encode a null string.");
        }
        outputStream.writeUTF(value.toString());
    }

    public void writeByte(byte value) throws IOException {
        outputStream.writeByte(value);
    }

    public void writeBytes(byte[] bytes, int offset, int count) throws IOException {
        outputStream.write(bytes, offset, count);
    }

    public void flush() throws IOException {
        outputStream.flush();
    }

    public void close() throws IOException {
        outputStream.close();
    }
}
