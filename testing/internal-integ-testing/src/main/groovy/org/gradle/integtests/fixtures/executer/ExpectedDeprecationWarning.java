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
import java.util.regex.Pattern;

/**
 * Represents a deprecation warning message that is expected to be emitted by a test.
 * <p>
 * This class exists to support the detection of deprecation warnings that span multiple lines,
 * which may include the keyword "{@code deprecated}" multiple times.
 */
public abstract class ExpectedDeprecationWarning {

    private final int numLines;

    public ExpectedDeprecationWarning(int numLines) {
        this.numLines = numLines;
    }

    public static ExpectedDeprecationWarning withMessage(String message) {
        Preconditions.checkArgument(message != null && !message.isEmpty(), "message must not be null or empty");
        int numLines = message.split("\n").length;
        return new ExpectedDeprecationWarning(numLines) {
            @Override
            protected boolean matchesNextLines(String nextLines) {
                return message.equals(nextLines);
            }

            @Override
            public String toString() {
                return message;
            }
        };
    }

    public static ExpectedDeprecationWarning withSingleLinePattern(String pattern) {
        Preconditions.checkArgument(pattern != null && !pattern.isEmpty(), "pattern must not be null or empty");
        return withPattern(Pattern.compile(pattern), 1);
    }

    public static ExpectedDeprecationWarning withMultiLinePattern(String pattern, int numLines) {
        Preconditions.checkArgument(pattern != null && !pattern.isEmpty(), "pattern must not be null or empty");
        return withPattern(Pattern.compile("(?m)" + pattern), numLines);
    }

    private static ExpectedDeprecationWarning withPattern(Pattern pattern, int numLines) {
        return new ExpectedDeprecationWarning(numLines) {
            @Override
            protected boolean matchesNextLines(String nextLines) {
                return pattern.matcher(nextLines).matches();
            }

            @Override
            public String toString() {
                return pattern.toString();
            }
        };
    }

    /**
     * Get the number of lines that the expected message spans.
     *
     * @return the number of lines in this message
     */
    public int getNumLines() {
        return numLines;
    }

    /**
     * Check if the given lines, starting at the given index, match the expected message.
     *
     * @param lines the lines to check
     * @param startIndex the index of the first line to check
     * @return {@code true} if the lines match the expected message, {@code false} otherwise
     */
    public boolean matchesNextLines(List<String> lines, int startIndex) {
        String nextLines = numLines == 1
            ? lines.get(startIndex)
            : String.join("\n", lines.subList(startIndex, Math.min(startIndex + numLines, lines.size())));
        return matchesNextLines(nextLines);
    }

    protected abstract boolean matchesNextLines(String nextLines);
}
