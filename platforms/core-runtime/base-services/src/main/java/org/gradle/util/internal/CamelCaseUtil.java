/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.util.internal;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility methods for converting camel case strings to other formats.
 * <p>
 * These methods were previously located in {@link TextUtil}, which prevented that type
 * from being usable in Test Workers.
 */
@NullMarked
public class CamelCaseUtil {
    private static final Pattern WORD_SEPARATOR = Pattern.compile("\\W+");
    private static final Pattern UPPER_CASE = Pattern.compile("(?=\\p{Upper})");

    public static String camelToKebabCase(String camelCase) {
        return Stream.of(UPPER_CASE.split(camelCase))
            .map(s -> s.toLowerCase(Locale.ROOT))
            .collect(Collectors.joining("-"));
    }

    /**
     * Converts an arbitrary string to a camel-case string which can be used in a Java identifier. Eg, with_underscores -&gt; withUnderscores
     */
    public static String toCamelCase(CharSequence string) {
        return toCamelCase(string, false);
    }

    public static String toLowerCamelCase(CharSequence string) {
        return toCamelCase(string, true);
    }

    private static String toCamelCase(CharSequence string, boolean lower) {
        if (string == null) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        Matcher matcher = WORD_SEPARATOR.matcher(string);
        int pos = 0;
        boolean first = true;
        while (matcher.find()) {
            String chunk = string.subSequence(pos, matcher.start()).toString();
            pos = matcher.end();
            if (chunk.isEmpty()) {
                continue;
            }
            if (lower && first) {
                chunk = StringUtils.uncapitalize(chunk);
                first = false;
            } else {
                chunk = StringUtils.capitalize(chunk);
            }
            builder.append(chunk);
        }
        String rest = string.subSequence(pos, string.length()).toString();
        if (lower && first) {
            rest = StringUtils.uncapitalize(rest);
        } else {
            rest = StringUtils.capitalize(rest);
        }
        builder.append(rest);
        return builder.toString();
    }
}
