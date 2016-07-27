/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.performance.fixture;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class GCEventParser {

    private final Pattern pattern;
    private final Pattern ignorePattern;

    GCEventParser(char decimalSeparator) {
        pattern = Pattern.compile(String.format("(.+): \\[(?:(?:Full )?GC(?: ?(?:[^\\s]+|\\(.+?\\)))?) (?:\\d+\\%s\\d+: )?(?:--)?\\[.*\\] (\\d+)K->(\\d+)K\\((\\d+)K\\)", decimalSeparator));
        ignorePattern = Pattern.compile(String.format("Java HotSpot.+|Memory:.+|/proc.+|CommandLine flags:.+|\\s*\\[Times: .+\\]\\s*"));
    }

    GCEvent parseLine(String line) {
        if (line.trim().isEmpty()) {
            return GCEvent.IGNORED;
        }

        Matcher matcher = pattern.matcher(line);
        if (!matcher.lookingAt()) {
            if (ignorePattern.matcher(line).matches()) {
                //I see this kind of events on windows. Let's see if this approach helps resolving them.
                return GCEvent.IGNORED;
            } else {
                throw new IllegalArgumentException("Unrecognized garbage collection event found in garbage collection log: " + line);
            }
        }

        DateTime timestamp = DateTime.parse(matcher.group(1));
        // Some JVMs generate an incorrect timezone offset in the timestamps. Discard timezone and use the local timezone instead
        timestamp = timestamp.toLocalDateTime().toDateTime(DateTimeZone.getDefault());
        long start = Long.parseLong(matcher.group(2));
        long end = Long.parseLong(matcher.group(3));
        long committed = Long.parseLong(matcher.group(4));

        return new GCEvent(start, end, committed, timestamp);
    }

    static class GCEvent {
        final long start;
        final long end;
        final long committed;
        final DateTime timestamp;
        final static GCEvent IGNORED = new GCEvent(-1, -1, -1, null);

        GCEvent(long start, long end, long committed, DateTime timestamp) {
            this.start = start;
            this.end = end;
            this.committed = committed;
            this.timestamp = timestamp;
        }
    }
}
