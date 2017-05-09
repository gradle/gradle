/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegExpPatternStep implements PatternStep {
    private static final String ESCAPE_CHARS = "\\[]^-&.{}()$+|<=!";

    private final Pattern pattern;

    public RegExpPatternStep(String pattern, boolean caseSensitive) {
        this.pattern = Pattern.compile(getRegExPattern(pattern), caseSensitive?0:Pattern.CASE_INSENSITIVE);
    }

    @Override
    public String toString() {
        return "{regexp: " + pattern + "}";
    }

    protected static String getRegExPattern(String pattern) {
        StringBuilder result = new StringBuilder();
        for (int i=0; i<pattern.length(); i++) {
            char next = pattern.charAt(i);
            if (next == '*') {
                result.append(".*");
            } else if (next == '?') {
                result.append(".");
            } else if (ESCAPE_CHARS.indexOf(next) >= 0) {
                result.append('\\');
                result.append(next);
            } else {
                result.append(next);
            }
        }
        return result.toString();
    }

    public boolean matches(String testString) {
        Matcher matcher = pattern.matcher(testString);
        return matcher.matches();
    }

}
