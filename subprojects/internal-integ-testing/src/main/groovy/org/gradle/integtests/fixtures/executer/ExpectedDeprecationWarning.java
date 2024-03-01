/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.fixtures.executer;

import com.google.common.base.Preconditions;

import java.util.List;

/**
 * Represents a deprecation warning message that is expected to be emitted by a test.
 * <p>
 * This class exists to support the detection of deprecation warnings that span multiple lines,
 * which may include the keyword "{@code deprecated}" multiple times.
 */
public final class ExpectedDeprecationWarning {
    private final String message;
    private final int numLines;

    public ExpectedDeprecationWarning(String message) {
        Preconditions.checkArgument(message != null && !message.isEmpty(), "message must not be null or empty");
        this.message = message;
        this.numLines = message.split("\n").length;
    }

    /**
     * Get the (possibly multi-line) message that is expected to be emitted by a test.
     * @return the expected message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Get the number of lines that the expected message spans.
     * @return the number of lines in this message
     */
    public int getNumLines() {
        return numLines;
    }

    /**
     * Check if the given lines, starting at the given index, match the expected message.
     * @param lines the lines to check
     * @param startIndex the index of the first line to check
     * @return {@code true} if the lines match the expected message, {@code false} otherwise
     */
    public boolean matchesNextLines(List<String> lines, int startIndex) {
        if (numLines == 1) {
            return lines.get(startIndex).equals(message); // Quicker match for single-line warnings
        } else {
            String actualLines = String.join("\n", lines.subList(startIndex, Math.min(startIndex + numLines, lines.size())));
            return message.equals(actualLines);
        }
    }

    @Override
    public String toString() {
        return message;
    }
}
