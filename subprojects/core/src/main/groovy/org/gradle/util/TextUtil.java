/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.util;

import org.apache.commons.lang.StringEscapeUtils;
import org.gradle.internal.SystemProperties;

import java.io.File;
import java.util.regex.Pattern;

public class TextUtil {
    private static final Pattern WHITESPACE = Pattern.compile("\\s*");

    /**
     * Returns the line separator for Windows.
     */
    public static String getWindowsLineSeparator() {
        return "\r\n";
    }

    /**
     * Returns the line separator for Unix.
     */
    public static String getUnixLineSeparator() {
        return "\n";
    }

    /**
     * Returns the line separator for this platform.
     */
    public static String getPlatformLineSeparator() {
        return SystemProperties.getLineSeparator();
    }

    /**
     * Converts all line separators in the specified string to the specified line separator.
     */
    public static String convertLineSeparators(String str, String sep) {
        return str == null ? null : str.replaceAll("\r\n|\r|\n", sep);
    }

    /**
     * Converts all line separators in the specified string to the platform's line separator.
     */
    public static String toPlatformLineSeparators(String str) {
        return str == null ? null : convertLineSeparators(str, getPlatformLineSeparator());
    }

    /**
     * Converts all line separators in the specified string to a single new line character.
     */
    public static String normaliseLineSeparators(String str) {
        return str == null ? null : convertLineSeparators(str, "\n");
    }

    /**
     * Converts all native file separators in the specified string to '/'.
     */
    public static String normaliseFileSeparators(String path) {
        return path.replaceAll(Pattern.quote(File.separator), "/");
    }

    /**
     * Escapes the toString() representation of {@code obj} for use in a literal string.
     * This is useful for interpolating variables into script strings, as well as in other situations.
     */
    public static String escapeString(Object obj) {
        return obj == null ? null : StringEscapeUtils.escapeJava(obj.toString());
    }

    /**
     * Tells whether the specified string contains any whitespace characters.
     */
    public static boolean containsWhitespace(String str) {
        for (int i = 0; i < str.length(); i++) {
            if (Character.isWhitespace(str.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Indents every line of {@code text} by {@code indent}. Empty lines
     * and lines that only contain whitespace are not indented.
     */
    public static String indent(String text, String indent) {
        StringBuilder builder = new StringBuilder();
        String[] lines = text.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!WHITESPACE.matcher(line).matches()) {
                builder.append(indent);
            }
            builder.append(line);
            if (i < lines.length - 1) {
                builder.append('\n');
            }
        }

        return builder.toString();
    }
}