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

import java.io.IOException;
import java.io.InputStream;

public abstract class AbstractDecoder implements Decoder {
    private DecoderStream stream;

    public InputStream getInputStream() {
        if (stream == null) {
            stream = new DecoderStream();
        }
        return stream;
    }

    public void readBytes(byte[] buffer) throws IOException {
        readBytes(buffer, 0, buffer.length);
    }

    protected abstract int maybeReadBytes(byte[] buffer, int offset, int count) throws IOException;

    private class DecoderStream extends InputStream {
        byte[] buffer = new byte[1];

        @Override
        public int read() throws IOException {
            int read = maybeReadBytes(buffer, 0, 1);
            if (read <= 0) {
                return read;
            }
            return buffer[0] & 0xff;
        }

        @Override
        public int read(byte[] buffer) throws IOException {
            return maybeReadBytes(buffer, 0, buffer.length);
        }

        @Override
        public int read(byte[] buffer, int offset, int count) throws IOException {
            return maybeReadBytes(buffer, offset, count);
        }
    }
}
