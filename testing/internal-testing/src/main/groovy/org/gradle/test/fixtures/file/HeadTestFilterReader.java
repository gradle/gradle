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
package org.gradle.test.fixtures.file;

import java.io.BufferedReader;
import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * A configurable {@link FilterReader} for tests that need a content filter type which takes
 * properties, replacing the use of Ant's parameterised filters (such as {@code HeadFilter}).
 *
 * <p>It emits the first {@code lines} lines of its input after skipping the first {@code skip}
 * lines. Properties are applied by Gradle via JavaBean setters <em>after</em> construction, so the
 * transformation is performed lazily on first read.
 */
public class HeadTestFilterReader extends FilterReader {
    private int lines = Integer.MAX_VALUE;
    private int skip;
    private Reader transformed;

    public HeadTestFilterReader(Reader in) {
        super(in);
    }

    public void setLines(int lines) {
        this.lines = lines;
    }

    public void setSkip(int skip) {
        this.skip = skip;
    }

    private Reader transformed() throws IOException {
        if (transformed == null) {
            BufferedReader reader = new BufferedReader(in);
            List<String> kept = new ArrayList<String>();
            String line;
            int index = 0;
            while (kept.size() < lines && (line = reader.readLine()) != null) {
                if (index++ >= skip) {
                    kept.add(line);
                }
            }
            transformed = new StringReader(String.join("\n", kept));
        }
        return transformed;
    }

    @Override
    public int read() throws IOException {
        return transformed().read();
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        return transformed().read(cbuf, off, len);
    }
}
