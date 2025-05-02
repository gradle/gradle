/*
 * Copyright 2018 the original author or authors.
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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import net.rubygrapefruit.ansi.AnsiParser;
import net.rubygrapefruit.ansi.console.AnsiConsole;
import net.rubygrapefruit.ansi.console.DiagnosticConsole;
import net.rubygrapefruit.ansi.token.NewLine;
import net.rubygrapefruit.ansi.token.Text;
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.Pair;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class LogContent {
    // see org.gradle.internal.logging.console.StyledTextOutputBackedRenderer.ISO_8601_DATE_TIME_FORMAT
    private final static Pattern DEBUG_PREFIX = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}[-+]\\d{4} \\[\\w+] \\[.+?] ");

    private final ImmutableList<String> lines;
    private final boolean definitelyNoDebugPrefix;
    private final boolean definitelyNoAnsiChars;

    private LogContent(ImmutableList<String> lines, boolean definitelyNoDebugPrefix, boolean definitelyNoAnsiChars) {
        this.lines = lines;
        this.definitelyNoDebugPrefix = definitelyNoDebugPrefix || lines.isEmpty();
        this.definitelyNoAnsiChars = definitelyNoAnsiChars || lines.isEmpty();
    }

    /**
     * Creates a new instance, from raw characters.
     */
    public static LogContent of(String chars) {
        return new LogContent(toLines(chars), false, false);
    }

    private static ImmutableList<String> toLines(String chars) {
        Builder<String> lines = ImmutableList.builder();
        int pos = 0;
        while (pos < chars.length()) {
            int next = chars.indexOf('\n', pos);
            if (next < 0) {
                lines.add(chars.substring(pos));
                pos = chars.length();
                continue;
            }

            lines.add(chars.substring(pos, getSubstringEnd(chars, pos, next)));
            pos = next + 1;
            if (pos == chars.length()) {
                // trailing EOL
                lines.add("");
            }
        }
        return lines.build();
    }

    private static int getSubstringEnd(String chars, int pos, int next) {
        if (next > pos && chars.charAt(next - 1) == '\r') {
             return next - 1;
        }
        return next;
    }

    /**
     * Creates a new instance from a sequence of lines (without the line separators).
     */
    public static LogContent of(List<String> lines) {
        return new LogContent(ImmutableList.copyOf(lines), false, false);
    }

    public static LogContent empty() {
        return new LogContent(ImmutableList.of(), true, true);
    }

    /**
     * Does not return the text of this content.
     */
    @Override
    public String toString() {
        // Intentionally not the text
        return lines.toString();
    }

    /**
     * Returns this content formatted using a new line char to separate lines.
     */
    public String withNormalizedEol() {
        if (lines.isEmpty()) {
            return "";
        }
        return Joiner.on('\n').join(lines);
    }

    /**
     * Returns this content separated into lines. The line does not include the line separator.
     */
    public ImmutableList<String> getLines() {
        return lines;
    }

    /**
     * Returns the first line. The text does not include the line separator.
     */
    public String getFirst() {
        return lines.get(0);
    }

    /**
     * Visits each line in this content. The line does not include the line separator.
     */
    public void eachLine(Action<? super String> action) {
        for (String line : lines) {
            action.execute(line);
        }
    }

    /**
     * Locates the log content starting with the first line that matches the given pattern, or null if no such line.
     *
     * @return a pair containing (content-before-matching-line, content-from-matching-line) or null if no match.
     */
    public @Nullable
    Pair<LogContent, LogContent> splitOnFirstMatchingLine(Pattern pattern) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (pattern.matcher(line).matches()) {
                LogContent before = new LogContent(lines.subList(0, i), definitelyNoDebugPrefix, definitelyNoAnsiChars);
                LogContent after = new LogContent(lines.subList(i, lines.size()), definitelyNoDebugPrefix, definitelyNoAnsiChars);
                return Pair.of(before, after);
            }
        }
        return null;
    }

    /**
     * Returns the number of lines that match the given pattern.
     */
    public int countMatches(Pattern pattern) {
        int count = 0;
        for (String line : lines) {
            if (pattern.matcher(line).matches()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Drops the first n lines.
     */
    public LogContent drop(int i) {
        return new LogContent(lines.subList(i, lines.size()), definitelyNoDebugPrefix, definitelyNoAnsiChars);
    }

    /**
     * Returns a copy of this log content with the debug prefix removed.
     */
    public LogContent removeDebugPrefix() {
        if (definitelyNoDebugPrefix) {
            return this;
        }
        List<String> result = new ArrayList<String>(lines.size());
        for (String line : lines) {
            java.util.regex.Matcher matcher = DEBUG_PREFIX.matcher(line);
            if (matcher.lookingAt()) {
                result.add(line.substring(matcher.end()));
            } else {
                result.add(line);
            }
        }
        return new LogContent(ImmutableList.copyOf(result), true, definitelyNoAnsiChars);
    }

    /**
     * Returns a copy of this log content with ANSI control characters interpreted to produce plain text.
     */
    public LogContent ansiCharsToPlainText() {
        if (definitelyNoAnsiChars) {
            return this;
        }
        try {
            AnsiConsole console = interpretAnsiChars();
            StringBuilder result = new StringBuilder();
            console.contents(token -> {
                if (token instanceof Text) {
                    result.append(((Text) token).getText());
                } else if (token instanceof NewLine) {
                    result.append("\n");
                }
            });
            return new LogContent(toLines(result.toString()), definitelyNoDebugPrefix, true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns a copy of this log content with ANSI control characters interpreted to produce plain text with text attributes included.
     */
    public LogContent ansiCharsToColorText() {
        if (definitelyNoAnsiChars) {
            return this;
        }
        try {
            AnsiConsole console = interpretAnsiChars();
            DiagnosticConsole diagnosticConsole = new DiagnosticConsole();
            for (int i = 0; i < console.getRows().size(); i++) {
                AnsiConsole.Row row = console.getRows().get(i);
                if (i > 0) {
                    diagnosticConsole.visit(NewLine.INSTANCE);
                }
                row.visit(diagnosticConsole);
            }
            return new LogContent(toLines(diagnosticConsole.toString()), definitelyNoDebugPrefix, true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private AnsiConsole interpretAnsiChars() throws IOException {
        AnsiConsole console = new AnsiConsole();
        AnsiParser parser = new AnsiParser();
        Writer writer = new OutputStreamWriter(parser.newParser("utf-8", console));
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                writer.write("\n");
            }
            writer.write(lines.get(i));
        }
        writer.flush();
        return console;
    }
}
