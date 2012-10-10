/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.util;

import org.gradle.api.Action;
import org.gradle.internal.SystemProperties;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An OutputStream which separates bytes written into lines. Uses the platform default encoding. Is not thread safe.
 *
 * @author Hans Dockter
 */
public class LineBufferingOutputStream extends OutputStream {
    private boolean hasBeenClosed;
    private final byte[] lineSeparator;
    private final int bufferIncrement;
    private final Action<String> action;
    private byte[] buf;
    private int count;

    public LineBufferingOutputStream(Action<String> action) {
        this(action, 2048);
    }

    public LineBufferingOutputStream(Action<String> action, int bufferLength) {
        this.action = action;
        bufferIncrement = bufferLength;
        buf = new byte[bufferLength];
        count = 0;
        lineSeparator = SystemProperties.getLineSeparator().getBytes();
    }

    /**
     * Closes this output stream and releases any system resources associated with this stream. The general contract of
     * <code>close</code> is that it closes the output stream. A closed stream cannot perform output operations and
     * cannot be reopened.
     */
    public void close() throws IOException {
        flush();
        hasBeenClosed = true;
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

        if (count == buf.length) {
            // grow the buffer
            final int newBufLength = buf.length + bufferIncrement;
            final byte[] newBuf = new byte[newBufLength];

            System.arraycopy(buf, 0, newBuf, 0, buf.length);
            buf = newBuf;
        }

        buf[count] = (byte) b;
        count++;
        if (endsWithLineSeparator()) {
            flush();
        }
    }

    private boolean endsWithLineSeparator() {
        if (count < lineSeparator.length) {
            return false;
        }
        for (int i = 0; i < lineSeparator.length; i++) {
            if (buf[count - lineSeparator.length + i] != lineSeparator[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Flushes this output stream and forces any buffered output bytes to be written out. The general contract of
     * <code>flush</code> is that calling it is an indication that, if any bytes previously written have been buffered
     * by the implementation of the output stream, such bytes should immediately be written to their intended
     * destination.
     */
    public void flush() {
        if (count != 0) {
            action.execute(new String(buf, 0, count));
        }
        reset();
    }

    private void reset() {
        if (buf.length > bufferIncrement) {
            buf = new byte[bufferIncrement];
        }
        count = 0;
    }
}