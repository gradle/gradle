/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.launcher.cli;

import org.jspecify.annotations.NullMarked;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Passes on only complete lines, so that a reader following the target as it grows never sees a partially written line.
 * Whatever follows the last line ending is held back until the next one, or until this stream is closed.
 */
@NullMarked
class LineBufferingOutputStream extends OutputStream {
    private final OutputStream target;
    private final ByteArrayOutputStream partialLine = new ByteArrayOutputStream();

    LineBufferingOutputStream(OutputStream target) {
        this.target = target;
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[]{(byte) b}, 0, 1);
    }

    @Override
    public void write(byte[] bytes, int offset, int length) throws IOException {
        int end = offset + length;
        int lastLineEnd = -1;
        for (int i = end - 1; i >= offset; i--) {
            if (bytes[i] == '\n') {
                lastLineEnd = i;
                break;
            }
        }
        if (lastLineEnd < 0) {
            partialLine.write(bytes, offset, length);
            return;
        }
        partialLine.write(bytes, offset, lastLineEnd + 1 - offset);
        partialLine.writeTo(target);
        partialLine.reset();
        partialLine.write(bytes, lastLineEnd + 1, end - lastLineEnd - 1);
        target.flush();
    }

    @Override
    public void flush() throws IOException {
        // Deliberately not flushing the partial line
        target.flush();
    }

    @Override
    public void close() throws IOException {
        partialLine.writeTo(target);
        partialLine.reset();
        target.close();
    }
}
