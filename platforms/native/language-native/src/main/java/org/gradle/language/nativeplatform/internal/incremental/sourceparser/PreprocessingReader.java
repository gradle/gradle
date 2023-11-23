/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.language.nativeplatform.internal.incremental.sourceparser;

import java.io.IOException;
import java.io.Reader;

/**
 * Replaces c-style comments with a single space, and removes line-continuation characters.
 * This code is largely adopted from org.apache.tools.ant.filters.StripJavaComments.
 */
public class PreprocessingReader {
    private final Reader reader;
    /**
     * The read-ahead characters, used for reading ahead up to 2 characters and pushing back into stream.
     * A value of -1 indicates that no character is in the buffer.
     */
    private int[] readAheadChars = new int[2];

    /**
     * Whether or not the parser is currently in the middle of a string literal.
     */
    private boolean inString;

    /**
     * Whether or not the last char has been a backslash.
     */
    private boolean quoted;

    public PreprocessingReader(Reader reader) {
        this.reader = reader;
        readAheadChars[0] = -1;
        readAheadChars[1] = -1;
    }

    /**
     * Collects the next line from the filtered stream into the given buffer. Does not include the line separators.
     *
     * @return true if next line is available (possibly empty), false when end of stream reached.
     */
    public boolean readNextLine(Appendable buffer) throws IOException {
        int ch;
        boolean read = false;
        while ((ch = read()) >= 0) {
            if (ch == '\n') {
                return true;
            }
            if (ch == '\r') {
                int next = next();
                if (next != '\n') {
                    pushBack(next);
                }
                return true;
            }
            buffer.append((char) ch);
            read = true;
        }
        return read;
    }

    /**
     * Returns the next character in the filtered stream:
     * <ul>
     *     <li>Comments will be replaced by a single space</li>
     *     <li>Line continuation (backslash-newline) will be removed</li>
     * </ul>
     */
    private int read() throws IOException {
        int ch = next();

        if (ch == '\\') {
            if (discardNewLine()) {
                return read();
            }
        }

        if (ch == '"' && !quoted) {
            inString = !inString;
            quoted = false;
        } else if (ch == '\\') {
            quoted = !quoted;
        } else {
            quoted = false;
            if (!inString) {
                if (ch == '/') {
                    ch = next();
                    if (ch == '/') {
                        while (ch != '\n' && ch != -1 && ch != '\r') {
                            ch = next();
                        }
                    } else if (ch == '*') {
                        while (ch != -1) {
                            ch = next();
                            if (ch == '*') {
                                ch = next();
                                while (ch == '*') {
                                    ch = next();
                                }

                                if (ch == '/') {
                                    ch = ' ';
                                    break;
                                }
                            }
                        }
                    } else {
                        pushBack(ch);
                        ch = '/';
                    }
                }
            }
        }

        return ch;
    }

    private boolean discardNewLine() throws IOException {
        int nextChar = next();
        if (nextChar == '\n') {
            return true; // '\\\n' discarded from stream
        } else if (nextChar == '\r') {
            int followingChar = next();
            if (followingChar == '\n') {
                return true; // '\\\r\n' discarded from stream
            }
            pushBack(nextChar);
            pushBack(followingChar);
            return false;
        } else {
            pushBack(nextChar);
            return false;
        }
    }

    private int next() throws IOException {
        if (readAheadChars[0] != -1) {
            int ch = readAheadChars[0];
            readAheadChars[0] = readAheadChars[1];
            readAheadChars[1] = -1;
            return ch;
        }

        return reader.read();
    }

    private void pushBack(int ch) {
        if (readAheadChars[1] != -1) {
            throw new IllegalStateException();
        }
        if (readAheadChars[0] != -1) {
            readAheadChars[1] = ch;
        } else {
            readAheadChars[0] = ch;
        }
    }
}
