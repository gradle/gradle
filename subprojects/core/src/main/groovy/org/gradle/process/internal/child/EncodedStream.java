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
 * <p>
 * by Szczepan Faber, created at: 5/25/12
 */
public abstract class EncodedStream {

    public static class EncodedInput extends InputStream {

        private InputStream delegate;

        public EncodedInput(java.io.InputStream delegate) {
            this.delegate = delegate;
        }

        public int read() throws IOException {
            byte[] bytes = new byte[2];
            int bytesRead = delegate.read(bytes);
            if (bytesRead == -1) {
                return -1;
            } else if (bytesRead != 2) {
                throw new RuntimeException("Unable to decode, expected 2 bytes but was " + bytesRead + ". It seems the stream was not encoded correctly.");
            }
            return hexToByte(new String(bytes));
        }

        public static int hexToByte(String s) {
            return Integer.parseInt(s, 16);
        }
    }

    public static class EncodedOutput extends OutputStream {

        private OutputStream delegate;

        public EncodedOutput(OutputStream delegate) {
            this.delegate = delegate;
        }

        public void write(int b) throws IOException {
            String encoded = byteToHex((byte) b);
            delegate.write(encoded.getBytes());
        }

        private final static char[] HEX_DIGIT = new char[] {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
        };

        public static String byteToHex(byte b) {
            // Returns hex String representation of byte b
            char[] array = new char[]{HEX_DIGIT[(b >> 4) & 0x0f], HEX_DIGIT[b & 0x0f]};
            return new String(array);
        }
    }
}
