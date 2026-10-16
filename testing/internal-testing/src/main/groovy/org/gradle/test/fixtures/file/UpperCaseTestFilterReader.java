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

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;

/**
 * A {@link FilterReader} that converts content to upper case, for tests that need a content filter
 * type which takes no configuration. Replaces the use of Ant's parameter-less filters (such as
 * {@code StripJavaComments}).
 */
public class UpperCaseTestFilterReader extends FilterReader {
    public UpperCaseTestFilterReader(Reader in) {
        super(in);
    }

    @Override
    public int read() throws IOException {
        int c = super.read();
        return c == -1 ? c : Character.toUpperCase(c);
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        int count = super.read(cbuf, off, len);
        for (int i = 0; i < count; i++) {
            cbuf[off + i] = Character.toUpperCase(cbuf[off + i]);
        }
        return count;
    }
}
