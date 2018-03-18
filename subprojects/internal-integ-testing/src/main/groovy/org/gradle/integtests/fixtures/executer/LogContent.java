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
import org.gradle.api.Action;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.Pair;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class LogContent {
    private static final Pattern DEBUG_PREFIX = Pattern.compile("\\d{2}:\\d{2}:\\d{2}\\.\\d{3} \\[\\w+] \\[.+] ");
    private final ImmutableList<String> lines;
    private final boolean definitelyNoDebugPrefix;

    private LogContent(ImmutableList<String> lines, boolean definitelyNoDebugPrefix) {
        this.lines = lines;
        this.definitelyNoDebugPrefix = definitelyNoDebugPrefix || lines.isEmpty();
    }

    /**
     * Creates a new instance, from raw characters.
     */
    public static LogContent of(String chars) {
        try {
            List<String> lines = new ArrayList<String>();
            BufferedReader reader = new BufferedReader(new StringReader(chars));
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            return new LogContent(ImmutableList.copyOf(lines), false);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static LogContent empty() {
        return new LogContent(ImmutableList.<String>of(), true);
    }

    /**
     * Returns this content formatted with new line chars to separate lines.
     */
    public String withNormalizedEol() {
        if (lines.isEmpty()) {
            return "";
        }
        return Joiner.on('\n').join(lines);
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
    Pair<LogContent, LogContent> findFirstLine(Pattern pattern) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (pattern.matcher(line).matches()) {
                LogContent before = new LogContent(lines.subList(0, i), definitelyNoDebugPrefix);
                LogContent after = new LogContent(lines.subList(i, lines.size()), definitelyNoDebugPrefix);
                return Pair.of(before, after);
            }
        }
        return null;
    }

    public int countLines(Pattern pattern) {
        int count = 0;
        for (String line : lines) {
            if (pattern.matcher(line).matches()) {
                count++;
            }
        }
        return count;
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
        return new LogContent(ImmutableList.copyOf(result), true);
    }
}
