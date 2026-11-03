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
import org.gradle.internal.logging.format.TersePrettyDurationFormatter;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.nativeintegration.console.ConsoleMetaData;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DefaultWorkInProgressFormatter {
    private final static List<StyledTextOutputEvent.Span> IDLE_SPANS = Collections.singletonList(new StyledTextOutputEvent.Span("> IDLE"));
    private static final String SEPARATOR = " > ";
    // As in the class names shortened by JavaClassNameFormatter
    private static final String ELLIPSIS = "...";
    // A shorter end is of little use
    private static final int MIN_END_LENGTH = 10;
    // In place of a dropped end, to show that the line goes on
    private static final String MORE = SEPARATOR + ELLIPSIS;
    private final ConsoleMetaData consoleMetaData;
    private final TersePrettyDurationFormatter durationFormatter = new TersePrettyDurationFormatter();
    // Shorter in tests
    private final long timeShownAfterMillis = Long.getLong("org.gradle.internal.console.time-shown-after", 10_000);

    public DefaultWorkInProgressFormatter(ConsoleMetaData consoleMetaData) {
        this.consoleMetaData = consoleMetaData;
    }

    public List<StyledTextOutputEvent.Span> format(ProgressOperation op, long now) {
        StringBuilder builder = new StringBuilder();
        // Its time is shown, so the time of a slow test class is seen, not only the time of its test task
        ProgressOperation deepestWithMessage = null;
        ProgressOperation current = op;
        while (current != null && !"org.gradle.internal.progress.BuildProgressLogger".equals(current.getCategory())) {
            String message = current.isHiddenWorkAction() ? null : current.getMessage();
            if (message != null) {
                if (deepestWithMessage == null) {
                    deepestWithMessage = current;
                }
                builder.insert(0, SEPARATOR).insert(SEPARATOR.length(), message);
                String owner = current.getOwner();
                if (owner != null) {
                    builder.insert(0, SEPARATOR).insert(SEPARATOR.length(), owner);
                }
            }
            current = current.getParent();
        }
        if (deepestWithMessage == null) {
            return IDLE_SPANS;
        }
        ProgressOperation countOwner = op.getWorkActionCountOwner();
        if (countOwner != null) {
            int completed = countOwner.getCompletedWorkActions();
            builder.append(SEPARATOR).append(completed).append(completed == 1 ? " work action completed" : " work actions completed");
        }
        builder.delete(0, 1);

        int maxWidth = maxWidth();
        long elapsed = now - deepestWithMessage.getStartTimestamp();
        if (elapsed >= timeShownAfterMillis) {
            String time = "  " + durationFormatter.format(elapsed);
            int width = maxWidth - time.length();
            // The time is only shown next to a part of the message of its operation
            if (keepsEnd(builder, width)) {
                return Arrays.asList(
                    new StyledTextOutputEvent.Span(StyledTextOutput.Style.Header, trim(builder, width)),
                    new StyledTextOutputEvent.Span(StyledTextOutput.Style.Normal, time)
                );
            }
        }
        return Collections.singletonList(new StyledTextOutputEvent.Span(StyledTextOutput.Style.Header, trim(builder, maxWidth)));
    }

    public List<StyledTextOutputEvent.Span> format() {
        return IDLE_SPANS;
    }

    private int maxWidth() {
        // Don't write to the right-most column, as on some consoles the cursor will wrap to the next line and currently wrapping causes
        // layout weirdness
        int cols = consoleMetaData.getCols();
        if (cols > 0) {
            return cols - 1;
        }
        // Assume 80 wide. This is to minimize wrapping on console where we don't know the width (eg mintty)
        // It's not intended to be a correct solution, simply a work around
        return 79;
    }

    private static String trim(StringBuilder formattedString, int width) {
        if (width < formattedString.length()) {
            return cut(formattedString.toString(), width);
        }
        return formattedString.toString();
    }

    // Whether a part of the last message of the line is kept at the given width
    private static boolean keepsEnd(StringBuilder line, int width) {
        if (line.length() <= width) {
            return true;
        }
        int separatorIndex = line.indexOf(SEPARATOR);
        if (separatorIndex < 0) {
            // At least one character of the message after the leading "> ", other than an ellipsis
            return width > 2 && cut(line.toString(), width).replace(ELLIPSIS, "").length() > 2;
        }
        return roomForEnd(separatorIndex, width) >= MIN_END_LENGTH;
    }

    private static String cut(String line, int width) {
        int separatorIndex = line.indexOf(SEPARATOR);
        if (separatorIndex < 0) {
            return cutFirstSegment(line, width);
        }
        int headLength = separatorIndex + SEPARATOR.length();
        int room = roomForEnd(separatorIndex, width);
        if (room >= MIN_END_LENGTH) {
            return line.substring(0, headLength) + ELLIPSIS + end(line, room);
        }
        String firstSegment = line.substring(0, separatorIndex);
        if (room >= 0) {
            return firstSegment + MORE;
        }
        if (width >= 2 * MORE.length()) {
            return cutFirstSegment(firstSegment, width - MORE.length()) + MORE;
        }
        // On a very narrow console, the whole width is given to the first segment
        return firstSegment.length() <= width ? firstSegment : cutFirstSegment(firstSegment, width);
    }

    // The width left for the end of a line, after its first segment, the separator and the ellipsis
    private static int roomForEnd(int separatorIndex, int width) {
        return width - separatorIndex - SEPARATOR.length() - ELLIPSIS.length();
    }

    // The task name is kept when its length is at most half of the width, else the first segment is cut near half of the width
    private static String cutFirstSegment(String firstSegment, int width) {
        int taskNameStart = firstSegment.lastIndexOf(':');
        if (taskNameStart >= 0) {
            int taskNameLength = firstSegment.length() - taskNameStart;
            int startLength = width - ELLIPSIS.length() - taskNameLength;
            if (taskNameLength <= width / 2 && startLength > 0) {
                return start(firstSegment, startLength) + ELLIPSIS + firstSegment.substring(taskNameStart);
            }
        }
        String start = start(firstSegment, width / 2);
        int room = width - start.length() - ELLIPSIS.length();
        if (room <= 0) {
            return prefix(firstSegment, width);
        }
        return start + ELLIPSIS + end(firstSegment, room);
    }

    // The start of the first segment, of at most the given length, so that a project name is not shown in part
    private static String start(String firstSegment, int length) {
        int projectNameEnd = firstSegment.lastIndexOf(':', length);
        return projectNameEnd < 0 ? prefix(firstSegment, length) : firstSegment.substring(0, projectNameEnd);
    }

    // The end of the text, of at most the given length, from its first boundary with at least half of this length after it, if any
    private static String end(String text, int length) {
        int startIndex = text.length() - length;
        int lastBoundaryIndex = text.length() - (length + 1) / 2;
        for (int i = startIndex; i <= lastBoundaryIndex; i++) {
            if (isBoundary(text.charAt(i - 1), text.charAt(i))) {
                return text.substring(i);
            }
        }
        return text.substring(splitsSurrogatePair(text, startIndex) ? startIndex + 1 : startIndex);
    }

    // The start of the text, of at most the given length
    private static String prefix(String text, int length) {
        return text.substring(0, splitsSurrogatePair(text, length) ? length - 1 : length);
    }

    // A character outside of the Basic Multilingual Plane, such as an emoji, is not shown in half
    private static boolean splitsSurrogatePair(String text, int index) {
        return index > 0 && index < text.length() && Character.isSurrogatePair(text.charAt(index - 1), text.charAt(index));
    }

    // After a space followed by a letter or a digit, after a '.' followed by a letter, or at a change from lower case to upper case.
    // So a size such as "1.7 MB" is not cut to "7 MB", and the end does not start with the '>' of a separator.
    private static boolean isBoundary(char before, char after) {
        return (before == ' ' && Character.isLetterOrDigit(after))
            || (before == '.' && Character.isLetter(after))
            || (Character.isLowerCase(before) && Character.isUpperCase(after));
    }
}
