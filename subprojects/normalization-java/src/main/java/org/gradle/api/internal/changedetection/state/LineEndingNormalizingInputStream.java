/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.primitives.Bytes;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * An input stream that normalizes line endings from '\r' and '\r\n' to '\n' while reading
 * the stream.
 */
public class LineEndingNormalizingInputStream extends FilterInputStream {
    int peekAhead = -1;

    public LineEndingNormalizingInputStream(InputStream delegate) {
        super(delegate);
    }

    public int read() throws IOException {
        // Get our next byte from the peek ahead buffer if it contains anything
        int next = peekAhead;

        // If there was something in the peek ahead buffer, use it, otherwise read the next byte
        if (next != -1) {
            peekAhead = -1;
        } else {
            next = super.read();
        }

        // If the next bytes are '\r' or '\r\n', replace it with '\n'
        if (next == '\r') {
            peekAhead = super.read();
            if (peekAhead == '\n') {
                peekAhead = -1;
            }
            next = '\n';
        }

        return next;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        byte[] original = new byte[len];

        // If there is something left over in the peekAhead buffer, use that as the first byte
        int readLimit = len;
        if (peekAhead != -1) {
            readLimit--;
        }

        int index = 0;
        int read = super.read(original, off, readLimit);
        if (read != -1) {
            Iterator<Byte> itr = Bytes.asList(original).subList(0, read).iterator();
            while (itr.hasNext()) {
                // Get our next byte from the peek ahead buffer if it contains anything
                int next = peekAhead;

                // If there was something in the peek ahead buffer, use it, otherwise get the next byte
                if (next != -1) {
                    peekAhead = -1;
                } else {
                    next = itr.next();
                }

                // If the next bytes are '\r' or '\r\n', replace it with '\n'
                if (next == '\r') {
                    peekAhead = itr.hasNext() ? itr.next() : super.read();
                    if (peekAhead == '\n') {
                        peekAhead = -1;
                    }
                    next = '\n';
                }

                b[index++] = (byte) next;
            }
        } else if (peekAhead != -1) {
            // If there is a character still left in the peekAhead buffer but not in the input stream, then normalize it and return
            int next = peekAhead;
            peekAhead = -1;
            if (next == '\r') {
                next = '\n';
            }
            b[index++] = (byte) next;
        }
        return index == 0 ? -1 : index;
    }

    @Override
    public void close() throws IOException {
        super.close();
    }
}
