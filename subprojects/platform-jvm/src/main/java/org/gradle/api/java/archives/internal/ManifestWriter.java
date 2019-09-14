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

import com.google.common.base.Utf8;

import java.io.FilterWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * The manifest specification says manifest lines must not exceed
 * 72 bytes when strings are encoded to UTF-8, so this writer inserts line breaks accordingly.
 * Note: keys can't exceed 70 bytes (as per spec). Values can't contain newlines.
 */
public class ManifestWriter extends FilterWriter {
    private static final char[] NEW_LINE = {'\r', '\n'};
    private static final char[] CONTINUATION = {'\r', '\n', ' '};
    private static final char[] COLON_SPACE = {':', ' '};
    private static final int MAX_LINE_SIZE = 72;

    private int lineSize;

    /**
     * Create a new manifest writer.
     *
     * @param out a Writer object to provide the underlying stream.
     * @throws NullPointerException if <code>out</code> is <code>null</code>
     */
    protected ManifestWriter(Writer out) {
        super(out);
    }

    /**
     * Writes newline sequence: {@code "\r\n"}
     * @throws IOException if write fails
     */
    public void newLine() throws IOException {
        lineSize = 0;
        out.write(NEW_LINE);
    }

    /**
     * Writes {@code "\r\n "} sequence that is used for wrapping long values.
     * @throws IOException if write fails
     */
    public void continuation() throws IOException {
        lineSize = 1;
        out.write(CONTINUATION);
    }

    /**
     * Writes {@code Name: Value} pair to the manifest.
     * @param key key. Key should be alphanumeric and it should fit in 70 bytes
     * @param value value. Value must not contain newline characters
     * @throws IOException if write fails
     */
    public void entry(String key, String value) throws IOException {
        write(key);
        colonSpace();
        write(value);
        newLine();
    }

    private void colonSpace() throws IOException {
        // Key cannot be wrapped, so we don't have to consider wrapping here
        lineSize += COLON_SPACE.length;
        out.write(COLON_SPACE);
    }

    private static boolean looksLikeSurrogateSplit(int position, char c) {
        return position + 2 > MAX_LINE_SIZE && Character.isHighSurrogate(c);
    }

    @Override
    public void write(int c) throws IOException {
        int charSize = utf8Bytes((char) c);
        int lineSize = this.lineSize + charSize;
        if (lineSize <= MAX_LINE_SIZE &&
            !looksLikeSurrogateSplit(lineSize, (char) c)) {
            this.lineSize = lineSize;
        } else {
            continuation();
            this.lineSize += charSize;
        }
        out.write(c);
    }

    @Override
    public void write(String str, int off, int len) throws IOException {
        if (len == 0) {
            // Just in case
            return;
        }
        // We optimize for off=0, len=str.length()
        if (off == 0 && len == str.length() && lineSize + len <= MAX_LINE_SIZE) {
            int encodedLength = Utf8.encodedLength(str);
            int lineSize = this.lineSize + encodedLength;
            if (lineSize <= MAX_LINE_SIZE) {
                out.write(str, off, len);
                this.lineSize = lineSize;
                return;
            }
        }
        // Bad luck, let's write chars one by one

        int lastIndex = off + len;
        int lineSize = this.lineSize;
        for (int blockStart = off; blockStart < lastIndex;) {
            int blockEnd;
            int charsConsumed = 0;
            for (blockEnd = blockStart; blockEnd < lastIndex; blockEnd++) {
                char c = str.charAt(blockEnd);
                int charSize = utf8Bytes(c);
                if (lineSize + charSize > MAX_LINE_SIZE ||
                    // Ensure the line would hold both surrogate pairs
                    looksLikeSurrogateSplit(lineSize + charSize, c)) {
                    break;
                }
                charsConsumed++;
                lineSize += charSize;
            }
            // Two cases:
            // 1) The first char does not fit
            if (blockStart == blockEnd) {
                continuation();
                lineSize = this.lineSize;
                continue;
            }
            // 2) A part of the string fits
            out.write(str, blockStart, blockEnd - blockStart);
            blockStart = blockEnd;

            // Write continuation if the value continues
            if (blockStart < lastIndex) {
                continuation();
                lineSize = this.lineSize;
            }
        }
        this.lineSize = lineSize;
    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        if (len == 0) {
            // Just in case
            return;
        }
        int lastIndex = off + len;
        int lineSize = this.lineSize;
        for (int blockStart = off; blockStart < lastIndex;) {
            int blockEnd;
            for (blockEnd = blockStart; blockEnd < lastIndex; blockEnd++) {
                char c = cbuf[blockEnd];
                int charSize = utf8Bytes(c);
                if (lineSize + charSize > MAX_LINE_SIZE ||
                    // Ensure the line would hold both surrogate pairs
                    looksLikeSurrogateSplit(lineSize + charSize, c)) {
                    break;
                }
                lineSize += charSize;
            }
            // Two cases:
            // 1) The first char does not fit
            if (blockStart == blockEnd) {
                continuation();
                lineSize = this.lineSize;
                continue;
            }
            // 2) A part of the string fits
            out.write(cbuf, blockStart, blockEnd - blockStart);
            blockStart = blockEnd;

            // Write continuation if the value continues
            if (blockStart < lastIndex) {
                continuation();
                lineSize = this.lineSize;
            }
        }
        this.lineSize = lineSize;
    }

    /**
     * Returns the number of bytes a character would consume.
     * Note: it assumes valid character sequence. For instance, it assumes
     * high surrogate will be followed by a low surrogate.
     * That might result in overestimation, however it does not hurt.
     * At worst the generated manifest would wrap a bit earlier than it could.
     * @param c character
     * @return number of bytes a codepoint would consume
     */
    private static int utf8Bytes(char c) {
        if (c <= 0x007F) {
            return 1;
        }
        if (c <= 0x07FF || Character.isSurrogate(c)) {
            return 2;
        }
        return 3;
    }
}
