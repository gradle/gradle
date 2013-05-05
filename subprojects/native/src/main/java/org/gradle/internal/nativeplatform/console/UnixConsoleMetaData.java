/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.nativeplatform.console;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnixConsoleMetaData implements ConsoleMetaData {
    public static final Logger LOGGER = LoggerFactory.getLogger(UnixConsoleMetaData.class);
    private final boolean stdout;
    private final boolean stderr;

    public UnixConsoleMetaData(boolean stdout, boolean stderr) {
        this.stdout = stdout;
        this.stderr = stderr;
    }

    public boolean isStdOut() {
        return stdout;
    }

    public boolean isStdErr() {
        return stderr;
    }

    public int getCols() {
        final String columns = System.getenv("COLUMNS");
        if (columns != null) {
            try {
                return Integer.parseInt(columns);
            } catch (NumberFormatException ex) {
                LOGGER.debug("Cannot parse COLUMNS environment variable to get console width. Value: '{}'", columns);
            }
        }
        return 0;
    }
}
