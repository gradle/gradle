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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Infers whether a file is a binary file or not by checking if there are any ASCII
 * control characters in the file.  If so, then it is likely a binary file.
 *
 * Note that all operations should delegate to the underlying input stream, except
 * for mark() and markSupported() which are unsupported.
 */
public class FileContentTypeDetectingInputStream extends FilterInputStream {
    private boolean controlCharactersFound;

    public FileContentTypeDetectingInputStream(InputStream delegate) {
        super(delegate);
    }

    @Override
    public int read() throws IOException {
        int next = super.read();
        checkForControlCharacters(next);
        return next;
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        int read = super.read(buffer);
        checkForControlCharacters(buffer, read);
        return read;
    }

    @Override
    public int read(byte[] buffer, int off, int len) throws IOException {
        int read = super.read(buffer, off, len);
        checkForControlCharacters(buffer, read);
        return read;
    }

    @Override
    public synchronized void reset() throws IOException {
        controlCharactersFound = false;
        super.reset();
    }

    @Override
    public synchronized void mark(int readlimit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    private void checkForControlCharacters(byte[] buffer, int length) {
        if (!controlCharactersFound) {
            for (int i = 0; i < length; i++) {
                checkForControlCharacters(buffer[i]);
            }
        }
    }

    private void checkForControlCharacters(int b) {
        if (!controlCharactersFound && isControlCharacter(b)) {
            controlCharactersFound = true;
        }
    }

    private static boolean isControlCharacter(int c) {
        return isInControlRange(c) && isNotCommonTextChar(c);
    }

    private static boolean isInControlRange(int c) {
        return c >= 0x00 && c < 0x20;
    }

    private static boolean isNotCommonTextChar(int c) {
        return !Character.isWhitespace(c);
    }

    public FileContentType getContentType() {
        return controlCharactersFound ? FileContentType.BINARY : FileContentType.TEXT;
    }

    @Override
    public void close() throws IOException {
        super.close();
    }
}
