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
import net.rubygrapefruit.ansi.AnsiParser;
import net.rubygrapefruit.ansi.console.AnsiConsole;
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
    private final static Pattern DEBUG_PREFIX = Pattern.compile("\\d{2}:\\d{2}:\\d{2}\\.\\d{3} \\[\\w+] \\[.+?] ");
    private final static Pattern JAVA_ILLEGAL_ACCESS_WARNING_PATTERN = Pattern.compile("(?ms)WARNING: An illegal reflective access operation has occurred$.+?"
        + "^WARNING: All illegal access operations will be denied in a future release\r?\n");

    private final ImmutableList<String> lines;
    private final boolean definitelyNoDebugPrefix;
    private final boolean definitelyNoAnsiChars;
    private final LogContent rawContent;

    private LogContent(ImmutableList<String> lines, boolean definitelyNoDebugPrefix, boolean definitelyNoAnsiChars, LogContent rawContent) {
        this.lines = lines;
        this.rawContent = rawContent == null ? this : rawContent;
        this.definitelyNoDebugPrefix = definitelyNoDebugPrefix || lines.isEmpty();
        this.definitelyNoAnsiChars = definitelyNoAnsiChars || lines.isEmpty();
    }

    /**
     * Creates a new instance, from raw characters.
     */
    public static LogContent of(String chars) {
        LogContent raw = new LogContent(toLines(chars), false, false, null);
        return new LogContent(toLines(stripJavaIllegalAccessWarnings(chars)), false, false, raw);
    }

    private static ImmutableList<String> toLines(String chars) {
        List<String> lines = new ArrayList<String>();
        int pos = 0;
        while (pos < chars.length()) {
            int next = chars.indexOf('\n', pos);
            if (next < 0) {
                lines.add(chars.substring(pos));
                pos = chars.length();
                continue;
            }
            if (next > pos && chars.charAt(next - 1) == '\r') {
                lines.add(chars.substring(pos, next - 1));
                pos = next + 1;
            } else {
                lines.add(chars.substring(pos, next));
                pos = next + 1;
            }
            if (pos == chars.length()) {
                // trailing EOL
                lines.add("");
            }
        }
        return ImmutableList.copyOf(lines);
    }

    /**
     * Creates a new instance from a sequence of lines (without the line separators).
     */
    public static LogContent of(List<String> lines) {
        return new LogContent(ImmutableList.copyOf(lines), false, false,null);
    }

    public static LogContent empty() {
        return new LogContent(ImmutableList.<String>of(), true, true, null);
    }

    /**
     * Returns the original content that this content was built from, after transforms such as {@link #removeDebugPrefix()} or {@link #splitOnFirstMatchingLine(Pattern)}.
     */
    public LogContent getRawContent() {
        return rawContent;
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

    private LogContent lines(int startLine, int endLine) {
        if (rawContent != this) {
            throw new UnsupportedOperationException("not implemented");
        }
        return new LogContent(lines.subList(startLine, endLine), definitelyNoDebugPrefix, definitelyNoAnsiChars, null);
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
     * @return a pair containing (content-before-matching-line, content-from-matching-line)
     */
    public @Nullable
    Pair<LogContent, LogContent> splitOnFirstMatchingLine(Pattern pattern) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (pattern.matcher(line).matches()) {
                LogContent before = new LogContent(lines.subList(0, i), definitelyNoDebugPrefix, definitelyNoAnsiChars, rawContent);
                LogContent after = new LogContent(lines.subList(i, lines.size()), definitelyNoDebugPrefix, definitelyNoAnsiChars, rawContent);
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
        return new LogContent(lines.subList(i, lines.size()), definitelyNoDebugPrefix, definitelyNoAnsiChars, rawContent);
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
        return new LogContent(ImmutableList.copyOf(result), true, definitelyNoAnsiChars, rawContent);
    }

    /**
     * Returns a copy of this log content with ANSI control characters removed.
     */
    public LogContent removeAnsiChars() {
        if (definitelyNoAnsiChars) {
            return this;
        }
        try {
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
            StringBuilder result = new StringBuilder();
            console.contents(token -> {
                if (token instanceof Text) {
                    result.append(((Text) token).getText());
                } else if (token instanceof NewLine) {
                    result.append("\n");
                }
            });
            return new LogContent(toLines(result.toString()), definitelyNoDebugPrefix, true, rawContent);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String stripJavaIllegalAccessWarnings(String result) {
        return JAVA_ILLEGAL_ACCESS_WARNING_PATTERN.matcher(result).replaceAll("");
    }
}
