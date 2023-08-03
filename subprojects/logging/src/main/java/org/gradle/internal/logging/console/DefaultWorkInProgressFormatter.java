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

import org.gradle.internal.logging.events.StyledTextOutputEvent;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;

import java.util.Collections;
import java.util.List;

public class DefaultWorkInProgressFormatter {
    private final static List<StyledTextOutputEvent.Span> IDLE_SPANS = Collections.singletonList(new StyledTextOutputEvent.Span("> IDLE"));
    private final ConsoleMetaData consoleMetaData;

    public DefaultWorkInProgressFormatter(ConsoleMetaData consoleMetaData) {
        this.consoleMetaData = consoleMetaData;
    }

    public List<StyledTextOutputEvent.Span> format(ProgressOperation op) {
        StringBuilder builder = new StringBuilder();
        ProgressOperation current = op;
        while (current != null && !"org.gradle.internal.progress.BuildProgressLogger".equals(current.getCategory())) {
            String message = current.getMessage();
            current = current.getParent();

            if (message == null) {
                continue;
            }

            builder.insert(0, " > ").insert(3, message);
        }
        if (builder.length() > 0) {
            builder.delete(0, 1);
        } else {
            return IDLE_SPANS;
        }

        return Collections.singletonList(new StyledTextOutputEvent.Span(StyledTextOutput.Style.Header, trim(builder)));
    }

    public List<StyledTextOutputEvent.Span> format() {
        return IDLE_SPANS;
    }

    private String trim(StringBuilder formattedString) {
        // Don't write to the right-most column, as on some consoles the cursor will wrap to the next line and currently wrapping causes
        // layout weirdness
        int maxWidth;
        int cols = consoleMetaData.getCols();
        if (cols > 0) {
            maxWidth = cols - 1;
        } else {
            // Assume 80 wide. This is to minimize wrapping on console where we don't know the width (eg mintty)
            // It's not intended to be a correct solution, simply a work around
            maxWidth = 79;
        }
        if (maxWidth < formattedString.length()) {
            return formattedString.substring(0, maxWidth);
        }
        return formattedString.toString();
    }
}
