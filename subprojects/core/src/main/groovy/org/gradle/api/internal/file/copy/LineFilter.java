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
package org.gradle.api.internal.file.copy;

import groovy.lang.Closure;

import org.gradle.internal.SystemProperties;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

public class LineFilter extends Reader {
    private final Closure closure;
    private String transformedLine;
    private int transformedIndex;
    private final BufferedReader bufferedIn;
    private final Reader in;

    /**
     * Creates a new filtered reader.
     *
     * @param closure a Closure to filter each line
     * @throws NullPointerException if <code>in</code> is <code>null</code>
     */
    public LineFilter(Reader in, Closure closure) {
        this.in = in;
        this.bufferedIn = new BufferedReader(in);
        this.closure = closure;
    }

    private String getTransformedLine() throws IOException {
        StringBuilder line = new StringBuilder();
        boolean eol = false;
        int ch;
        while (!eol && (ch = bufferedIn.read()) >= 0) {
            if (ch == '\n') {
                eol = true;
            } else if (ch == '\r') {
                eol = true;
                bufferedIn.mark(1);
                if (bufferedIn.read() != '\n') {
                    bufferedIn.reset();
                }
            } else {
                line.append((char) ch);
            }
        }
        if (line.length() == 0 && !eol) {
            return null;
        }

        StringBuilder result = new StringBuilder();
        result.append(closure.call(line.toString()).toString());
        if (eol) {
            result.append(SystemProperties.getLineSeparator());
        }
        return result.toString();
    }

    private void ensureData() throws IOException {
        if (transformedLine == null || transformedIndex >= transformedLine.length()) {
            transformedLine = getTransformedLine();
            transformedIndex = 0;
        }
    }

    @Override
    public int read() throws IOException {
        ensureData();
        if (transformedLine == null || transformedLine.length() == 0) {
            return -1;
        }
        return transformedLine.charAt(transformedIndex++);
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        for (int i = 0; i < len; i++) {
            final int c = read();
            if (c == -1) {
                if (i == 0) {
                    return -1;
                } else {
                    return i;
                }
            }
            cbuf[off + i] = (char) c;
        }
        return len;
    }

    public void close() throws IOException {
        in.close();
    }
}
