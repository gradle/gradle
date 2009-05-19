/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.logging;


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

import java.io.IOException;
import java.io.OutputStream;

import org.slf4j.Marker;

/**
 * @author Hans Dockter
 */
public class StandardOutputLoggingAdapter extends OutputStream {
    /**
     * Used to maintain the contract of [EMAIL PROTECTED] #close()}.
     */
    private boolean hasBeenClosed = false;

    private final byte[] lineSeparator;
    private final int bufferIncrement;

    /**
     * The internal buffer where data is stored.
     */
    private byte[] buf;

    /**
     * The number of valid bytes in the buffer. This value is always in the range <tt>0</tt> through
     * <tt>buf.length</tt>; elements <tt>buf[0]</tt> through <tt>buf[count-1]</tt> contain valid byte data.
     */
    private int count;

    /**
     * The category to write to.
     */
    private Logger logger;

    /**
     * The priority to use when writing to the Category.
     */
    private Level level;

    /**
     * The marker to use when writing to the Category.
     */
    private Marker marker;

    /**
     * Creates the OutputStream to flush to the given Category.
     *
     * @param log the Logger to write to
     * @param level the Level to use when writing to the Logger
     * @throws IllegalArgumentException if cat == null or priority == null
     */
    public StandardOutputLoggingAdapter(Logger log, Level level)
            throws IllegalArgumentException {
        this(log, level, 2048);
    }

    /**
     * Creates the OutputStream to flush to the given Category.
     *
     * @param log the Logger to write to
     * @param level the Level to use when writing to the Logger
     * @param bufferLength The initial buffer length to use
     * @throws IllegalArgumentException if cat == null or priority == null
     */
    public StandardOutputLoggingAdapter(Logger log, Level level, int bufferLength)
            throws IllegalArgumentException {
        bufferIncrement = bufferLength;
        if (log == null) {
            throw new IllegalArgumentException("cat == null");
        }
        if (level == null) {
            throw new IllegalArgumentException("priority == null");
        }

        this.level = level;

        logger = log;
        buf = new byte[bufferLength];
        count = 0;
        lineSeparator = System.getProperty("line.separator").getBytes();
    }

    /**
     * Closes this output stream and releases any system resources associated with this stream. The general contract of
     * <code>close</code> is that it closes the output stream. A closed stream cannot perform output operations and
     * cannot be reopened.
     */
    public void close() {
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
            int length = count;
            if (endsWithLineSeparator()) {
                length -= lineSeparator.length;
            }
            String message = new String(buf, 0, length);
            logger.filterAndLog(Logger.FQCN, marker, level, message, null, null);
        }
        reset();
    }

    private void reset() {
        if (buf.length > bufferIncrement) {
            buf = new byte[bufferIncrement];
        }
        count = 0;
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }

    public Marker getMarker() {
        return marker;
    }

    public void setMarker(Marker marker) {
        this.marker = marker;
    }
}

