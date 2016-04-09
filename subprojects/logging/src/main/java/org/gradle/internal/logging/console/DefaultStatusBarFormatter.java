/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.logging.console;

import org.gradle.internal.nativeintegration.console.ConsoleMetaData;

public class DefaultStatusBarFormatter {
    private final ConsoleMetaData consoleMetaData;

    public DefaultStatusBarFormatter(ConsoleMetaData consoleMetaData) {
        this.consoleMetaData = consoleMetaData;
    }

    public String format(ProgressOperation op) {
        StringBuilder builder = new StringBuilder();
        ProgressOperation current = op;
        while(current != null) {
            String message = current.getMessage();
            current = current.getParent();

            if (message == null) {
                continue;
            }

            builder.insert(0, " > ").insert(3, message);
        }
        if (builder.length() > 0) {
            builder.delete(0, 1);
        }
        return trim(builder);
    }

    private String trim(StringBuilder formattedString) {
        // Don't write to the right-most column, as on some consoles the cursor will wrap to the next line
        int width = consoleMetaData.getCols() - 1;
        if (width > 0 && width < formattedString.length()) {
            return formattedString.substring(0, width);
        }
        return formattedString.toString();
    }
}
