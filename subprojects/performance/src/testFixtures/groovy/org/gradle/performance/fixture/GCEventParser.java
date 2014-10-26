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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class GCEventParser {

    private final Pattern pattern;

    GCEventParser(char decimalSeparator) {
        pattern = Pattern.compile(String.format("\\d+\\%s\\d+: \\[(?:(?:Full GC(?: [^\\s]+)?)|GC) (\\d+\\%s\\d+: )?\\[.*\\] (\\d+)K->(\\d+)K\\((\\d+)K\\)", decimalSeparator, decimalSeparator));
    }

    GCEvent parseLine(String line) {
        Matcher matcher = pattern.matcher(line);
        if (!matcher.lookingAt()) {
            throw new IllegalArgumentException("Unrecognized garbage collection event found in garbage collection log: " + line);
        }

        long start = Long.parseLong(matcher.group(2));
        long end = Long.parseLong(matcher.group(3));
        long committed = Long.parseLong(matcher.group(4));

        return new GCEvent(start, end, committed);
    }

    static class GCEvent {
        final long start;
        final long end;
        final long committed;

        GCEvent(long start, long end, long committed) {
            this.start = start;
            this.end = end;
            this.committed = committed;
        }
    }
}
