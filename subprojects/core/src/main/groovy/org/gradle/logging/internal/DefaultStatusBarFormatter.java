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

package org.gradle.logging.internal;

import org.gradle.internal.console.ConsoleMetaData;

import java.util.List;

public class DefaultStatusBarFormatter implements StatusBarFormatter {
    private final ConsoleMetaData consoleMetaData;

    public DefaultStatusBarFormatter(ConsoleMetaData consoleMetaData) {
        this.consoleMetaData = consoleMetaData;
    }

    public String format(List<ConsoleBackedProgressRenderer.Operation> operations) {
        StringBuilder builder = new StringBuilder();
        for (ConsoleBackedProgressRenderer.Operation operation : operations) {
            String message = operation.getMessage();
            if (message == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append("> ");
            builder.append(message);
        }
        return trim(builder.toString());
    }

    private String trim(String formattedString) {
        int width = consoleMetaData.getCols();
        if (width > 0 && width < formattedString.length()) {
            return formattedString.substring(0, width);
        }
        return formattedString;
    }
}
