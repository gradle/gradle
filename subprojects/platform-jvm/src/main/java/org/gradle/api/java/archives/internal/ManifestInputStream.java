/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.java.archives.internal;

import java.io.IOException;
import java.io.InputStream;

/**
 * Reads manifest file, and automatically removes value wraps.
 * <p>Manifest specification requires that lines should not exceed 72 bytes
 * in UTF-8 encoded format.
 * Note: this class operates on a byte level and it relies on the fact that
 * ' ', '\r', and ' ' are "always" the same byte values, and those should
 * never happen in the middle of another "character".</p>
 * <p>Certain Manifest writer implementations are known to split surrogate paris
 * with a continuation when surrogate pair reaches 72 bytes limit (e.g. OpenJDK 8),
 * so byte-oriented processing helps for those cases as well.
 * The continuation is removed on a byte level (before character decoding),
 * so UTF-8 sequence becomes valid.</p>
 * <p>Newlines are normalized to LF ({@code '\n'}) (for simplified processing later).</p>
 */
public class ManifestInputStream extends InputStream {
    private final byte[] one = new byte[1];
    private final byte[] buffer = new byte[8192];
    private int first;
    private int last;
    private boolean eofSeen;

    private final InputStream in;

    /**
     * Creates a <code>ManifestInputStream</code> for processing a manifest.
     * <p>Input manifest can be in any encoding (however the specification says manifests
     * should use UTF-8).
     * End-of-line markers will be converted to LF ({@code '\n'}).</p>
     *
     * @param in the underlying input stream with manifest to process
     */
    protected ManifestInputStream(InputStream in) {
        this.in = in;
    }

    @Override
    public int read() throws IOException {
        while (true) {
            // Read might return 0
            int res = read(one);
            if (res != 0) {
                return res == 1 ? one[0] & 0xff : -1;
            }
        }
    }

    private void fill() throws IOException {
        if (eofSeen) {
            return;
        }
        // Ensure the buffer contains at least 10 bytes
        int currentBytes = last - first;
        if (currentBytes == 0) {
            last = 0;
        } else {
            if (currentBytes > 10) {
                return;
            }
            // Too few chars in the buffer. De-fragment the buffer and read more
            System.arraycopy(buffer, first, buffer, 0, last - first);
            last -= first;
        }
        first = 0;
        // Fill the rest of the buffer
        int read = 0;
        for (int i = 0; i < 42 && read == 0; i++) {
            read = in.read(buffer, last, buffer.length - last);
        }
        if (read == 0) {
            throw new IllegalStateException(in + ".read(byte[" + buffer.length + "], " + last + "," + (buffer.length - last) + ") produces 0");
        }
        if (read == -1) {
            eofSeen = true;
            return;
        }
        last += read;
    }

    @Override
    public int read(byte[] buf, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        int end = off + len;
        int i = off;
        byte[] buffer = this.buffer;
        while (i < end) {
            // The following transformations are made:
            // \r \n SPACE -> <empty>
            // \r SPACE -> <empty>
            // \n SPACE -> <empty>
            // \r \n _ -> \n _
            // \r _ -> \n _
            // \n _ -> \n _
            //
            // \r \r -> \n \n
            // \r \r SPACE -> \n
            //
            // 26 byte (ctrl-z) is trimmed if it is located at the end of the stream
            fill();
            int first = this.first;
            int last = this.last;
            if (first == last) {
                break;
            }
            boolean eofSeen = this.eofSeen;
            // It might be that the buffer boundary is between \r and \n,
            // and we still need to combine \r\n into a single \n.
            // The approach is to keep the last 3 bytes unused.
            // The next read would copy the last 3 bytes to the start of the buffer.
            // However in case the end of stream is reached, we need to process all the bytes
            while (i < end && (first + 3 < last || eofSeen && first < last)) {
                byte b = buffer[first++];
                // Replace \r -> \n, \r\n -> \n
                // Note: first is already incremented, so buffer[first] is the char after "b"
                if (eofSeen && b == 26 && first == last) {
                    // Trim 26 (ctrl-z) from the end of the stream
                    break;
                }
                if (b == '\n') {
                    if (first != last && buffer[first] == ' ') {
                        // \n SPACE -> empty
                        first++;
                        continue;
                    }
                    // "\n NON-SPACE" goes here and we process it as regular \n
                } else if (b == '\r') {
                    if (first == last) {
                        // \r EOF -> \n
                        b = '\n';
                    } else {
                        byte b2 = buffer[first];
                        if (b2 == ' ') {
                            // \r SPACE -> empty
                            first++;
                            continue;
                        }
                        if (b2 != '\n') {
                            // \r _ -> \n _
                            b = '\n';
                        } else if (first + 1 == last || buffer[first + 1] != ' ') {
                            // \r \n EOF -> \n
                            // \r \n _ -> \n _
                            b = '\n';
                            first++;
                        } else {
                            // \r \n SPACE -> continue
                            first += 2;
                            continue;
                        }
                    }
                }
                buf[i++] = b;
            }
            this.first = first;
        }
        if (i != off) {
            return i - off;
        }
        return eofSeen ? -1 : 0;
    }
}
