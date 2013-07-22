/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.process.internal.child;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Provides Input/OutputStream implementations that are able to encode/decode using a simple algorithm (byte<->2 digit hex string(2 bytes)).
 * Useful when streams are interpreted a text streams as it happens on IBM java for standard input.
 */
public abstract class EncodedStream {
    private final static char[] HEX_DIGIT = new char[] {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    };

    public static class EncodedInput extends InputStream {

        private InputStream delegate;

        public EncodedInput(java.io.InputStream delegate) {
            this.delegate = delegate;
        }

        public int read() throws IOException {
            int byte1 = delegate.read();
            if (byte1 < 0) {
                return -1;
            }
            int byte2 = delegate.read();
            if (byte2 < 0) {
                throw new IOException("Unable to decode, expected 2 bytes but received only 1 byte. It seems the stream was not encoded correctly.");
            }
            return (hexToByte(byte1) << 4) | hexToByte(byte2);
        }

        public static int hexToByte(int s) throws IOException {
            if (s >= '0' && s <= '9') {
                return s - '0';
            }
            if (s >= 'a' && s <= 'f') {
                return s - 'a' + 10;
            }
            throw new IOException(String.format("Unexpected value %s received. It seems the stream was not encoded correctly.", s));
        }
    }

    public static class EncodedOutput extends OutputStream {

        private final OutputStream delegate;

        public EncodedOutput(OutputStream delegate) {
            this.delegate = delegate;
        }

        public void write(int b) throws IOException {
            delegate.write(HEX_DIGIT[(b >> 4) & 0x0f]);
            delegate.write(HEX_DIGIT[b & 0x0f]);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
