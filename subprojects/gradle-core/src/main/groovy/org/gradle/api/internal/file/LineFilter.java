/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file;

import groovy.lang.Closure;

import java.io.*;

public class LineFilter extends Reader{
    private Closure closure;
    private String transformedLine;
    private int transformedIndex = 0;
    private BufferedReader bufferedIn;
    private String lineTerminator;


    /**
     * Creates a new filtered reader.
     *
     * @param closure a Closure to filter each line
     * @throws NullPointerException if <code>in</code> is <code>null</code>
     */
    public LineFilter(Reader in, Closure closure) {
        super();
        this.bufferedIn = new BufferedReader(in);
        this.closure = closure;
        lineTerminator = System.getProperty("line.separator");
    }

    private String getTransformedLine() throws IOException {
        String originalLine = bufferedIn.readLine();
        if (originalLine != null) {
            StringBuilder result = new StringBuilder((String) closure.call(originalLine));
            result.append(lineTerminator);
            return result.toString();
        }
        return null;
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
        if (transformedLine == null) {
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
                }
                else {
                    return i;
                }
            }
            cbuf[off + i] = (char) c;
        }
        return len;
    }

    public void close() throws IOException {
        bufferedIn.close();
    }
}
