/*
 * Copyright 2021 the original author or authors.
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
import org.apache.commons.lang3.Strings;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.UncheckedException;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility methods for working with text.
 * <p>
 * To keep this class usable from Workers, do <strong>NOT</strong> add dependencies on Guava, which
 * we don't want to make available at runtime in TestWorkers.
 */
public class TextUtil {
    private static final Pattern WHITESPACE = Pattern.compile("\\s*");
    private static final Pattern LINE_SEPARATORS = Pattern.compile("\r\n|\r|\n");
    private static final Pattern NON_UNIX_LINE_SEPARATORS = Pattern.compile("\r\n|\r");
    private static final Pattern WORD_SEPARATOR = Pattern.compile("\\W+");
    private static final Pattern UPPER_CASE = Pattern.compile("(?=\\p{Upper})");

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
        return SystemProperties.getInstance().getLineSeparator();
    }

    /**
     * Converts all line separators in the specified string to the specified line separator.
     */
    @Nullable
    public static String convertLineSeparators(@Nullable String str, String sep) {
        return str == null ? null : replaceLineSeparatorsOf(str, sep);
    }

    /**
     * Converts all line separators in the specified non-null string to the Unix line separator {@code \n}.
     */
    public static String convertLineSeparatorsToUnix(String str) {
        return replaceAll(NON_UNIX_LINE_SEPARATORS, str, "\n");
    }

    /**
     * Converts all line separators in the specified non-null string to the specified line separator.
     */
    public static String replaceLineSeparatorsOf(String string, String bySeparator) {
        if (isMultiLeLine(string)) {
            return replaceAll(LINE_SEPARATORS, string, bySeparator);
        } else {
            return string;
        }
    }

    private static boolean isMultiLeLine(String string) {
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            if (c == '\n' || c == '\r') {
                return true;
            }
        }
        return false;
    }

    private static String replaceAll(Pattern pattern, CharSequence inString, String byString) {
        return pattern.matcher(inString).replaceAll(byString);
    }

    /**
     * Converts all line separators in the specified string to the platform's line separator.
     */
    @Contract("null -> null; !null -> !null")
    public static @Nullable String toPlatformLineSeparators(@Nullable String str) {
        return str == null ? null : replaceLineSeparatorsOf(str, getPlatformLineSeparator());
    }

    /**
     * Converts all line separators in the specified nullable string to a single new line character ({@code \n}).
     *
     * @return null if the given string is null
     */
    @Contract("null -> null; !null -> !null")
    @Nullable
    public static String normaliseLineSeparators(@Nullable String str) {
        return str == null ? null : convertLineSeparatorsToUnix(str);
    }

    /**
     * Converts all native file separators in the specified string to '/'.
     */
    public static String normaliseFileSeparators(String path) {
        return path.replace(File.separatorChar, '/');
    }

    /**
     * Checks if a String is whitespace, empty ("") or null.
     * <p>
     * This function was copied from Apache Commons-Lang to restore TAPI compatibility
     * with Java 6 on old Gradle versions because StringUtils require SQLException which
     * is absent from Java 6.
     */
    public static boolean isBlank(@Nullable String str) {
        int strLen;
        if (str == null || (strLen = str.length()) == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Capitalizes a String changing the first letter to title case as
     * per {@link Character#toTitleCase(char)}. No other letters are changed.
     * <p>
     * This function was copied from Apache Commons-Lang to restore TAPI compatibility
     * with Java 6 on old Gradle versions because StringUtils require SQLException which
     * is absent from Java 6.
     */
    @Contract("null -> null; !null -> !null")
    public static @Nullable String capitalize(@Nullable String str) {
        if (str == null || str.length() == 0) {
            return str;
        }
        return Character.toTitleCase(str.charAt(0)) + str.substring(1);
    }

    /**
     * Escapes the toString() representation of {@code obj} for use in a literal string.
     * This is useful for interpolating variables into script strings, as well as in other situations.
     */
    @Contract("null -> null; !null -> !null")
    public static @Nullable String escapeString(@Nullable Object obj) {
        return obj == null ? null : escapeJavaStyleString(obj.toString(), false, false);
    }

    /**
     * This function was copied from Apache Commons-Lang to restore TAPI compatibility
     * with Java 6 on old Gradle versions because StringUtils require SQLException which
     * is absent from Java 6.
     */
    @Contract("null, _, _ -> null; !null, _, _ -> !null")
    private static @Nullable String escapeJavaStyleString(@Nullable String str, boolean escapeSingleQuotes, boolean escapeForwardSlash) {
        if (str == null) {
            return null;
        }
        try {
            StringWriter writer = new StringWriter(str.length() * 2);
            escapeJavaStyleString(writer, str, escapeSingleQuotes, escapeForwardSlash);
            return writer.toString();
        } catch (IOException ioe) {
            // this should never ever happen while writing to a StringWriter
            throw UncheckedException.throwAsUncheckedException(ioe);
        }
    }


    private static void escapeJavaStyleString(
        Writer out, String str, boolean escapeSingleQuote,
        boolean escapeForwardSlash
    ) throws IOException {
        if (out == null) {
            throw new IllegalArgumentException("The Writer must not be null");
        }
        if (str == null) {
            return;
        }
        int sz;
        sz = str.length();
        for (int i = 0; i < sz; i++) {
            char ch = str.charAt(i);

            // handle unicode
            if (ch > 0xfff) {
                out.write("\\u" + hex(ch));
            } else if (ch > 0xff) {
                out.write("\\u0" + hex(ch));
            } else if (ch > 0x7f) {
                out.write("\\u00" + hex(ch));
            } else if (ch < 32) {
                switch (ch) {
                    case '\b':
                        out.write('\\');
                        out.write('b');
                        break;
                    case '\n':
                        out.write('\\');
                        out.write('n');
                        break;
                    case '\t':
                        out.write('\\');
                        out.write('t');
                        break;
                    case '\f':
                        out.write('\\');
                        out.write('f');
                        break;
                    case '\r':
                        out.write('\\');
                        out.write('r');
                        break;
                    default:
                        if (ch > 0xf) {
                            out.write("\\u00" + hex(ch));
                        } else {
                            out.write("\\u000" + hex(ch));
                        }
                        break;
                }
            } else {
                switch (ch) {
                    case '\'':
                        if (escapeSingleQuote) {
                            out.write('\\');
                        }
                        out.write('\'');
                        break;
                    case '"':
                        out.write('\\');
                        out.write('"');
                        break;
                    case '\\':
                        out.write('\\');
                        out.write('\\');
                        break;
                    case '/':
                        if (escapeForwardSlash) {
                            out.write('\\');
                        }
                        out.write('/');
                        break;
                    default:
                        out.write(ch);
                        break;
                }
            }
        }
    }

    private static String hex(char ch) {
        return Integer.toHexString(ch).toUpperCase(Locale.ENGLISH);
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

    public static String shorterOf(String s1, String s2) {
        if (s2.length() >= s1.length()) {
            return s1;
        } else {
            return s2;
        }
    }

    /**
     * Same behavior as Groovy minus operator between Strings
     *
     * @param originalString original string
     * @param removeString string to remove
     * @return string with removeString removed or the original string if it did not contain removeString
     */
    public static String minus(String originalString, String removeString) {
        String s = originalString.toString();
        int index = s.indexOf(removeString);
        if (index == -1) {
            return s;
        }
        int end = index + removeString.length();
        if (s.length() > end) {
            return s.substring(0, index) + s.substring(end);
        }
        return s.substring(0, index);
    }

    public static String normaliseFileAndLineSeparators(String in) {
        return normaliseLineSeparators(normaliseFileSeparators(in));
    }

    /**
     * This method returns the plural ending for an english word for trivial cases depending on the number of elements a list has.
     *
     * @param collection which size is used to determine the plural ending
     * @return "s" if the collection has more than one element, an empty string otherwise
     */
    public static String getPluralEnding(Collection<?> collection) {
        return collection.size() > 1 ? "s" : "";
    }

    public static String endLineWithDot(String txt) {
        if (txt.endsWith(".") || txt.endsWith("\n")) {
            return txt;
        }
        return txt + ".";
    }

    // TODO: This should probably also live in GUtil to be with other camel/kebab case methods
    public static String screamingSnakeToKebabCase(String text) {
        return Strings.CS.replace(text.toLowerCase(Locale.ENGLISH), "_", "-");
    }

    /**
     * Converts a camel case string to kebab case. E.g. fooBar -&gt; foo-bar
     */
    public static String camelToKebabCase(String camelCase) {
        return Stream.of(UPPER_CASE.split(camelCase))
            .map(s -> s.toLowerCase(Locale.ROOT))
            .collect(Collectors.joining("-"));
    }

    /**
     * Converts an arbitrary string to a camel-case string which can be used in a Java identifier. Eg, with_underscores -&gt; withUnderscores
     */
    @Contract("null -> null; !null -> !null")
    public static @Nullable String toCamelCase(@Nullable CharSequence string) {
        return toCamelCase(string, false);
    }

    @Contract("null -> null; !null -> !null")
    public static @Nullable String toLowerCamelCase(@Nullable CharSequence string) {
        return toCamelCase(string, true);
    }

    @Contract("null, _ -> null; !null, _ -> !null")
    private static @Nullable String toCamelCase(@Nullable CharSequence string, boolean lower) {
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

    public static String removeTrailing(String originalString, String suffix) {
        if (originalString.endsWith(suffix)) {
            return originalString.substring(0, originalString.length() - suffix.length());
        }
        return originalString;
    }
}
