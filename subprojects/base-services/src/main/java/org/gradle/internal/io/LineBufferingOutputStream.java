/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.io;

import org.gradle.internal.SystemProperties;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An OutputStream which separates bytes written into lines of text. Uses the platform default encoding. Is not thread safe.
 */
public class LineBufferingOutputStream extends OutputStream {
    private final static int LINE_MAX_LENGTH = 1024 * 1024; // Split line if a single line goes over 1 MB
    private boolean hasBeenClosed;
    private final TextStream handler;
    private StreamByteBuffer buffer;
    private final OutputStream output;
    private final byte lastLineSeparatorByte;
    private final int lineMaxLength;
    private int counter;

    public LineBufferingOutputStream(TextStream handler) {
        this(handler, 2048);
    }

    public LineBufferingOutputStream(TextStream handler, int bufferLength) {
        this(handler, bufferLength, LINE_MAX_LENGTH);
    }

    public LineBufferingOutputStream(TextStream handler, int bufferLength, int lineMaxLength) {
        this.handler = handler;
        buffer = new StreamByteBuffer(bufferLength);
        this.lineMaxLength = lineMaxLength;
        output = buffer.getOutputStream();
        byte[] lineSeparator = SystemProperties.getInstance().getLineSeparator().getBytes();
        lastLineSeparatorByte = lineSeparator[lineSeparator.length - 1];
    }

    /**
     * Closes this output stream and releases any system resources associated with this stream. The general contract of
     * <code>close</code> is that it closes the output stream. A closed stream cannot perform output operations and
     * cannot be reopened.
     */
    public void close() throws IOException {
        hasBeenClosed = true;
        flush();
        handler.endOfStream(null);
    }

    /**
     * Writes the specified byte to this output stream. The general contract for <code>write</code> is that one byte is
     * written to the output stream. The byte to be written is the eight low-order bits of the argument <code>b</code>.
     * The 24 high-order bits of <code>b</code> are ignored.
     *
     * @param b the <code>byte</code> to write
     * @throws java.io.IOException if an I/O error occurs. In particular, an <code>IOException</code> may be thrown if
     * the output stream has been closed.
     */
    public void write(final int b) throws IOException {
        if (hasBeenClosed) {
            throw new IOException("The stream has been closed.");
        }
        output.write(b);
        counter++;
        if (endsWithLineSeparator(b) || counter >= lineMaxLength) {
            flush();
        }
    }

    // only check for the last byte of a multi-byte line separator
    // besides this, always check for '\n'
    // this handles '\r' (MacOSX 9), '\r\n' (Windows) and '\n' (Linux/Unix/MacOSX 10)
    private boolean endsWithLineSeparator(int b) {
        byte currentByte = (byte) (b & 0xff);
        return currentByte == lastLineSeparatorByte || currentByte == '\n';
    }

    public void flush() {
        String text = buffer.readAsString();
        if (text.length() > 0) {
            handler.text(text);
        }
        counter = 0;
    }
}
