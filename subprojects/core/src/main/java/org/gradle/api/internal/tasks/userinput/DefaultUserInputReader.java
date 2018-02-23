/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.userinput;

import org.gradle.api.UncheckedIOException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

public class DefaultUserInputReader implements UserInputReader {

    private static final char UNIX_NEW_LINE = '\n';
    private static final char WINDOWS_NEW_LINE = '\r';

    @Override
    public String readInput() {
        Reader br = new InputStreamReader(System.in);
        StringBuilder out = new StringBuilder();

        while (true) {
            try {
                int c = br.read();

                if (isEOF(c)) {
                    return null;
                }

                if (!isLineSeparator((char)c)) {
                    out.append((char)c);
                } else {
                    break;
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        return out.toString();
    }

    private boolean isEOF(int c) {
        return c == 4 || c == -1;
    }

    private boolean isLineSeparator(char c) {
        return c == UNIX_NEW_LINE || c == WINDOWS_NEW_LINE;
    }
}
