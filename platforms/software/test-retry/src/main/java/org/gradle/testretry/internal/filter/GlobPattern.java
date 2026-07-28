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
package org.gradle.testretry.internal.filter;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class GlobPattern {

    public static final Pattern STAR_CHAR_PATTERN = Pattern.compile("\\*");
    public static final String MATCH_ANY = ".*?";

    private final String string;
    private final Pattern pattern;

    private GlobPattern(String string, Pattern pattern) {
        this.string = string;
        this.pattern = pattern;
    }

    static GlobPattern from(String string) {
        String patternString = STAR_CHAR_PATTERN.splitAsStream(string)
            .map(Pattern::quote)
            .collect(Collectors.joining(MATCH_ANY));

        if (string.endsWith("*")) {
            patternString = patternString + MATCH_ANY;
        }

        Pattern pattern = Pattern.compile(patternString);

        return new GlobPattern(patternString, pattern);
    }

    boolean matches(String test) {
        return pattern.matcher(test).matches();
    }

    @Override
    public String toString() {
        return string;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        GlobPattern that = (GlobPattern) o;

        return string.equals(that.string);
    }

    @Override
    public int hashCode() {
        return string.hashCode();
    }
}
